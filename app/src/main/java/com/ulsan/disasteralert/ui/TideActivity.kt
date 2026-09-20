package com.ulsan.disasteralert.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulsan.disasteralert.BuildConfig
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.databinding.ActivityTideBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 조위 현황 화면.
 * 만조·간조 시각표와 함께, 강우 시 배수가 가능한 시간대를 판단할 수 있게 표시한다.
 */
class TideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadTide()
    }

    private fun loadTide() {
        binding.textStatus.text = "조위 조회 중..."

        lifecycleScope.launch {
            val key = BuildConfig.TIDE_SERVICE_KEY
            if (key.isBlank()) {
                binding.textStatus.text =
                    "국립해양조사원 API 키가 설정되지 않았습니다.\nlocal.properties에 TIDE_SERVICE_KEY를 추가하세요."
                return@launch
            }

            val status = withContext(Dispatchers.IO) {
                if (!TideRepository.stationCodeVerified(this@TideActivity)) {
                    runCatching { TideRepository.verifyStationCode(this@TideActivity, key) }
                }
                runCatching { TideRepository.getTideStatus(this@TideActivity, key) }.getOrNull()
            }

            if (status == null) {
                binding.textStatus.text = "조위 정보를 가져오지 못했습니다. API 키와 관측소 코드를 확인하세요."
                return@launch
            }
            render(status)
        }
    }

    private fun render(status: TideStatus) {
        binding.textStatus.text = "${status.station.name} 조위관측소 · ${
            SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
        } 기준"

        binding.containerContent.removeAllViews()

        // 현재 상황 요약
        val summaryColor = when {
            status.drainageBlocked -> "#B71C1C"
            status.isNearHighTide -> "#F44336"
            else -> "#4CAF50"
        }
        binding.containerContent.addView(makeCard(status.description, summaryColor, 15f, bold = true))

        // 배수 가능 시간대 안내 — 실무적으로 가장 유용한 정보
        val drainageInfo = buildString {
            appendLine("배수 여건")
            appendLine()
            if (status.drainageBlocked) {
                appendLine("현재 자연배수 불가 상태입니다.")
                appendLine("강제 배수(펌프) 없이는 저지대 물이 빠지지 않습니다.")
            } else if (status.isNearHighTide) {
                appendLine("만조 시간대로 배수 속도가 느립니다.")
                appendLine("이 시간대에 강한 비가 오면 저지대 수위가 빠르게 오릅니다.")
            } else {
                appendLine("현재는 자연배수가 가능한 시간대입니다.")
                status.minutesToNextHighTide?.let {
                    appendLine("다음 만조까지 약 ${it}분 — 그 전에 배수 작업을 마치는 것이 유리합니다.")
                }
            }
            if (status.isSpringTide) {
                appendLine()
                append("※ 대조기(사리)입니다. 만조 조위가 평소보다 높아 배수 차단 시간이 길어집니다.")
            }
        }
        binding.containerContent.addView(makeCard(drainageInfo, "#424242", 14f))

        // 만조/간조 시각
        status.nextHighTide?.let { next ->
            val text = buildString {
                appendLine("다음 만조")
                appendLine()
                appendLine(SimpleDateFormat("MM월 dd일 HH:mm", Locale.KOREA).format(Date(next.timeMillis)))
                append("조위 ${next.levelCm}cm")
                if (next.levelCm >= status.station.drainageBlockLevelCm) {
                    appendLine()
                    append("→ 배수 차단 임계(${status.station.drainageBlockLevelCm}cm) 초과 예상")
                }
            }
            binding.containerContent.addView(makeCard(text, "#1565C0", 14f))
        }

        // 차바 교훈 안내
        binding.containerContent.addView(
            makeCard(
                "참고: 2016년 태풍 차바 당시 울산 피해가 커진 결정적 원인은 " +
                "집중호우 시간대가 만조와 겹쳐 빗물이 바다로 빠져나가지 못한 것이었습니다. " +
                "호우 예보가 있을 때는 만조 시각을 먼저 확인하고 대응 계획을 세우세요.",
                "#757575", 13f
            )
        )

        // 폭풍해일 보정 안내 — 값이 왜 예보와 다른지 알려준다
        if (TyphoonRepository.hasActiveThreat(this)) {
            binding.containerContent.addView(
                makeCard(
                    "태풍 영향권이라 폭풍해일 여유 " +
                    "${TideRepository.STORM_SURGE_MARGIN_CM}cm를 더해 판단하고 있습니다.\n\n" +
                    "조석예보는 천문조라, 태풍 접근 시 기압 저하와 취송류로 " +
                    "실제 해수면이 예보보다 높아집니다. 예보값만 믿으면 위험을 낮게 봅니다.",
                    "#F57C00", 13f
                )
            )
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
}
