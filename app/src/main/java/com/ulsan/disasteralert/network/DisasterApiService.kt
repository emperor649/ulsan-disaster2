package com.ulsan.disasteralert.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 행정안전부 긴급재난문자 (safetydata.go.kr V2 API).
 *
 * 발급 화면 기준 실제 엔드포인트:
 *   https://www.safetydata.go.kr/V2/api/DSSP-IF-00247?serviceKey=...
 *
 * DSSP-IF-00247 = 긴급재난문자 조회
 * 일일 호출 한도 1,000건 (개발계정 기본값)
 *
 * V2는 header/body 최상위 구조이며 body가 배열입니다. (발급 화면에서 확인)
 */
interface DisasterApiService {

    @GET("V2/api/DSSP-IF-00247")
    suspend fun getDisasterMessages(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("returnType") returnType: String = "json",
        /** 생성일자 yyyyMMdd. 미지정 시 최근 데이터 */
        @Query("crtDt") crtDt: String? = null,
        /** 수신지역명. "울산" 처럼 부분 일치로 필터됩니다 */
        @Query("rgnNm") regionName: String? = null
    ): DisasterMsgResponse
}

data class DisasterMsgResponse(
    val header: DisasterHeader?,
    val numOfRows: Int?,
    val pageNo: Int?,
    val totalCount: Int?,
    val body: List<DisasterMsgItem>?
)

data class DisasterHeader(
    val resultMsg: String?,
    val resultCode: String?,
    val errorMsg: String?
)

/**
 * 긴급재난문자 1건. V2 응답 필드명(대문자 스네이크).
 */
data class DisasterMsgItem(
    val SN: String?,            // 일련번호 (V2는 문자열)
    val CRT_DT: String?,        // 생성일시
    val MSG_CN: String?,        // 메시지 내용
    val RCPTN_RGN_NM: String?,  // 수신지역명
    val EMRG_STEP_NM: String?,  // 긴급단계명 (위급재난/긴급재난/안전안내)
    val DST_SE_NM: String?,     // 재해구분명 (호우, 태풍, 지진 등)
    val REG_YMD: String?,
    val MDFCN_YMD: String?
)
