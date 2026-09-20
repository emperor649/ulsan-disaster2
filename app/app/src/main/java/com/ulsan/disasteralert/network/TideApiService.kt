package com.ulsan.disasteralert.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 국립해양조사원 조석예보 (data.go.kr 경유).
 *
 * 실제 발급 화면 기준:
 *   End Point: https://apis.data.go.kr/1192136/tideFcstHghLw
 *   상세기능:   /GetTideFcstHghLwApiService
 *   포맷:       JSON + XML
 *   일일 트래픽: 10,000건
 *
 * ⚠️ khoa.go.kr 직접 호출 방식과 다릅니다.
 *   - 인증 파라미터명이 ServiceKey가 아니라 serviceKey
 *   - 응답 구조가 공공데이터포털 표준(response/header/body/items)
 *   - 실시간 조위 관측은 이 API에 없고 **조석예보(고조/저조)만** 제공합니다
 *
 * 울산 예보지점 코드: DT_0020 (활용가이드 예보지점 목록에서 확인)
 *
 * 실시간 조위가 없어도 만조 판정은 가능합니다 —
 * 조석은 천문학적으로 계산되는 값이라 예보가 곧 실측에 가깝기 때문입니다.
 * (다만 태풍 내습 시 해일로 실제 조위가 예보보다 높아질 수 있습니다. 후술)
 */
interface TideApiService {

    /**
     * 조석예보(고조·저조) 조회.
     *
     * @param obsCode 조위관측소 코드 (울산: DT_0020 — 활용가이드에서 확인됨)
     * @param date    조회 날짜 yyyyMMdd
     */
    @GET("1192136/tideFcstHghLw/GetTideFcstHghLwApiService")
    suspend fun getTidePrediction(
        @Query("serviceKey") serviceKey: String,
        @Query("ObsCode") obsCode: String,
        @Query("Date") date: String,
        @Query("ResultType") resultType: String = "json",
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("pageNo") pageNo: Int = 1
    ): TidePredictionResponse
}

// ── 공공데이터포털 표준 응답 구조 ──
data class TidePredictionResponse(val response: TideResponseBody?)
data class TideResponseBody(val header: TideHeader?, val body: TideBody?)
data class TideHeader(val resultCode: String?, val resultMsg: String?)
data class TideBody(
    val items: TideItems?,
    val numOfRows: Int?,
    val pageNo: Int?,
    val totalCount: Int?
)
data class TideItems(val item: List<TidePredictionItem>?)

/**
 * 고조·저조 1건.
 *
 * 필드명 표기가 가이드 버전에 따라 스네이크/카멜로 갈릴 수 있어 양쪽을 모두 받습니다.
 * effectiveXxx 접근자로 어느 쪽이 와도 동작합니다.
 */
data class TidePredictionItem(
    val tph_time: String?,   // 극조시각
    val tph_level: String?,  // 극조위 (cm)
    val hl_code: String?,    // 고조/저조 구분
    val obs_post_id: String?,
    val obs_post_name: String?,
    // 대체 표기 가능성 — 가이드 확인 후 정리
    val tphTime: String?,
    val tphLevel: String?,
    val hlCode: String?
) {
    val effectiveTime: String? get() = tph_time ?: tphTime
    val effectiveLevel: String? get() = tph_level ?: tphLevel
    val effectiveCode: String? get() = hl_code ?: hlCode
}
