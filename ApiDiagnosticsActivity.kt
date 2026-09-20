package com.ulsan.disasteralert.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulsan.disasteralert.data.ApiDiagnostics
import com.ulsan.disasteralert.data.ConfigStatus
import com.ulsan.disasteralert.databinding.ActivityApiDiagnosticsBinding
import kotlinx.coroutines.launch

/**
 * API 연결 진단 화면.
 * 4개 외부 API를 실제 호출해 어디까지 되고 어디서 막히는지 보여준다.
 * 앱 설정 직후 가장 먼저 확인해야 하는 화면.
 */
class ApiDiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApiDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRun.setOnClickListener { runDiagnostics() }
        runDiagnostics()
    }

    private fun runDiagnostics() {
        binding.textSummary.text = "진단 중... (API 응답 대기)"
        binding.containerResults.removeAllViews()
        binding.buttonRun.isEnabled = false

        lifecycleScope.launch {
            val results = ApiDiagnostics.runAll(this@ApiDiagnosticsActivity)
            binding.buttonRun.isEnabled = true

            val okCount = results.count { it.status == ApiDiagnostics.Status.OK }
            binding.textSummary.text = buildString {
                append("$okCount / ${results.size} 정상")
                if (okCount < results.size) {
                    appendLine()
                    append("아래 항목을 해결해야 실시간 판단이 동작합니다")
                } else {
                    appendLine()
                    append("모든 API가 정상입니다. 실시간 위험도 판단이 가능합니다")
                }
            }

            // 설정값 검증 현황을 먼저 보여준다 — API가 되더라도 값이 틀리면 결과가 틀린다
            binding.containerResults.addView(makeSection("설정값 검증 현황"))
            binding.containerResults.addView(
                makeCard(ConfigStatus.summary, "#1565C0")
            )
            listOf(
                ConfigStatus.Level.ESTIMATED,
                ConfigStatus.Level.PLACEHOLDER
            ).forEach { lv ->
                ConfigStatus.byLevel(lv).forEach { item ->
                    binding.containerResults.addView(
                        makeCard(
                            "[${lv.label}] ${item.name}\n" +
                            "현재: ${item.value}\n\n" +
                            "출처: ${item.source}\n" +
                            "영향: ${item.impact}",
                            lv.colorHex
                        )
                    )
                }
            }

            binding.containerResults.addView(makeSection("API 연결 상태"))
            results.forEach { r ->
                val color = when (r.status) {
                    ApiDiagnostics.Status.OK -> "#2E7D32"
                    ApiDiagnostics.Status.NO_KEY -> "#F57C00"
                    ApiDiagnostics.Status.NO_DATA -> "#F57C00"
                    else -> "#C62828"
                }
                val icon = if (r.status == ApiDiagnostics.Status.OK) "●" else "○"

                val text = buildString {
                    appendLine("$icon ${r.apiName}")
                    append(r.message)
                    r.sampleData?.let {
                        appendLine(); appendLine()
                        append(it)
                    }
                    r.fixHint?.let {
                        appendLine(); appendLine()
                        append("해결: $it")
                    }
                }
                binding.containerResults.addView(makeCard(text, color))
            }
        }
    }

    private fun makeSection(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 28; p.bottomMargin = 12
            layoutParams = p
        }
    }

    private fun makeCard(text: String, colorHex: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor(colorHex))
            setTypeface(Typeface.MONOSPACE)
            setPadding(24, 24, 24, 24)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.bottomMargin = 20
            layoutParams = p
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }
}
