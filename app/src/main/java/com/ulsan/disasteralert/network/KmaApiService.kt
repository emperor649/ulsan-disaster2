package com.ulsan.disasteralert.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 기상청_기상특보 조회서비스 + 초단기실황(강수량)
 * 공공데이터포털에서 서비스키 발급 필요: https://www.data.go.kr
 *
 * 실제 엔드포인트/파라미터명은 포털에서 발급받은 명세서 기준으로
 * 아래 시그니처를 맞춰 조정해야 합니다. (여기서는 표준 파라미터 형태로 작성)
 */
interface KmaApiService {

    // 기상특보 발표 현황
    @GET("VilageFcstInfoService_2.0/getWthrWrnList")
    suspend fun getWeatherWarnings(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("dataType") dataType: String = "JSON",
        @Query("stnId") stationId: String? = null // 특정 지점만 조회 시
    ): KmaWarningResponse

    // 초단기실황 (현재 강수량 등 실측)
    @GET("VilageFcstInfoService_2.0/getUltraSrtNcst")
    suspend fun getUltraShortNowcast(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("dataType") dataType: String = "JSON",
        @Query("base_date") baseDate: String,   // yyyyMMdd
        @Query("base_time") baseTime: String,   // HHmm (매시 정시+10분 이후)
        @Query("nx") nx: Int,                   // 격자 X
        @Query("ny") ny: Int                    // 격자 Y
    ): KmaNowcastResponse
}

data class KmaWarningResponse(val response: KmaWarningBody)
data class KmaWarningBody(val header: KmaHeader, val body: KmaWarningItems?)
data class KmaWarningItems(val items: KmaWarningItemWrapper?)
data class KmaWarningItemWrapper(val item: List<KmaWarningItem>)
data class KmaWarningItem(
    val regId: String,
    val regName: String,
    val warnVar: String,   // 특보 종류 코드
    val warnStress: String, // 주의보/경보 코드
    val tmFc: String,      // 발표시각
    val command: String
)

data class KmaNowcastResponse(val response: KmaNowcastBody)
data class KmaNowcastBody(val header: KmaHeader, val body: KmaNowcastItems?)
data class KmaNowcastItems(val items: KmaNowcastItemWrapper?)
data class KmaNowcastItemWrapper(val item: List<KmaNowcastItem>)
data class KmaNowcastItem(
    val category: String,  // RN1(1시간 강수량) 등
    val obsrValue: String,
    val baseDate: String,
    val baseTime: String,
    val nx: Int,
    val ny: Int
)

data class KmaHeader(val resultCode: String, val resultMsg: String)
