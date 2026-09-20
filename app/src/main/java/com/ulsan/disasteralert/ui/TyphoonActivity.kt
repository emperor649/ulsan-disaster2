package com.ulsan.disasteralert.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulsan.disasteralert.BuildConfig
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.databinding.ActivityTyphoonBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 태풍 대비 현황.
 *
 * 실시간 위험도와 별개 축이다. 여기서 다루는 건 "며칠 뒤 무엇을 준비하나"이고,
 * 그중 가장 값어치 있는 산출물은 최근접 시각과 만조가 겹치는지 여부다.
 */
class TyphoonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTyphoonBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTyphoonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonRefresh.setOnClickListener { load() }
        load()
    }

    private fun load() {
        binding.textStatus.text = "태풍 정보 조회 중..."
        binding.containerContent.removeAllViews()

        lifecycleScope.launch {
            if (BuildConfig.KMA_APIHUB_KEY.isBlank()) {
                binding.textStatus.text =
                    "기상청 API허브 인증키가 필요합니다.\n" +
                    "apihub.kma.go.kr에서 발급받아 local.properties의\n" +
                    "KMA_APIHUB_KEY에 넣으세요.\n" +
                    "(data.go.kr 키와는 다른 키입니다)"
                return@launch
            }

            val approaches = withContext(Dispatchers.IO) {
                runCatching {
                    TyphoonRepository.getApproaches(
                        this@TyphoonActivity,
                        BuildConfig.KMA_APIHUB_KEY,
                        BuildConfig.TIDE_SERVICE_KEY
                    )
                }.getOrDefault(emptyList())
            }

            render(approaches)
        }
    }

    private fun render(approaches: List<TyphoonApproach>) {
        binding.containerContent.removeAllViews()

        if (approaches.isEmpty()) {
            binding.textStatus.text = "현재 울산 영향권에 접근 중인 태풍이 없습니다."
            binding.containerContent.addView(
                makeCard(
                    "발생 중인 태풍이 없거나, 있더라도 울산에서 400km 밖에 있습니다.\n\n" +
                    "태풍이 접근하면 이 화면에 최근접 시각, 통과 방향, " +
                    "그리고 만조와 겹치는지 여부가 표시됩니다.",
                    "#757575", 13f
                )
            )
            return
        }

        binding.textStatus.text = "영향권 태풍 ${approaches.size}개 · ${
            SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
        } 기준"

        approaches.sortedByDescending { it.preparednessLevel.ordinal }.forEach { a ->
            // 헤드라인
            binding.containerContent.addView(
                makeCard(a.headline, a.preparednessLevel.colorHex, 16f, bold = true)
            )

            // 만조 겹침 — 가장 중요한 정보라 별도로 강조
            a.tideCoincidence?.let { t ->
                if (t.severity != CoincidenceSeverity.NONE) {
                    val color = when (t.severity) {
                        CoincidenceSeverity.CRITICAL -> "#B71C1C"
                        CoincidenceSeverity.OVERLAP -> "#F44336"
                        else -> "#FF9800"
                    }
                    val text = buildString {
                        appendLine("만조 겹침: ${t.severity.displayName}")
                        appendLine()
                        appendLine("만조 시각 ${SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(t.highTideTimeMillis))}")
                        appendLine("조위 ${t.highTideLevelCm}cm${if (t.isSpringTide) " (대조기)" else ""}")
                        append("태풍 최근접과 ${kotlin.math.abs(t.offsetMinutes)}분 차이")
                    }
                    binding.containerContent.addView(makeCard(text, color, 14f, bold = true))
                }
            }

            // 브리핑
            a.briefing.forEach { line ->
                binding.containerContent.addView(makeCard(line, "#424242", 13f))
            }

            // 제원
            val spec = buildString {
                appendLine("태풍 제원")
                appendLine()
                a.closestPoint.centralPressureHpa?.let { appendLine("중심기압 ${it}hPa") }
                a.closestPoint.maxWindMs?.let { appendLine("최대풍속 ${it}m/s") }
                a.closestPoint.intensity?.let { appendLine("강도 $it") }
                a.closestPoint.size?.let { appendLine("크기 $it") }
                a.closestPoint.strongWindRadiusKm?.let { appendLine("강풍반경 ${it.toInt()}km") }
                a.closestPoint.stormRadiusKm?.let { append("폭풍반경 ${it.toInt()}km") }
            }
            binding.containerContent.addView(makeCard(spec, "#757575", 12f))

            // 구분선 역할
            binding.containerContent.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 32
                )
            })
        }

        // 신뢰도 안내는 항상 마지막에
        binding.containerContent.addView(
            makeCard(
                "진로 예보는 시간이 지날수록 정확해집니다. 3일 전 예보는 예보원 반경이 커서 " +
                "실제 진로가 크게 달라질 수 있습니다. 이 화면의 판단은 참고용이며, " +
                "공식 대응은 기상청 발표와 시 재난안전대책본부 지침을 따르세요.",
                "#8D6E63", 12f
            )
        )
    }

    private fun makeCard(text: String, colorHex: String, sizeSp: Float, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(24, 20, 24, 20)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.bottomMargin = 12
            layoutParams = p
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }
}
