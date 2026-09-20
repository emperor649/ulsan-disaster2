package com.ulsan.disasteralert.util

import kotlin.math.*

/**
 * 기상청 격자좌표 변환 (Lambert Conformal Conic).
 *
 * 기상청 API는 위경도가 아니라 5km 격자 좌표(nx, ny)를 씁니다.
 * 아래 파라미터는 기상청이 공개한 "위경도-격자 변환" 공식값입니다.
 */
object GridConverter {

    private const val RE = 6371.00877   // 지구 반경 (km)
    private const val GRID = 5.0        // 격자 간격 (km)
    private const val SLAT1 = 30.0      // 표준위도 1
    private const val SLAT2 = 60.0      // 표준위도 2
    private const val OLON = 126.0      // 기준점 경도
    private const val OLAT = 38.0       // 기준점 위도
    private const val XO = 43           // 기준점 X좌표
    private const val YO = 136          // 기준점 Y좌표

    data class Grid(val nx: Int, val ny: Int)

    fun toGrid(latitude: Double, longitude: Double): Grid {
        val degRad = PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * degRad
        val slat2 = SLAT2 * degRad
        val olon = OLON * degRad
        val olat = OLAT * degRad

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)

        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn

        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(PI * 0.25 + latitude * degRad * 0.5)
        ra = re * sf / ra.pow(sn)

        var theta = longitude * degRad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = floor(ra * sin(theta) + XO + 0.5).toInt()
        val ny = floor(ro - ra * cos(theta) + YO + 0.5).toInt()
        return Grid(nx, ny)
    }
}

/**
 * 울산 지역 프리셋. 위 공식으로 미리 계산한 값이라 앱에서 바로 선택할 수 있습니다.
 *
 * 격자가 5km 단위라 중구와 남구처럼 인접한 구는 같은 격자(102,84)를 공유합니다.
 * 이 경우 강수량 데이터는 동일하지만, 지역 취약도·상습침수지·하천 관측소가 다르므로
 * 최종 위험도는 구별로 다르게 산출됩니다.
 */
object UlsanGridPresets {

    data class Preset(
        val displayName: String,
        val district: String,
        val nx: Int,
        val ny: Int,
        val latitude: Double,
        val longitude: Double
    )

    val presets = listOf(
        Preset("울산 중구", "중구", 102, 84, 35.5694, 129.3325),
        Preset("울산 남구", "남구", 102, 84, 35.5438, 129.3300),
        Preset("울산 동구", "동구", 104, 83, 35.5047, 129.4167),
        Preset("울산 북구", "북구", 103, 85, 35.5825, 129.3614),
        Preset("울주군 (청량)", "울주군", 101, 83, 35.5222, 129.2422),
        Preset("울주군 언양읍", "울주군", 98, 84, 35.5678, 129.1236),
        Preset("울주군 범서읍", "울주군", 100, 84, 35.5678, 129.2308),
        Preset("울주군 온산읍", "울주군", 102, 81, 35.4331, 129.3392),
        Preset("울주군 두서면", "울주군", 98, 86, 35.6683, 129.1150),
        Preset("울주군 삼동면", "울주군", 99, 83, 35.5100, 129.1600),
        Preset("울주군 서생면", "울주군", 102, 80, 35.3475, 129.2953),
        Preset("북구 매곡동", "북구", 102, 85, 35.6080, 129.3350)
    )

    val districts = listOf("중구", "남구", "동구", "북구", "울주군")
}
