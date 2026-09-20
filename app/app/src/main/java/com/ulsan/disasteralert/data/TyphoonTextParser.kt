package com.ulsan.disasteralert.data

import java.text.SimpleDateFormat
import java.util.*

/**
 * 기상청 API허브 태풍정보 텍스트 파서.
 *
 * API허브는 JSON이 아니라 평문을 반환합니다. 대략 아래 형태입니다.
 *
 *   #START7777
 *   # TYP  SEQ  TYP_TM      FT_TM       LAT   LON    DIR SP  PS  WS RAD15 RAD25 RAD ...
 *   # 태풍번호 발표번호 분석시각 예측시각 위도 경도 ...
 *    18, 12, 201108070100, 201108070100, 28.5, 128.3, NNE, 20, 965, 35, 280, 100, 0, ...
 *   #7777END
 *
 * 설계 방침: **컬럼 순서를 가정하지 않습니다.**
 * help=1로 받은 변수명 헤더에서 이름→인덱스 맵을 만든 뒤 이름으로 값을 꺼냅니다.
 * 기상청이 컬럼을 추가하거나 순서를 바꿔도 깨지지 않습니다.
 */
object TyphoonTextParser {

    /** 결측값 표기 — 기상청 공통 규약 */
    private val MISSING = setOf("-9", "-99", "-999", "-9999", "", "N/A")

    fun parse(raw: String): List<Typhoon> {
        val lines = raw.lines()

        // 1) 변수명 헤더 찾기 — 주석행 중 알려진 컬럼명을 여럿 포함한 줄
        val headerIndex = lines.indexOfFirst { line ->
            val t = line.trimStart('#', ' ')
            t.contains("TYP_TM") && t.contains("LAT") && t.contains("LON")
        }
        if (headerIndex < 0) return emptyList()

        val columns = lines[headerIndex]
            .trimStart('#', ' ')
            .split(',', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val idx = columns.withIndex().associate { (i, name) -> name.uppercase() to i }

        // 2) 데이터 행 파싱 — 주석(#)과 빈 줄 제외
        val rows = lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { parseRow(it, idx) }

        if (rows.isEmpty()) return emptyList()

        // 3) 태풍번호별로 묶기 (동시 발생 대응)
        return rows.groupBy { it.typhoonNumber }
            .map { (num, points) ->
                Typhoon(
                    id = num,
                    name = points.firstOrNull { it.location != null }?.location ?: num,
                    koreanName = null,   // typ_now는 태풍명을 주지 않습니다. typ_list로 별도 조회 필요
                    points = points.map { it.point }.sortedBy { it.timeMillis },
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
    }

    private data class Row(
        val typhoonNumber: String,
        val point: TyphoonPoint,
        val location: String?
    )

    private fun parseRow(line: String, idx: Map<String, Int>): Row? {
        val f = line.split(',').map { it.trim() }
        if (f.size < 6) return null

        fun get(name: String): String? {
            val i = idx[name.uppercase()] ?: return null
            val v = f.getOrNull(i)?.trim() ?: return null
            return if (v in MISSING) null else v
        }
        fun num(name: String): Double? = get(name)?.toDoubleOrNull()?.let {
            if (it <= -9.0 && it >= -9999.0 && it % 1.0 == 0.0 && it.toInt() in listOf(-9, -99, -999, -9999)) null else it
        }

        val lat = num("LAT") ?: return null
        val lon = num("LON") ?: return null

        // 분석시각(TYP_TM)과 예측시각(FT_TM). 예측 행은 FT_TM이 분석시각보다 미래입니다.
        val analysisTime = get("TYP_TM")?.let { parseUtc(it) }
        val forecastTime = get("FT_TM")?.let { parseUtc(it) }
        val time = forecastTime ?: analysisTime ?: return null

        val typNum = get("TYP") ?: get("TYP_SEQ") ?: "unknown"

        return Row(
            typhoonNumber = typNum,
            location = get("LOC"),
            point = TyphoonPoint(
                timeMillis = time,
                latitude = lat,
                longitude = lon,
                centralPressureHpa = num("PS")?.toInt(),
                maxWindMs = num("WS"),
                strongWindRadiusKm = num("RAD15"),
                stormRadiusKm = num("RAD25"),
                // RAD = 70% 이상 예상확률반경. 진로 불확실성을 나타내는 값이라
                // 예보원 반경으로 사용합니다.
                forecastErrorRadiusKm = num("RAD"),
                isForecast = analysisTime != null && forecastTime != null && forecastTime > analysisTime,
                intensity = classifyIntensity(num("WS")),
                size = classifySize(num("RAD15"))
            )
        )
    }

    /** 시각 표기: yyyyMMddHHmm (UTC) */
    private fun parseUtc(s: String): Long? {
        val cleaned = s.filter { it.isDigit() }
        val pattern = when (cleaned.length) {
            12 -> "yyyyMMddHHmm"
            10 -> "yyyyMMddHH"
            14 -> "yyyyMMddHHmmss"
            else -> return null
        }
        return runCatching {
            SimpleDateFormat(pattern, Locale.KOREA).apply {
                timeZone = TimeZone.getTimeZone("UTC")   // API허브는 UTC 기준
            }.parse(cleaned)?.time
        }.getOrNull()
    }

    /** 최대풍속으로 강도 분류 (기상청 기준) */
    private fun classifyIntensity(windMs: Double?): String? = when {
        windMs == null -> null
        windMs >= 54 -> "초강력"
        windMs >= 44 -> "매우강"
        windMs >= 33 -> "강"
        windMs >= 25 -> "중"
        else -> "약"
    }

    /** 강풍반경으로 크기 분류 (기상청 기준) */
    private fun classifySize(rad15Km: Double?): String? = when {
        rad15Km == null -> null
        rad15Km >= 800 -> "초대형"
        rad15Km >= 500 -> "대형"
        rad15Km >= 300 -> "중형"
        else -> "소형"
    }
}
