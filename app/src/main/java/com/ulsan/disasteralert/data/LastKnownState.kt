package com.ulsan.disasteralert.data

import android.content.Context
import org.json.JSONObject

/**
 * 마지막 정상 수신 상태 캐시.
 *
 * 호우 특보가 나면 전국에서 조회가 몰려 공공 API가 느려지거나 실패한다.
 * 하필 가장 필요한 순간에 그렇다.
 *
 * 조회에 실패했을 때 조용히 넘어가면 사용자는 "아무 일 없음"으로 오해한다.
 * 그래서 마지막 정상값과 그 시각을 남겨두고, 데이터가 오래됐으면 명시적으로 알린다.
 */
object LastKnownState {

    private const val PREFS = "last_known_state"

    /** 이 시간(분)을 넘긴 데이터는 '오래됨'으로 표시 */
    private const val STALE_THRESHOLD_MINUTES = 45

    data class Cached(
        val district: String,
        val riskScore: Int,
        val riskLevel: String,
        val hourlyRainMm: Double,
        val cumulative24hMm: Double,
        val summary: String,
        val timestampMillis: Long
    ) {
        val ageMinutes: Int
            get() = ((System.currentTimeMillis() - timestampMillis) / 60000).toInt()

        val isStale: Boolean
            get() = ageMinutes > STALE_THRESHOLD_MINUTES
    }

    fun save(context: Context, cached: Cached) {
        val json = JSONObject().apply {
            put("d", cached.district); put("s", cached.riskScore)
            put("lv", cached.riskLevel); put("hr", cached.hourlyRainMm)
            put("c24", cached.cumulative24hMm); put("sum", cached.summary)
            put("t", cached.timestampMillis)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(cached.district, json.toString()).apply()
    }

    fun load(context: Context, district: String): Cached? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(district, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            Cached(
                district = o.getString("d"),
                riskScore = o.getInt("s"),
                riskLevel = o.getString("lv"),
                hourlyRainMm = o.getDouble("hr"),
                cumulative24hMm = o.getDouble("c24"),
                summary = o.getString("sum"),
                timestampMillis = o.getLong("t")
            )
        }.getOrNull()
    }

    /**
     * 연속 실패 횟수를 센다. 일정 횟수 이상 실패하면 사용자에게 알린다 —
     * "조용한 실패"가 가장 위험하다.
     */
    fun recordFailure(context: Context, district: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "fail_$district"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        return count
    }

    fun clearFailures(context: Context, district: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("fail_$district", 0).apply()
    }

    fun failureCount(context: Context, district: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("fail_$district", 0)

    /**
     * 연속 실패가 이 횟수를 넘으면 "데이터 수신 중단" 알림을 보낸다.
     * 15분 폴링 기준 3회 = 약 45분간 데이터 없음.
     */
    const val FAILURE_ALERT_THRESHOLD = 3
}
