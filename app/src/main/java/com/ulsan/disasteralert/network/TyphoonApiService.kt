package com.ulsan.disasteralert.network

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 기상청 API허브 (apihub.kma.go.kr) — 태풍정보.
 *
 * ⚠️ data.go.kr과 **완전히 다른 시스템**입니다.
 *   - 인증 파라미터명이 serviceKey가 아니라 **authKey**
 *   - 응답이 JSON이 아니라 **평문 텍스트** (공백 또는 콤마 구분)
 *   - 따라서 Gson 컨버터를 쓸 수 없고 문자열로 받아 직접 파싱합니다
 *
 * 엔드포인트:
 *   https://apihub.kma.go.kr/api/typ01/url/typ_now.php
 *
 * 키 발급: https://apihub.kma.go.kr → 마이페이지 → 인증키 관리
 */
interface TyphoonApiService {

    /**
     * 태풍정보 + 예측 (시점 기준).
     *
     * 파라미터 선택 근거:
     *   disp=1  콤마 구분 — 파싱이 쉽습니다 (0은 고정폭이라 컬럼 폭 변경에 취약)
     *   help=1  변수명 헤더 포함 — 컬럼 순서가 바뀌어도 이름으로 찾을 수 있습니다
     *   mode=2  가장 최근의 분석정보 + 예측정보 — 현재 위치와 앞으로의 진로를 함께 받습니다
     *   tm 생략 현재 시각 기준 (지정 시 과거 12시간 내 발표 자료)
     *   typ 생략 그해 가장 마지막 태풍번호
     */
    @Streaming
    @GET("api/typ01/url/typ_now.php")
    suspend fun getTyphoonRaw(
        @Query("authKey") authKey: String,
        @Query("mode") mode: Int = 2,
        @Query("disp") disp: Int = 1,
        @Query("help") help: Int = 1,
        @Query("tm") tm: String? = null,      // yyyyMMddHHmm (UTC)
        @Query("typ") typhoonNumber: String? = null,
        @Query("YY") year: String? = null
    ): okhttp3.ResponseBody
}
