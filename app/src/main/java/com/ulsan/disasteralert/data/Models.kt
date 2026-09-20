package com.ulsan.disasteralert.data

/**
 * 기상청 특보(주의보/경보) 항목
 * 원본: 공공데이터포털 - 기상특보 조회서비스
 */
data class WeatherWarning(
    val regionCode: String,      // 특보 구역 코드
    val regionName: String,
    val warningType: String,     // 호우, 태풍, 강풍, 대설, 폭염 등
    val warningLevel: WarningLevel,
    val announcedAt: String,     // 발표 시각 (yyyyMMddHHmm)
    val command: String          // 발표/해제/연장
)

enum class WarningLevel(val displayName: String, val score: Int) {
    NONE("없음", 0),
    ADVISORY("주의보", 2),   // 주의보
    WARNING("경보", 4)       // 경보
}

/**
 * 초단기실황/AWS 관측 강수량
 */
data class PrecipitationObservation(
    val stationId: String,
    val regionName: String,
    val hourlyRainMm: Double,     // 시간당 강수량(mm)
    val cumulativeRainMm: Double, // 누적 강수량(mm)
    val observedAt: String
)

/**
 * 행정안전부 재난 위기경보 / 재난문자(CBS)
 */
data class DisasterAlert(
    val alertId: String,
    val disasterType: String,     // 호우, 지진, 산불, 대설, 태풍 등
    val crisisLevel: CrisisLevel,
    val regionName: String,
    val message: String,
    val issuedAt: String,
    val issuingAgency: String     // 행안부, 기상청, 산림청 등
)

enum class CrisisLevel(val displayName: String, val score: Int) {
    INTEREST("관심", 1),
    CAUTION("주의", 3),
    ALERT("경계", 6),
    SERIOUS("심각", 10)
}

/**
 * 지역 단위로 통합된 최종 위험도 결과
 */
data class RegionRiskStatus(
    val regionName: String,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val activeWarnings: List<WeatherWarning>,
    val latestPrecipitation: PrecipitationObservation?,
    val activeDisasterAlerts: List<DisasterAlert>,
    val updatedAt: String
)

enum class RiskLevel(val displayName: String, val colorHex: String) {
    SAFE("안전", "#4CAF50"),
    CAUTION("주의", "#FFC107"),
    WARNING("경고", "#FF9800"),
    DANGER("위험", "#F44336"),
    SEVERE("심각", "#B71C1C")
}
