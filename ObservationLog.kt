package com.ulsan.disasteralert.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 실측 데이터 피드백 루프.
 *
 * 과거 사례 재현으로 초기 임계값을 잡았다면, 그 다음은 실제 운영 데이터로 계속 보정해야 한다.
 * 과거 사례는 3~4건뿐이고 보간 추정치가 섞여 있어서, 그것만으로는 정밀도에 한계가 있다.
 *
 * 두 가지를 기록한다.
 *   1) 매 폴링마다의 관측값 + 그때 산출된 위험도 (자동)
 *   2) 실제로 피해가 발생했는지 (사용자 입력)
 *
 * 이 둘을 대조하면 "경보를 냈는데 아무 일도 없었다"(과민)와
 * "피해가 났는데 경보가 없었다"(누락)를 실제 데이터로 셀 수 있다.
 */
object ObservationLog {

    private const val PREFS = "observation_log"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 2000   // 약 3주치 (15분 폴링 기준)

    /**
     * 한 시점의 관측 스냅샷.
     * 나중에 임계값을 재계산하려면 판단 근거가 된 값들이 다 있어야 한다.
     */
    data class Snapshot(
        val timestampMillis: Long,
        val district: String,
        val hourlyRainMm: Double,
        val cumulative24hMm: Double,
        val riverLevelM: Double?,
        val riverStage: String?,
        val tideLevelCm: Int?,
        val nearHighTide: Boolean,
        val riskScore: Int,
        val riskLevel: String,
        val alertSent: Boolean,
        /** 사용자가 나중에 입력하는 값 — 실제 피해 발생 여부 */
        val damageReported: Boolean = false,
        val damageNote: String = ""
    )

    fun record(context: Context, snapshot: Snapshot) {
        val records = load(context).toMutableList()
        records.add(snapshot)
        while (records.size > MAX_RECORDS) records.removeAt(0)
        save(context, records)
    }

    /**
     * 특정 시각 전후의 기록에 피해 발생 사실을 표시한다.
     * 침수가 났을 때 사용자가 "여기 잠겼다"고 입력하면,
     * 그 시점 관측값이 곧 실제 침수 임계값이 된다.
     */
    fun reportDamage(
        context: Context,
        district: String,
        occurredAtMillis: Long,
        note: String,
        windowMinutes: Int = 60
    ): Int {
        val records = load(context).toMutableList()
        val window = windowMinutes * 60_000L
        var updated = 0

        for (i in records.indices) {
            val r = records[i]
            if (r.district == district &&
                kotlin.math.abs(r.timestampMillis - occurredAtMillis) <= window
            ) {
                records[i] = r.copy(damageReported = true, damageNote = note)
                updated++
            }
        }
        if (updated > 0) save(context, records)
        return updated
    }

    fun load(context: Context): List<Snapshot> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Snapshot(
                timestampMillis = o.getLong("t"),
                district = o.getString("d"),
                hourlyRainMm = o.getDouble("hr"),
                cumulative24hMm = o.getDouble("c24"),
                riverLevelM = if (o.isNull("rl")) null else o.getDouble("rl"),
                riverStage = o.optString("rs").ifEmpty { null },
                tideLevelCm = if (o.isNull("tc")) null else o.getInt("tc"),
                nearHighTide = o.optBoolean("nht", false),
                riskScore = o.getInt("s"),
                riskLevel = o.getString("lv"),
                alertSent = o.optBoolean("a", false),
                damageReported = o.optBoolean("dmg", false),
                damageNote = o.optString("note", "")
            )
        }
    }

    private fun save(context: Context, records: List<Snapshot>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("t", r.timestampMillis); put("d", r.district)
                put("hr", r.hourlyRainMm); put("c24", r.cumulative24hMm)
                if (r.riverLevelM != null) put("rl", r.riverLevelM) else put("rl", JSONObject.NULL)
                put("rs", r.riverStage ?: "")
                if (r.tideLevelCm != null) put("tc", r.tideLevelCm) else put("tc", JSONObject.NULL)
                put("nht", r.nearHighTide)
                put("s", r.riskScore); put("lv", r.riskLevel); put("a", r.alertSent)
                put("dmg", r.damageReported); put("note", r.damageNote)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
