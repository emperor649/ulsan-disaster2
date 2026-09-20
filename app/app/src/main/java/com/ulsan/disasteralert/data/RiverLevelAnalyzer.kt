package com.ulsan.disasteralert.data

import kotlin.math.abs

/**
 * 하천 수위 분석.
 *
 * 설계 의도: 현재 수위(절대값)만 보면 대응이 늦는다.
 * 차바 당시 울산은 강우 시작 2시간 만에 하천이 범람했고, 주민 안내가 늦었다는 지적을 받았다.
 * 따라서 이 분석기는 세 가지를 함께 낸다.
 *   1) 현재 어느 단계인가 (관심/주의보/경보/계획홍수위)
 *   2) 얼마나 빠르게 오르고 있나 (m/h)
 *   3) 다음 단계까지 몇 분 남았나 → 이것이 실제 대피 리드타임
 */
object RiverLevelAnalyzer {

    /** 상승률 계산에 사용할 최근 관측 구간 (분) */
    private const val RISE_RATE_WINDOW_MINUTES = 60

    /** 이 속도 이상으로 오르면 "급상승"으로 분류 */
    const val RAPID_RISE_THRESHOLD_M_PER_HOUR = 0.5

    fun analyze(
        station: RiverStation,
        readings: List<WaterLevelReading>
    ): RiverLevelStatus? {
        val sorted = readings.sortedBy { it.observedAtMillis }
        val latest = sorted.lastOrNull() ?: return null
        val current = latest.waterLevelM

        val stage = when {
            current >= station.dangerLevel -> RiverStage.DANGER
            current >= station.alertLevel -> RiverStage.ALERT
            current >= station.warningLevel -> RiverStage.WARNING
            current >= station.attentionLevel -> RiverStage.ATTENTION
            else -> RiverStage.NORMAL
        }

        val riseRate = calculateRiseRate(sorted)
        val nextThreshold = nextThresholdFor(station, stage)
        val minutesToNext = if (riseRate > 0.01 && nextThreshold != null) {
            (((nextThreshold - current) / riseRate) * 60).toInt().coerceAtLeast(0)
        } else null

        val percentToDanger = ((current / station.dangerLevel) * 100).toInt()

        return RiverLevelStatus(
            station = station,
            currentLevelM = current,
            stage = stage,
            riseRateMPerHour = riseRate,
            minutesToNextStage = minutesToNext,
            percentToDangerLevel = percentToDanger,
            reachesEvacuationRatio = (current / station.dangerLevel) >= OfficialCriteria.RV_EVAC_LEVEL_RATIO,
            observedAtMillis = latest.observedAtMillis
        )
    }

    /**
     * 최근 1시간 관측값으로 선형 상승률(m/h)을 구한다.
     * 관측 간격이 10분이므로 보통 6개 샘플이 들어온다.
     */
    private fun calculateRiseRate(sorted: List<WaterLevelReading>): Double {
        if (sorted.size < 2) return 0.0
        val latest = sorted.last()
        val cutoff = latest.observedAtMillis - RISE_RATE_WINDOW_MINUTES * 60_000L
        val window = sorted.filter { it.observedAtMillis >= cutoff }
        if (window.size < 2) return 0.0

        val first = window.first()
        val elapsedHours = (latest.observedAtMillis - first.observedAtMillis) / 3_600_000.0
        if (elapsedHours <= 0) return 0.0

        return (latest.waterLevelM - first.waterLevelM) / elapsedHours
    }

    private fun nextThresholdFor(station: RiverStation, stage: RiverStage): Double? = when (stage) {
        RiverStage.NORMAL -> station.attentionLevel
        RiverStage.ATTENTION -> station.warningLevel
        RiverStage.WARNING -> station.alertLevel
        RiverStage.ALERT -> station.dangerLevel
        RiverStage.DANGER -> null
    }

    /**
     * 수위 상황을 사람이 읽을 수 있는 경고 문장으로 변환.
     * 실무자가 이 문장만 보고 판단할 수 있도록 구체적인 수치와 리드타임을 담는다.
     */
    fun describeStatus(status: RiverLevelStatus): String = buildString {
        append("${status.station.name} ${"%.2f".format(status.currentLevelM)}m")
        append(" · ${status.stage.displayName}")
        append(" (계획홍수위의 ${status.percentToDangerLevel}%)")

        when {
            status.riseRateMPerHour >= RAPID_RISE_THRESHOLD_M_PER_HOUR -> {
                appendLine()
                append("급상승 중 — 시간당 ${"%.2f".format(status.riseRateMPerHour)}m")
            }
            status.riseRateMPerHour > 0.01 -> {
                appendLine()
                append("상승 중 — 시간당 ${"%.2f".format(status.riseRateMPerHour)}m")
            }
            status.riseRateMPerHour < -0.01 -> {
                appendLine()
                append("하강 중 — 시간당 ${"%.2f".format(abs(status.riseRateMPerHour))}m")
            }
        }

        status.minutesToNextStage?.let { minutes ->
            if (minutes <= 180) {
                appendLine()
                val nextStageName = when (status.stage) {
                    RiverStage.NORMAL -> "관심수위"
                    RiverStage.ATTENTION -> "홍수주의보 수위"
                    RiverStage.WARNING -> "홍수경보 수위"
                    RiverStage.ALERT -> "계획홍수위"
                    RiverStage.DANGER -> ""
                }
                append("현 추세 유지 시 ${nextStageName} 도달까지 약 ${minutes}분")
            }
        }

        if (status.stage >= RiverStage.WARNING) {
            appendLine()
            append("영향 구역: ${status.station.protectedAreas.joinToString(", ")}")
        }
    }

    /**
     * 상류 지점의 상승이 하류에 미칠 영향을 경고한다.
     * 태화강 사연교(상류) → 태화교(중류)는 통상 1~2시간의 시차가 있어,
     * 상류 급상승은 하류 도심 지역의 사전 대응 신호가 된다.
     */
    fun upstreamWarning(statuses: List<RiverLevelStatus>): String? {
        val upstream = statuses.find { it.station.name.contains("사연교") } ?: return null
        if (upstream.riseRateMPerHour < RAPID_RISE_THRESHOLD_M_PER_HOUR) return null

        return "상류 ${upstream.station.name}가 시간당 " +
                "${"%.2f".format(upstream.riseRateMPerHour)}m로 급상승 중입니다. " +
                "통상 1~2시간 후 중류(태화교) 수위 상승으로 이어집니다. " +
                "도심 저지대 사전 통제를 지금 시작하세요."
    }
}
