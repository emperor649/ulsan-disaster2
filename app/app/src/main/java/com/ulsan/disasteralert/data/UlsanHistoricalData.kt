package com.ulsan.disasteralert.data

/**
 * 울산 지역 과거 재해 이력 시드 데이터.
 *
 * 출처: 기상청 태풍분석보고서, 울산시 재난 발표자료, 지역 언론 보도 등을 정리한 값.
 * ※ 운영 시에는 이 하드코딩 데이터를 서버 DB로 옮기고,
 *   행안부 재해연보 / 국가재난관리시스템(NDMS) 원본 통계로 교체·검증할 것.
 */
object UlsanHistoricalData {

    val events: List<HistoricalEvent> = listOf(
        HistoricalEvent(
            eventId = "chaba_2016",
            name = "태풍 차바",
            date = "20161005",
            eventType = EventType.TYPHOON,
            maxHourlyRainMm = 104.2,   // 울산 관측소 시간당 최대 (지점 사상 최대 기록)
            totalRainMm = 266.0,       // 울산 일 강수량 (북구 매곡동은 370mm)
            durationHours = 8,
            tideCoincided = true,      // 만조 시간대와 겹쳐 배수 불가 → 피해 급증
            affectedDistricts = listOf("중구", "남구", "북구", "울주군", "동구"),
            damageSummary = "태화강 범람, 주택침수 2,968건·차량침수 1,670건 등 총 6,289건, 피해액 약 1,930억원",
            severityScore = 10
        ),
        HistoricalEvent(
            eventId = "heavyrain_202507",
            name = "2025년 7월 집중호우",
            date = "20250719",
            eventType = EventType.HEAVY_RAIN,
            maxHourlyRainMm = 58.5,
            totalRainMm = 332.0,       // 울주군 두서면 3일 누적
            durationHours = 60,
            tideCoincided = false,
            affectedDistricts = listOf("울주군", "중구", "남구", "북구"),
            damageSummary = "태화강 사연교 홍수경보·태화교 홍수주의보, 산사태 위기경보 심각, 대규모 단수, 112신고 181건",
            severityScore = 8
        ),
        HistoricalEvent(
            eventId = "heavyrain_202508",
            name = "2025년 8월 호우",
            date = "20250804",
            eventType = EventType.HEAVY_RAIN,
            maxHourlyRainMm = 40.0,
            totalRainMm = 113.8,
            durationHours = 12,
            tideCoincided = false,
            affectedDistricts = listOf("남구", "중구", "북구", "울주군"),
            damageSummary = "삼산동 정전, 화봉동 엘리베이터 침수, 우정동 지하차도 침수, 상습침수 3개소 통제",
            severityScore = 5
        ),
        HistoricalEvent(
            eventId = "heavyrain_202408",
            name = "2024년 8월 호우",
            date = "20240820",
            eventType = EventType.HEAVY_RAIN,
            maxHourlyRainMm = 35.0,
            totalRainMm = 90.0,
            durationHours = 10,
            tideCoincided = false,
            affectedDistricts = listOf("울주군"),
            damageSummary = "온산읍 원산리 차량 3대 침수, 서생면 주택 침수로 주민 고립·구조, 신고 75건",
            severityScore = 4
        ),
        HistoricalEvent(
            eventId = "heavyrain_202608",
            name = "2026년 8월 호우·강풍",
            date = "20260817",
            eventType = EventType.HEAVY_RAIN,
            maxHourlyRainMm = 30.0,
            totalRainMm = 145.3,
            durationHours = 20,
            tideCoincided = false,
            affectedDistricts = listOf("중구", "남구", "북구", "울주군"),
            damageSummary = "교동 담벼락 붕괴, 신정동 가로수 전도(부상 1명), 우정동·야음동 도로 침수",
            severityScore = 4
        )
    )

    /**
     * 상습 침수 지점.
     * 뉴스·재난기록에서 반복 확인된 곳 위주로 구성했으며,
     * 좌표는 대략값이므로 실제 배포 전 정확한 측량값으로 교체 필요.
     */
    val hotspots: List<FloodHotspot> = listOf(
        // ── 중구 ──
        FloodHotspot(
            "우정동 지하차도", "중구", HotspotType.UNDERPASS, 35.5620, 129.3080,
            historicalOccurrences = 3, triggerHourlyRainMm = 35.0, triggerCumulativeRainMm = 90.0,
            notes = "2025년 8월 호우 시 차량 통행 불가 수준 침수. 인명피해 직결 위험 최상위."
        ),
        FloodHotspot(
            "태화종합시장 일원", "중구", HotspotType.LOWLAND_MARKET, 35.5560, 129.3120,
            historicalOccurrences = 3, triggerHourlyRainMm = 40.0, triggerCumulativeRainMm = 100.0,
            notes = "차바 당시 최대 피해지. 2025년에도 사전 배수펌프 가동. 태화강 중류 저지대."
        ),
        FloodHotspot(
            "유곡동·태화동 일원", "중구", HotspotType.RESIDENTIAL, 35.5640, 129.3020,
            historicalOccurrences = 2, triggerHourlyRainMm = 45.0, triggerCumulativeRainMm = 110.0,
            notes = "차바 당시 주택 침수 집중 발생. 배수 인프라 부족 지적된 구역."
        ),
        FloodHotspot(
            "신삼호교 하부", "중구", HotspotType.BRIDGE_UNDERPASS, 35.5490, 129.3050,
            historicalOccurrences = 2, triggerHourlyRainMm = 35.0, triggerCumulativeRainMm = 85.0,
            notes = "상습 침수 지역으로 반복 통제됨."
        ),

        // ── 남구 ──
        FloodHotspot(
            "태화강 국가정원 둔치", "남구", HotspotType.RIVERSIDE_ROAD, 35.5450, 129.3260,
            historicalOccurrences = 3, triggerHourlyRainMm = 30.0, triggerCumulativeRainMm = 80.0,
            notes = "하천 수위 상승 시 가장 먼저 잠기는 구역. 산책로 이용객 대피 우선 대상."
        ),
        FloodHotspot(
            "삼호교 남단", "남구", HotspotType.BRIDGE_UNDERPASS, 35.5530, 129.2960,
            historicalOccurrences = 3, triggerHourlyRainMm = 35.0, triggerCumulativeRainMm = 90.0,
            notes = "차바·2025년 호우 모두 통제. 하부 도로 침수 반복."
        ),
        FloodHotspot(
            "여천오거리", "남구", HotspotType.RIVERSIDE_ROAD, 35.5300, 129.3400,
            historicalOccurrences = 2, triggerHourlyRainMm = 45.0, triggerCumulativeRainMm = 100.0,
            notes = "차바 당시 도로 침수 통제. 저지대 교차로."
        ),
        FloodHotspot(
            "번영교 일원", "남구", HotspotType.BRIDGE_UNDERPASS, 35.5420, 129.3350,
            historicalOccurrences = 2, triggerHourlyRainMm = 35.0, triggerCumulativeRainMm = 85.0,
            notes = "상습 침수 지역."
        ),

        // ── 북구 ──
        FloodHotspot(
            "상방지하차도", "북구", HotspotType.UNDERPASS, 35.5850, 129.3600,
            historicalOccurrences = 2, triggerHourlyRainMm = 40.0, triggerCumulativeRainMm = 95.0,
            notes = "차바 당시 침수 통제. 이후 진입 차단시설 설치 대상지."
        ),
        FloodHotspot(
            "매곡동 일원", "북구", HotspotType.RESIDENTIAL, 35.6080, 129.3350,
            historicalOccurrences = 1, triggerHourlyRainMm = 60.0, triggerCumulativeRainMm = 150.0,
            notes = "차바 당시 시간당 124mm·총 370mm 관측. 울산 내 최대 강수 기록 지점."
        ),
        FloodHotspot(
            "천곡문화센터 앞 도로", "북구", HotspotType.RIVERSIDE_ROAD, 35.6200, 129.3550,
            historicalOccurrences = 1, triggerHourlyRainMm = 45.0, triggerCumulativeRainMm = 120.0,
            notes = "2025년 7월 호우 시 통제."
        ),

        // ── 울주군 ──
        FloodHotspot(
            "언양읍 반천리 일원", "울주군", HotspotType.RIVERSIDE_ROAD, 35.5750, 129.1200,
            historicalOccurrences = 2, triggerHourlyRainMm = 45.0, triggerCumulativeRainMm = 150.0,
            notes = "2025년 7월 차량 50여대 침수. 아파트 인근 주차장 저지대."
        ),
        FloodHotspot(
            "온산읍 원산사거리", "울주군", HotspotType.INDUSTRIAL, 35.4300, 129.3400,
            historicalOccurrences = 2, triggerHourlyRainMm = 35.0, triggerCumulativeRainMm = 85.0,
            notes = "2024년 차량 3대 침수. 산업단지 인접 상습 침수 교차로."
        ),
        FloodHotspot(
            "두서면 일원", "울주군", HotspotType.LANDSLIDE_ZONE, 35.6800, 129.1000,
            historicalOccurrences = 2, triggerHourlyRainMm = 50.0, triggerCumulativeRainMm = 200.0,
            notes = "2025년 7월 3일간 332mm 최다 관측. 산사태·낙석 발생 이력."
        ),
        FloodHotspot(
            "삼동면 왕방·사촌·하잠마을", "울주군", HotspotType.RESIDENTIAL, 35.5100, 129.1600,
            historicalOccurrences = 1, triggerHourlyRainMm = 50.0, triggerCumulativeRainMm = 180.0,
            notes = "2025년 7월 주민 대피 권고 발령 구역."
        ),
        FloodHotspot(
            "범서읍 천상리", "울주군", HotspotType.RIVERSIDE_ROAD, 35.5650, 129.2200,
            historicalOccurrences = 1, triggerHourlyRainMm = 60.0, triggerCumulativeRainMm = 200.0,
            notes = "차바 당시 하천 증수로 가옥 유실 발생."
        ),
        FloodHotspot(
            "서생면 일원", "울주군", HotspotType.RESIDENTIAL, 35.3400, 129.3100,
            historicalOccurrences = 1, triggerHourlyRainMm = 40.0, triggerCumulativeRainMm = 90.0,
            notes = "2024년 주택 침수로 주민 고립·구조 사례."
        ),

        // ── 동구 ──
        FloodHotspot(
            "동부동 일원", "동구", HotspotType.RESIDENTIAL, 35.5000, 129.4300,
            historicalOccurrences = 1, triggerHourlyRainMm = 60.0, triggerCumulativeRainMm = 200.0,
            notes = "차바 당시 강풍으로 전선 절단·2천 가구 정전."
        )
    )

    /** 울산 강수 극한 기후 지수 (참고 기준선) */
    const val ULSAN_MAX_DAILY_RAIN_NORMAL = 134.0   // 1일 최다 강수량 평년값
    const val ULSAN_MAX_5DAY_RAIN_NORMAL = 209.6    // 5일 최대 강수량 평년값

    fun hotspotsIn(district: String): List<FloodHotspot> =
        hotspots.filter { it.district == district }

    fun eventsAffecting(district: String): List<HistoricalEvent> =
        events.filter { it.affectedDistricts.contains(district) }
}
