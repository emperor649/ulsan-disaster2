package com.ulsan.disasteralert.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 시간당 관측 강수량을 누적해 3시간/12시간/일 누적치와 강우 지속시간을 산출한다.
 *
 * 과거 사례 비교(예: "차바 때 총 266mm 대비 현재 몇 %")를 하려면
 * 단발 관측치가 아니라 누적값이 필요하기 때문에 반드시 있어야 하는 모듈이다.
 *
 * ※ 프로덕션에서는 Room DB로 교체 권장. 여기서는 최소 동작용 구현.
 */
object RainAccumulator {

    private const val PREFS = "rain_accumulator"
    private const val MAX_RECORDS = 24 * 3 // 3일치 보관

    data class RainRecord(val timestampMillis: Long, val hourlyMm: Double)

    data class Summary(
        val cumulative3h: Double,
        val cumulative12h: Double,
        val cumulative24h: Double,
        val cumulative72h: Double,
        val rainDurationHours: Int  // 연속으로 비가 온 시간
    )

    fun record(context: Context, region: String, hourlyMm: Double) {
        val records = load(context, region).toMutableList()
        records.add(RainRecord(System.currentTimeMillis(), hourlyMm))
        while (records.size > MAX_RECORDS) records.removeAt(0)
        save(context, region, records)
    }

    fun summarize(context: Context, region: String): Summary {
        val records = load(context, region).sortedBy { it.timestampMillis }
        val now = System.currentTimeMillis()

        fun sumWithin(hours: Int): Double {
            val cutoff = now - hours * 3600_000L
            return records.filter { it.timestampMillis >= cutoff }.sumOf { it.hourlyMm }
        }

        // 최근부터 거슬러 올라가며 강수(0.1mm 이상)가 끊기지 않은 구간 길이 계산
        var duration = 0
        for (r in records.reversed()) {
            if (r.hourlyMm >= 0.1) duration++ else break
        }

        return Summary(
            cumulative3h = sumWithin(3),
            cumulative12h = sumWithin(12),
            cumulative24h = sumWithin(24),
            cumulative72h = sumWithin(72),
            rainDurationHours = duration
        )
    }

    private fun load(context: Context, region: String): List<RainRecord> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(region, "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            RainRecord(obj.getLong("t"), obj.getDouble("mm"))
        }
    }

    private fun save(context: Context, region: String, records: List<RainRecord>) {
        val array = JSONArray()
        records.forEach {
            array.put(JSONObject().apply {
                put("t", it.timestampMillis)
                put("mm", it.hourlyMm)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(region, array.toString()).apply()
    }
}
