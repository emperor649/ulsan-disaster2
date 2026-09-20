package com.ulsan.disasteralert.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val KMA_BASE_URL = "https://apis.data.go.kr/1360000/"
    private const val DISASTER_BASE_URL = "https://www.safetydata.go.kr/"
    private const val FLOOD_BASE_URL = "https://api.hrfco.go.kr/"
    private const val TIDE_BASE_URL = "https://apis.data.go.kr/"
    private const val KMA_APIHUB_BASE_URL = "https://apihub.kma.go.kr/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val kmaApi: KmaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KMA_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KmaApiService::class.java)
    }

    val disasterApi: DisasterApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DISASTER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DisasterApiService::class.java)
    }

    /** 한강홍수통제소 — 전국 하천 수위 및 홍수특보 */
    val floodApi: FloodApiService by lazy {
        Retrofit.Builder()
            .baseUrl(FLOOD_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FloodApiService::class.java)
    }

    /** 국립해양조사원 — 조석예보 및 실시간 조위 */
    val tideApi: TideApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TIDE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TideApiService::class.java)
    }

    /**
     * 기상청 API허브 — 태풍정보.
     * data.go.kr과 별개 시스템이며 인증 파라미터명(authKey)과 응답 형식(평문)이 다릅니다.
     */
    val typhoonApi: TyphoonApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KMA_APIHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TyphoonApiService::class.java)
    }

    /** 기상청 API허브 — 지상관측(AWS/ASOS) 실황 */
    val awsApi: AwsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KMA_APIHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AwsApiService::class.java)
    }
}
