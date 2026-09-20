package com.ulsan.disasteralert.data

/**
 * 태풍 진로 상의 한 시점.
 * 기상청 태풍정보에서 오는 실황 또는 예보 지점.
 */
data class TyphoonPoint(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val centralPressureHpa: Int?,
    val maxWindMs: Double?,
    /** 강풍반경 (km) — 이 안에 들면 초속 15m 이상 */
    val strongWindRadiusKm: Double?,
    /** 폭풍반경 (km) — 이 안에 들면 초속 25m 이상 */
    val stormRadiusKm: Double?,
    /** 예보원 반경 (km) — 진로 불확실성. 예보에만 존재 */
    val forecastErrorRadiusKm: Double?,
    val isForecast: Boolean,
    val intensity: String?,      // 중/강/매우강/초강력
    val size: String?            // 소형/중형/대형/초대형
)

data class Typhoon(
    val id: String,              // 태풍 번호 (예: 202618)
    val name: String,            // 태풍명
    val koreanName: String?,
    val points: List<TyphoonPoint>,
    val updatedAtMillis: Long
)

/**
 * 울산 기준 태풍 접근 분석 결과.
 */
data class TyphoonApproach(
    val typhoon: Typhoon,
    /** 울산에 가장 가까워지는 예보 지점 */
    val closestPoint: TyphoonPoint,
    val closestDistanceKm: Double,
    val hoursToClosest: Int,
    /** 예보원 반경을 감안한 최소 가능 거리 — 불확실성을 그대로 보여주기 위함 */
    val minPossibleDistanceKm: Double,
    /** 통과 방향. 울산 서쪽 통과면 위험반원에 들어간다 */
    val passageSide: PassageSide,
    /** 강풍반경 안에 들어오는지 */
    val entersStrongWindRadius: Boolean,
    val entersStormRadius: Boolean,
    /** 최근접 시각 전후의 만조 정보 — 차바형 복합재해 판정의 핵심 */
    val tideCoincidence: TideCoincidence?,
    val preparednessLevel: PreparednessLevel,
    val confidence: ForecastConfidence,
    val headline: String,
    val briefing: List<String>
)

/**
 * 태풍이 울산의 어느 쪽으로 지나가는가.
 *
 * 북반구 태풍은 진행방향 오른쪽(동쪽)이 위험반원이다.
 * 태풍이 울산 서쪽으로 지나가면 울산이 위험반원에 들어가 바람과 강수가 훨씬 강해진다.
 * 차바(2016)가 이 경우였다.
 */
enum class PassageSide(val displayName: String, val riskWeight: Double) {
    WEST("서쪽 통과 (울산이 위험반원)", 1.4),
    EAST("동쪽 통과 (울산이 가항반원)", 0.85),
    OVERHEAD("울산 직상 통과", 1.5),
    UNKNOWN("경로 불명확", 1.0)
}

/**
 * 태풍 최근접 시각과 만조가 겹치는지.
 *
 * 차바 피해가 커진 결정적 원인이었다. 태풍 예보는 3일 전에 나오고
 * 조석은 몇 달 뒤까지 계산되므로, 이 조합은 사흘 전에 미리 알 수 있다.
 * 사전 배수·펌프 배치·통제 계획을 그 시점에 짤 수 있다는 뜻이다.
 */
data class TideCoincidence(
    val highTideTimeMillis: Long,
    val highTideLevelCm: Int,
    /** 태풍 최근접 시각과 만조 시각의 차이 (분). 음수면 만조가 먼저 */
    val offsetMinutes: Int,
    val isSpringTide: Boolean,
    val severity: CoincidenceSeverity
)

enum class CoincidenceSeverity(val displayName: String) {
    NONE("겹치지 않음"),
    NEAR("근접 (3시간 이내)"),
    OVERLAP("겹침 (90분 이내)"),
    CRITICAL("완전 중첩 + 대조기")
}

/**
 * 대비 단계.
 *
 * 실시간 위험도(RiskLevel)와 완전히 분리된 축이다.
 * 위험도는 "지금 침수가 임박했나"(분 단위), 대비 단계는 "며칠 뒤 무엇을 준비하나"(일 단위).
 * 둘을 같은 점수에 더하면 비도 안 오는데 '위험'이 이틀간 떠 있게 되고, 그러면 아무도 안 본다.
 */
enum class PreparednessLevel(val displayName: String, val colorHex: String) {
    NONE("해당 없음", "#4CAF50"),
    WATCH("관찰", "#8BC34A"),          // 영향권 가능성 있음, 진로 주시
    PREPARE("대비", "#FFC107"),        // 영향권 유력, 사전 조치 시작
    READY("비상 준비", "#FF9800"),     // 직접 영향 확실, 인력·장비 배치
    IMMINENT("임박", "#F44336")        // 24시간 내 최근접
}

/**
 * 예보 신뢰도. 진로 예보는 3일 전이면 오차가 크다.
 * "온다/안 온다"로 단정하지 않고 불확실성을 그대로 보여주기 위한 값.
 */
enum class ForecastConfidence(val displayName: String, val note: String) {
    LOW("낮음", "예보원 반경이 커서 진로가 크게 바뀔 수 있습니다"),
    MEDIUM("보통", "진로가 어느 정도 좁혀졌으나 변동 가능성이 남아 있습니다"),
    HIGH("높음", "최근접이 임박해 진로가 확정적입니다")
}
