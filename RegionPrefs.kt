package com.ulsan.disasteralert.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RegisteredRegion(
    val name: String,
    val nx: Int,
    val ny: Int,
    val district: String   // 중구/남구/동구/북구/울주군 — 취약도·하천·상습침수지 매칭 기준
)

/**
 * 사용자가 등록한 관심 지역 목록.
 * 프로덕션에서는 Room DB로 교체 권장 — 여기서는 최소 동작용 구현.
 */
object RegionPrefs {

    private const val PREFS_NAME = "region_prefs"
    private const val KEY_REGIONS = "registered_regions"

    fun getRegisteredRegions(context: Context): List<RegisteredRegion> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REGIONS, "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            RegisteredRegion(
                obj.getString("name"),
                obj.getInt("nx"),
                obj.getInt("ny"),
                obj.optString("district", obj.getString("name"))
            )
        }
    }

    fun addRegion(context: Context, region: RegisteredRegion) {
        val current = getRegisteredRegions(context).toMutableList()
        if (current.any { it.name == region.name }) return
        current.add(region)
        saveAll(context, current)
    }

    fun removeRegion(context: Context, regionName: String) {
        saveAll(context, getRegisteredRegions(context).filterNot { it.name == regionName })
    }

    private fun saveAll(context: Context, regions: List<RegisteredRegion>) {
        val array = JSONArray()
        regions.forEach {
            array.put(JSONObject().apply {
                put("name", it.name)
                put("nx", it.nx)
                put("ny", it.ny)
                put("district", it.district)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_REGIONS, array.toString()).apply()
    }
}
