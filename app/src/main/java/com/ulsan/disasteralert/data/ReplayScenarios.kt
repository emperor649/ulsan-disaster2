package com.ulsan.disasteralert.data

/**
 * 과거 재해의 시간별 전개를 재현하기 위한 시계열 데이터.
 *
 * 목적: 실제 호우가 오기 전에 임계값이 맞는지 검증한다.
 * 차바 당시 관측값을 시간순으로 앱에 넣었을 때,
 * 실제 피해 발생 시점보다 얼마나 먼저 경보가 뜨는지(리드타임)를 측정한다.
 *
 * 리드타임이 30분 미만이면 대피에 쓸 수 없는 경보다 — 임계값을 낮춰야 한다.
 */
data class ReplayFrame(
    val minutesFromStart: Int,
    val hourlyRainMm: Double,
    val cumulativeRainMm: Double,
    val riverLevelM: Double?,      // 태화교 기준 (없으면 null)
    val tideLevelCm: Int?,
    val isNearHighTide: Boolean,
    val weatherWarning: WarningLevel,
    val crisisLevel: CrisisLevel?,
    /** 이 시점에 실제로 피해가 발생했는지 — 리드타임 측정의 기준점 */
    val actualDamageOccurred: Boolean = false,
    val damageNote: String = ""
)

data class ReplayScenario(
    val eventId: String,
    val name: String,
    val district: String,
    val description: String,
    val frames: List<ReplayFrame>
)

/**
 * 실제 관측 기록과 언론 보도의 시각 정보를 재구성한 시나리오.
 *
 * ⚠️ 프레임별 수치는 보도된 값 사이를 보간한 추정치가 섞여 있습니다.
 * 기상청 AWS 원시 관측자료(10분 단위)를 확보하면 정확도가 크게 올라갑니다.
 * 울산기상대 또는 기상자료개방포털(data.kma.go.kr)에서 과거 관측자료 신청 가능.
 */
object ReplayScenarios {

    /**
     * 태풍 차바 — 2016년 10월 5일
     *
     * 실제 기록:
     *  - 오전부터 강우 시작, 10:30~11:30 시간당 104.2mm (울산 관측소 사상 최대)
     *  - 12:00까지 누적 263.8mm
     *  - 12:30 태화강 홍수주의보 발효
     *  - 만조 시간대와 겹쳐 배수 불가 → 태화강 범람
     *  - "비가 쏟아진 지 2시간 만에 강물 범람" (보도)
     */
    val chaba2016 = ReplayScenario(
        eventId = "chaba_2016",
        name = "태풍 차바 (2016.10.05)",
        district = "중구",
        description = "만조와 겹친 극한 호우. 시간당 104.2mm는 울산 관측 사상 최대. " +
                "강우 시작 약 2시간 만에 태화강 범람.",
        frames = listOf(
            ReplayFrame(0,   5.0,   5.0,  2.1, 120, false, WarningLevel.ADVISORY, null),
            ReplayFrame(30,  12.0,  11.0, 2.3, 135, false, WarningLevel.ADVISORY, CrisisLevel.INTEREST),
            ReplayFrame(60,  25.0,  25.0, 2.8, 150, false, WarningLevel.WARNING, CrisisLevel.INTEREST),
            ReplayFrame(90,  45.0,  48.0, 3.4, 165, true,  WarningLevel.WARNING, CrisisLevel.CAUTION),
            ReplayFrame(120, 78.0,  87.0, 4.3, 178, true,  WarningLevel.WARNING, CrisisLevel.CAUTION),
            ReplayFrame(150, 104.2, 139.0, 5.4, 190, true, WarningLevel.WARNING, CrisisLevel.ALERT),
            ReplayFrame(180, 95.0,  186.0, 6.6, 196, true, WarningLevel.WARNING, CrisisLevel.ALERT,
                actualDamageOccurred = true,
                damageNote = "태화강 범람 시작. 태화종합시장·우정동 침수"),
            ReplayFrame(210, 60.0,  216.0, 7.6, 192, true, WarningLevel.WARNING, CrisisLevel.SERIOUS,
                actualDamageOccurred = true,
                damageNote = "중구 전역 침수. 주택 2,968동·차량 1,670대"),
            ReplayFrame(240, 35.0,  240.0, 8.1, 180, false, WarningLevel.WARNING, CrisisLevel.SERIOUS,
                actualDamageOccurred = true,
                damageNote = "계획홍수위 초과. 피해 최대"),
            ReplayFrame(300, 12.0,  263.8, 7.2, 155, false, WarningLevel.WARNING, CrisisLevel.SERIOUS)
        )
    )

    /**
     * 2025년 7월 집중호우 — 7월 17~19일
     *
     * 실제 기록:
     *  - 3일간 지속, 울주군 두서면 332mm 최다
     *  - 시간당 평균 58.5mm
     *  - 7/19 05:40 태화강 사연교 홍수경보, 태화교·병영교 홍수주의보
     *  - 산림청 산사태 위기경보 '심각' (7/19 13:30)
     *  - 만조와는 겹치지 않음
     */
    val heavyRain2025 = ReplayScenario(
        eventId = "heavyrain_202507",
        name = "2025년 7월 집중호우 (7.17~19)",
        district = "울주군",
        description = "3일간 누적형 호우. 만조와 겹치지 않았으나 누적 강수량이 커서 " +
                "하천 수위가 서서히 상승, 산사태 위기경보 심각까지 격상.",
        frames = listOf(
            ReplayFrame(0,    8.0,  8.0,   2.0, null, false, WarningLevel.ADVISORY, null),
            ReplayFrame(360,  15.0, 62.0,  2.4, null, false, WarningLevel.ADVISORY, CrisisLevel.INTEREST),
            ReplayFrame(720,  22.0, 128.0, 3.1, null, false, WarningLevel.WARNING, CrisisLevel.CAUTION),
            ReplayFrame(1080, 35.0, 195.0, 3.9, null, false, WarningLevel.WARNING, CrisisLevel.CAUTION),
            ReplayFrame(1440, 48.0, 248.0, 4.8, null, false, WarningLevel.WARNING, CrisisLevel.ALERT),
            ReplayFrame(1680, 58.5, 292.0, 5.6, null, false, WarningLevel.WARNING, CrisisLevel.ALERT,
                actualDamageOccurred = true,
                damageNote = "사연교 홍수경보 발령. 언양 반천리 차량 50여대 침수"),
            ReplayFrame(1800, 42.0, 318.0, 6.1, null, false, WarningLevel.WARNING, CrisisLevel.SERIOUS,
                actualDamageOccurred = true,
                damageNote = "산사태 위기경보 심각. 삼동면 3개 마을 대피 권고"),
            ReplayFrame(1920, 18.0, 332.0, 5.8, null, false, WarningLevel.WARNING, CrisisLevel.SERIOUS,
                actualDamageOccurred = true,
                damageNote = "송수관로 파손, 6만8천명 단수")
        )
    )

    /**
     * 2025년 8월 호우 — 비교적 경미했던 사례.
     * 이 경우엔 과도한 경보(오경보)가 뜨지 않아야 정상이다.
     */
    val moderateRain2025 = ReplayScenario(
        eventId = "heavyrain_202508",
        name = "2025년 8월 호우 (8.3~4)",
        district = "남구",
        description = "국지적 침수는 있었으나 대규모 피해는 없었던 사례. " +
                "이 시나리오에서 '위험/심각' 단계가 뜨면 과민 경보로 판단해야 함.",
        frames = listOf(
            ReplayFrame(0,   10.0, 10.0,  2.0, 110, false, WarningLevel.ADVISORY, null),
            ReplayFrame(180, 25.0, 48.0,  2.5, 140, false, WarningLevel.ADVISORY, CrisisLevel.INTEREST),
            ReplayFrame(360, 40.0, 92.0,  3.2, 168, true,  WarningLevel.WARNING, CrisisLevel.CAUTION,
                actualDamageOccurred = true,
                damageNote = "우정동 지하차도 침수, 삼산동 정전"),
            ReplayFrame(540, 15.0, 113.8, 2.9, 145, false, WarningLevel.ADVISORY, CrisisLevel.CAUTION)
        )
    )

    val all = listOf(chaba2016, heavyRain2025, moderateRain2025)
}
