package com.ulsan.disasteralert.data

/**
 * 지하차도 단계별 대응 기준.
 *
 * 출처: 2026년 여름철 자연재난 대비 재난상황 대응계획서 — 지하차도 침수 대응
 *
 * ── 이 기준이 중요한 이유 ──
 * 침수심 센서가 외부 연계되지 않아 자동 판정을 포기했었습니다.
 * 그런데 대응계획서는 지하차도에 **별도의 4단계 기준**을 두고 있었고,
 * 심각 단계를 제외하면 **전부 강수량·특보로 판정 가능**합니다.
 *
 * 즉 센서 없이도 관심→주의→경계까지는 자동으로 알릴 수 있습니다.
 * 심각 단계(침수심 5cm)만 현장 확인이 필요합니다.
 *
 * 앱의 역할이 "확인하세요"에서 "지금 몇 단계이고 무엇을 하세요"로 올라갑니다.
 */
object UnderpassStage {

    enum class Stage(val label: String, val colorHex: String, val rank: Int) {
        NORMAL("평상", "#4CAF50", 0),
        ATTENTION("관심", "#FBC02D", 1),
        CAUTION("주의", "#F57C00", 2),
        ALERT("경계", "#E64A19", 3),
        SERIOUS("심각", "#B71C1C", 4)
    }

    /** 단계 판정 근거 */
    data class Trigger(val stage: Stage, val description: String, val observed: String)

    data class Judgement(
        val stage: Stage,
        val triggers: List<Trigger>,
        /** 단계별 주요 조치사항 — 대응계획서 원문 */
        val actions: List<String>,
        /** 즉시 통제 사유에 해당하는지 (별도 기준) */
        val immediateControlReasons: List<String>,
        /** 현장 배치 인원 기준 */
        val staffingNote: String
    )

    data class Input(
        val forecastDailyMm: Double? = null,
        val cumulative3hMm: Double? = null,
        val cumulative12hMm: Double? = null,
        val preliminaryAlert: Boolean = false,      // 예비특보 발효
        val heavyRainAdvisory: Boolean = false,     // 호우주의보
        val heavyRainWarning: Boolean = false,      // 호우경보
        val typhoonAdvisory: Boolean = false,       // 태풍주의보
        val floodAdvisory: Boolean = false,         // 홍수주의보
        val floodWarning: Boolean = false,          // 홍수경보
        val majorDisasterLikely: Boolean = false,   // 대규모 재난발생 가능성 확실
        /** 침수심 (cm) — 센서 미연계 시 null */
        val floodDepthCm: Double? = null,
        /** 배수펌프 미작동 또는 처리용량 초과로 물이 고이거나 증가 */
        val pumpFailureOrOverflow: Boolean = false,
        /** 홍수통제소·하천관리청으로부터 하천범람 우려 통보 */
        val riverOverflowNotice: Boolean = false,
        /** 경찰·기상청 등 관계기관으로부터 통제 필요 통보 */
        val agencyControlRequest: Boolean = false
    )

    // ── 단계별 임계값 ──
    const val ATTENTION_DAILY_MM = 30.0
    const val CAUTION_3H_MM = 60.0
    const val CAUTION_12H_MM = 110.0
    const val ALERT_3H_MM = 90.0
    const val ALERT_12H_MM = 180.0
    const val SERIOUS_DEPTH_CM = 5.0

    fun judge(input: Input): Judgement {
        val t = mutableListOf<Trigger>()

        // ── 심각 ──
        if (input.majorDisasterLikely) {
            t.add(Trigger(Stage.SERIOUS, "태풍·호우로 인한 대규모 재난발생 가능성 확실 시", "상황 판단"))
        }
        if (input.floodWarning) {
            t.add(Trigger(Stage.SERIOUS, "홍수경보", "홍수경보 발효 중"))
        }
        input.floodDepthCm?.let {
            if (it >= SERIOUS_DEPTH_CM)
                t.add(Trigger(Stage.SERIOUS, "침수심 5cm", "침수심 ${it}cm"))
        }

        // ── 경계 ──
        if (input.heavyRainWarning) {
            t.add(Trigger(Stage.ALERT, "호우경보", "호우경보 발효 중"))
        }
        input.cumulative3hMm?.let {
            if (it >= ALERT_3H_MM)
                t.add(Trigger(Stage.ALERT, "3시간 90mm 이상", "3시간 ${fmt(it)}mm"))
        }
        input.cumulative12hMm?.let {
            if (it >= ALERT_12H_MM)
                t.add(Trigger(Stage.ALERT, "12시간 180mm 이상", "12시간 ${fmt(it)}mm"))
        }
        if (input.floodAdvisory) {
            t.add(Trigger(Stage.ALERT, "홍수주의보", "홍수주의보 발효 중"))
        }

        // ── 주의 ──
        if (input.heavyRainAdvisory) {
            t.add(Trigger(Stage.CAUTION, "호우주의보", "호우주의보 발효 중"))
        }
        input.cumulative3hMm?.let {
            if (it >= CAUTION_3H_MM)
                t.add(Trigger(Stage.CAUTION, "3시간 60mm 이상", "3시간 ${fmt(it)}mm"))
        }
        input.cumulative12hMm?.let {
            if (it >= CAUTION_12H_MM)
                t.add(Trigger(Stage.CAUTION, "12시간 110mm 이상", "12시간 ${fmt(it)}mm"))
        }
        if (input.typhoonAdvisory) {
            t.add(Trigger(Stage.CAUTION, "태풍주의보", "태풍주의보 발효 중"))
        }

        // ── 관심 ──
        input.forecastDailyMm?.let {
            if (it >= ATTENTION_DAILY_MM)
                t.add(Trigger(Stage.ATTENTION, "강우량 30mm/일 이상 예보", "일강우 예보 ${fmt(it)}mm"))
        }
        if (input.preliminaryAlert) {
            t.add(Trigger(Stage.ATTENTION, "예비특보 발효", "예비특보 발효 중"))
        }

        val stage = t.maxByOrNull { it.stage.rank }?.stage ?: Stage.NORMAL

        return Judgement(
            stage = stage,
            triggers = t.filter { it.stage == stage },
            actions = actionsFor(stage),
            immediateControlReasons = immediateControl(input),
            staffingNote = staffing(stage)
        )
    }

    private fun fmt(v: Double) = "%.0f".format(v)

    /**
     * 단계별 주요 조치사항 — 대응계획서 원문 그대로.
     * 앱이 새로 만든 지침이 아닙니다.
     */
    private fun actionsFor(stage: Stage): List<String> = when (stage) {
        Stage.NORMAL -> emptyList()
        Stage.ATTENTION -> listOf(
            "현장배치 담당자 확인",
            "유관기관 비상연락처 및 담당자 확인",
            "진입차단·배수시설 점검"
        )
        Stage.CAUTION -> listOf(
            "현장담당자 배치",
            "차단시설 작동상태 확인",
            "우회도로 안내 준비"
        )
        Stage.ALERT -> listOf(
            "현장담당자 실시간 상황보고 및 통제준비",
            "유관기관 지원요청",
            "차단시설 가동 준비"
        )
        Stage.SERIOUS -> listOf(
            "진입차단시설 가동",
            "즉시 통제 실시",
            "우회도로 안내"
        )
    }

    /**
     * 즉시 통제 사유.
     *
     * 단계와 별개로 운영되는 기준입니다. 이 중 하나라도 해당하면 단계와 무관하게 즉시 통제입니다.
     * 특히 세 번째 항목 — **경찰이 통제 필요를 통보하는 경우** — 는
     * 경찰 쪽에서 이 앱을 쓸 때 직접 근거가 되는 조항입니다.
     */
    private fun immediateControl(input: Input): List<String> {
        val r = mutableListOf<String>()
        if (input.pumpFailureOrOverflow)
            r.add("배수펌프 미작동·처리용량 초과로 지하차도 내 물이 고이거나 증가")
        if (input.riverOverflowNotice)
            r.add("홍수통제소·하천관리청 등으로부터 하천범람 우려 통보")
        if (input.agencyControlRequest)
            r.add("경찰·기상청 등 관계기관으로부터 통제 필요 통보")
        input.floodDepthCm?.let {
            if (it >= SERIOUS_DEPTH_CM)
                r.add("도로관리청 판단 (최대 침수심 5cm)")
        }
        return r
    }

    /** 현장 배치 인원 기준 */
    private fun staffing(stage: Stage): String =
        if (stage.rank >= Stage.CAUTION.rank)
            "침수우려 높은 지하차도 4인 (도로관리기관·읍면동·이통장/방재단·경찰), 일반 지하차도 2인 이상"
        else
            "배치 전 — 담당자 및 비상연락처 확인 단계"

    /**
     * 침수우려가 높은 지하차도 17개소.
     * 이 지점들은 4인 배치 대상이며, Underpasses의 고위험 등급과 일치합니다.
     */
    val highConcernNames = listOf(
        "우정", "명정", "동천", "번영교강북", "학성교강북",          // 중구 5
        "삼호", "번영교강남", "명촌강남", "삼산", "학성교강남",        // 남구 5
        "명촌본선", "상방", "화봉",                                // 북구 3
        "반천산단진입도로", "대안", "신화", "울산역"                  // 울주군 4
    )

    /**
     * 외수침수(하천범람) 우려 지하차도 7개소와 연계 하천.
     *
     * 내수침수(자체 배수 불량)와 달리, 이 지점들은 **하천 수위**가 직접적인 위험 요인입니다.
     * 하천 수위 데이터로 선제 판단이 가능한 곳들입니다.
     */
    val externalFloodRisk = mapOf(
        "명촌강남" to "태화강",
        "명촌본선" to "태화강",
        "상방" to "명촌천",
        "울산역" to "태화강",
        "신화" to "작괘천"
    )

    /**
     * 신화지하차도는 작괘천 제방보다 지반고가 높아 외수침수 위험이 낮습니다.
     * (대응계획서 각주)
     */
    const val SINHWA_NOTE = "신화지하차도는 작괘천 제방보다 지반고가 높아 위험이 낮음"
}
