package com.ulsan.disasteralert.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.databinding.ActivityCalibrationBinding
import com.ulsan.disasteralert.util.UlsanGridPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 임계값 검증 화면.
 *
 * 과거 사례 재현 결과와 실측 데이터를 함께 보여주고,
 * 어느 쪽을 어떻게 조정해야 하는지 제안한다.
 *
 * 여기서 '피해 보고' 입력도 받는다 — 실제 침수가 났을 때 입력해두면
 * 그 시점 관측값이 실제 침수 임계값으로 기록된다.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRun.setOnClickListener { runCalibration() }
        binding.buttonReportDamage.setOnClickListener { showDamageReportDialog() }
        runCalibration()
    }

    private fun runCalibration() {
        binding.textSummary.text = "검증 중..."
        binding.containerContent.removeAllViews()

        lifecycleScope.launch {
            val report = withContext(Dispatchers.Default) {
                ThresholdCalibrator.calibrate(this@CalibrationActivity)
            }
            render(report)
        }
    }

    private fun render(report: ThresholdCalibrator.Report) {
        binding.textSummary.text = report.overallSummary
        binding.containerContent.removeAllViews()

        // ── 1. 과거 사례 재현 결과 ──
        addSectionHeader("과거 사례 재현")
        report.replayResults.forEach { r ->
            val text = buildString {
                appendLine("${r.scenario.name}  [${r.verdict.displayName}]")
                appendLine()
                r.leadTimeMinutes?.let { appendLine("리드타임: ${it}분") }
                r.firstWarningAtMinute?.let { appendLine("첫 경고: 시작 후 ${it}분") }
                r.firstDangerAtMinute?.let { appendLine("첫 위험: 시작 후 ${it}분") }
                r.actualDamageAtMinute?.let { appendLine("실제 피해: 시작 후 ${it}분") }
                appendLine()
                append(r.diagnosis)
            }
            val card = makeCard(text, r.verdict.colorHex)
            card.setOnClickListener { showFrameDetail(r) }
            binding.containerContent.addView(card)
        }

        // ── 2. 실측 데이터 현황 ──
        addSectionHeader("실측 데이터")
        binding.containerContent.addView(
            makeCard(
                "누적 관측 ${report.observationCount}건 (${report.observationDays}일)\n\n" +
                "폴링할 때마다 관측값과 산출된 위험도가 함께 기록됩니다.\n" +
                "실제 침수가 발생하면 아래 '피해 보고' 버튼으로 입력하세요 — " +
                "그 시점 관측값이 곧 실제 침수 임계값이 됩니다.",
                "#424242"
            )
        )

        // ── 3. 조치 항목 ──
        addSectionHeader("조치 항목")
        report.findings.forEach { f ->
            val text = buildString {
                appendLine("[${f.severity.displayName}] ${f.title}")
                appendLine()
                appendLine(f.evidence)
                appendLine()
                append("→ ${f.recommendation}")
            }
            binding.containerContent.addView(makeCard(text, f.severity.colorHex))
        }
    }

    /** 시나리오를 탭하면 프레임별 위험도 전개를 보여준다 */
    private fun showFrameDetail(result: ReplayEngine.ScenarioResult) {
        val detail = buildString {
            appendLine(result.scenario.description)
            appendLine()
            appendLine("시간  강수(시간당/누적)  위험도")
            appendLine("─".repeat(34))
            result.frames.forEach { f ->
                val h = f.frame.minutesFromStart / 60
                val m = f.frame.minutesFromStart % 60
                append(String.format("%02d:%02d  %5.1f/%6.1f  %2d점 %s",
                    h, m, f.frame.hourlyRainMm, f.frame.cumulativeRainMm,
                    f.riskScore, f.riskLevel.displayName))
                if (f.frame.actualDamageOccurred) append("  ← 실제 피해")
                appendLine()
                if (f.frame.damageNote.isNotBlank()) {
                    appendLine("        ${f.frame.damageNote}")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(result.scenario.name)
            .setMessage(detail)
            .setPositiveButton("닫기", null)
            .show()
    }

    /** 실제 피해 발생을 기록 — 임계값 보정의 핵심 입력 */
    private fun showDamageReportDialog() {
        val districts = UlsanGridPresets.districts
        var selectedDistrict = districts.first()
        val cal = Calendar.getInstance()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }

        val spinner = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CalibrationActivity,
                android.R.layout.simple_spinner_dropdown_item, districts
            )
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    selectedDistrict = districts[pos]
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        container.addView(spinner)

        val timeLabel = TextView(this).apply {
            text = "발생 시각: 지금 (${
                java.text.SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(cal.time)
            })"
            setPadding(0, 32, 0, 16)
        }
        container.addView(timeLabel)

        val timeButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "시각 변경"
            setOnClickListener {
                DatePickerDialog(this@CalibrationActivity, { _, y, mo, d ->
                    cal.set(y, mo, d)
                    TimePickerDialog(this@CalibrationActivity, { _, h, min ->
                        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                        timeLabel.text = "발생 시각: ${
                            java.text.SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(cal.time)
                        }"
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        container.addView(timeButton)

        val noteInput = android.widget.EditText(this).apply {
            hint = "무엇이 침수/피해를 입었는지 (예: 우정동 지하차도 통행 불가)"
            setPadding(0, 32, 0, 0)
        }
        container.addView(noteInput)

        AlertDialog.Builder(this)
            .setTitle("피해 보고")
            .setMessage("실제 침수·피해가 발생한 시점을 기록합니다. 임계값 보정에 사용됩니다.")
            .setView(container)
            .setPositiveButton("기록") { _, _ ->
                val note = noteInput.text.toString().ifBlank { "피해 발생" }
                val updated = ObservationLog.reportDamage(
                    this, selectedDistrict, cal.timeInMillis, note
                )
                if (updated > 0) {
                    Toast.makeText(this, "관측 기록 ${updated}건에 반영했습니다", Toast.LENGTH_SHORT).show()
                    runCalibration()
                } else {
                    Toast.makeText(
                        this,
                        "해당 시각 전후의 관측 기록이 없습니다.\n앱이 그 시간에 폴링하지 않았을 수 있습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addSectionHeader(title: String) {
        binding.containerContent.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 24; p.bottomMargin = 12
            layoutParams = p
        })
    }

    private fun makeCard(text: String, colorHex: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor(colorHex))
            setPadding(24, 24, 24, 24)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.bottomMargin = 16
            layoutParams = p
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }
}
