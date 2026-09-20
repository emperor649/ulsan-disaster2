package com.ulsan.disasteralert.data

/**
 * 조위관측소 제원
 */
data class TideStation(
    val code: String,          // KHOA 관측소 코드 (예: DT_00XX)
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /**
     * 이 조위를 넘으면 하천 하구 배수가 사실상 막히는 기준 (cm, 관측소 기준면 기준).
     * 태화강·동천 하구부 배수문 및 우수토실 표고에서 산출해야 하는 값이며,
     * 정확한 값은 울산시 하수도정비기본계획 / 자연재해저감 종합계획에서 확인 필요.
     */
    val drainageBlockLevelCm: Int
)

/** 고조(만조) 또는 저조(간조) 예보 1건 */
data class TideExtreme(
    val timeMillis: Long,
    val levelCm: Int,
    val isHighTide: Boolean    // true = 고조(만조), false = 저조(간조)
)

/**
 * 현재 조위 상황 종합.
 *
 * 차바(2016) 당시 울산 피해가 커진 결정적 원인은 폭우 시간대가 만조와 겹쳐
 * 빗물이 바다로 빠져나가지 못하고 태화강이 역류·범람한 것이었다.
 * 따라서 조위는 단순 참고값이 아니라 위험도 판단의 핵심 변수다.
 */
data class TideStatus(
    val station: TideStation,
    val currentLevelCm: Int?,          // 실시간 관측 조위. 결측 시 null
    val nextHighTide: TideExtreme?,    // 다음 만조
    val previousHighTide: TideExtreme?,// 직전 만조
    val minutesToNextHighTide: Int?,
    val isNearHighTide: Boolean,       // 만조 전후 임계시간 이내
    val isSpringTide: Boolean,         // 대조기(사리) — 만조 조위가 평소보다 높음
    val drainageBlocked: Boolean,      // 현재 조위가 배수 차단 수준
    val tideMultiplier: Double,        // 위험도 보정 계수
    val description: String
)

/**
 * 하천 수위 + 조위를 결합한 배수 위험 판단.
 *
 * 하구부에서는 하천 수위가 높고 조위도 높으면 물이 나갈 곳이 없어진다(배수 불량).
 * 이 조합이 울산 도심 침수의 전형적 패턴이다.
 */
data class BackwaterRisk(
    val level: BackwaterLevel,
    val riverStationName: String?,
    val message: String
)

enum class BackwaterLevel(val displayName: String, val score: Int) {
    NONE("정상", 0),
    REDUCED("배수 지연", 3),      // 조위 상승으로 배수 속도 저하
    BLOCKED("배수 차단", 7),      // 조위가 하천 수위에 근접, 자연배수 불가
    REVERSE("역류 위험", 12)      // 조위가 하천 수위보다 높음 — 바닷물 역류
}
