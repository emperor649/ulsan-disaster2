package com.ulsan.disasteralert.data

/**
 * 수위관측소 제원.
 *
 * 기준홍수위는 4단계로 관리된다 (하천법 및 홍수예보 기준):
 *  - 관심수위(attention): 하천 수위가 올라가기 시작해 예의주시가 필요한 단계
 *  - 주의보수위(warning): 홍수주의보 발령 기준
 *  - 경보수위(alert): 홍수경보 발령 기준
 *  - 계획홍수위(danger): 제방이 견디도록 설계된 최대 수위. 초과 시 범람 위험
 */
data class RiverStation(
    val code: String,              // 홍수통제소 관측소 코드
    val name: String,              // 예: "태화강(사연교)"
    val riverName: String,         // 태화강, 동천 등
    val district: String,          // 관할 구·군
    val latitude: Double,
    val longitude: Double,
    val attentionLevel: Double,    // 관심수위 (m)
    val warningLevel: Double,      // 주의보수위 (m)
    val alertLevel: Double,        // 경보수위 (m)
    val dangerLevel: Double,       // 계획홍수위 (m) = 제방 만수위 기준
    val protectedAreas: List<String>, // 이 지점 범람 시 침수되는 구역
    val notes: String
)

/**
 * 특정 시각의 수위 관측값
 */
data class WaterLevelReading(
    val stationCode: String,
    val waterLevelM: Double,
    val flowRate: Double?,         // 유량 (m³/s), 제공되지 않는 지점도 있음
    val observedAtMillis: Long
)

/**
 * 수위 분석 결과.
 *
 * 핵심: 현재 수위보다 "상승 속도"와 "임계 도달 예상시간"이 실무적으로 훨씬 중요하다.
 * 차바 당시 울산은 비가 쏟아진 지 2시간 만에 하천이 범람했고,
 * 주민 안내가 늦었다는 지적이 나왔다. 도달 예상시간은 대피 리드타임을 확보하기 위한 값이다.
 */
data class RiverLevelStatus(
    val station: RiverStation,
    val currentLevelM: Double,
    val stage: RiverStage,
    val riseRateMPerHour: Double,       // 최근 상승 속도 (m/h). 음수면 하강 중
    val minutesToNextStage: Int?,       // 다음 단계 도달까지 예상 시간(분). 하강 중이면 null
    val percentToDangerLevel: Int,      // 계획홍수위(제방 만수위) 대비 도달률
    /**
     * 대응계획서 대피기준 ③ "하천제방 만수위 90% 도달" 충족 여부.
     * 계획홍수위를 만수위로 보고 계산한다.
     */
    val reachesEvacuationRatio: Boolean = false,
    val observedAtMillis: Long
)

enum class RiverStage(val displayName: String, val score: Int, val colorHex: String) {
    NORMAL("평상", 0, "#4CAF50"),
    ATTENTION("관심", 3, "#FFC107"),
    WARNING("홍수주의보", 7, "#FF9800"),
    ALERT("홍수경보", 12, "#F44336"),
    DANGER("계획홍수위 초과", 18, "#B71C1C")
}

/**
 * 홍수통제소가 실제 발령한 홍수특보
 */
data class FloodForecast(
    val stationCode: String,
    val stationName: String,
    val forecastType: String,      // 홍수주의보 / 홍수경보
    val issuedAt: String,
    val content: String
)
