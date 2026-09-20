package com.ulsan.disasteralert.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulsan.disasteralert.BuildConfig
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.databinding.ActivityRiverLevelBinding
import com.ulsan.disasteralert.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 하천 수위 현황 화면.
 * 각 관측소의 현재 수위, 기준홍수위 대비 위치, 상승 속도, 다음 단계 도달 예상시간을 표시한다.
 */
class RiverLevelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiverLevelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiverLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val district = intent.getStringExtra(EXTRA_DISTRICT)
        loadRiverLevels(district)
    }

    private fun loadRiverLevels(district: String?) {
        binding.textStatus.text = "수위 조회 중..."

        lifecycleScope.launch {
            val apiKey = BuildConfig.FLOOD_SERVICE_KEY
            if (apiKey.isBlank()) {
                binding.textStatus.text =
                    "홍수통제소 API 키가 설정되지 않았습니다.\nlocal.properties에 FLOOD_SERVICE_KEY를 추가하세요."
                return@launch
            }

            val results = withContext(Dispatchers.IO) {
                if (StationSyncHelper.needsSync(this@RiverLevelActivity)) {
                    runCatching {
                        StationSyncHelper.syncUlsanStations(this@RiverLevelActivity, apiKey)
                    }
                }

                StationSyncHelper.effectiveStations(this@RiverLevelActivity, district)
                    .filterNot { it.code.startsWith("TODO_") }
                    .mapNotNull { station ->
                        runCatching {
                            val resp = ApiClient.floodApi.getWaterLevels(apiKey, station.code)
                            val readings = resp.content.orEmpty().mapNotNull { item ->
                                val lv = item.wl.toDoubleOrNull() ?: return@mapNotNull null
                                WaterLevelReading(
                                    item.wlobscd, lv, item.fw?.toDoubleOrNull(),
                                    parseYmdhm(item.ymdhm)
                                )
                            }
                            RiverLevelAnalyzer.analyze(station, readings)
                        }.getOrNull()
                    }
            }

            render(results)
        }
    }

    private fun render(statuses: List<RiverLevelStatus>) {
        binding.containerStations.removeAllViews()

        if (statuses.isEmpty()) {
            binding.textStatus.text = buildString {
                appendLine("표시할 수위 관측소가 없습니다.")
                appendLine()
                append("관측소 제원 동기화가 필요하거나, 해당 지역에 기준홍수위가 설정된 관측소가 없을 수 있습니다.")
            }
            return
        }

        binding.textStatus.text = "관측소 ${statuses.size}개소 · ${
            SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
        } 기준"

        // 상류 급상승 경고를 최상단에 노출
        RiverLevelAnalyzer.upstreamWarning(statuses)?.let { warning ->
            binding.containerStations.addView(makeCard(warning, "#B71C1C", 15f, bold = true))
        }

        statuses.sortedByDescending { it.stage.score }.forEach { status ->
            val text = buildString {
                appendLine(RiverLevelAnalyzer.describeStatus(status))
                appendLine()
                append("기준: 관심 ${status.station.attentionLevel}m / ")
                append("주의보 ${status.station.warningLevel}m / ")
                append("경보 ${status.station.alertLevel}m / ")
                append("계획홍수위 ${status.station.dangerLevel}m")
                if (status.station.notes.isNotBlank()) {
                    appendLine()
                    appendLine()
                    append(status.station.notes)
                }
            }
            binding.containerStations.addView(makeCard(text, status.stage.colorHex, 14f))
        }
    }

    private fun makeCard(text: String, colorHex: String, sizeSp: Float, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24, 24, 24, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 20
            layoutParams = params
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }

    private fun parseYmdhm(ymdhm: String): Long =
        runCatching { SimpleDateFormat("yyyyMMddHHmm", Locale.KOREA).parse(ymdhm)?.time }
            .getOrNull() ?: System.currentTimeMillis()

    companion object {
        const val EXTRA_DISTRICT = "extra_district"
    }
}
