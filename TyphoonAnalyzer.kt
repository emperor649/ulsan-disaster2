package com.ulsan.disasteralert.data

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * 울산 기준 태풍 접근 분석.
 *
 * 설계 원칙 세 가지.
 *
 * 1) **위험도 점수에 더하지 않는다.**
 *    실시간 위험도는 "지금 침수가 임박했나"(분 단위), 대비 단계는 "며칠 뒤 뭘 준비하나"(일 단위).
 *    시간 척도가 달라서 섞으면 비도 안 오는데 '위험'이 이틀 떠 있게 되고, 그러면 아무도 안 본다.
 *
 * 2) **거리보다 통과 방향이 중요하다.**
 *    북반구 태풍은 진행방향 오른쪽이 위험반원이다. 태풍이 울산 서쪽으로 지나가면
 *    같은 거리라도 바람과 강수가 훨씬 심하다. 차바가 이 경우였다.
 *
 * 3) **불확실성을 숨기지 않는다.**
 *    3일 전 진로 예보는 오차가 크다. "온다/안 온다"로 단정하면 빗나갔을 때 신뢰를 잃는다.
 *    예보원 반경을 그대로 노출하고, 신뢰도를 함께 표시한다.
 */
object TyphoonAnalyzer {

    /** 울산 중심 좌표 (시청 기준) */
    private const val ULSAN_LAT = 35.5384
    private const val ULSAN_LON = 129.3114

    /** 이 거리 안으로 들어오면 영향권으로 본다 (km) */
    private const val INFLUENCE_DISTANCE_KM = 400.0

    /** 만조 겹침 판정 창 (분) */
    private const val TIDE_OVERLAP_MINUTES = 90
    private const val TIDE_NEAR_MINUTES = 180

    fun analyze(typhoon: Typhoon, tideExtremes: List<TideExtreme>): TyphoonApproach? {
        val forecasts = typhoon.points.filter { it.isForecast || it.timeMillis >= System.currentTimeMillis() }
        if (forecasts.isEmpty()) return null

        // 울산에 가장 가까워지는 지점
        val closest = forecasts.minByOrNull { distanceKm(it.latitude, it.longitude) } ?: return null
        val distance = distanceKm(closest.latitude, closest.longitude)

        // 예보원 반경을 뺀 최소 가능 거리 — 불확실성의 하한
        val errorRadius = closest.forecastErrorRadiusKm ?: 0.0
        val minPossible = max(0.0, distance - errorRadius)

        // 영향권 밖이고, 예보원 반경을 감안해도 안 들어오면 분석 종료
        if (minPossible > INFLUENCE_DISTANCE_KM) return null

        val hoursToClosest = ((closest.timeMillis - System.currentTimeMillis()) / 3_600_000L).toInt()
        val side = determinePassageSide(forecasts, closest)

        val entersStrong = closest.strongWindRadiusKm?.let { minPossible <= it } ?: false
        val entersStorm = closest.stormRadiusKm?.let { minPossible <= it } ?: false

        val tideCoincidence = findTideCoincidence(closest.timeMillis, tideExtremes)
        val confidence = judgeConfidence(hoursToClosest, errorRadius)

        val level = determinePreparedness(
            distance, minPossible, hoursToClosest, side, entersStrong, entersStorm, tideCoincidence
        )

        return TyphoonApproach(
            typhoon = typhoon,
            closestPoint = closest,
            closestDistanceKm = distance,
            hoursToClosest = hoursToClosest,
            minPossibleDistanceKm = minPossible,
            passageSide = side,
            entersStrongWindRadius = entersStrong,
            entersStormRadius = entersStorm,
            tideCoincidence = tideCoincidence,
            preparednessLevel = level,
            confidence = confidence,
            headline = buildHeadline(typhoon, closest, distance, hoursToClosest, side, level),
            briefing = buildBriefing(
                typhoon, closest, distance, minPossible, errorRadius,
                hoursToClosest, side, entersStrong, entersStorm, tideCoincidence, confidence, level
            )
        )
    }

    /** 두 지점 사이 거리 (Haversine, km) */
    private fun distanceKm(lat: Double, lon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat - ULSAN_LAT)
        val dLon = Math.toRadians(lon - ULSAN_LON)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(ULSAN_LAT)) * cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /**
     * 통과 방향 판정.
     *
     * 최근접 지점의 경도가 울산보다 서쪽이면 울산은 태풍 진행방향 오른쪽 =
     * 위험반원에 놓인다. 다만 태풍이 북동진하는 경우가 많아 진행 방향도 함께 본다.
     */
    private fun determinePassageSide(
        forecasts: List<TyphoonPoint>,
        closest: TyphoonPoint
    ): PassageSide {
        val lonDiff = closest.longitude - ULSAN_LON

        // 최근접 지점이 울산과 거의 같은 경도면 직상 통과로 본다
        if (abs(lonDiff) < 0.35) return PassageSide.OVERHEAD

        // 최근접 전후 지점으로 진행 방향을 확인 — 남하하는 태풍은 반대가 된다
        val idx = forecasts.indexOf(closest)
        val next = forecasts.getOrNull(idx + 1)
        val movingNorth = next?.let { it.latitude > closest.latitude } ?: true

        return when {
            !movingNorth -> PassageSide.UNKNOWN   // 이례적 경로 — 단정하지 않는다
            lonDiff < 0 -> PassageSide.WEST       // 태풍이 울산 서쪽 → 울산이 위험반원
            else -> PassageSide.EAST              // 태풍이 울산 동쪽(바다) → 울산이 가항반원
        }
    }

    /**
     * 태풍 최근접 시각과 만조가 겹치는지 확인.
     *
     * 이 함수가 이 모듈의 핵심이다. 태풍 예보는 3일 전에 나오고 조석은 몇 달 뒤까지
     * 계산되므로, 차바형 복합재해 조건을 사흘 전에 미리 알 수 있다.
     */
    private fun findTideCoincidence(
        closestTimeMillis: Long,
        extremes: List<TideExtreme>
    ): TideCoincidence? {
        val highTides = extremes.filter { it.isHighTide }
        if (highTides.isEmpty()) return null

        val nearest = highTides.minByOrNull {
            abs(it.timeMillis - closestTimeMillis)
        } ?: return null

        val offsetMinutes = ((nearest.timeMillis - closestTimeMillis) / 60_000L).toInt()
        val absOffset = abs(offsetMinutes)

        if (absOffset > TIDE_NEAR_MINUTES) {
            return TideCoincidence(
                nearest.timeMillis, nearest.levelCm, offsetMinutes,
                isSpringTide = false, severity = CoincidenceSeverity.NONE
            )
        }

        // 대조기 판정 — 해당 날짜의 조위차로 본다
        val dayStart = nearest.timeMillis - 12 * 3_600_000L
        val dayEnd = nearest.timeMillis + 12 * 3_600_000L
        val sameDay = extremes.filter { it.timeMillis in dayStart..dayEnd }
        val range = (sameDay.maxOfOrNull { it.levelCm } ?: 0) - (sameDay.minOfOrNull { it.levelCm } ?: 0)
        val isSpring = range >= 130

        val severity = when {
            absOffset <= TIDE_OVERLAP_MINUTES && isSpring -> CoincidenceSeverity.CRITICAL
            absOffset <= TIDE_OVERLAP_MINUTES -> CoincidenceSeverity.OVERLAP
            else -> CoincidenceSeverity.NEAR
        }

        return TideCoincidence(nearest.timeMillis, nearest.levelCm, offsetMinutes, isSpring, severity)
    }

    private fun judgeConfidence(hoursToClosest: Int, errorRadiusKm: Double): ForecastConfidence = when {
        hoursToClosest <= 24 || errorRadiusKm <= 80 -> ForecastConfidence.HIGH
        hoursToClosest <= 48 || errorRadiusKm <= 180 -> ForecastConfidence.MEDIUM
        else -> ForecastConfidence.LOW
    }

    private fun determinePreparedness(
        distance: Double,
        minPossible: Double,
        hoursToClosest: Int,
        side: PassageSide,
        entersStrong: Boolean,
        entersStorm: Boolean,
        tide: TideCoincidence?
    ): PreparednessLevel {
        // 방향 가중치를 적용한 실효 거리 — 서쪽 통과면 더 가까운 것처럼 취급
        val effectiveDistance = distance / side.riskWeight

        var level = when {
            entersStorm -> PreparednessLevel.READY
            entersStrong -> PreparednessLevel.PREPARE
            effectiveDistance <= 200 -> PreparednessLevel.PREPARE
            effectiveDistance <= INFLUENCE_DISTANCE_KM -> PreparednessLevel.WATCH
            else -> PreparednessLevel.NONE
        }

        // 24시간 내 최근접이면 한 단계 격상
        if (hoursToClosest in 0..24 && level >= PreparednessLevel.PREPARE) {
            level = PreparednessLevel.IMMINENT
        }

        // 만조 완전 중첩은 그 자체로 격상 사유 — 차바의 교훈
        if (tide?.severity == CoincidenceSeverity.CRITICAL && level >= PreparednessLevel.WATCH) {
            level = maxOf(level, PreparednessLevel.READY)
        }

        return level
    }

    private fun buildHeadline(
        typhoon: Typhoon,
        closest: TyphoonPoint,
        distance: Double,
        hours: Int,
        side: PassageSide,
        level: PreparednessLevel
    ): String {
        val name = typhoon.koreanName ?: typhoon.name
        val timeStr = SimpleDateFormat("M/d HH시", Locale.KOREA).format(Date(closest.timeMillis))
        return "태풍 $name · $timeStr 최근접 ${distance.toInt()}km · ${level.displayName}"
    }

    /**
     * 실무자가 이 브리핑만 읽고 대비 계획을 짤 수 있도록 구성한다.
     */
    private fun buildBriefing(
        typhoon: Typhoon,
        closest: TyphoonPoint,
        distance: Double,
        minPossible: Double,
        errorRadius: Double,
        hours: Int,
        side: PassageSide,
        entersStrong: Boolean,
        entersStorm: Boolean,
        tide: TideCoincidence?,
        confidence: ForecastConfidence,
        level: PreparednessLevel
    ): List<String> {
        val lines = mutableListOf<String>()
        val fmt = SimpleDateFormat("M월 d일 HH시 mm분", Locale.KOREA)

        // 1) 최근접 정보
        lines.add(
            "최근접 예상: ${fmt.format(Date(closest.timeMillis))} (${hours}시간 후), 울산에서 ${distance.toInt()}km"
        )

        // 2) 불확실성 — 숨기지 않는다
        if (errorRadius > 0) {
            lines.add(
                "예보원 반경 ${errorRadius.toInt()}km. 진로에 따라 최소 ${minPossible.toInt()}km까지 접근할 수 있습니다. " +
                "신뢰도 ${confidence.displayName} — ${confidence.note}"
            )
        }

        // 3) 통과 방향 — 울산에서 가장 중요한 변수
        when (side) {
            PassageSide.WEST -> lines.add(
                "울산 서쪽으로 통과할 전망입니다. 울산이 위험반원에 들어가 " +
                "같은 거리라도 바람과 강수가 훨씬 강해집니다. 차바(2016)와 같은 경로 유형입니다."
            )
            PassageSide.OVERHEAD -> lines.add(
                "울산 직상 또는 극근접 통과 전망입니다. 최대 강도의 영향을 받습니다."
            )
            PassageSide.EAST -> lines.add(
                "울산 동쪽 해상으로 통과할 전망입니다. 울산은 가항반원에 들어 " +
                "같은 거리 대비 영향이 상대적으로 작습니다. 다만 진로가 서쪽으로 틀리면 조건이 반전됩니다."
            )
            PassageSide.UNKNOWN -> lines.add(
                "진로가 통상적이지 않아 위험반원 판정을 유보합니다. 기상청 발표를 직접 확인하세요."
            )
        }

        // 4) 반경 진입 여부
        if (entersStorm) {
            lines.add("폭풍반경(초속 25m 이상)에 진입합니다. 시설물 고정과 옥외 작업 중단이 필요합니다.")
        } else if (entersStrong) {
            lines.add("강풍반경(초속 15m 이상)에 진입합니다. 간판·비계 등 낙하물 점검이 필요합니다.")
        }

        // 5) 만조 겹침 — 이 모듈의 핵심 산출물
        tide?.let { t ->
            val tideTime = fmt.format(Date(t.highTideTimeMillis))
            when (t.severity) {
                CoincidenceSeverity.CRITICAL -> lines.add(
                    "⚠ 최근접 시각과 만조가 완전히 겹칩니다. 만조 $tideTime, 조위 ${t.highTideLevelCm}cm (대조기). " +
                    "빗물이 바다로 빠지지 못해 태화강·동천 하구가 역류할 수 있습니다. " +
                    "차바 당시 울산 피해가 커진 것이 정확히 이 조건이었습니다. " +
                    "만조 전에 사전 배수를 마치고, 배수펌프와 이동식 펌프를 미리 배치하세요."
                )
                CoincidenceSeverity.OVERLAP -> lines.add(
                    "최근접 시각과 만조가 겹칩니다. 만조 $tideTime, 조위 ${t.highTideLevelCm}cm. " +
                    "강우 최성기에 자연배수가 제한됩니다. 하구 저지대 사전 배수와 펌프 배치를 계획하세요."
                )
                CoincidenceSeverity.NEAR -> {
                    val dir = if (t.offsetMinutes > 0) "이후" else "이전"
                    lines.add(
                        "만조는 최근접 ${abs(t.offsetMinutes) / 60}시간 $dir ($tideTime, ${t.highTideLevelCm}cm)입니다. " +
                        "완전히 겹치지는 않으나 강우가 길어지면 만조 시간대에 걸칠 수 있습니다."
                    )
                }
                CoincidenceSeverity.NONE -> lines.add(
                    "최근접 시각과 만조는 겹치지 않습니다. 배수 여건은 상대적으로 양호합니다."
                )
            }
        } ?: lines.add("조위 정보를 가져오지 못해 만조 겹침 판정을 하지 못했습니다.")

        // 6) 단계별 조치
        when (level) {
            PreparednessLevel.IMMINENT -> lines.add(
                "24시간 내 최근접입니다. 상습침수지 사전 통제, 인력·장비 전진 배치, " +
                "취약계층 사전 연락을 지금 시행하세요."
            )
            PreparednessLevel.READY -> lines.add(
                "직접 영향이 예상됩니다. 비상근무 편성, 배수시설 점검, 하천변 시설물 철거를 시작하세요."
            )
            PreparednessLevel.PREPARE -> lines.add(
                "영향권 진입이 유력합니다. 배수로 정비, 공사장 안전조치, 자재 결박 등 사전 조치를 시작하세요."
            )
            PreparednessLevel.WATCH -> lines.add(
                "아직 진로 변동 가능성이 큽니다. 12시간 간격으로 예보를 확인하고 대비 계획만 점검해 두세요."
            )
            PreparednessLevel.NONE -> {}
        }

        return lines
    }

    /**
     * 여러 태풍이 동시에 있을 때 가장 위협적인 것을 고른다.
     * (2019년처럼 동시 발생이 드물지 않다)
     */
    fun mostThreatening(approaches: List<TyphoonApproach>): TyphoonApproach? =
        approaches.maxWithOrNull(
            compareBy<TyphoonApproach> { it.preparednessLevel.ordinal }
                .thenBy { -it.closestDistanceKm }
        )
}
