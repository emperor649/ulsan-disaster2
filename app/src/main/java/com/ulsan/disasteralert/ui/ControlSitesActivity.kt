package com.ulsan.disasteralert.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.databinding.ActivityControlSitesBinding

/**
 * 대응계획서 통제지점 현황.
 *
 * 현재 관측값을 기준으로 어느 지점이 통제 대상인지 보여줍니다.
 * "중구 위험도 14점"보다 "우정동 지하차도 기준 도달"이
 * 현장에서 훨씬 쓸모 있는 정보이기 때문입니다.
 */
class ControlSitesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityControlSitesBinding
    private val districts = listOf("중구", "남구", "동구", "북구", "울주군")

    private var hourly = 0.0
    private var cumulative = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControlSitesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spinnerDistrict.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, districts
        )
        binding.spinnerDistrict.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long
                ) = render(districts[pos])
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }

        binding.seekHourly.setOnSeekBarChangeListener(simpleSeek { v ->
            hourly = v.toDouble()
            binding.textHourly.text = "시우량 ${v}mm"
            render(districts[binding.spinnerDistrict.selectedItemPosition])
        })
        binding.seekCumulative.setOnSeekBarChangeListener(simpleSeek { v ->
            cumulative = (v * 5).toDouble()
            binding.textCumulative.text = "누적강우 ${v * 5}mm"
            render(districts[binding.spinnerDistrict.selectedItemPosition])
        })

        render("중구")
    }

    private fun render(district: String) {
        // 시간별 누적은 슬라이더 값에서 근사한다 (실제 앱은 관측 이력에서 산출)
        val obs = SiteControlEvaluator.Observation(
            hourlyRainMm = hourly,
            cumulativeRainMm = cumulative,
            sustainedHeavyRate = hourly >= 50.0,
            cumulative3hMm = minOf(cumulative, hourly * 3),
            cumulative12hMm = minOf(cumulative, hourly * 12),
            dailyRainMm = cumulative,
            continuousRainMm = cumulative,
            heavyRainWarning = hourly >= 30.0,
            heavyRainAdvisory = hourly >= 20.0
        )
        val statuses = SiteControlEvaluator.evaluate(district, obs)

        binding.textSummary.text = SiteControlEvaluator.summarize(statuses, obs)
        binding.container.removeAllViews()

        val triggered = SiteControlEvaluator.triggered(statuses)
        val imminent = SiteControlEvaluator.imminent(statuses)
        val undet = SiteControlEvaluator.undeterminable(statuses)

        if (triggered.isNotEmpty()) {
            addHeader("통제 대상 ${triggered.size}개소")
            triggered.forEach { addSite(it, true) }
        }
        if (imminent.isNotEmpty()) {
            addHeader("임박 ${imminent.size}개소")
            imminent.forEach { addSite(it, false) }
        }
        // 지하차도 — 전수 기준. 통제지점에 실리지 않은 곳까지 포함한다
        val underpasses = SiteControlEvaluator.underpassesToCheck(district, obs)
        if (underpasses.isNotEmpty()) {
            addHeader("지하차도 확인 필요 ${underpasses.size}개소")
            binding.container.addView(
                makeCard(
                    "침수심 센서가 현장 차단기 전용이라 외부에서 값을 받을 수 없습니다.\n" +
                    "강수 여건상 위험할 수 있으니 CCTV 또는 현장 순찰로 확인하세요.\n\n" +
                    "※ 통제지점에 지정되지 않은 지하차도도 포함되어 있습니다.\n" +
                    "   지정 여부와 무관하게 침수 위험은 있습니다.",
                    "#B71C1C", 13f
                )
            )
            underpasses.forEach { u ->
                val hasCriterion = Underpasses.hasControlCriterion(u)
                val text = buildString {
                    append("[${u.grade.label}] ${u.name}")
                    appendLine()
                    append("관리: ${u.organization}")
                    if (!hasCriterion) {
                        appendLine()
                        append("※ 공식 통제기준 미지정 — 판단 근거가 더 부족합니다")
                    }
                }
                binding.container.addView(makeCard(text, u.grade.colorHex, 13f))
            }
        }

        val fieldCheck = SiteControlEvaluator.needsFieldCheck(statuses, obs)
        if (fieldCheck.isNotEmpty()) {
            addHeader("기타 판정 불가 ${fieldCheck.size}개소")
            binding.container.addView(
                makeCard(
                    "강수 여건상 위험할 수 있으나 자동 판정이 불가능한 지점입니다.\n\n" +
                    "침수심 센서가 현장 차단기에만 연결되어 있어 외부에서 값을 받을 수 없습니다.\n" +
                    "CCTV 또는 현장 순찰로 직접 확인하세요.\n\n" +
                    "※ 앱은 이 지점들을 '이상 없음'으로 판단하지 않습니다.",
                    "#B71C1C", 13f
                )
            )
            fieldCheck.forEach { addSite(it, false) }
        }

        if (undet.isNotEmpty()) {
            addHeader("판정 불가 ${undet.size}개소")
            binding.container.addView(
                makeCard(
                    "외부 데이터가 없어 판정할 수 없는 지점입니다.\n" +
                    "지하차도 침수센서(연계 불가), 산림청 토양함수지수, 하천 교량상부 수위 등.\n" +
                    "강수량이 기준에 못 미쳐도 이 지점들은 별도 확인이 필요합니다.",
                    "#8D6E63", 12f
                )
            )
            undet.take(10).forEach { addSite(it, false) }
        }

        // 전체 지점 목록
        addHeader("${district} 전체 ${statuses.size}개소")
        val byHazard = statuses.groupBy { it.site.hazard }
        byHazard.forEach { (h, list) ->
            binding.container.addView(
                makeCard(
                    "${h.icon} ${h.displayName} ${list.size}개소\n" +
                    list.map { it.site.criterion.label }.distinct()
                        .joinToString("\n") { "· $it" },
                    "#546E7A", 12.5f
                )
            )
        }
    }

    private fun addSite(st: SiteControlEvaluator.SiteStatus, isTriggered: Boolean) {
        val color = when {
            isTriggered && st.site.isCritical -> "#B71C1C"
            isTriggered -> "#E65100"
            st.progressPercent == null -> "#8D6E63"
            else -> "#F57C00"
        }
        val text = buildString {
            if (st.site.isCritical) append("‼ ")
            appendLine("${st.site.name}  [${st.site.hazard.displayName}]")
            appendLine(st.site.address)
            appendLine()
            appendLine("기준: ${st.site.criterion.label}")
            appendLine("관측: ${st.observed}")
            st.progressPercent?.let { appendLine("도달률: ${it}%") }
            appendLine()
            appendLine("통제범위: ${st.site.scope}")
            append(st.reason)
        }
        binding.container.addView(makeCard(text, color, 13f))
    }

    private fun addHeader(title: String) {
        binding.container.addView(TextView(this).apply {
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
        })
    }

    private fun makeCard(text: String, colorHex: String, sizeSp: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
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

    private fun simpleSeek(onChange: (Int) -> Unit) =
        object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, fromUser: Boolean) =
                onChange(v)
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        }
}
