package com.ulsan.disasteralert.data

/**
 * 과거 사례 재현 검증.
 *
 * 과거 관측값을 시간순으로 넣고 각 시점의 위험도를 계산해서,
 * 실제 피해 발생 시점보다 **얼마나 먼저** 경보가 떴는지 측정한다.
 *
 * 판단 기준:
 *   - 리드타임 60분 이상 → 대피·통제에 실질적으로 쓸 수 있음
 *   - 30~60분 → 제한적. 차량 이동, 지하차도 차단 정도는 가능
 *   - 30분 미만 → 사실상 사후 통보. 임계값을 낮춰야 함
 *   - 피해 후 경보 → 실패
 *
 * 동시에 반대 방향도 본다: 피해가 없었던 시나리오에서 고위험 경보가 뜨면 과민 경보다.
 * 과민 경보가 반복되면 현장에서 알림을 무시하게 되므로, 이쪽도 똑같이 중요하다.
 */
object ReplayEngine {

    data class FrameResult(
        val frame: ReplayFrame,
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val triggeredHotspots: List<String>
    )

    data class ScenarioResult(
        val scenario: ReplayScenario,
        val frames: List<FrameResult>,
        /** 첫 '경고' 단계 도달 시점 (분) */
        val firstWarningAtMinute: Int?,
        /** 첫 '위험' 단계 도달 시점 (분) */
        val firstDangerAtMinute: Int?,
        /** 실제 피해 발생 시점 (분) */
        val actualDamageAtMinute: Int?,
        /** 리드타임: 위험 경보가 실제 피해보다 몇 분 먼저 떴는가 */
        val leadTimeMinutes: Int?,
        val verdict: Verdict,
        val diagnosis: String
    )

    enum class Verdict(val displayName: String, val colorHex: String) {
        EXCELLENT("우수", "#2E7D32"),      // 리드타임 60분 이상
        ADEQUATE("보통", "#F57C00"),        // 30~60분
        TOO_LATE("경보 지연", "#C62828"),   // 30분 미만 또는 피해 후
        OVER_SENSITIVE("과민 경보", "#F57C00"), // 경미한 사례에 고위험 경보
        NO_ALERT("경보 실패", "#B71C1C")    // 경보가 아예 안 뜸
    }

    fun run(scenario: ReplayScenario): ScenarioResult {
        val results = scenario.frames.map { frame ->
            evaluateFrame(frame, scenario.district)
        }

        val firstWarning = results.firstOrNull {
            it.riskLevel.ordinal >= RiskLevel.WARNING.ordinal
        }?.frame?.minutesFromStart

        val firstDanger = results.firstOrNull {
            it.riskLevel.ordinal >= RiskLevel.DANGER.ordinal
        }?.frame?.minutesFromStart

        val damageAt = scenario.frames.firstOrNull { it.actualDamageOccurred }?.minutesFromStart

        val leadTime = if (firstDanger != null && damageAt != null) damageAt - firstDanger else null

        val (verdict, diagnosis) = judge(scenario, results, firstWarning, firstDanger, damageAt, leadTime)

        return ScenarioResult(
            scenario, results, firstWarning, firstDanger, damageAt, leadTime, verdict, diagnosis
        )
    }

    private fun evaluateFrame(frame: ReplayFrame, district: String): FrameResult {
        // 실시간 데이터 구조로 변환
        val warnings = if (frame.weatherWarning != WarningLevel.NONE) {
            listOf(
                WeatherWarning(
                    regionCode = "replay", regionName = district,
                    warningType = "호우", warningLevel = frame.weatherWarning,
                    announcedAt = "", command = "발표"
                )
            )
        } else emptyList()

        val precipitation = PrecipitationObservation(
            stationId = "replay", regionName = district,
            hourlyRainMm = frame.hourlyRainMm,
            cumulativeRainMm = frame.cumulativeRainMm,
            observedAt = ""
        )

        val alerts = frame.crisisLevel?.let {
            listOf(
                DisasterAlert(
                    alertId = "replay", disasterType = "호우", crisisLevel = it,
                    regionName = district, message = "", issuedAt = "", issuingAgency = "재현"
                )
            )
        } ?: emptyList()

        val base = RiskCalculator.calculate(
            district, warnings, precipitation, alerts, ""
        )

        // 하천 수위 재구성
        val riverStatuses = frame.riverLevelM?.let { level ->
            val station = UlsanRiverStations.stations.find { it.district == district }
                ?: UlsanRiverStations.stations.first()
            listOf(
                RiverLevelStatus(
                    station = station,
                    currentLevelM = level,
                    stage = when {
                        level >= station.dangerLevel -> RiverStage.DANGER
                        level >= station.alertLevel -> RiverStage.ALERT
                        level >= station.warningLevel -> RiverStage.WARNING
                        level >= station.attentionLevel -> RiverStage.ATTENTION
                        else -> RiverStage.NORMAL
                    },
                    riseRateMPerHour = 0.0,  // 프레임 간 차분은 아래에서 별도 계산 가능
                    minutesToNextStage = null,
                    percentToDangerLevel = ((level / station.dangerLevel) * 100).toInt(),
                    observedAtMillis = 0L
                )
            )
        } ?: emptyList()

        // 조위 재구성
        val tideStatus = frame.tideLevelCm?.let { cm ->
            val station = TideRepository.defaultStationForReplay()
            TideStatus(
                station = station,
                currentLevelCm = cm,
                nextHighTide = null, previousHighTide = null,
                minutesToNextHighTide = null,
                isNearHighTide = frame.isNearHighTide,
                isSpringTide = false,
                drainageBlocked = cm >= station.drainageBlockLevelCm,
                tideMultiplier = run {
                    var m = 1.0
                    if (frame.isNearHighTide) m += 0.25
                    if (cm >= station.drainageBlockLevelCm) m += 0.15
                    m.coerceAtMost(1.5)
                },
                description = ""
            )
        }

        val composite = CompositeRiskCalculator.evaluate(
            baseStatus = base,
            district = district,
            cumulativeRainMm = frame.cumulativeRainMm,
            rainDurationHours = frame.minutesFromStart / 60,
            tideStatus = tideStatus,
            riverStatuses = riverStatuses
        )

        return FrameResult(
            frame = frame,
            riskScore = composite.finalScore,
            riskLevel = composite.finalLevel,
            triggeredHotspots = composite.hotspotsAtRisk.map { "${it.first.name}(${it.second}%)" }
        )
    }

    private fun judge(
        scenario: ReplayScenario,
        results: List<FrameResult>,
        firstWarning: Int?,
        firstDanger: Int?,
        damageAt: Int?,
        leadTime: Int?
    ): Pair<Verdict, String> {

        val maxLevel = results.maxByOrNull { it.riskLevel.ordinal }?.riskLevel ?: RiskLevel.SAFE
        val isMinorEvent = scenario.eventId == "heavyrain_202508"

        // 경미한 사례에서 심각 단계가 뜨면 과민
        if (isMinorEvent && maxLevel.ordinal >= RiskLevel.SEVERE.ordinal) {
            return Verdict.OVER_SENSITIVE to
                "경미한 사례인데 '${maxLevel.displayName}' 단계까지 올라갔습니다. " +
                "실제로는 국지적 침수만 있었습니다. 이 수준이 반복되면 현장에서 알림을 무시하게 됩니다. " +
                "지역 취약계수 또는 하천 수위 가중치를 낮추는 것을 검토하세요."
        }

        if (damageAt == null) {
            return Verdict.ADEQUATE to "이 시나리오에는 피해 발생 시점이 기록되어 있지 않습니다."
        }

        if (firstDanger == null) {
            return Verdict.NO_ALERT to
                "실제 피해가 발생했는데 '위험' 단계 경보가 한 번도 뜨지 않았습니다. " +
                "최고 도달 단계는 '${maxLevel.displayName}'입니다. 임계값이 너무 높습니다."
        }

        return when {
            leadTime == null || leadTime < 0 ->
                Verdict.TOO_LATE to
                    "피해가 발생한 뒤에야 경보가 떴습니다. 사후 통보에 불과합니다. " +
                    "임계값을 대폭 낮춰야 합니다."
            leadTime < 30 ->
                Verdict.TOO_LATE to
                    "리드타임이 ${leadTime}분에 불과합니다. 대피 안내에 쓰기 어렵습니다. " +
                    "차량 이동조차 빠듯한 시간입니다."
            leadTime < 60 ->
                Verdict.ADEQUATE to
                    "리드타임 ${leadTime}분. 지하차도 차단이나 차량 이동은 가능하지만 " +
                    "주민 대피에는 부족합니다. 여유를 더 확보하는 것이 좋습니다."
            else ->
                Verdict.EXCELLENT to
                    "리드타임 ${leadTime}분. 사전 통제와 주민 안내에 충분한 시간입니다." +
                    (if (firstWarning != null) " '경고' 단계는 ${damageAt - firstWarning}분 전에 발생했습니다." else "")
        }
    }

    /** 모든 시나리오를 한 번에 검증 */
    fun runAll(): List<ScenarioResult> = ReplayScenarios.all.map { run(it) }

    /**
     * 전체 검증 결과를 종합해 임계값 조정 방향을 제안한다.
     */
    fun summarize(results: List<ScenarioResult>): String = buildString {
        val late = results.count { it.verdict == Verdict.TOO_LATE || it.verdict == Verdict.NO_ALERT }
        val over = results.count { it.verdict == Verdict.OVER_SENSITIVE }
        val good = results.count { it.verdict == Verdict.EXCELLENT }

        appendLine("검증 ${results.size}건 · 우수 $good · 지연 $late · 과민 $over")
        appendLine()

        when {
            late > 0 && over > 0 ->
                append("경보가 늦는 사례와 과민한 사례가 동시에 있습니다. " +
                        "단순히 임계값을 올리거나 내리는 것으로는 해결되지 않습니다. " +
                        "지역별·지점별로 임계값을 다르게 가져가야 합니다.")
            late > 0 ->
                append("경보가 늦습니다. 상습침수지 임계값을 10~20% 낮추거나, " +
                        "하천 수위 가중치를 높이는 것을 검토하세요.")
            over > 0 ->
                append("경보가 과민합니다. 반복되면 현장에서 무시하게 됩니다. " +
                        "지역 취약계수 상한을 낮추는 것을 검토하세요.")
            else ->
                append("현재 임계값 설정이 적절합니다. 다만 검증에 쓴 과거 데이터가 " +
                        "보간 추정치를 포함하므로, 기상청 원시 관측자료로 재검증하는 것이 좋습니다.")
        }
    }
}
