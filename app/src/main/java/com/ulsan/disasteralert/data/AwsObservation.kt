package com.ulsan.disasteralert.data

import kotlin.math.*

/**
 * 울산 지역 AWS(방재기상관측) 지점.
 *
 * 지점번호는 기상청 기상자료개방포털 방재기상관측 지점 목록에서 확인한 값입니다. (검증 완료)
 */
data class AwsStation(
    val id: String,
    val name: String,
    val district: String,
    val latitude: Double,
    val longitude: Double
)

object UlsanAwsStations {

    /**
     * 울산 지역 방재기상관측(AWS) 지점.
     *
     * 지점번호는 기상청 기상자료개방포털 '방재기상관측 > 지점 선택' 화면에서 확인한 값입니다.
     * (전국 약 510여 지점 중 울산광역시 소속 8개 지점)
     *
     * 152(울산)는 종관기상관측(ASOS)이라 AWS 목록과 별개이나,
     * 강수량을 제공하므로 함께 조회합니다.
     */
    val stations = listOf(
        // ── 종관관측(ASOS) ──
        AwsStation("152", "울산", "남구", 35.5822, 129.3350),

        // ── 방재기상관측(AWS) ──
        AwsStation("943", "매곡", "북구", 35.6080, 129.3350),
        AwsStation("949", "정자", "북구", 35.6497, 129.4194),
        AwsStation("901", "울기", "동구", 35.4850, 129.4330),
        AwsStation("898", "장생포", "남구", 35.5100, 129.3750),
        AwsStation("900", "두서", "울주군", 35.6683, 129.1150),
        AwsStation("854", "삼동", "울주군", 35.5108, 129.1656),
        AwsStation("954", "온산", "울주군", 35.4331, 129.3392),
        AwsStation("924", "간절곶", "울주군", 35.3597, 129.3572)
    )

    /**
     * 상습침수지와 관측소의 대응.
     *
     * 차바 당시 시간당 124mm를 기록한 곳이 매곡(943)입니다.
     * 울산 관측소(152) 최대치는 104.2mm였으니, 지점을 잘못 고르면
     * 실제 극값보다 20mm 낮게 판단하게 됩니다.
     */
    val criticalStations = mapOf(
        "943" to "매곡 — 차바 당시 울산 최대 강수 기록 지점",
        "900" to "두서 — 2025년 7월 3일 누적 332mm 최다 기록 지점",
        "954" to "온산 — 원산사거리 상습 침수 인근"
    )

    /**
     * 구·군별 관측소.
     *
     * ⚠️ 중구에는 AWS가 없습니다. 태화강 중류 도심부라 가장 중요한 지역인데도 그렇습니다.
     * 이 경우 인접 관측소(남구 울산 152, 북구 매곡 943)로 대체합니다.
     * 다만 대체값이므로 중구 판단은 다른 구보다 불확실성이 큽니다.
     */
    fun inDistrict(district: String): List<AwsStation> {
        val direct = stations.filter { it.district == district }
        if (direct.isNotEmpty()) return direct

        // 관측소가 없는 구(중구)는 인접 지점으로 대체
        return stations.filter { it.id in setOf("152", "943") }
    }

    /** 이 구에 자체 관측소가 있는지 — 없으면 판단 신뢰도가 낮다 */
    fun hasOwnStation(district: String) = stations.any { it.district == district }

    /** 특정 좌표에서 가장 가까운 관측소 */
    fun nearest(lat: Double, lon: Double): AwsStation? =
        stations.minByOrNull { haversine(lat, lon, it.latitude, it.longitude) }

    /**
     * 상습침수지에서 가장 가까운 관측소를 찾는다.
     * 구 단위 평균보다 해당 지점 인근 실측이 훨씬 정확하다.
     */
    fun nearestTo(hotspot: FloodHotspot): AwsStation? =
        nearest(hotspot.latitude, hotspot.longitude)

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}

/** AWS 관측값 1건 */
data class AwsReading(
    val stationId: String,
    val stationName: String?,
    val observedAtMillis: Long,
    /** 시간 강수량 (mm) */
    val hourlyRainMm: Double?,
    /** 일 누적 강수량 (mm) */
    val dailyRainMm: Double?,
    val temperatureC: Double?,
    val windSpeedMs: Double?,
    /** 품질검사 플래그: 0=정상, 1=오류, 9=결측, null=미제공 */
    val qcFlag: Int? = null
) {
    val isReliable: Boolean get() = qcFlag == null || qcFlag == 0
}

/**
 * 지역 내 여러 AWS 관측값을 종합한 결과.
 *
 * 설계 의도: **최댓값을 쓴다.**
 * 구 전체 평균을 쓰면 국지 호우가 희석된다. 차바 당시 울산 평균은 시간당 40mm대였지만
 * 북구 매곡동은 124mm였고, 실제 피해는 그 지점에서 났다.
 * 재난 대응에서는 "어디선가 위험한 비가 오고 있는가"가 중요하지 평균이 중요하지 않다.
 */
data class DistrictRainfall(
    val district: String,
    val readings: List<AwsReading>,
    val maxHourlyRainMm: Double,
    val maxStation: AwsReading?,
    val avgHourlyRainMm: Double,
    val maxDailyRainMm: Double
) {
    /** 지점 간 편차가 크면 국지성 호우 — 지역 평균으로 판단하면 위험하다 */
    val isLocalized: Boolean
        get() = maxHourlyRainMm > 0 && (maxHourlyRainMm - avgHourlyRainMm) >= maxHourlyRainMm * 0.5

    /** 품질검사에서 걸러진 지점 수 */
    val excludedCount: Int
        get() = readings.count { !it.isReliable }

    val summary: String
        get() = buildString {
            append("시간당 최대 ${"%.1f".format(maxHourlyRainMm)}mm")
            maxStation?.stationName?.let { append(" ($it)") }
            if (readings.size > 1) {
                append(" · 지역 평균 ${"%.1f".format(avgHourlyRainMm)}mm")
            }
            if (isLocalized) {
                append("\n국지성 호우 — 지점 간 편차가 큽니다. 평균값으로 판단하지 마세요.")
            }
            if (excludedCount > 0) {
                append("\n※ ${excludedCount}개 지점은 관측값 오류·결측으로 제외했습니다.")
            }
        }
}
