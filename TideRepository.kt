package com.ulsan.disasteralert.data

import android.content.Context
import com.ulsan.disasteralert.network.ApiClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * 조위 조회 및 분석.
 *
 * 설계 핵심 — "만조인가 아닌가"의 이분법으로는 부족하다.
 *   1) 만조 시각과의 근접도 (전후 몇 분인가)
 *   2) 만조 조위의 절대 높이 (대조기 사리 만조는 소조기보다 훨씬 높다)
 *   3) 배수 차단 임계 초과 여부
 * 이 세 가지를 함께 봐야 실제 배수 불량 상황을 잡아낼 수 있다.
 */
object TideRepository {

    private const val PREFS = "tide_cache"
    private const val KEY_STATION_CODE = "ulsan_station_code"
    private const val KEY_PREDICTION_DATE = "cached_prediction_date"
    private const val KEY_PREDICTION_DATA = "cached_prediction_data"

    /** 만조 전후 이 시간(분) 이내면 "겹친다"고 판단 */
    private const val HIGH_TIDE_WINDOW_MINUTES = 90

    /**
     * 울산 조위관측소.
     *
     * 코드 DT_0020은 조석예보 활용가이드의 예보지점 목록에서 확인한 값입니다.
     * (참고: 인천 DT_0001, 부산 DT_0005, 통영 DT_0014, 울산 DT_0020)
     * drainageBlockLevelCm(배수 차단 임계 조위)은 반드시 검증이 필요한 값이다 —
     * 울산시 하수도정비기본계획의 하구 배수문 표고에서 산출해야 정확하다.
     */
    private val DEFAULT_STATION = TideStation(
        code = "DT_0020",              // 울산 — 활용가이드 예보지점 목록에서 확인됨
        name = "울산",
        latitude = 35.5017,
        longitude = 129.3872,
        drainageBlockLevelCm = 180     // ⚠️ 임시값 — 실제 배수문 표고로 교체 필수
    )

    /** 대조기 판정: 만조-간조 조위차가 이 값 이상이면 사리로 본다 (cm) */
    private const val SPRING_TIDE_RANGE_CM = 130

    /**
     * 폭풍해일 여유값 (cm).
     *
     * 조석예보는 천문조입니다. 태풍이 접근하면 기압 저하(1hPa당 약 1cm)와
     * 취송류로 실제 해수면이 예보보다 높아집니다. 차바 당시에도 그랬습니다.
     * 태풍 영향권일 때는 이 값을 더해 안전측으로 판단합니다.
     */
    const val STORM_SURGE_MARGIN_CM = 50

    /**
     * @param stormSurgeExpected 태풍 영향권이면 true — 폭풍해일 여유를 더해 안전측 판단
     */
    suspend fun getTideStatus(
        context: Context,
        serviceKey: String,
        stormSurgeExpected: Boolean = false
    ): TideStatus? {
        if (serviceKey.isBlank()) return null

        val station = loadStation(context)
        val now = Calendar.getInstance()

        val extremes = runCatching { fetchExtremes(context, serviceKey, station, now) }
            .getOrNull() ?: return null
        if (extremes.isEmpty()) return null

        // 조석예보 API에는 실시간 관측값이 없습니다.
        // 대신 인접한 고조·저조 사이를 보간해 현재 조위를 추정합니다.
        // 조석은 천문학적으로 계산되는 값이라 보간 오차가 크지 않습니다.
        val estimatedLevel = interpolateLevel(extremes, now.timeInMillis)

        // 태풍 영향권이면 폭풍해일 여유를 더해 판단합니다.
        // 예보보다 실제가 높을 수 있으므로 낮게 잡는 쪽이 더 위험합니다.
        val adjusted = estimatedLevel?.let {
            if (stormSurgeExpected) it + STORM_SURGE_MARGIN_CM else it
        }

        return analyze(station, extremes, adjusted, now)
    }

    /**
     * 조위 상황을 분석해 위험도 보정 계수를 산출한다.
     *
     * 계수 설계:
     *   - 만조 근접 + 대조기 + 배수차단 임계 초과 → 최대 1.5배
     *   - 만조 근접만 → 1.25배
     *   - 간조 시간대 → 1.0배 (가중 없음)
     * 차바 사례가 첫 번째 경우에 해당한다.
     */
    fun analyze(
        station: TideStation,
        extremes: List<TideExtreme>,
        currentLevelCm: Int?,
        now: Calendar
    ): TideStatus {
        val nowMillis = now.timeInMillis
        val highTides = extremes.filter { it.isHighTide }

        val nextHigh = highTides.filter { it.timeMillis > nowMillis }.minByOrNull { it.timeMillis }
        val prevHigh = highTides.filter { it.timeMillis <= nowMillis }.maxByOrNull { it.timeMillis }

        val minutesToNext = nextHigh?.let { ((it.timeMillis - nowMillis) / 60000).toInt() }
        val minutesSincePrev = prevHigh?.let { ((nowMillis - it.timeMillis) / 60000).toInt() }

        val isNear = (minutesToNext != null && minutesToNext <= HIGH_TIDE_WINDOW_MINUTES) ||
                (minutesSincePrev != null && minutesSincePrev <= HIGH_TIDE_WINDOW_MINUTES)

        // 대조기 판정: 오늘의 최대 조위차
        val maxLevel = extremes.maxOfOrNull { it.levelCm } ?: 0
        val minLevel = extremes.minOfOrNull { it.levelCm } ?: 0
        val isSpring = (maxLevel - minLevel) >= SPRING_TIDE_RANGE_CM

        // 배수 차단 판정 — 실측값 우선, 없으면 인접 만조 예보값으로 추정
        val effectiveLevel = currentLevelCm
            ?: if (isNear) (nextHigh ?: prevHigh)?.levelCm else null
        val blocked = effectiveLevel != null && effectiveLevel >= station.drainageBlockLevelCm

        var multiplier = 1.0
        if (isNear) multiplier += 0.25
        if (isSpring && isNear) multiplier += 0.10
        if (blocked) multiplier += 0.15
        multiplier = multiplier.coerceAtMost(1.5)

        val description = buildString {
            currentLevelCm?.let { append("현재 조위 ${it}cm") }
            nextHigh?.let {
                if (isNotEmpty()) append(" · ")
                val timeStr = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(it.timeMillis))
                append("다음 만조 $timeStr (${it.levelCm}cm")
                if (minutesToNext != null) append(", ${minutesToNext}분 후")
                append(")")
            }
            if (isSpring) {
                if (isNotEmpty()) appendLine()
                append("대조기(사리) — 만조 조위가 평소보다 높습니다")
            }
            if (blocked) {
                if (isNotEmpty()) appendLine()
                append("조위가 배수 차단 수준입니다. 하천·우수관 자연배수가 어렵습니다")
            } else if (isNear) {
                if (isNotEmpty()) appendLine()
                append("만조 시간대와 겹칩니다. 하구 저지대 배수 지연에 유의하세요")
            }
        }

        return TideStatus(
            station = station,
            currentLevelCm = currentLevelCm,
            nextHighTide = nextHigh,
            previousHighTide = prevHigh,
            minutesToNextHighTide = minutesToNext,
            isNearHighTide = isNear,
            isSpringTide = isSpring,
            drainageBlocked = blocked,
            tideMultiplier = multiplier,
            description = description
        )
    }

    /**
     * 인접 고조·저조 사이를 정현파로 보간해 현재 조위를 추정한다.
     *
     * 조석은 사인 곡선에 가깝게 변하므로, 두 극값 사이를
     * 코사인 보간하면 선형 보간보다 훨씬 실제에 가깝습니다.
     *
     * ⚠️ 한계: 이것은 천문조(예보)일 뿐입니다.
     *   태풍 내습 시에는 기압 저하와 취송류로 실제 해수면이 예보보다
     *   수십 cm 높아질 수 있습니다(폭풍해일). 차바 때도 그랬습니다.
     *   즉 이 추정값은 **실제보다 낮게 나올 수 있으며**, 태풍 상황에서는
     *   안전측 판단을 위해 여유를 두어야 합니다.
     */
    private fun interpolateLevel(extremes: List<TideExtreme>, nowMillis: Long): Int? {
        val sorted = extremes.sortedBy { it.timeMillis }
        val before = sorted.lastOrNull { it.timeMillis <= nowMillis } ?: return null
        val after = sorted.firstOrNull { it.timeMillis > nowMillis } ?: return null

        val span = (after.timeMillis - before.timeMillis).toDouble()
        if (span <= 0) return before.levelCm

        val progress = (nowMillis - before.timeMillis) / span
        // 코사인 보간: 극값 부근에서 완만하고 중간에서 가파름 — 실제 조석 곡선과 유사
        val factor = (1 - kotlin.math.cos(progress * Math.PI)) / 2
        return (before.levelCm + (after.levelCm - before.levelCm) * factor).toInt()
    }

    /**
     * 조석예보를 받아온다. 예보는 하루 단위로 고정된 값이라
     * 같은 날짜면 캐시를 재사용해 API 호출을 아낀다.
     */
    private suspend fun fetchExtremes(
        context: Context,
        serviceKey: String,
        station: TideStation,
        now: Calendar
    ): List<TideExtreme> {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(now.time)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (prefs.getString(KEY_PREDICTION_DATE, null) == dateStr) {
            prefs.getString(KEY_PREDICTION_DATA, null)?.let { cached ->
                return parseCache(cached)
            }
        }

        // 오늘 + 내일 조회 (자정 부근에서 "다음 만조"가 내일일 수 있음)
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowStr = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(tomorrow.time)

        val items = mutableListOf<TideExtreme>()
        listOf(dateStr, tomorrowStr).forEach { d ->
            runCatching {
                ApiClient.tideApi.getTidePrediction(serviceKey, station.code, d)
                    .response?.body?.items?.item.orEmpty()
                    .forEach { item -> item.toExtreme()?.let { items.add(it) } }
            }
        }

        if (items.isNotEmpty()) {
            prefs.edit()
                .putString(KEY_PREDICTION_DATE, dateStr)
                .putString(KEY_PREDICTION_DATA, serializeCache(items))
                .apply()
        }
        return items
    }

    private fun com.ulsan.disasteralert.network.TidePredictionItem.toExtreme(): TideExtreme? {
        val timeStr = effectiveTime ?: return null
        val level = effectiveLevel?.toDoubleOrNull()?.toInt() ?: return null
        // 표기 형식이 가이드마다 달라 여러 패턴을 시도합니다
        val millis = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
            "yyyyMMddHHmm", "yyyyMMddHHmmss"
        ).firstNotNullOfOrNull { p ->
            runCatching {
                SimpleDateFormat(p, Locale.KOREA).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Seoul")   // 조석은 KST
                }.parse(timeStr)?.time
            }.getOrNull()
        } ?: return null

        // 고조/저조 구분은 "고조"/"저조" 또는 코드값(H/L)으로 내려올 수 있습니다
        val code = effectiveCode ?: return null
        val isHigh = code.contains("고") || code.equals("H", true) || code.equals("고조", true)

        return TideExtreme(millis, level, isHigh)
    }

    // ── 관측소 코드 검증 ──

    /**
     * 조석예보 API에는 관측소 목록 조회가 없습니다.
     * 코드가 맞는지는 실제 조회 성공 여부로 판단합니다 —
     * 잘못된 코드면 데이터가 비어서 돌아옵니다.
     */
    suspend fun verifyStationCode(context: Context, serviceKey: String): Boolean {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        val ok = runCatching {
            ApiClient.tideApi.getTidePrediction(serviceKey, DEFAULT_STATION.code, dateStr)
                .response?.body?.items?.item.orEmpty().isNotEmpty()
        }.getOrDefault(false)

        if (ok) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STATION_CODE, DEFAULT_STATION.code).apply()
        }
        return ok
    }

    private fun loadStation(context: Context): TideStation {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATION_CODE, null)
        return if (saved != null) DEFAULT_STATION.copy(code = saved) else DEFAULT_STATION
    }

    /** 재현 검증에서 사용할 기본 관측소 (네트워크 없이 동작) */
    fun defaultStationForReplay(): TideStation = DEFAULT_STATION

    fun stationCodeVerified(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATION_CODE, null) != null

    // ── 캐시 직렬화 ──

    private fun serializeCache(items: List<TideExtreme>): String {
        val arr = org.json.JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("t", it.timeMillis); put("l", it.levelCm); put("h", it.isHighTide)
            })
        }
        return arr.toString()
    }

    private fun parseCache(json: String): List<TideExtreme> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TideExtreme(o.getLong("t"), o.getInt("l"), o.getBoolean("h"))
        }
    }

    // ── 배수 위험 판단 ──

    /**
     * 하천 수위와 조위를 결합해 배수 위험을 판단한다.
     *
     * 하구부에서 조위가 하천 수위에 근접하거나 넘어서면 물이 나갈 곳이 없어진다.
     * 차바 당시 울산 도심 침수가 정확히 이 패턴이었다.
     */
    fun assessBackwater(
        tide: TideStatus?,
        riverStatuses: List<RiverLevelStatus>
    ): BackwaterRisk {
        if (tide == null) return BackwaterRisk(BackwaterLevel.NONE, null, "")

        // 하구에 가장 가까운(하류) 관측소를 기준으로 판단
        val downstream = riverStatuses.find { it.station.name.contains("태화교") }
            ?: riverStatuses.maxByOrNull { it.station.latitude * -1 }

        val level = when {
            tide.drainageBlocked && (downstream?.stage?.score ?: 0) >= RiverStage.WARNING.score ->
                BackwaterLevel.REVERSE
            tide.drainageBlocked ->
                BackwaterLevel.BLOCKED
            tide.isNearHighTide && (downstream?.stage?.score ?: 0) >= RiverStage.ATTENTION.score ->
                BackwaterLevel.BLOCKED
            tide.isNearHighTide ->
                BackwaterLevel.REDUCED
            else -> BackwaterLevel.NONE
        }

        val message = when (level) {
            BackwaterLevel.REVERSE ->
                "만조와 하천 고수위가 겹쳐 역류 위험이 있습니다. " +
                "하구 저지대(${downstream?.station?.protectedAreas?.joinToString(", ") ?: "태화강 하류"}) " +
                "즉시 통제하고 배수펌프를 최대 가동하세요."
            BackwaterLevel.BLOCKED ->
                "조위가 높아 하천·우수관 자연배수가 차단된 상태입니다. " +
                "강우가 계속되면 저지대 수위가 급격히 오릅니다. 강제 배수 준비를 하세요."
            BackwaterLevel.REDUCED ->
                "만조 시간대로 배수 속도가 평소보다 느립니다. 저지대 상황을 주시하세요."
            BackwaterLevel.NONE -> ""
        }

        return BackwaterRisk(level, downstream?.station?.name, message)
    }
}
