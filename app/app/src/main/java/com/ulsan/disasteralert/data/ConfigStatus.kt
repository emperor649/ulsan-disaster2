package com.ulsan.disasteralert.data

/**
 * 설정값 검증 현황.
 *
 * 이 앱은 수십 개의 외부 값(엔드포인트, 관측소 코드, 임계값)에 의존합니다.
 * 그중 무엇이 확인된 값이고 무엇이 추정값인지 뒤섞이면,
 * 나중에 결과가 이상할 때 어디를 의심해야 할지 알 수 없게 됩니다.
 *
 * 그래서 검증 상태를 코드로 관리합니다. 진단 화면에서 한눈에 볼 수 있고,
 * 값을 확인할 때마다 여기를 갱신하면 됩니다.
 */
object ConfigStatus {

    enum class Level(val label: String, val colorHex: String) {
        /** 공식 문서·발급화면에서 직접 확인함 */
        VERIFIED("확인됨", "#2E7D32"),
        /** 자동 동기화로 채워지나 아직 실행 전 */
        PENDING_SYNC("동기화 대기", "#1565C0"),
        /** 추정값 — API 응답으로 확인 필요 */
        ESTIMATED("추정값", "#F57C00"),
        /** 임시값 — 시청 자료로만 확인 가능 */
        PLACEHOLDER("임시값", "#C62828")
    }

    data class Item(
        val name: String,
        val value: String,
        val level: Level,
        val source: String,
        /** 틀렸을 때 무슨 일이 벌어지는가 */
        val impact: String
    )

    val items = listOf(
        // ── 확인 완료 ──
        Item(
            "긴급재난문자 엔드포인트", "V2/api/DSSP-IF-00247",
            Level.VERIFIED, "safetydata.go.kr 발급 화면",
            "틀리면 재난문자를 전혀 못 받음"
        ),
        Item(
            "조석예보 엔드포인트", "apis.data.go.kr/1192136/tideFcstHghLw",
            Level.VERIFIED, "data.go.kr 발급 화면",
            "틀리면 만조 판정 불가"
        ),
        Item(
            "울산 조위관측소 코드", "DT_0020",
            Level.VERIFIED, "조석예보 활용가이드 예보지점 목록",
            "틀리면 다른 지역 조위로 만조를 판단하게 됨"
        ),
        Item(
            "태풍정보 엔드포인트", "apihub.kma.go.kr/api/typ01/url/typ_now.php",
            Level.VERIFIED, "API허브 명세",
            "틀리면 태풍 대비 단계가 동작하지 않음"
        ),
        Item(
            "울산 AWS 지점번호", "152, 943, 949, 901, 898, 900, 854, 954, 924",
            Level.VERIFIED, "기상자료개방포털 방재기상관측 지점 목록",
            "틀리면 격자 보간값으로 조용히 폴백되어 국지 극값을 놓침"
        ),
        Item(
            "AWS 매분자료", "api/typ01/cgi-bin/url/nph-aws2_min",
            Level.VERIFIED, "API허브 AWS 매분자료 조회 명세",
            "15분 강수량 실측 — 지하공간 침수 기준 판정의 근거"
        ),
        Item(
            "AWS 품질 플래그 기준", "0=정상 / 1=오류 / 9=결측",
            Level.VERIFIED, "기상자료개방포털 자료설명",
            "무시하면 장비 오류값이 최댓값으로 잡혀 오경보"
        ),
        Item(
            "하천재해 기준", "통제 200mm/30mm · 대피 250mm/만수위90%/홍수경보",
            Level.VERIFIED, "울산시 여름철 자연재난 대비 재난상황 대응계획서",
            "공식 기준과 어긋나면 현장 혼선 — 앱이 매뉴얼을 덮어쓰게 됨"
        ),
        Item(
            "산사태 기준", "통제 누적200&연속100 · 대피 누적250&연속100, 취약시간 30mm",
            Level.VERIFIED, "울산시 대응계획서 산사태 유형",
            "연속강우 조건을 빠뜨리면 산사태 경보가 과다 발령됨"
        ),
        Item(
            "지하공간 침수 기준", "통제 15분20mm&1h지속 · 대피 15분30mm 또는 시간당55mm",
            Level.VERIFIED, "울산시 대응계획서 지하공간 침수 유형",
            "15분 단위 기준이라 시간당으로만 보면 지하차도 익사를 놓침"
        ),

        Item(
            "지하차도 4단계 기준", "관심 30mm/일 · 주의 3h60 · 경계 3h90 · 심각 침수심5cm",
            Level.VERIFIED, "울산시 대응계획서 지하차도 침수 대응",
            "센서 없이도 경계 단계까지 자동 판정 가능 — 판정 포기할 뻔했던 영역"
        ),
        Item(
            "지하차도 전수 목록", "30개소 (고위험 17 · 저위험 13)",
            Level.VERIFIED, "울산시 대응계획서 별첨 지하차도 관리현황",
            "통제지점 목록에는 일부만 실려 있어, 이 목록이 없으면 사각지대가 생김"
        ),
        Item(
            "통제지점 목록", "중구26·남구21·동구4·북구57·울주군108 (총 216)",
            Level.VERIFIED, "울산시 대응계획서 인명피해 우려지역 사전통제 목록",
            "공식 지정 지점이므로 이 목록이 판단의 기준"
        ),
        Item(
            "지점별 통제 기준", "20종 — 구·군마다 완전히 다른 체계",
            Level.VERIFIED, "울산시 대응계획서 지점별 통제 기준 열",
            "구·군마다 기준 체계가 달라 통일하면 한쪽이 통째로 누락됨"
        ),

        // ── 동기화 대기 ──
        Item(
            "하천 수위관측소 코드", "앱 첫 실행 시 자동 조회",
            Level.PENDING_SYNC, "홍수통제소 관측소 제원 API",
            "동기화 실패 시 하천 수위 판단 자체가 빠짐"
        ),

        // ── 추정값 (API 응답으로 확인 필요) ──
        Item(
            "AWS 시간자료 엔드포인트", "api/typ01/url/awsh.php",
            Level.ESTIMATED, "추정 — 매분자료는 확인됨, 시간자료는 미확인",
            "틀리면 매분자료 합산 또는 ASOS로 폴백 가능"
        ),
        Item(
            "AWS 강수량 컬럼명", "RN_HR1 / RN_HOUR / RN / RN_60M 중 자동 탐색",
            Level.ESTIMATED, "추정 — 응답 헤더행 확인 필요",
            "모두 불일치 시 강수량이 0으로 읽혀 위험도가 과소평가됨"
        ),
        Item(
            "하천 수위 인증 방식", "경로형(/{key}/...) 가정",
            Level.ESTIMATED, "추정 — 쿼리형일 가능성 있음",
            "틀리면 하천 수위 조회가 전부 실패"
        ),

        // ── 임시값 (시청 자료 필요) ──
        Item(
            "태화강 계획홍수위", "태화교 8.0m / 사연교 7.5m",
            Level.PLACEHOLDER, "울산시 하천기본계획 필요",
            "하천 단계 점수(최대 18점) + 대피기준③ 만수위 90% 판정이 통째로 어긋남"
        ),
        Item(
            "동천 계획홍수위", "병영교 5.5m",
            Level.PLACEHOLDER, "울산시 하천기본계획 필요",
            "동천 유역 판단이 부정확"
        ),
        Item(
            "배수 차단 임계 조위", "180cm",
            Level.PLACEHOLDER, "울산시 하수도정비기본계획 (하구 배수문 표고)",
            "만조 배수 차단 판정이 무의미해짐"
        ),
        Item(
            "침수심 계측", "연계 불가 확정 (센서는 있으나 외부 제공 안 됨)",
            Level.PLACEHOLDER, "현장 차단기 전용 — 시스템 연계 경로 없음",
            "심각 단계(침수심 5cm)만 판정 불가. 경계 단계까지는 강수·특보로 자동 판정됨"
        ),
        Item(
            "해일높이", "미연계",
            Level.PLACEHOLDER, "해일 특보 또는 조위관측소 실측",
            "남구 장생포 해안지구 판정 불가"
        ),
        Item(
            "상황판단회의 입력", "미구현",
            Level.PLACEHOLDER, "담당자 수동 입력 기능",
            "중구 태화·우정지구는 회의 결정 사항이라 자동 판정 불가"
        ),
        Item(
            "토양함수지수 연계", "미연계",
            Level.PLACEHOLDER, "산림청 산사태정보시스템",
            "울주군 산사태 지점 다수가 이 기준을 쓰는데 판정 불가"
        ),
        Item(
            "폭풍해일 여유값", "50cm",
            Level.PLACEHOLDER, "경험적 설정 — 지역 해일고 자료로 보정 필요",
            "태풍 시 조위를 과소·과대 평가"
        ),
        Item(
            "과거 사례 시계열", "차바·2025.7 등 보간 추정",
            Level.PLACEHOLDER, "기상자료개방포털 AWS 원시 관측자료로 교체 가능",
            "임계값 검증 결과의 신뢰도가 낮아짐"
        )
    )

    fun byLevel(level: Level) = items.filter { it.level == level }

    val verifiedCount get() = items.count { it.level == Level.VERIFIED }
    val needsWorkCount get() = items.count {
        it.level == Level.ESTIMATED || it.level == Level.PLACEHOLDER
    }

    /**
     * 지금 무엇을 해야 하는지 한 줄로 알려준다.
     */
    val nextAction: String get() = when {
        byLevel(Level.ESTIMATED).isNotEmpty() ->
            "API 응답으로 확인 가능한 항목이 ${byLevel(Level.ESTIMATED).size}개 있습니다. " +
            "진단 화면을 실행해 응답 원문을 확인하세요."
        byLevel(Level.PLACEHOLDER).isNotEmpty() ->
            "남은 항목은 모두 울산시 자료가 있어야 확인됩니다. " +
            "재난안전과·하천관리과·하수도과에 자료를 요청하세요."
        else -> "모든 설정값이 확인되었습니다."
    }

    val summary: String get() = buildString {
        appendLine("확인됨 $verifiedCount · 미확인 $needsWorkCount")
        appendLine()
        append(nextAction)
    }
}
