package com.ulsan.disasteralert.data

import android.content.Context
import com.ulsan.disasteralert.BuildConfig
import com.ulsan.disasteralert.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * API 연결 진단.
 *
 * 4개 외부 API를 실제로 호출해서 어디까지 되고 어디서 막히는지 알려준다.
 * "앱은 켜지는데 알림이 안 온다"는 상황의 원인을 찾는 데 쓴다 —
 * 대부분 키 미발급, 활용신청 미승인, 관측소 코드 불일치 중 하나다.
 */
object ApiDiagnostics {

    enum class Status { OK, NO_KEY, AUTH_FAILED, NO_DATA, NETWORK_ERROR }

    data class Result(
        val apiName: String,
        val status: Status,
        val message: String,
        val sampleData: String? = null,
        val fixHint: String? = null
    )

    suspend fun runAll(context: Context, nx: Int = 102, ny: Int = 84): List<Result> =
        withContext(Dispatchers.IO) {
            listOf(
                checkKmaWarning(),
                checkKmaNowcast(nx, ny),
                checkDisasterMessage(),
                checkTyphoonApiHub(),
                checkAwsObservation(district = "중구"),
                checkAwsMinute(),
                checkFloodStations(context),
                checkTideStation(context)
            )
        }

    // ── 1. 기상청 특보 ──
    private suspend fun checkKmaWarning(): Result {
        val key = BuildConfig.KMA_SERVICE_KEY
        if (key.isBlank()) return Result(
            "기상청 · 기상특보", Status.NO_KEY,
            "서비스키가 없습니다",
            fixHint = "data.go.kr에서 '기상청_기상특보 조회서비스' 활용신청 후\n" +
                    "local.properties에 KMA_SERVICE_KEY 추가"
        )

        return runCatching {
            val resp = ApiClient.kmaApi.getWeatherWarnings(serviceKey = key)
            val code = resp.response.header.resultCode
            val items = resp.response.body?.items?.item.orEmpty()

            when {
                code == "00" && items.isNotEmpty() -> Result(
                    "기상청 · 기상특보", Status.OK,
                    "정상 (특보 ${items.size}건 수신)",
                    sampleData = items.take(2).joinToString("\n") {
                        "${it.regName} · ${it.warnVar} (${it.tmFc})"
                    }
                )
                code == "00" -> Result(
                    "기상청 · 기상특보", Status.OK,
                    "정상 (현재 발효 중인 특보 없음)",
                    sampleData = "특보가 없는 것은 정상 상태입니다"
                )
                else -> Result(
                    "기상청 · 기상특보", Status.AUTH_FAILED,
                    "응답 코드 $code: ${resp.response.header.resultMsg}",
                    fixHint = interpretKmaError(code)
                )
            }
        }.getOrElse { e ->
            Result("기상청 · 기상특보", Status.NETWORK_ERROR,
                e.message ?: "호출 실패",
                fixHint = "엔드포인트 경로가 발급받은 API 명세와 다를 수 있습니다.\n" +
                        "KmaApiService.kt의 @GET 경로를 명세서와 대조하세요.")
        }
    }

    // ── 2. 기상청 초단기실황 (강수량) ──
    private suspend fun checkKmaNowcast(nx: Int, ny: Int): Result {
        val key = BuildConfig.KMA_SERVICE_KEY
        if (key.isBlank()) return Result(
            "기상청 · 초단기실황(강수량)", Status.NO_KEY, "서비스키가 없습니다"
        )

        // 초단기실황은 매시 정시 발표 후 10분 뒤부터 조회 가능
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, -40) }
        val baseDate = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(cal.time)
        val baseTime = SimpleDateFormat("HH", Locale.KOREA).format(cal.time) + "00"

        return runCatching {
            val resp = ApiClient.kmaApi.getUltraShortNowcast(
                serviceKey = key, baseDate = baseDate, baseTime = baseTime, nx = nx, ny = ny
            )
            val code = resp.response.header.resultCode
            val items = resp.response.body?.items?.item.orEmpty()
            val rain = items.find { it.category == "RN1" }

            when {
                code == "00" && rain != null -> Result(
                    "기상청 · 초단기실황(강수량)", Status.OK,
                    "정상 (격자 $nx,$ny)",
                    sampleData = "시간당 강수량 ${rain.obsrValue}mm (${rain.baseDate} ${rain.baseTime})"
                )
                code == "00" -> Result(
                    "기상청 · 초단기실황(강수량)", Status.NO_DATA,
                    "응답은 왔으나 강수량(RN1) 항목이 없습니다",
                    fixHint = "격자좌표($nx,$ny)가 유효한지 확인하세요"
                )
                else -> Result(
                    "기상청 · 초단기실황(강수량)", Status.AUTH_FAILED,
                    "응답 코드 $code: ${resp.response.header.resultMsg}",
                    fixHint = interpretKmaError(code)
                )
            }
        }.getOrElse { e ->
            Result("기상청 · 초단기실황(강수량)", Status.NETWORK_ERROR, e.message ?: "호출 실패")
        }
    }

    // ── 3. 행안부 재난문자 ──
    private suspend fun checkDisasterMessage(): Result {
        val key = BuildConfig.DISASTER_SERVICE_KEY
        if (key.isBlank()) return Result(
            "행정안전부 · 재난문자", Status.NO_KEY,
            "서비스키가 없습니다",
            fixHint = "safetydata.go.kr 가입 후 '재난문자 발송현황' 활용신청\n" +
                    "local.properties에 DISASTER_SERVICE_KEY 추가"
        )

        val today = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        return runCatching {
            val resp = ApiClient.disasterApi.getDisasterMessages(
                serviceKey = key, crtDt = today, regionName = "울산"
            )
            val code = resp.header?.resultCode
            val items = resp.body.orEmpty()

            // V2는 정상 코드가 "00"으로 오며, 데이터가 없어도 성공입니다
            if (code == "00" || items.isNotEmpty()) {
                Result(
                    "행정안전부 · 긴급재난문자", Status.OK,
                    "정상 (울산 지역 오늘 ${items.size}건 / 전체 ${resp.totalCount ?: 0}건)",
                    sampleData = items.take(2).joinToString("\n") {
                        "${it.RCPTN_RGN_NM} · ${it.DST_SE_NM} · ${it.EMRG_STEP_NM}"
                    }.ifEmpty { "오늘 울산 지역 재난문자 없음 (정상 상태)" }
                )
            } else {
                Result(
                    "행정안전부 · 긴급재난문자", Status.AUTH_FAILED,
                    "$code: ${resp.header?.resultMsg ?: resp.header?.errorMsg ?: "알 수 없는 오류"}",
                    fixHint = interpretSafetyDataError(code)
                )
            }
        }.getOrElse { e ->
            Result(
                "행정안전부 · 긴급재난문자", Status.NETWORK_ERROR,
                e.message ?: "호출 실패",
                fixHint = "엔드포인트는 V2/api/DSSP-IF-00247 입니다.\n" +
                        "발급 화면의 URL과 일치하는지 확인하세요."
            )
        }
    }

    // ── 기상청 API허브 · AWS 실황 ──
    private suspend fun checkAwsObservation(district: String): Result {
        val key = BuildConfig.KMA_APIHUB_KEY
        if (key.isBlank()) return Result(
            "기상청 API허브 · AWS 실황", Status.NO_KEY,
            "인증키가 없습니다 (태풍과 동일 키)",
            fixHint = "apihub.kma.go.kr 인증키를 KMA_APIHUB_KEY에 넣으세요"
        )

        return runCatching {
            val raw = runCatching {
                ApiClient.awsApi.getAwsHourly(authKey = key).string()
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: ApiClient.awsApi.getSurfaceHourly(authKey = key).string()

            val readings = AwsRepository.parse(raw)
            val known = UlsanAwsStations.stations.map { it.id }.toSet()
            val matched = readings.filter { it.stationId in known }

            when {
                readings.isEmpty() -> Result(
                    "기상청 API허브 · AWS 실황", Status.NO_DATA,
                    "응답은 왔으나 파싱된 관측값이 없습니다",
                    sampleData = "응답 앞부분:\n" + raw.take(400),
                    fixHint = "엔드포인트가 맞는지, 컬럼명이 예상과 다른지 확인하세요.\n" +
                            "위 원문의 헤더행 변수명을 알려주시면 파서를 맞출 수 있습니다."
                )
                matched.isEmpty() -> Result(
                    "기상청 API허브 · AWS 실황", Status.NO_DATA,
                    "전국 ${readings.size}개 지점 수신, 그러나 울산 지점이 매칭되지 않음",
                    sampleData = "수신된 지점번호 예시: " +
                            readings.take(12).joinToString(", ") { it.stationId },
                    fixHint = "등록된 울산 지점번호(152, 943, 949, 901, 898, 900, 854, 954, 924)가\n" +
                            "이 API의 응답에 없습니다. 엔드포인트가 다른 자료를 반환하고 있을 수 있습니다."
                )
                else -> {
                    val maxR = matched.maxByOrNull { it.hourlyRainMm ?: -1.0 }
                    Result(
                        "기상청 API허브 · AWS 실황", Status.OK,
                        "정상 (울산 ${matched.size}개 지점 / 전국 ${readings.size}개)",
                        sampleData = "※ 중구는 자체 AWS가 없어 인접 지점(울산152·매곡943)으로 대체합니다\n\n" +
                        matched.take(6).joinToString("\n") {
                            "  ${it.stationName ?: it.stationId}: " +
                            "시간당 ${it.hourlyRainMm ?: 0.0}mm, 일 ${it.dailyRainMm ?: 0.0}mm"
                        } + (maxR?.let { "\n\n최대: ${it.stationName} ${it.hourlyRainMm ?: 0.0}mm" } ?: "")
                    )
                }
            }
        }.getOrElse { e ->
            Result(
                "기상청 API허브 · AWS 실황", Status.NETWORK_ERROR,
                e.message ?: "호출 실패",
                fixHint = "awsh.php와 kma_sfctm2.php 모두 실패했습니다.\n" +
                        "API허브 '지상관측' 메뉴의 실제 경로를 확인하세요."
            )
        }
    }

    // ── 기상청 API허브 · AWS 매분자료 ──
    private suspend fun checkAwsMinute(): Result {
        val key = BuildConfig.KMA_APIHUB_KEY
        if (key.isBlank()) return Result(
            "기상청 API허브 · AWS 매분자료", Status.NO_KEY,
            "인증키가 없습니다 (태풍·AWS와 동일 키)"
        )

        // 매곡(943) 기준 15분 조회 — 울산 최대 강수 기록 지점
        return runCatching {
            val rain = AwsRepository.getRecentMinuteRain(key, "943", 15)
            if (rain == null) {
                Result(
                    "기상청 API허브 · AWS 매분자료", Status.NO_DATA,
                    "매곡(943) 15분 자료를 받지 못했습니다",
                    fixHint = "경로는 api/typ01/cgi-bin/url/nph-aws2_min 입니다.\n" +
                            "단일 지점은 하루 이내, 전체 지점은 10분 이내만 조회됩니다.\n" +
                            "분 강수량 컬럼명(RN_60M 등)이 다를 수 있으니 응답 헤더를 확인하세요."
                )
            } else {
                Result(
                    "기상청 API허브 · AWS 매분자료", Status.OK,
                    "정상 — 매곡(943) 최근 15분 ${"%.1f".format(rain)}mm",
                    sampleData = "지하공간 침수 기준 판정에 이 값이 쓰입니다.\n" +
                            "통제: 15분 20mm 이상 · 대피: 15분 30mm 이상"
                )
            }
        }.getOrElse { e ->
            Result(
                "기상청 API허브 · AWS 매분자료", Status.NETWORK_ERROR,
                e.message ?: "호출 실패",
                fixHint = "경로 확인: api/typ01/cgi-bin/url/nph-aws2_min"
            )
        }
    }

    // ── 기상청 API허브 · 태풍 ──
    private suspend fun checkTyphoonApiHub(): Result {
        val key = BuildConfig.KMA_APIHUB_KEY
        if (key.isBlank()) return Result(
            "기상청 API허브 · 태풍", Status.NO_KEY,
            "인증키가 없습니다",
            fixHint = "apihub.kma.go.kr 가입 → 마이페이지 → 인증키 관리\n" +
                    "local.properties에 KMA_APIHUB_KEY 추가\n" +
                    "(data.go.kr 키와는 다른 키입니다)"
        )

        return runCatching {
            val raw = ApiClient.typhoonApi.getTyphoonRaw(authKey = key).string()

            when {
                raw.contains("인증", true) || raw.contains("AUTH", true) && raw.length < 300 ->
                    Result(
                        "기상청 API허브 · 태풍", Status.AUTH_FAILED,
                        "인증 오류 응답",
                        sampleData = raw.take(200),
                        fixHint = "인증키가 유효한지, 태풍 API 사용 권한이 있는지 확인하세요"
                    )
                else -> {
                    val typhoons = TyphoonTextParser.parse(raw)
                    if (typhoons.isEmpty()) {
                        Result(
                            "기상청 API허브 · 태풍", Status.OK,
                            "정상 (현재 발생 중인 태풍 없음)",
                            sampleData = "응답 수신됨. 태풍 발생 시 진로 데이터가 채워집니다.\n\n" +
                                    "응답 앞부분:\n" + raw.take(300)
                        )
                    } else {
                        val t = typhoons.first()
                        Result(
                            "기상청 API허브 · 태풍", Status.OK,
                            "정상 (태풍 ${typhoons.size}개, 진로점 ${t.points.size}개)",
                            sampleData = "태풍번호 ${t.id}\n" +
                                    t.points.take(3).joinToString("\n") {
                                        "  ${it.latitude}, ${it.longitude} · ${it.centralPressureHpa ?: "-"}hPa"
                                    }
                        )
                    }
                }
            }
        }.getOrElse { e ->
            Result(
                "기상청 API허브 · 태풍", Status.NETWORK_ERROR,
                e.message ?: "호출 실패",
                fixHint = "엔드포인트는 api/typ01/url/typ_now.php 입니다.\n" +
                        "파싱 실패라면 응답 원문을 확인해 컬럼명을 대조하세요."
            )
        }
    }

    // ── 4. 홍수통제소 수위 ──
    private suspend fun checkFloodStations(context: Context): Result {
        val key = BuildConfig.FLOOD_SERVICE_KEY
        if (key.isBlank()) return Result(
            "홍수통제소 · 하천 수위", Status.NO_KEY,
            "서비스키가 없습니다",
            fixHint = "hrfco.go.kr OpenAPI 메뉴에서 인증키 신청\nlocal.properties에 FLOOD_SERVICE_KEY 추가"
        )

        return runCatching {
            val synced = StationSyncHelper.syncUlsanStations(context, key)
            if (synced.isEmpty()) {
                Result(
                    "홍수통제소 · 하천 수위", Status.NO_DATA,
                    "울산 관측소를 찾지 못했습니다",
                    fixHint = "기준홍수위가 설정된 관측소만 채택합니다.\n" +
                            "StationSyncHelper의 검색 키워드를 넓혀보세요"
                )
            } else {
                // 첫 관측소로 실제 수위까지 조회해본다
                val first = synced.first()
                val levels = runCatching {
                    ApiClient.floodApi.getWaterLevels(key, first.code).content.orEmpty()
                }.getOrDefault(emptyList())

                Result(
                    "홍수통제소 · 하천 수위", Status.OK,
                    "정상 (울산 관측소 ${synced.size}개소 동기화)",
                    sampleData = buildString {
                        synced.take(4).forEach { appendLine("${it.name} [${it.code}] 계획홍수위 ${it.dangerLevel}m") }
                        levels.lastOrNull()?.let { append("최근 수위: ${it.wl}m (${it.ymdhm})") }
                    }
                )
            }
        }.getOrElse { e ->
            Result("홍수통제소 · 하천 수위", Status.NETWORK_ERROR, e.message ?: "호출 실패")
        }
    }

    // ── 5. 조석예보 (data.go.kr 경유) ──
    private suspend fun checkTideStation(context: Context): Result {
        val key = BuildConfig.TIDE_SERVICE_KEY
        if (key.isBlank()) return Result(
            "국립해양조사원 · 조석예보", Status.NO_KEY,
            "서비스키가 없습니다",
            fixHint = "data.go.kr에서 '국립해양조사원_조석예보(고,저조)' 활용신청\n" +
                    "local.properties에 TIDE_SERVICE_KEY 추가"
        )

        return runCatching {
            val today = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            val station = TideRepository.defaultStationForReplay()
            val resp = ApiClient.tideApi.getTidePrediction(key, station.code, today)
            val code = resp.response?.header?.resultCode
            val items = resp.response?.body?.items?.item.orEmpty()

            when {
                items.isNotEmpty() -> {
                    val highs = items.count {
                        (it.effectiveCode ?: "").let { c -> c.contains("고") || c.equals("H", true) }
                    }
                    Result(
                        "국립해양조사원 · 조석예보", Status.OK,
                        "정상 (관측소 ${station.code}, 오늘 ${items.size}건 · 만조 ${highs}회)",
                        sampleData = items.take(4).joinToString("\n") {
                            "  ${it.effectiveTime} · ${it.effectiveLevel}cm · ${it.effectiveCode}"
                        }
                    )
                }
                code == "00" -> Result(
                    "국립해양조사원 · 조석예보", Status.NO_DATA,
                    "응답은 정상이나 데이터가 없습니다",
                    fixHint = "관측소 코드 ${station.code}(울산)로 조회했으나 데이터가 없습니다.\n" +
                            "날짜 파라미터 형식(yyyyMMdd) 또는 응답 필드명을 확인하세요.\n" +
                            "활용가이드 hwp의 출력결과 항목과 대조가 필요합니다."
                )
                else -> Result(
                    "국립해양조사원 · 조석예보", Status.AUTH_FAILED,
                    "$code: ${resp.response?.header?.resultMsg ?: "알 수 없는 오류"}",
                    fixHint = interpretKmaError(code ?: "")
                )
            }
        }.getOrElse { e ->
            Result(
                "국립해양조사원 · 조석예보", Status.NETWORK_ERROR,
                e.message ?: "호출 실패",
                fixHint = "엔드포인트는 1192136/tideFcstHghLw/GetTideFcstHghLwApiService 입니다.\n" +
                        "응답 필드명이 다르면 활용가이드 hwp와 대조가 필요합니다."
            )
        }
    }

    /** safetydata.go.kr V2 오류 코드 */
    private fun interpretSafetyDataError(code: String?): String = when (code) {
        "01" -> "인증키가 유효하지 않습니다. 재발급 여부를 확인하세요"
        "02" -> "일일 호출 한도(1,000건)를 초과했습니다"
        "03" -> "조회 결과가 없습니다 (오류가 아닐 수 있음)"
        "04" -> "필수 파라미터가 누락되었습니다"
        "99" -> "서버 오류. 잠시 후 재시도하세요"
        else -> "발급 화면의 URL(V2/api/DSSP-IF-00247)과 코드가 일치하는지 확인하세요"
    }

    /** 기상청 API 오류 코드 해석 — 실제로 자주 만나는 것들 */
    private fun interpretKmaError(code: String): String = when (code) {
        "01" -> "애플리케이션 에러 — 잠시 후 재시도"
        "02" -> "DB 에러 — 기상청 서버 문제, 재시도"
        "03" -> "데이터 없음 — 조회 시각/격자를 확인하세요"
        "04" -> "HTTP 에러"
        "12" -> "폐기된 서비스입니다. 신규 버전 API로 교체 필요"
        "20" -> "서비스 접근 거부 — 활용신청이 승인되었는지 확인"
        "22" -> "일일 호출 한도 초과 — 폴링 주기를 늘리세요"
        "30" -> "등록되지 않은 서비스키 — 키 값을 다시 확인"
        "31" -> "기한 만료된 서비스키 — 연장 신청 필요"
        "32" -> "등록되지 않은 도메인/IP"
        "33" -> "서명되지 않은 호출"
        else -> "발급받은 API 명세서의 오류코드표를 확인하세요"
    }
}
