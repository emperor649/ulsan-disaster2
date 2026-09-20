package com.ulsan.disasteralert.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.util.districtOf
import com.ulsan.disasteralert.databinding.ActivityHistoryAnalysisBinding
import com.ulsan.disasteralert.util.RainAccumulator

/**
 * 과거 재해 이력 분석 화면.
 *
 * 1) 이 지역의 취약도 프로필 (과거 몇 번, 어느 강수량에서 피해가 났는지)
 * 2) 현재 강수량이 과거 사례 대비 몇 % 수준인지
 * 3) 상습 침수지 목록과 각 지점의 임계값 도달률
 */
class HistoryAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryAnalysisBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // "울산 남구"가 그대로 넘어와도 "남구"로 정규화한다
        val raw = intent.getStringExtra(EXTRA_DISTRICT) ?: "중구"
        val district = districtOf(raw)
        binding.textDistrict.text = raw

        renderVulnerability(district)
        renderCurrentComparison(district)
        renderHotspots(district)
        renderPastEvents(district)
    }

    private fun renderVulnerability(district: String) {
        val profile = VulnerabilityAnalyzer.profileFor(district)
        binding.textVulnerabilitySummary.text = buildString {
            appendLine("취약도 계수: ${"%.2f".format(profile.vulnerabilityMultiplier)}배")
            appendLine("과거 재해 발생: ${profile.pastEventCount}회")
            appendLine("상습 침수지: ${profile.hotspotCount}개소 (인명피해 직결 ${profile.criticalHotspotCount}개소)")
            appendLine("과거 최저 피해 발생 강수량: 시간당 ${profile.lowestDamageThresholdMm}mm")
            if (profile.tideSensitive) {
                append("만조 영향권 — 만조와 겹칠 경우 위험도 35% 가중 적용")
            }
        }
    }

    private fun renderCurrentComparison(district: String) {
        val summary = RainAccumulator.summarize(this, district)
        val hourly = summary.cumulative3h / 3.0

        binding.textCurrentRain.text = buildString {
            appendLine("최근 3시간: ${"%.1f".format(summary.cumulative3h)}mm")
            appendLine("최근 24시간: ${"%.1f".format(summary.cumulative24h)}mm")
            append("강우 지속: ${summary.rainDurationHours}시간")
        }

        val matches = VulnerabilityAnalyzer.matchSimilarEvents(
            hourlyRainMm = hourly,
            cumulativeRainMm = summary.cumulative24h,
            durationHours = summary.rainDurationHours,
            tideCoinciding = false
        )

        binding.containerSimilarEvents.removeAllViews()
        if (matches.isEmpty()) {
            binding.containerSimilarEvents.addView(
                makeTextView("현재 강수량은 과거 피해 사례 대비 유의미한 수준이 아닙니다.", 14f, "#757575")
            )
        } else {
            matches.forEach { match ->
                val color = when {
                    match.progressPercent >= 100 -> "#B71C1C"
                    match.progressPercent >= 70 -> "#F44336"
                    else -> "#FF9800"
                }
                binding.containerSimilarEvents.addView(
                    makeTextView(
                        "${match.event.name} 대비 ${match.progressPercent}%\n${match.event.damageSummary}",
                        14f, color
                    )
                )
            }
        }
    }

    private fun renderHotspots(district: String) {
        val summary = RainAccumulator.summarize(this, district)
        val hourly = summary.cumulative3h / 3.0
        val atRisk = VulnerabilityAnalyzer
            .hotspotsAtRisk(district, hourly, summary.cumulative24h)
            .associate { it.first.name to it.second }

        binding.containerHotspots.removeAllViews()
        UlsanHistoricalData.hotspotsIn(district).forEach { spot ->
            val percent = atRisk[spot.name]
            val color = when {
                percent == null -> "#757575"
                percent >= 100 -> "#B71C1C"
                percent >= 90 -> "#F44336"
                else -> "#FF9800"
            }
            val text = buildString {
                append("${spot.name} · ${spot.hotspotType.displayName}")
                if (percent != null) append("  [${percent}%]")
                appendLine()
                append("임계: 시간당 ${spot.triggerHourlyRainMm}mm / 누적 ${spot.triggerCumulativeRainMm}mm")
                appendLine()
                append(spot.notes)
            }
            binding.containerHotspots.addView(makeTextView(text, 13f, color))
        }
    }

    private fun renderPastEvents(district: String) {
        binding.containerPastEvents.removeAllViews()
        UlsanHistoricalData.eventsAffecting(district).forEach { event ->
            val text = buildString {
                appendLine("${event.name} (${event.date}) · ${event.eventType.displayName}")
                append("최대 시간당 ${event.maxHourlyRainMm}mm / 총 ${event.totalRainMm}mm")
                if (event.tideCoincided) append(" · 만조 겹침")
                appendLine()
                append(event.damageSummary)
            }
            binding.containerPastEvents.addView(makeTextView(text, 13f, "#424242"))
        }
    }

    private fun makeTextView(text: String, sizeSp: Float, colorHex: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(android.graphics.Color.parseColor(colorHex))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 20
            layoutParams = params
        }
    }

    companion object {
        const val EXTRA_DISTRICT = "extra_district"
    }
}
