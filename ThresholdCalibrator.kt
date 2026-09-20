package com.ulsan.disasteralert.data

import android.content.Context

/**
 * 임계값 보정.
 *
 * 두 개의 근거를 결합한다.
 *   A) 과거 사례 재현 결과 — 데이터는 적지만 극한 상황을 포함한다
 *   B) 실측 운영 데이터 — 최근 실제 관측이지만 대부분 평상시라 극한 사례가 드물다
 *
 * 둘 중 하나만 쓰면 각각의 약점이 그대로 남는다.
 * A만 쓰면 3~4건의 보간 추정치에 임계값 전체를 거는 셈이고,
 * B만 쓰면 큰 재해를 한 번도 안 겪은 채로 "경보가 너무 잦다"며 임계값을 계속 올리게 된다.
 * (이게 실제로 위험하다 — 평온한 몇 달을 근거로 둔감해진 상태에서 차바급이 오는 것)
 *
 * 그래서 극한 영역은 A가, 일상 영역은 B가 담당하도록 가중치를 나눈다.
 */
object ThresholdCalibrator {

    data class Finding(
        val severity: Severity,
        val title: String,
        val evidence: String,
        val recommendation: String
    )

    enum class Severity(val displayName: String, val colorHex: String) {
        CRITICAL("반드시 조치", "#C62828"),
        WARNING("검토 필요", "#F57C00"),
        INFO("참고", "#1565C0"),
        OK("양호", "#2E7D32")
    }

    data class Report(
        val replayResults: List<ReplayEngine.ScenarioResult>,
        val observationCount: Int,
        val observationDays: Int,
        val findings: List<Finding>,
        val overallSummary: String
    )

    fun calibrate(context: Context): Report {
        val replay = ReplayEngine.runAll()
        val obs = ObservationLog.load(context)
        val findings = mutableListOf<Finding>()

        // ── A. 과거 재현에서 나온 문제 ──
        replay.forEach { r ->
            when (r.verdict) {
                ReplayEngine.Verdict.NO_ALERT -> findings.add(
                    Finding(
                        Severity.CRITICAL,
                        "${r.scenario.name} — 경보 누락",
                        r.diagnosis,
                        "이 시나리오에서 위험 경보가 뜨도록 임계값을 낮추세요. " +
                        "특히 ${r.scenario.district}의 상습침수지 임계 강수량을 재검토해야 합니다."
                    )
                )
                ReplayEngine.Verdict.TOO_LATE -> findings.add(
                    Finding(
                        Severity.CRITICAL,
                        "${r.scenario.name} — 리드타임 부족",
                        r.diagnosis,
                        "상습침수지 임계값을 15~20% 낮추거나, 누적 강수량 가중치를 높이세요. " +
                        "하천 수위 상승률 가중치를 올리는 것도 효과적입니다."
                    )
                )
                ReplayEngine.Verdict.OVER_SENSITIVE -> findings.add(
                    Finding(
                        Severity.WARNING,
                        "${r.scenario.name} — 과민 경보",
                        r.diagnosis,
                        "지역 취약계수 상한(현재 2.0)을 1.7 정도로 낮추는 것을 검토하세요."
                    )
                )
                ReplayEngine.Verdict.ADEQUATE -> findings.add(
                    Finding(
                        Severity.INFO,
                        "${r.scenario.name} — 개선 여지 있음",
                        r.diagnosis,
                        "당장 조치가 필요하진 않으나, 리드타임을 60분 이상으로 늘리면 더 안전합니다."
                    )
                )
                ReplayEngine.Verdict.EXCELLENT -> findings.add(
                    Finding(Severity.OK, "${r.scenario.name} — 양호", r.diagnosis, "현 설정 유지")
                )
            }
        }

        // ── B. 실측 데이터에서 나온 문제 ──
        val days = if (obs.isEmpty()) 0 else {
            ((obs.maxOf { it.timestampMillis } - obs.minOf { it.timestampMillis }) / 86_400_000L).toInt() + 1
        }

        if (obs.size < 100) {
            findings.add(
                Finding(
                    Severity.INFO,
                    "실측 데이터 부족",
                    "누적 관측 ${obs.size}건 (${days}일). 통계적 판단에는 최소 수백 건이 필요합니다.",
                    "당분간은 과거 재현 결과를 기준으로 운영하세요. " +
                    "실측이 쌓이면 지역별 임계값을 실제 데이터로 조정할 수 있습니다."
                )
            )
        } else {
            // 오경보율: 경보를 냈는데 피해 보고가 없었던 비율
            val alerts = obs.filter { it.alertSent }
            val falseAlarms = alerts.count { !it.damageReported }
            val falseAlarmRate = if (alerts.isEmpty()) 0.0 else falseAlarms.toDouble() / alerts.size

            // 누락: 피해 보고가 있었는데 경보가 없었던 경우
            val damages = obs.filter { it.damageReported }
            val missed = damages.count { !it.alertSent }

            if (missed > 0) {
                val missedSnapshots = damages.filter { !it.alertSent }
                val minRain = missedSnapshots.minOfOrNull { it.hourlyRainMm } ?: 0.0
                findings.add(
                    Finding(
                        Severity.CRITICAL,
                        "실측 경보 누락 ${missed}건",
                        "피해가 보고된 시점에 경보가 발송되지 않았습니다. " +
                        "해당 시점 최저 시간당 강수량은 ${"%.1f".format(minRain)}mm였습니다.",
                        "이 값이 실제 침수 임계값입니다. " +
                        "해당 지역 상습침수지의 triggerHourlyRainMm를 ${"%.0f".format(minRain)}mm 이하로 조정하세요."
                    )
                )
            }

            if (falseAlarmRate > 0.7 && alerts.size >= 10) {
                findings.add(
                    Finding(
                        Severity.WARNING,
                        "오경보율 ${(falseAlarmRate * 100).toInt()}%",
                        "경보 ${alerts.size}건 중 ${falseAlarms}건에서 피해 보고가 없었습니다.",
                        "다만 '피해 보고 없음'이 곧 '피해 없음'은 아닙니다 — 사용자가 입력을 안 했을 수도 있습니다. " +
                        "입력 누락이 아닌 것이 확실할 때만 임계값을 올리세요."
                    )
                )
            }

            // 실측 최대치가 과거 사례에 한참 못 미치면 검증 한계를 알린다
            val maxObserved = obs.maxOfOrNull { it.hourlyRainMm } ?: 0.0
            if (maxObserved < 50.0) {
                findings.add(
                    Finding(
                        Severity.INFO,
                        "극한 구간 미검증",
                        "관측 기간 중 최대 시간당 강수량은 ${"%.1f".format(maxObserved)}mm였습니다. " +
                        "차바(104.2mm)의 절반에도 못 미칩니다.",
                        "실측만으로는 극한 상황의 임계값을 검증할 수 없습니다. " +
                        "이 구간은 과거 재현 결과를 계속 신뢰하고, " +
                        "평온한 기간이 길다고 임계값을 올리지 마세요."
                    )
                )
            }
        }

        return Report(
            replayResults = replay,
            observationCount = obs.size,
            observationDays = days,
            findings = findings.sortedBy { it.severity.ordinal },
            overallSummary = buildSummary(replay, obs.size, findings)
        )
    }

    private fun buildSummary(
        replay: List<ReplayEngine.ScenarioResult>,
        obsCount: Int,
        findings: List<Finding>
    ): String = buildString {
        val critical = findings.count { it.severity == Severity.CRITICAL }

        appendLine(ReplayEngine.summarize(replay))
        appendLine()
        appendLine("실측 누적 ${obsCount}건")
        appendLine()

        when {
            critical > 0 ->
                append("반드시 조치할 항목이 ${critical}건 있습니다. " +
                        "이 상태로 운영하면 실제 재해 시 경보가 제 역할을 못 할 수 있습니다.")
            obsCount < 100 ->
                append("과거 재현 기준으로는 문제없으나 실측 데이터가 부족합니다. " +
                        "운영하면서 계속 축적하세요. 침수가 발생하면 반드시 피해 보고를 입력해야 " +
                        "임계값이 실제 데이터로 보정됩니다.")
            else ->
                append("과거 재현과 실측 양쪽 모두 큰 문제가 없습니다. " +
                        "다만 극한 구간은 과거 데이터에만 의존하므로, " +
                        "기상청 원시 관측자료를 확보해 재검증하는 것을 권합니다.")
        }
    }
}
