package com.ulsan.disasteralert.network

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 기상청 API허브 — 지상관측(AWS/ASOS) 실황.
 *
 * 왜 초단기실황 대신 AWS인가:
 *   초단기실황(data.go.kr)은 5km 격자 보간값이라 국지적 극값이 평활화됩니다.
 *   차바 당시 북구 매곡동 시간당 124mm 같은 값은 격자로는 잡히지 않습니다.
 *   AWS는 실제 관측소에서 잰 값이라 지점 단위 극값을 그대로 보여줍니다.
 *
 * 울산 지역 AWS는 10곳 이상이라, 상습침수지에 가장 가까운 관측소를 골라 쓸 수 있습니다.
 *
 * ⚠️ 엔드포인트 확인 필요
 *   API허브 '지상관측' 메뉴의 실제 경로와 대조하세요.
 *   메뉴에 따라 kma_sfctm2.php(종관), awsh.php(AWS 시간자료) 등으로 나뉩니다.
 *   진단 화면에서 응답 원문을 확인할 수 있게 해두었습니다.
 */
interface AwsApiService {

    /**
     * AWS 매분자료 — 1분 단위 관측.
     *
     * 지하공간 침수 기준이 **15분 단위**라 이 자료가 반드시 필요합니다.
     * 시간당 값을 4로 나눠 근사하면 집중호우의 앞부분 쏠림을 놓칩니다.
     * (시간당 60mm가 앞 15분에 40mm 몰린 경우, 근사값 15mm로는 대피 기준을 못 잡음)
     *
     * 실제 발급 화면 기준 경로 — cgi-bin이 들어갑니다:
     *   api/typ01/cgi-bin/url/nph-aws2_min
     *
     * 조회 기간 제한:
     *   전체지점(stn=0) → 10분 이내
     *   1개 지점       → 하루 이내
     * 15분 구간을 받으려면 지점을 지정해야 합니다.
     *
     * @param tm1 시작시각 yyyyMMddHHmm (KST). 생략 시 tm2와 동일
     * @param tm2 종료시각 yyyyMMddHHmm (KST). 생략 시 현재
     */
    @Streaming
    @GET("api/typ01/cgi-bin/url/nph-aws2_min")
    suspend fun getAwsMinute(
        @Query("authKey") authKey: String,
        @Query("tm1") tm1: String? = null,
        @Query("tm2") tm2: String? = null,
        @Query("stn") stationId: String = "0",
        @Query("disp") disp: Int = 1,
        @Query("help") help: Int = 1
    ): okhttp3.ResponseBody

    /**
     * AWS 시간자료 — 지점별 시간 강수량 포함.
     *
     * @param tm  조회 시각 yyyyMMddHHmm (KST). 생략 시 최근
     * @param stn 지점번호. 0이면 전체 지점
     */
    @Streaming
    @GET("api/typ01/url/awsh.php")
    suspend fun getAwsHourly(
        @Query("authKey") authKey: String,
        @Query("tm") tm: String? = null,
        @Query("stn") stationId: String = "0",
        @Query("disp") disp: Int = 1,
        @Query("help") help: Int = 1
    ): okhttp3.ResponseBody

    /**
     * 종관기상관측(ASOS) 시간자료 — AWS 경로가 막힐 때의 대체 경로.
     */
    @Streaming
    @GET("api/typ01/url/kma_sfctm2.php")
    suspend fun getSurfaceHourly(
        @Query("authKey") authKey: String,
        @Query("tm") tm: String? = null,
        @Query("stn") stationId: String = "0",
        @Query("disp") disp: Int = 1,
        @Query("help") help: Int = 1
    ): okhttp3.ResponseBody
}
