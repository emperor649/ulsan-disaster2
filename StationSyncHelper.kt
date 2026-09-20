package com.ulsan.disasteralert.data

import android.content.Context
import com.ulsan.disasteralert.network.ApiClient
import com.ulsan.disasteralert.network.StationInfoItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 홍수통제소 API에서 울산 지역 수위관측소 제원을 받아와 로컬에 저장한다.
 *
 * UlsanRiverStations.kt의 하드코딩 값(TODO_ 코드, 임시 기준홍수위)을
 * 실제 API 값으로 대체하기 위한 모듈이다. 앱 최초 실행 시 1회,
 * 이후에는 월 1회 정도만 갱신하면 충분하다 (관측소 제원은 자주 바뀌지 않음).
 */
object StationSyncHelper {

    private const val PREFS = "river_stations"
    private const val KEY_SYNCED = "synced_stations"
    private const val KEY_LAST_SYNC = "last_sync_millis"

    /** 울산 관측소를 식별할 키워드 */
    private val ULSAN_KEYWORDS = listOf("울산", "태화강", "동천", "회야강", "외황강")
    private val TARGET_STATION_NAMES = listOf("사연", "태화교", "병영", "구영", "삼호")

    suspend fun syncUlsanStations(context: Context, apiKey: String): List<RiverStation> {
        val response = ApiClient.floodApi.getStationInfo(apiKey)
        val all = response.content.orEmpty()

        val ulsanStations = all.filter { item ->
            val addr = item.addr ?: ""
            val name = item.obsnm ?: ""
            ULSAN_KEYWORDS.any { addr.contains(it) || name.contains(it) }
        }

        val mapped = ulsanStations.mapNotNull { it.toRiverStation() }
        save(context, mapped)
        return mapped
    }

    private fun StationInfoItem.toRiverStation(): RiverStation? {
        val name = obsnm ?: return null
        val lat = lat?.toDoubleOrNull() ?: return null
        val lon = lon?.toDoubleOrNull() ?: return null

        // 기준홍수위가 없는 관측소는 위험도 판단에 쓸 수 없으므로 제외
        val danger = srswl?.toDoubleOrNull() ?: return null
        val alert = almwl?.toDoubleOrNull() ?: (danger * 0.85)
        val warning = wrnwl?.toDoubleOrNull() ?: (danger * 0.70)
        val attention = attwl?.toDoubleOrNull() ?: (danger * 0.55)

        val district = when {
            addr?.contains("중구") == true -> "중구"
            addr?.contains("남구") == true -> "남구"
            addr?.contains("동구") == true -> "동구"
            addr?.contains("북구") == true -> "북구"
            addr?.contains("울주") == true -> "울주군"
            else -> "울산"
        }

        // 하드코딩 정의에 있는 지점이면 protectedAreas와 notes를 승계
        val known = UlsanRiverStations.stations.find { name.contains(it.name.substringAfter("(").substringBefore(")")) }

        return RiverStation(
            code = wlobscd,
            name = name,
            riverName = known?.riverName ?: name.substringBefore("("),
            district = district,
            latitude = lat,
            longitude = lon,
            attentionLevel = attention,
            warningLevel = warning,
            alertLevel = alert,
            dangerLevel = danger,
            protectedAreas = known?.protectedAreas ?: emptyList(),
            notes = known?.notes ?: ""
        )
    }

    fun loadSynced(context: Context): List<RiverStation> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SYNCED, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RiverStation(
                code = o.getString("code"),
                name = o.getString("name"),
                riverName = o.getString("river"),
                district = o.getString("district"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
                attentionLevel = o.getDouble("att"),
                warningLevel = o.getDouble("wrn"),
                alertLevel = o.getDouble("alm"),
                dangerLevel = o.getDouble("srs"),
                protectedAreas = o.optJSONArray("areas")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                notes = o.optString("notes", "")
            )
        }
    }

    private fun save(context: Context, stations: List<RiverStation>) {
        val array = JSONArray()
        stations.forEach { s ->
            array.put(JSONObject().apply {
                put("code", s.code); put("name", s.name); put("river", s.riverName)
                put("district", s.district); put("lat", s.latitude); put("lon", s.longitude)
                put("att", s.attentionLevel); put("wrn", s.warningLevel)
                put("alm", s.alertLevel); put("srs", s.dangerLevel)
                put("areas", JSONArray(s.protectedAreas)); put("notes", s.notes)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SYNCED, array.toString())
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    /** 동기화된 관측소가 있으면 그걸, 없으면 하드코딩 정의를 반환 */
    fun effectiveStations(context: Context, district: String? = null): List<RiverStation> {
        val synced = loadSynced(context)
        val base = synced.ifEmpty { UlsanRiverStations.stations }
        return if (district != null) base.filter { it.district == district } else base
    }

    fun needsSync(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)
        val thirtyDays = 30L * 24 * 3600 * 1000
        return loadSynced(context).isEmpty() || (System.currentTimeMillis() - last) > thirtyDays
    }
}
