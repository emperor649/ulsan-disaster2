package com.ulsan.disasteralert.data

import com.ulsan.disasteralert.network.ApiClient
import java.text.SimpleDateFormat
import java.util.*

/**
 * AWS 실황 조회 및 파싱.
 *
 * API허브는 평문 응답이므로 태풍 파서와 같은 방식을 씁니다 —
 * help=1로 변수명 헤더를 받아 이름으로 값을 꺼내므로 컬럼 순서에 의존하지 않습니다.
 */
object AwsRepository {

    private val MISSING = setOf("-9", "-99", "-999", "-9999", "-50", "", "N/A")

    /** 매분자료의 분 강수량 컬럼 후보명 */
    private val MINUTE_RAIN_KEYS = listOf("RN_60M", "RN", "RN_1M", "RN_MIN")

    /** 강수량 컬럼 후보명 — 기상청 자료 종류에 따라 표기가 다릅니다 */
    private val HOURLY_RAIN_KEYS = listOf("RN_HR1", "RN_HOUR", "RN", "RN_60M")
    private val DAILY_RAIN_KEYS = listOf("RN_DAY", "RN_D", "RN_SUM")

    /**
     * 품질검사 플래그 컬럼 후보명.
     *
     * 기상청 AWS 자료는 관측값의 정상 여부를 플래그로 제공합니다.
     *   0 = 정상, 1 = 오류, 9 = 결측
     *
     * 오류값을 걸러내지 않으면 장비 이상으로 튄 값이 그대로 위험도에 들어갑니다.
     * 시간당 200mm 같은 비현실적 값 하나가 최댓값으로 잡히면 오경보가 납니다.
     */
    private val RAIN_QC_KEYS = listOf("RN_QC", "RN_HR1_QC", "QC_RN", "RN_FLAG")

    /**
     * 물리적 상한 (mm/h).
     * 국내 관측 사상 최대 시간강수량이 약 145mm(1998 순천)이므로
     * 이를 넘는 값은 장비 오류로 간주합니다. QC 플래그가 없을 때의 방어선입니다.
     */
    private const val PHYSICAL_MAX_HOURLY_MM = 180.0

    /**
     * 지역 강수 현황을 조회한다.
     * AWS 경로가 실패하면 종관관측(ASOS)으로 자동 대체한다.
     */
    suspend fun getDistrictRainfall(apiKey: String, district: String): DistrictRainfall? {
        if (apiKey.isBlank()) return null

        val raw = runCatching {
            ApiClient.awsApi.getAwsHourly(authKey = apiKey).string()
        }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains("ERROR", true) }
            ?: runCatching {
                ApiClient.awsApi.getSurfaceHourly(authKey = apiKey).string()
            }.getOrNull()
            ?: return null

        val all = parse(raw)
        if (all.isEmpty()) return null

        // 해당 구·군의 관측소만 추림. 없으면 가장 가까운 관측소 하나라도 사용
        val targetIds = UlsanAwsStations.inDistrict(district).map { it.id }.toSet()
        var readings = all.filter { it.stationId in targetIds }

        if (readings.isEmpty()) {
            val fallbackIds = UlsanAwsStations.stations.map { it.id }.toSet()
            readings = all.filter { it.stationId in fallbackIds }
        }
        if (readings.isEmpty()) return null

        val rains = readings.mapNotNull { it.hourlyRainMm }
        val maxHourly = rains.maxOrNull() ?: 0.0
        val avgHourly = if (rains.isEmpty()) 0.0 else rains.average()
        val maxDaily = readings.mapNotNull { it.dailyRainMm }.maxOrNull() ?: 0.0

        return DistrictRainfall(
            district = district,
            readings = readings,
            maxHourlyRainMm = maxHourly,
            maxStation = readings.maxByOrNull { it.hourlyRainMm ?: -1.0 },
            avgHourlyRainMm = avgHourly,
            maxDailyRainMm = maxDaily
        )
    }

    /**
     * 최근 N분간 강수량을 합산한다.
     *
     * 지하공간 침수 기준(15분 20mm / 30mm)을 판정하려면 분 단위 자료가 필요하다.
     * 시간당 값을 4로 나눠 근사하면, 집중호우가 앞쪽에 몰린 경우를 놓친다 —
     * 그런데 지하차도 익사는 바로 그런 순간에 일어난다.
     *
     * @param minutes 합산할 구간 (기본 15분)
     * @return 지점별 구간 합산 강수량. 조회 실패 시 null
     */
    suspend fun getRecentMinuteRain(
        apiKey: String,
        stationId: String,
        minutes: Int = 15
    ): Double? {
        if (apiKey.isBlank()) return null

        val fmt = SimpleDateFormat("yyyyMMddHHmm", Locale.KOREA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
        // 관측 자료는 약간의 지연이 있으므로 2분 여유를 둔다
        now.add(Calendar.MINUTE, -2)
        val tm2 = fmt.format(now.time)
        now.add(Calendar.MINUTE, -minutes)
        val tm1 = fmt.format(now.time)

        val raw = runCatching {
            ApiClient.awsApi.getAwsMinute(
                authKey = apiKey, tm1 = tm1, tm2 = tm2, stationId = stationId
            ).string()
        }.getOrNull() ?: return null

        if (raw.isBlank()) return null

        val readings = parseMinute(raw, stationId)
        if (readings.isEmpty()) return null

        // 분 강수량을 합산. 누적 표기인 경우를 대비해 음수 차분은 버린다.
        return readings.sum()
    }

    /**
     * 매분자료 파싱 — 특정 지점의 분 강수량만 뽑는다.
     */
    fun parseMinute(raw: String, stationId: String): List<Double> {
        val lines = raw.lines()
        val headerIndex = lines.indexOfFirst { line ->
            val t = line.trimStart('#', ' ').uppercase()
            t.contains("STN") && MINUTE_RAIN_KEYS.any { t.contains(it) }
        }
        if (headerIndex < 0) return emptyList()

        val columns = lines[headerIndex].trimStart('#', ' ')
            .split(',', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        val idx = columns.withIndex().associate { (i, n) -> n.uppercase() to i }

        return lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line ->
                val f = line.split(',').map { it.trim() }
                fun get(name: String): String? {
                    val i = idx[name.uppercase()] ?: return null
                    val v = f.getOrNull(i)?.trim() ?: return null
                    return if (v in MISSING) null else v
                }
                val stn = get("STN") ?: return@mapNotNull null
                if (stationId != "0" && stn != stationId) return@mapNotNull null

                MINUTE_RAIN_KEYS.firstNotNullOfOrNull { get(it)?.toDoubleOrNull() }
                    ?.takeIf { it in 0.0..PHYSICAL_MAX_HOURLY_MM }
            }
    }

    /**
     * 평문 응답 파싱. 컬럼 순서를 가정하지 않는다.
     */
    fun parse(raw: String): List<AwsReading> {
        val lines = raw.lines()

        val headerIndex = lines.indexOfFirst { line ->
            val t = line.trimStart('#', ' ').uppercase()
            t.contains("STN") && (HOURLY_RAIN_KEYS.any { t.contains(it) } || t.contains("TM"))
        }
        if (headerIndex < 0) return emptyList()

        val columns = lines[headerIndex].trimStart('#', ' ')
            .split(',', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        val idx = columns.withIndex().associate { (i, n) -> n.uppercase() to i }

        val stationNames = UlsanAwsStations.stations.associate { it.id to it.name }

        return lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line ->
                val f = line.split(',').map { it.trim() }
                if (f.size < 3) return@mapNotNull null

                fun get(name: String): String? {
                    val i = idx[name.uppercase()] ?: return null
                    val v = f.getOrNull(i)?.trim() ?: return null
                    return if (v in MISSING) null else v
                }
                fun firstNum(keys: List<String>): Double? =
                    keys.firstNotNullOfOrNull { get(it)?.toDoubleOrNull() }
                        ?.takeIf { it > -50.0 }

                // 품질검사 플래그 확인 — 0만 정상으로 취급
                val qcFlag = RAIN_QC_KEYS.firstNotNullOfOrNull { get(it)?.toIntOrNull() }
                val qcPassed = qcFlag == null || qcFlag == 0

                /** 강수량은 QC와 물리적 상한을 모두 통과한 값만 사용 */
                fun validRain(keys: List<String>): Double? {
                    if (!qcPassed) return null
                    val v = firstNum(keys) ?: return null
                    return if (v in 0.0..PHYSICAL_MAX_HOURLY_MM) v else null
                }

                val stn = get("STN") ?: get("STN_ID") ?: return@mapNotNull null
                val time = get("TM")?.let { parseKst(it) } ?: System.currentTimeMillis()

                AwsReading(
                    stationId = stn,
                    stationName = stationNames[stn] ?: get("STN_NM"),
                    observedAtMillis = time,
                    hourlyRainMm = validRain(HOURLY_RAIN_KEYS),
                    dailyRainMm = validRain(DAILY_RAIN_KEYS),
                    qcFlag = qcFlag,
                    temperatureC = get("TA")?.toDoubleOrNull()?.takeIf { it > -50 },
                    windSpeedMs = get("WS")?.toDoubleOrNull()?.takeIf { it >= 0 }
                )
            }
    }

    /** 지상관측 시각은 KST 기준 (태풍정보는 UTC라 다릅니다 — 혼동 주의) */
    private fun parseKst(s: String): Long? {
        val d = s.filter { it.isDigit() }
        val pattern = when (d.length) {
            12 -> "yyyyMMddHHmm"
            10 -> "yyyyMMddHH"
            else -> return null
        }
        return runCatching {
            SimpleDateFormat(pattern, Locale.KOREA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Seoul")
            }.parse(d)?.time
        }.getOrNull()
    }
}
