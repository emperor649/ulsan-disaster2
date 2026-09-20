package com.ulsan.disasteralert.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ulsan.disasteralert.data.Events
import com.ulsan.disasteralert.databinding.ActivityEventsBinding
import com.ulsan.disasteralert.util.UlsanGridPresets
import java.text.SimpleDateFormat
import java.util.*

/**
 * 다중운집 행사 등록·조회.
 *
 * 판단 기능은 없습니다. 알림에 "이날 이 지역에 행사가 있다"를 덧붙이기 위한 입력 화면입니다.
 */
class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Events.pruneOld(this)
        binding.buttonAdd.setOnClickListener { showAddDialog() }
        render()
    }

    private fun render() {
        val list = Events.all(this)
        binding.container.removeAllViews()

        binding.textSummary.text = if (list.isEmpty())
            "등록된 행사가 없습니다"
        else
            "등록 ${list.size}건 · 종료 7일 후 자동 삭제"

        if (list.isEmpty()) {
            binding.container.addView(
                makeCard(
                    "다중운집 행사를 등록해 두면 알림에 참고 정보로 표시됩니다.\n\n" +
                    "같은 호우여도 사람이 많을 때와 없을 때는 대응이 다릅니다.\n" +
                    "앱이 판단하지는 않고, 상황 인지를 돕는 용도입니다.",
                    "#757575", 13f
                )
            )
            return
        }

        val now = System.currentTimeMillis()
        val today = list.filter { it.isOnDay(now) }
        val future = list.filter { it.startMillis > now && !it.isOnDay(now) }
        val past = list.filter { it.endMillis < now && !it.isOnDay(now) }

        if (today.isNotEmpty()) { addHeader("오늘 ${today.size}건"); today.forEach { addEvent(it, "#C62828") } }
        if (future.isNotEmpty()) { addHeader("예정 ${future.size}건"); future.forEach { addEvent(it, "#1565C0") } }
        if (past.isNotEmpty()) { addHeader("종료 ${past.size}건"); past.forEach { addEvent(it, "#9E9E9E") } }
    }

    private fun addEvent(e: Events.Event, color: String) {
        val text = buildString {
            appendLine(e.name)
            appendLine("${e.periodLabel} · ${e.place} (${e.district})")
            append(e.crowdLabel)
            if (e.organizer.isNotBlank()) append(" · ${e.organizer}")
            if (e.note.isNotBlank()) { appendLine(); append(e.note) }
        }
        val card = makeCard(text, color, 13f)
        card.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("삭제")
                .setMessage("${e.name}을(를) 삭제할까요?")
                .setPositiveButton("삭제") { _, _ -> Events.remove(this, e.id); render() }
                .setNegativeButton("취소", null)
                .show()
            true
        }
        binding.container.addView(card)
    }

    private fun showAddDialog() {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0)
        }
        val end = (start.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 18) }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val nameIn = EditText(this).apply { hint = "행사명" }
        val placeIn = EditText(this).apply { hint = "장소 (예: 태화강 국가정원)" }
        val crowdIn = EditText(this).apply {
            hint = "예상 인원 (숫자, 모르면 비워두세요)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val orgIn = EditText(this).apply { hint = "주최 (선택)" }
        val noteIn = EditText(this).apply { hint = "비고 (선택)" }

        val districts = UlsanGridPresets.districts
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@EventsActivity,
                android.R.layout.simple_spinner_dropdown_item, districts)
        }

        val fmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
        val startLabel = TextView(this).apply { setPadding(0, 24, 0, 8) }
        val endLabel = TextView(this).apply { setPadding(0, 8, 0, 8) }
        fun refresh() {
            startLabel.text = "시작: ${fmt.format(start.time)}"
            endLabel.text = "종료: ${fmt.format(end.time)}"
        }
        refresh()

        fun pick(cal: Calendar, after: () -> Unit) {
            DatePickerDialog(this, { _, y, mo, d ->
                cal.set(y, mo, d)
                TimePickerDialog(this, { _, h, mi ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, mi); after()
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        startLabel.setOnClickListener { pick(start) { refresh() } }
        endLabel.setOnClickListener { pick(end) { refresh() } }

        listOf(nameIn, placeIn, spinner, startLabel, endLabel, crowdIn, orgIn, noteIn)
            .forEach { box.addView(it) }
        box.addView(TextView(this).apply {
            text = "시작·종료 시각을 탭하면 변경됩니다"
            textSize = 11f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, 12, 0, 0)
        })

        AlertDialog.Builder(this)
            .setTitle("행사 등록")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("등록") { _, _ ->
                val name = nameIn.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "행사명을 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (end.timeInMillis <= start.timeInMillis) {
                    Toast.makeText(this, "종료가 시작보다 빠릅니다", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Events.add(this, Events.Event(
                    id = Events.newId(),
                    name = name,
                    place = placeIn.text.toString().trim(),
                    district = districts[spinner.selectedItemPosition],
                    startMillis = start.timeInMillis,
                    endMillis = end.timeInMillis,
                    expectedCrowd = crowdIn.text.toString().toIntOrNull(),
                    organizer = orgIn.text.toString().trim(),
                    note = noteIn.text.toString().trim()
                ))
                render()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addHeader(title: String) {
        binding.container.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = 24; p.bottomMargin = 10
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = 12
            layoutParams = p
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }
}
