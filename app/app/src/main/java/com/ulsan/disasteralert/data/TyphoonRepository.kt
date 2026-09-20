package com.ulsan.disasteralert.data

import android.content.Context
import com.ulsan.disasteralert.network.ApiClient
import java.text.SimpleDateFormat
import java.util.*

/**
 * 태풍 정보 조회 및 조위 연계.
 *
 * 태풍 정보는 보통 3시간 간격으로 갱신되므로 자주 부를 필요가 없다.
 * 조석예보와 함께 묶어 최근접 시각의 만조 겹침을 판정한다.
 */
object TyphoonRepository {

    private const val PREFS = "typhoon_cache"
    private const val KEY_LAST_FETCH = "last_fetch"

    /** 태풍 정보 갱신 주기 (분). 발표가 3시간 간격이라 1시간이면 충분 */
    private const val REFRESH_INTERVAL_MINUTES = 60

    suspend fun getApproaches(
        context: Context,
        kmaKey: String,
        tideKey: String
    ): List<TyphoonApproach> {
        if (kmaKey.isBlank()) return emptyList()

        val typhoons = runCatching { fetchActiveTyphoons(kmaKey) }.getOrNull().orEmpty()
        if (typhoons.isEmpty()) return emptyList()

        // 최근접 시각을 포함하는 기간의 조석예보를 확보한다.
        // 태풍 예보는 최대 5일 앞까지 나오므로 그만큼 조석도 받아야 한다.
        val tideExtremes = if (tideKey.isNotBlank()) {
            runCatching { fetchTideRange(context, tideKey, days = 6) }.getOrNull().orEmpty()
        } else emptyList()

        return typhoons.mapNotNull { TyphoonAnalyzer.analyze(it, tideExtremes) }
    }

    /**
     * API허브에서 평문 응답을 받아 파싱한다.
     * mode=2로 현재 분석정보와 예측정보를 함께 받는다.
     */
    private suspend fun fetchActiveTyphoons(apiKey: String): List<Typhoon> {
        val body = ApiClient.typhoonApi.getTyphoonRaw(authKey = apiKey)
        val raw = body.string()

        // 발생 중인 태풍이 없으면 데이터 행 없이 헤더만 옵니다 — 오류가 아닙니다
        if (raw.isBlank()) return emptyList()

        return TyphoonTextParser.parse(raw)
    }

    /**
     * 여러 날짜의 조석예보를 한 번에 확보한다.
     * 태풍 최근접이 며칠 뒤일 수 있어 하루치만으로는 부족하다.
     */
    private suspend fun fetchTideRange(
        context: Context,
        tideKey: String,
        days: Int
    ): List<TideExtreme> {
        val result = mutableListOf<TideExtreme>()
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val station = TideRepository.defaultStationForReplay()

        repeat(days) {
            val dateStr = fmt.format(cal.time)
            runCatching {
                ApiClient.tideApi.getTidePrediction(tideKey, station.code, dateStr)
                    .response?.body?.items?.item.orEmpty()
                    .forEach { item ->
                        val timeStr = item.effectiveTime ?: return@forEach
                        val level = item.effectiveLevel?.toDoubleOrNull()?.toInt() ?: return@forEach
                        val code = item.effectiveCode ?: return@forEach
                        val millis = runCatching {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).parse(timeStr)?.time
                        }.getOrNull() ?: return@forEach
                        val isHigh = code.contains("고") || code.equals("H", true)
                        result.add(TideExtreme(millis, level, isHigh))
                    }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    fun shouldRefresh(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FETCH, 0L)
        return System.currentTimeMillis() - last > REFRESH_INTERVAL_MINUTES * 60_000L
    }

    private const val KEY_ACTIVE_THREAT = "active_threat"

    /** 태풍 영향권 여부 — 조위 폭풍해일 보정에 사용 */
    fun hasActiveThreat(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE_THREAT, false)

    fun setActiveThreat(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVE_THREAT, active).apply()
    }

    fun markRefreshed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_FETCH, System.currentTimeMillis()).apply()
    }
}
