package com.ulsan.disasteralert.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ulsan.disasteralert.R
import com.ulsan.disasteralert.data.CompositeRiskCalculator
import com.ulsan.disasteralert.data.RegionRiskStatus
import com.ulsan.disasteralert.data.RiskLevel
import com.ulsan.disasteralert.data.RiverLevelAnalyzer
import com.ulsan.disasteralert.data.RiverStage
import com.ulsan.disasteralert.data.BackwaterLevel
import com.ulsan.disasteralert.data.LastKnownState
import com.ulsan.disasteralert.data.TyphoonApproach
import com.ulsan.disasteralert.data.PreparednessLevel
import com.ulsan.disasteralert.data.CoincidenceSeverity
import com.ulsan.disasteralert.data.OfficialCriteria
import com.ulsan.disasteralert.data.SiteControlEvaluator
import com.ulsan.disasteralert.data.Underpasses
import com.ulsan.disasteralert.data.UnderpassStage

object NotificationHelper {

    private const val CHANNEL_CRITICAL = "channel_critical"   // 위험/심각 - 긴급, 소리+진동
    private const val CHANNEL_NORMAL = "channel_normal"       // 주의/경고 - 일반

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val critical = NotificationChannel(
            CHANNEL_CRITICAL,
            context.getString(R.string.channel_name_critical),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_desc_critical)
            enableVibration(true)
            enableLights(true)
        }

        val normal = NotificationChannel(
            CHANNEL_NORMAL,
            context.getString(R.string.channel_name_normal),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_desc_normal)
        }

        manager.createNotificationChannel(critical)
        manager.createNotificationChannel(normal)
    }

    fun notifyRiskStatus(context: Context, status: RegionRiskStatus) {
        val isCritical = status.riskLevel == RiskLevel.DANGER || status.riskLevel == RiskLevel.SEVERE
        val channelId = if (isCritical) CHANNEL_CRITICAL else CHANNEL_NORMAL

        val title = "[${status.riskLevel.displayName}] ${status.regionName} 기상·재난 알림"
        val bodyParts = mutableListOf<String>()
        status.activeWarnings.forEach { bodyParts.add("${it.warningType} ${it.warningLevel.displayName}") }
        status.activeDisasterAlerts.forEach { bodyParts.add("${it.disasterType} 위기경보 ${it.crisisLevel.displayName}") }
        status.latestPrecipitation?.let { bodyParts.add("시간당 강수량 ${it.hourlyRainMm}mm") }
        val body = bodyParts.joinToString(" · ").ifEmpty { "위험 요소가 감지되었습니다" }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(status.regionName.hashCode(), builder.build())
    }

    /**
     * 태풍 대비 단계 알림.
     *
     * 실시간 위험 알림과 채널을 나눈다. 태풍 대비는 며칠 전부터 뜨는 정보라
     * 긴급 채널로 보내면 정작 침수 임박 알림이 묻힌다.
     */
    fun notifyTyphoonPreparedness(context: Context, approach: TyphoonApproach) {
        val urgent = approach.preparednessLevel == PreparednessLevel.IMMINENT ||
                approach.tideCoincidence?.severity == CoincidenceSeverity.CRITICAL

        val body = buildString {
            approach.briefing.forEach { appendLine("· $it"); appendLine() }
        }.trim()

        val builder = NotificationCompat.Builder(
            context, if (urgent) CHANNEL_CRITICAL else CHANNEL_NORMAL
        )
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(approach.headline)
            .setContentText(approach.briefing.firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        context.getSystemService(NotificationManager::class.java)
            .notify(("typhoon_" + approach.typhoon.id).hashCode(), builder.build())
    }

    /**
     * 데이터 수신 실패 알림.
     *
     * 재난 상황에서 공공 API는 실제로 자주 죽는다. 그때 앱이 조용히 있으면
     * 사용자는 "위험이 없다"고 오해한다. 그래서 명시적으로 알린다.
     */
    fun notifyDataFailure(
        context: Context,
        district: String,
        failureCount: Int,
        lastKnown: LastKnownState.Cached?
    ) {
        val body = buildString {
            appendLine("${district}의 기상·재난 데이터를 ${failureCount}회 연속 받지 못했습니다.")
            appendLine()
            if (lastKnown != null) {
                appendLine("마지막 확인 (${lastKnown.ageMinutes}분 전):")
                appendLine("위험도 ${lastKnown.riskScore}점 · ${lastKnown.riskLevel}")
                appendLine("시간당 강수량 ${lastKnown.hourlyRainMm}mm")
                appendLine()
                append("현재 상황은 이보다 나빠졌을 수 있습니다. 기상청·안전디딤돌 앱을 직접 확인하세요.")
            } else {
                append("네트워크 상태와 API 설정을 확인하세요. 그동안은 공식 재난 정보를 직접 확인하시기 바랍니다.")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_CRITICAL)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("데이터 수신 중단 — $district")
            .setContentText("${failureCount}회 연속 수신 실패")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        context.getSystemService(NotificationManager::class.java)
            .notify(("fail_" + district).hashCode(), builder.build())
    }

    /**
     * 과거 이력을 연계한 종합 위험도 알림.
     * 단순히 "호우경보"가 아니라 "차바 때의 62% 수준, 우정동 지하차도 침수 임박" 처럼
     * 판단과 조치를 바로 할 수 있는 정보를 담는다.
     */
    fun notifyCompositeRisk(
        context: Context,
        result: CompositeRiskCalculator.CompositeResult,
        /** 관내 행사 참고 문구. 없으면 null */
        eventNotice: String? = null
    ) {
        val level = result.finalLevel
        // 공식 대피 기준 도달이면 무조건 긴급 채널
        val isCritical = level == RiskLevel.DANGER || level == RiskLevel.SEVERE ||
                result.officialJudgement.level == OfficialCriteria.ActionLevel.EVACUATE ||
                result.underpassStage.stage.rank >= UnderpassStage.Stage.ALERT.rank
        val channelId = if (isCritical) CHANNEL_CRITICAL else CHANNEL_NORMAL

        // 공식 기준 도달 시 제목에 먼저 표시 — 이것이 실제 조치 근거다
        val official = result.officialJudgement
        val triggeredCount = SiteControlEvaluator.triggered(result.siteStatuses).size
        val checkCount = result.underpassesToCheck.size
        val title = if (triggeredCount > 0) {
            "[통제] ${result.baseStatus.regionName} · ${triggeredCount}개소 기준 도달" +
                (if (checkCount > 0) " · 지하차도 확인 ${checkCount}" else "")
        } else if (result.underpassStage.stage != UnderpassStage.Stage.NORMAL) {
            "[지하차도 ${result.underpassStage.stage.label}] ${result.baseStatus.regionName}"
        } else if (official.level != OfficialCriteria.ActionLevel.NONE) {
            val types = official.byHazard
                .filter { it.value == official.level }
                .keys.joinToString("·") { it.displayName }
            "[${official.level.displayName}] ${result.baseStatus.regionName} · $types"
        } else {
            "[${level.displayName}] ${result.baseStatus.regionName} 위험도 ${result.finalScore}점"
        }

        val body = buildString {
            // ── 공식 기준을 최상단에 ──
            // 앱 추정치보다 먼저 나와야 한다. 조치의 근거는 매뉴얼이지 앱이 아니다.
            // ── 지점별 통제 대상을 가장 먼저 ──
            // 현장에서 바로 조치할 수 있는 가장 구체적인 정보다
            val triggered = SiteControlEvaluator.triggered(result.siteStatuses)
            val imminent = SiteControlEvaluator.imminent(result.siteStatuses)

            if (triggered.isNotEmpty()) {
                appendLine("■ 통제 대상 지점 (${triggered.size}개소)")
                triggered.take(5).forEach {
                    val mark = if (it.site.isCritical) "‼" else "·"
                    appendLine("$mark ${it.site.name} (${it.site.address})")
                    appendLine("   ${it.site.criterion.label} → ${it.observed}")
                    appendLine("   통제범위: ${it.site.scope}")
                }
                if (triggered.size > 5) appendLine("   외 ${triggered.size - 5}개소")
                appendLine()
            }
            // 자동 판정이 안 되는 지하차도 — 침묵하면 "이상 없음"으로 오해한다
            // ── 지하차도 단계 ──
            // 대응계획서가 별도 4단계 기준을 두고 있어, 센서 없이도 경계까지 판정된다
            val up = result.underpassStage
            if (up.stage != UnderpassStage.Stage.NORMAL) {
                appendLine("■ 지하차도 ${up.stage.label} 단계")
                up.triggers.take(3).forEach {
                    appendLine("  ${it.description} → ${it.observed}")
                }
                appendLine()
                appendLine("  [조치사항]")
                up.actions.forEach { appendLine("  · $it") }

                if (up.immediateControlReasons.isNotEmpty()) {
                    appendLine()
                    appendLine("  ‼ 즉시 통제 사유 해당")
                    up.immediateControlReasons.forEach { appendLine("  · $it") }
                }

                val high = result.underpassesToCheck.filter {
                    it.grade == Underpasses.RiskGrade.HIGH
                }
                if (high.isNotEmpty()) {
                    appendLine()
                    appendLine("  [침수우려 높음 ${high.size}개소 — 4인 배치 대상]")
                    high.take(8).forEach { appendLine("  · ${it.name}") }
                }

                if (up.stage.rank < UnderpassStage.Stage.SERIOUS.rank) {
                    appendLine()
                    appendLine("  ※ 침수심은 센서 미연계로 확인 불가 — 심각 단계 판정은 현장 확인 필요")
                }
                appendLine()
            }

            if (imminent.isNotEmpty()) {
                appendLine("■ 임박 지점 (${imminent.size}개소)")
                imminent.take(3).forEach {
                    appendLine("· ${it.site.name} — ${it.progressPercent}%")
                }
                appendLine()
            }

            if (official.level != OfficialCriteria.ActionLevel.NONE) {
                appendLine("■ 울산시 대응계획서 기준")

                // 유형별로 묶어서 보여준다 — 지하공간과 산사태는 대응 방식이 완전히 다르다
                official.byHazard
                    .filter { it.value != OfficialCriteria.ActionLevel.NONE }
                    .toList()
                    .sortedByDescending { it.second.rank }
                    .forEach { (hazard, lv) ->
                        appendLine()
                        appendLine("${hazard.icon} ${hazard.displayName} — ${lv.displayName}")
                        official.triggers
                            .filter { it.hazard == hazard && it.level == lv }
                            .take(2)
                            .forEach {
                                appendLine("  ${it.clause} ${it.description}")
                                appendLine("     → ${it.observed}")
                            }
                    }

                appendLine()
                official.actions.take(3).forEach { appendLine("· $it") }
                appendLine()
                appendLine("■ 앱 참고 판단 (위험도 ${result.finalScore}점 · ${level.displayName})")
            }

            // 현재 기상 상황
            val conditions = mutableListOf<String>()
            result.baseStatus.activeWarnings.forEach {
                conditions.add("${it.warningType} ${it.warningLevel.displayName}")
            }
            result.baseStatus.activeDisasterAlerts.forEach {
                conditions.add("${it.disasterType} 위기경보 ${it.crisisLevel.displayName}")
            }
            result.baseStatus.latestPrecipitation?.let {
                conditions.add("시간당 ${it.hourlyRainMm}mm")
            }
            if (conditions.isNotEmpty()) appendLine(conditions.joinToString(" · "))

            // 하천 수위 — 가장 직접적인 침수 지표라 최상단에 배치
            result.riverStatuses
                .filter { it.stage >= RiverStage.ATTENTION }
                .sortedByDescending { it.stage.score }
                .take(2)
                .forEach {
                    appendLine()
                    appendLine("▶ " + RiverLevelAnalyzer.describeStatus(it))
                }

            // 상류 급상승 → 하류 도심 선행 경고
            result.upstreamWarning?.let {
                appendLine()
                appendLine("▶ $it")
            }

            // 배수 위험 — 조위와 하천 수위의 결합
            if (result.backwaterRisk.level != BackwaterLevel.NONE) {
                appendLine()
                appendLine("▶ [${result.backwaterRisk.level.displayName}] ${result.backwaterRisk.message}")
            } else result.tideStatus?.let { tide ->
                if (tide.isNearHighTide || tide.drainageBlocked) {
                    appendLine()
                    appendLine("▶ ${tide.description}")
                }
            }

            // 과거 사례 비교
            result.similarEvents.firstOrNull()?.let {
                appendLine()
                appendLine("▶ ${it.warningMessage}")
            }

            // 침수 임박 지점
            if (result.hotspotsAtRisk.isNotEmpty()) {
                appendLine()
                appendLine("▶ 임박 지점:")
                result.hotspotsAtRisk.take(3).forEach { (spot, percent) ->
                    appendLine("  · ${spot.name} (${percent}%)")
                }
            }

            // 행사 참고 정보 — 판단에 개입하지 않고 맨 뒤에 덧붙인다
            eventNotice?.let {
                appendLine()
                appendLine("─────────────")
                append(it)
                appendLine()
            }

            // 행동요령
            if (result.actionGuidance.isNotEmpty()) {
                appendLine()
                result.actionGuidance.take(3).forEach { appendLine("▶ $it") }
            }
        }.trim()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(result.baseStatus.regionName.hashCode(), builder.build())
    }
}
