package com.ulsan.disasteralert.network

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 한강홍수통제소 개방 API (전국 수문자료 제공).
 * 인증키 발급: https://www.hrfco.go.kr/web/openapiPage/openApi.do
 * 또는 공공데이터포털 "한강홍수통제소_표준수문DB" (data.go.kr/data/3040409)
 *
 * URL 구조: http://api.hrfco.go.kr/{apikey}/{자료종류}/{조회구분}/{주기}/{관측소코드}.json
 *   자료종류: waterlevel(수위), rainfall(강수량), dam(댐), fldfct(홍수예보)
 *   주기: 10M(10분), 1H(시간), 1D(일)
 *
 * 호출 제한: 분당 1,000건 수준. 여러 지점을 폴링할 때 간격 조절 필요.
 */
interface FloodApiService {

    /** 특정 관측소의 최근 수위 목록 (10분 단위) */
    @GET("{apiKey}/waterlevel/list/10M/{obsCode}.json")
    suspend fun getWaterLevels(
        @Path("apiKey") apiKey: String,
        @Path("obsCode") obsCode: String
    ): WaterLevelResponse

    /** 특정 기간 수위 조회 (상승률 계산용 이력 확보) */
    @GET("{apiKey}/waterlevel/list/10M/{obsCode}/{startDate}/{endDate}.json")
    suspend fun getWaterLevelRange(
        @Path("apiKey") apiKey: String,
        @Path("obsCode") obsCode: String,
        @Path("startDate") startDate: String,  // yyyyMMddHHmm
        @Path("endDate") endDate: String
    ): WaterLevelResponse

    /** 수위관측소 제원 목록 (관측소 코드와 기준홍수위를 여기서 확보) */
    @GET("{apiKey}/waterlevel/info.json")
    suspend fun getStationInfo(
        @Path("apiKey") apiKey: String
    ): StationInfoResponse

    /** 현재 발령 중인 홍수특보 */
    @GET("{apiKey}/fldfct/list.json")
    suspend fun getFloodForecasts(
        @Path("apiKey") apiKey: String
    ): FloodForecastResponse
}

data class WaterLevelResponse(val content: List<WaterLevelItem>?)
data class WaterLevelItem(
    val wlobscd: String,   // 관측소 코드
    val ymdhm: String,     // 관측 시각 yyyyMMddHHmm
    val wl: String,        // 수위 (m). 결측 시 빈 문자열
    val fw: String?        // 유량 (m³/s)
)

data class StationInfoResponse(val content: List<StationInfoItem>?)
data class StationInfoItem(
    val wlobscd: String,
    val obsnm: String,     // 관측소명
    val agcnm: String?,    // 관할기관
    val addr: String?,     // 주소
    val lon: String?,      // 경도
    val lat: String?,      // 위도
    val attwl: String?,    // 관심수위
    val wrnwl: String?,    // 주의보수위
    val almwl: String?,    // 경보수위
    val srswl: String?     // 계획홍수위
)

data class FloodForecastResponse(val content: List<FloodForecastItem>?)
data class FloodForecastItem(
    val fldctlcd: String?,  // 발령 통제소
    val wlobscd: String?,   // 관측소 코드
    val obsnm: String?,     // 관측소명
    val fldfctnm: String?,  // 특보명 (홍수주의보/홍수경보)
    val fctocrtm: String?,  // 발령 시각
    val fctcn: String?      // 특보 내용
)
