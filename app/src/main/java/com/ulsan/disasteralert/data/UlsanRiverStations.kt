package com.ulsan.disasteralert.data

/**
 * 울산 지역 주요 수위관측소.
 *
 * ⚠️ 이 파일은 미검증 영역입니다. (2025.9 기준 유일하게 남은 API 미확정 항목)
 *
 * 관측소 코드는 앱 첫 실행 시 StationSyncHelper가 홍수통제소 API에서 받아 자동으로 채웁니다.
 * 다만 **기준홍수위는 API가 주는 값과 시 하천기본계획 값이 다를 수 있으므로**,
 * 최종적으로는 울산시 하천관리과 자료로 대조해야 합니다.
 *
 * 확보 방법:
 *   1) FloodApiService.getStationInfo() 호출 → 전국 관측소 제원이 내려옴
 *   2) addr에 "울산"이 포함된 항목만 필터
 *   3) obsnm으로 사연교/태화교/병영교 매칭 후 wlobscd(코드)와
 *      attwl/wrnwl/almwl/srswl(기준홍수위 4단계)를 이 파일에 반영
 *
 * StationSyncHelper.syncUlsanStations()가 이 과정을 자동화합니다.
 * 첫 실행 시 반드시 한 번 동기화한 뒤 사용하세요.
 *
 * 아래 값은 2025년 7월 실제 홍수특보 발령 지점을 기준으로 한 구조 예시이며,
 * 기준홍수위는 임시값(placeholder)입니다.
 */
object UlsanRiverStations {

    val stations: List<RiverStation> = listOf(
        RiverStation(
            code = "TODO_SAYEON",          // ← API 동기화로 채울 것
            name = "태화강(사연교)",
            riverName = "태화강",
            district = "울주군",
            latitude = 35.5810, longitude = 129.1980,
            attentionLevel = 3.0,
            warningLevel = 4.5,
            alertLevel = 6.0,
            dangerLevel = 7.5,
            protectedAreas = listOf("범서읍 천상리", "언양읍 반천리"),
            notes = "태화강 상류. 2025년 7월 19일 05:40 홍수경보 발령 지점. " +
                    "이 지점 수위 상승은 1~2시간 뒤 중류 태화교 상승으로 이어짐 — 선행 지표로 활용 가치가 큼."
        ),
        RiverStation(
            code = "TODO_TAEHWA",
            name = "태화강(태화교)",
            riverName = "태화강",
            district = "중구",
            latitude = 35.5540, longitude = 129.3100,
            attentionLevel = 3.5,
            warningLevel = 5.0,
            alertLevel = 6.5,
            dangerLevel = 8.0,
            protectedAreas = listOf("태화종합시장", "우정동", "태화동", "유곡동", "태화강 국가정원 둔치"),
            notes = "태화강 중류. 2025년 7월 홍수주의보 발령. " +
                    "차바 당시 이 구간 범람으로 중구 전역 침수. 도심 피해와 직결되는 핵심 지점."
        ),
        RiverStation(
            code = "TODO_BYEONGYEONG",
            name = "동천(병영교)",
            riverName = "동천",
            district = "중구",
            latitude = 35.5720, longitude = 129.3350,
            attentionLevel = 2.5,
            warningLevel = 3.5,
            alertLevel = 4.5,
            dangerLevel = 5.5,
            protectedAreas = listOf("반구동", "학성동", "북구 화봉동"),
            notes = "동천. 2025년 7월 홍수주의보 발령. 태화강 지류로 본류 수위가 높으면 배수가 막혀 역류 위험."
        )
    )

    fun stationsIn(district: String): List<RiverStation> =
        stations.filter { it.district == district }

    /** 특정 침수 예상지역을 보호하는 관측소 찾기 */
    fun stationsProtecting(areaName: String): List<RiverStation> =
        stations.filter { s -> s.protectedAreas.any { it.contains(areaName) } }

    fun byCode(code: String): RiverStation? = stations.find { it.code == code }

    /** 관측소 코드가 아직 동기화되지 않았는지 확인 */
    fun needsSync(): Boolean = stations.any { it.code.startsWith("TODO_") }
}
