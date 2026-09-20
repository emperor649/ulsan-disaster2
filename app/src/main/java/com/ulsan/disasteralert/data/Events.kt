package com.ulsan.disasteralert.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 다중운집 행사 — 참고 정보.
 *
 * ── 판단하지 않습니다 ──
 * 이 모듈은 위험도 점수나 통제 판정에 개입하지 않습니다.
 * "이날 이 지역에 행사가 있다"는 사실만 알림에 덧붙입니다.
 *
 * 같은 호우여도 사람이 많을 때와 없을 때는 다른 상황입니다.
 * 그 차이를 사람이 판단할 수 있게 재료만 제공하는 것이 이 모듈의 역할입니다.
 *
 * 인원 규모에 따라 자동으로 경보를 올리거나 하지 않습니다 —
 * 그건 근거 없는 판단이 되고, 현장 사정은 앱이 알 수 없습니다.
 */
object Events {

    private const val PREFS = "events"
    private const val KEY_LIST = "list"

    data class Event(
        val id: String,
        val name: String,
        val place: String,
        val district: String,
        /** 시작 일시 (millis) */
        val startMillis: Long,
        /** 종료 일시. 당일 행사면 같은 날 늦은 시각 */
        val endMillis: Long,
        /** 예상 인원. 모르면 null */
        val expectedCrowd: Int? = null,
        val organizer: String = "",
        val note: String = ""
    ) {
        val isOutdoor: Boolean get() = true  // 현재는 야외 행사만 다룹니다

        fun overlaps(fromMillis: Long, toMillis: Long): Boolean =
            startMillis <= toMillis && endMillis >= fromMillis

        /** 오늘 열리는 행사인지 */
        fun isOnDay(dayMillis: Long): Boolean {
            val cal = Calendar.getInstance().apply { timeInMillis = dayMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            val end = start + 24 * 3600_000L - 1
            return overlaps(start, end)
        }

        val crowdLabel: String
            get() = expectedCrowd?.let {
                when {
                    it >= 10000 -> "${it / 10000}만명 규모"
                    it >= 1000 -> "${it / 1000}천명 규모"
                    else -> "${it}명 규모"
                }
            } ?: "인원 미기재"

        val periodLabel: String
            get() {
                val d = SimpleDateFormat("M/d", Locale.KOREA)
                val t = SimpleDateFormat("HH:mm", Locale.KOREA)
                val sameDay = d.format(Date(startMillis)) == d.format(Date(endMillis))
                return if (sameDay)
                    "${d.format(Date(startMillis))} ${t.format(Date(startMillis))}~${t.format(Date(endMillis))}"
                else
                    "${d.format(Date(startMillis))}~${d.format(Date(endMillis))}"
            }

        /** 알림 한 줄 요약 */
        val summary: String
            get() = buildString {
                append(name)
                append(" (")
                append(place)
                if (expectedCrowd != null) { append(", "); append(crowdLabel) }
                append(")")
            }
    }

    // ── 저장소 ──

    fun all(context: Context): List<Event> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Event(
                id = o.getString("id"),
                name = o.getString("name"),
                place = o.getString("place"),
                district = o.getString("district"),
                startMillis = o.getLong("start"),
                endMillis = o.getLong("end"),
                expectedCrowd = if (o.isNull("crowd")) null else o.getInt("crowd"),
                organizer = o.optString("org", ""),
                note = o.optString("note", "")
            )
        }.sortedBy { it.startMillis }
    }

    fun add(context: Context, e: Event) {
        val list = all(context).toMutableList()
        list.removeAll { it.id == e.id }   // 같은 id면 갱신
        list.add(e)
        save(context, list)
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    /** 지난 행사 정리 — 종료 후 7일이 지나면 삭제 */
    fun pruneOld(context: Context) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600_000
        val kept = all(context).filter { it.endMillis >= cutoff }
        if (kept.size != all(context).size) save(context, kept)
    }

    private fun save(context: Context, list: List<Event>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("name", e.name); put("place", e.place)
                put("district", e.district)
                put("start", e.startMillis); put("end", e.endMillis)
                if (e.expectedCrowd != null) put("crowd", e.expectedCrowd) else put("crowd", JSONObject.NULL)
                put("org", e.organizer); put("note", e.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }

    // ── 조회 ──

    /** 오늘 해당 구·군에서 열리는 행사 */
    fun todayIn(context: Context, district: String): List<Event> {
        val now = System.currentTimeMillis()
        return all(context).filter { it.district == district && it.isOnDay(now) }
    }

    /** 앞으로 N시간 내에 진행 중이거나 시작하는 행사 */
    fun upcomingIn(context: Context, district: String, hours: Int = 24): List<Event> {
        val now = System.currentTimeMillis()
        val until = now + hours * 3600_000L
        return all(context).filter { it.district == district && it.overlaps(now, until) }
    }

    /**
     * 알림에 붙일 참고 문구.
     *
     * 판단을 담지 않습니다. "행사가 있다"까지만 말하고,
     * 취소·축소 여부는 사람이 정합니다.
     */
    fun noticeFor(context: Context, district: String): String? {
        val events = upcomingIn(context, district, 24)
        if (events.isEmpty()) return null

        return buildString {
            append("※ 관내 행사 ${events.size}건")
            events.take(3).forEach {
                appendLine()
                append("  · ${it.periodLabel} ${it.summary}")
            }
            if (events.size > 3) {
                appendLine()
                append("  외 ${events.size - 3}건")
            }
        }
    }

    fun newId(): String = "evt_" + System.currentTimeMillis()
}
