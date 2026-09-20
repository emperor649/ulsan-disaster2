package com.ulsan.disasteralert.data

/**
 * 과거 실제 발생한 재해 사례.
 * 현재 관측되는 강수 패턴이 과거 어느 사례와 유사한지 매칭하는 데 사용한다.
 */
data class HistoricalEvent(
    val eventId: String,
    val name: String,              // 예: "태풍 차바"
    val date: String,              // yyyyMMdd
    val eventType: EventType,
    val maxHourlyRainMm: Double,   // 당시 최대 시간당 강수량
    val totalRainMm: Double,       // 당시 총 누적 강수량
    val durationHours: Int,        // 강우 지속 시간
    val tideCoincided: Boolean,    // 만조와 겹쳤는지 (울산 침수의 핵심 변수)
    val affectedDistricts: List<String>,
    val damageSummary: String,
    val severityScore: Int         // 0~10, 실제 피해 규모 기준
)

enum class EventType(val displayName: String) {
    TYPHOON("태풍"),
    HEAVY_RAIN("집중호우"),
    LANDSLIDE("산사태"),
    FLOOD("하천범람")
}

/**
 * 반복적으로 침수 피해가 확인된 지점.
 * 뉴스·재난 기록에서 2회 이상 등장한 곳을 상습 침수지로 분류한다.
 */
data class FloodHotspot(
    val name: String,
    val district: String,          // 중구, 남구, 동구, 북구, 울주군
    val hotspotType: HotspotType,
    val latitude: Double,
    val longitude: Double,
    val historicalOccurrences: Int,     // 과거 침수 확인 횟수
    val triggerHourlyRainMm: Double,    // 이 지점이 침수되기 시작한 시간당 강수량 관측치
    val triggerCumulativeRainMm: Double,// 누적 강수량 기준
    val notes: String
)

enum class HotspotType(val displayName: String, val baseWeight: Int) {
    UNDERPASS("지하차도", 5),        // 인명피해 직결, 최우선
    RIVERSIDE_ROAD("하천변 도로", 4),
    LOWLAND_MARKET("저지대 상권", 4),
    RESIDENTIAL("주택 밀집지", 3),
    BRIDGE_UNDERPASS("교량 하부", 3),
    LANDSLIDE_ZONE("산사태 우려지", 4),
    INDUSTRIAL("산업단지 인근", 2)
}

/**
 * 지역(구·군)별 과거 이력에서 도출한 취약도 지수.
 * 실시간 위험도 점수에 곱해지는 보정 계수를 제공한다.
 */
data class VulnerabilityProfile(
    val district: String,
    val pastEventCount: Int,            // 과거 재해 발생 횟수
    val hotspotCount: Int,              // 상습 침수지 개수
    val criticalHotspotCount: Int,      // 지하차도 등 인명피해 직결 지점 수
    val lowestDamageThresholdMm: Double,// 과거 피해가 발생한 최저 시간당 강수량
    val tideSensitive: Boolean,         // 만조 영향을 받는 하구 지역인지
    val vulnerabilityMultiplier: Double // 최종 보정 계수 (1.0 = 평균)
)

/**
 * 현재 강수 상황과 과거 사례의 유사도 매칭 결과
 */
data class SimilarEventMatch(
    val event: HistoricalEvent,
    val similarityPercent: Int,      // 0~100
    val progressPercent: Int,        // 당시 강수량 대비 현재 진행률
    val warningMessage: String
)
