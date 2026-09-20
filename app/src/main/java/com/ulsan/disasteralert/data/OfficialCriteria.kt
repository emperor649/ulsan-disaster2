package com.ulsan.disasteralert.data

/**
 * 울산시 여름철 자연재난 대비 재난상황 대응계획서 — 재해 유형별 공식 통제·대피 기준.
 *
 * 출처: 2026년 여름철 자연재난 대비 재난상황 대응계획서 (울산시)
 *
 * ── 왜 유형별로 나누는가 ──
 * 같은 비라도 어디가 위험해지는지는 완전히 다릅니다.
 *   · 지하공간은 15분 20mm면 통제 — 시간당으로 환산하면 이미 늦습니다
 *   · 산사태는 누적강우가 필수 조건 — 단기 폭우만으로는 발령되지 않습니다
 *   · 하천은 200mm 누적 또는 시간당 30mm 예보
 *
 * 하나의 기준으로 뭉뚱그리면 지하차도 익사나 새벽 산사태를 놓칩니다.
 *
 * ── 왜 앱 점수와 분리하는가 ──
 * 앱 위험도는 가중합산한 **추정치**, 아래는 시가 정한 **공식 기준**입니다.
 * 섞으면 앱이 매뉴얼을 덮어쓰는 모양이 되고, 현장에서 무엇을 따를지 혼선이 생깁니다.
 * 앱의 역할은 판단을 대신하는 것이 아니라 기준 도달을 먼저 알리는 것입니다.
 */
object OfficialCriteria {

    enum class HazardType(val displayName: String, val icon: String) {
        LANDSLIDE("산사태", "⛰"),
        RIVER("하천재해", "🌊"),
        UNDERGROUND("지하공간 침수", "🕳")
    }

    enum class ActionLevel(val displayName: String, val colorHex: String, val rank: Int) {
        NONE("해당 없음", "#4CAF50", 0),
        CONTROL("통제", "#FF9800", 1),
        EVACUATE("대피", "#C62828", 2)
    }

    data class Trigger(
        val hazard: HazardType,
        val level: ActionLevel,
        val clause: String,
        val description: String,
        val observed: String
    )

    data class Judgement(
        val level: ActionLevel,
        val triggers: List<Trigger>,
        /** 유형별 최고 단계 */
        val byHazard: Map<HazardType, ActionLevel>,
        val summary: String,
        val actions: List<String>
    )

    /**
     * 판정에 필요한 관측·예보 입력.
     *
     * 15분 강우량이 별도로 필요합니다 — 지하공간 기준이 15분 단위이기 때문입니다.
     * AWS 매분자료(nph-aws2_min)에서 실측값을 받아 채웁니다.
     * 조회 실패 시에만 시간당÷4로 근사하며, 그 경우 관측값 문구에 명시합니다.
     */
    data class Input(
        val cumulativeRainMm: Double,      // 누적강우 (호우 시작 이후)
        val continuousRainMm: Double,      // 연속강우 (산사태 기준에 필수)
        val hourlyRainMm: Double,          // 관측 시간당
        val rain15minMm: Double? = null,   // 관측 15분 (지하공간용)
        val dailyRainMm: Double? = null,   // 일강우
        val forecastHourlyMm: Double? = null,
        val forecast3hMm: Double? = null,
        val forecastDailyMm: Double? = null,
        val riverLevelRatio: Double? = null,   // 제방 만수위 대비 (0~1+)
        val floodAlertIssued: Boolean = false,
        val damDischarge: Boolean = false,
        val undergroundDepthCm: Double? = null, // 지하공간 침수심 예상
        /** 취약시간대(일몰 후 ~ 일출 전) 여부 — 산사태 대피③ */
        val isVulnerableHours: Boolean = false,
        /** 15분 20mm가 1시간 이상 지속될 전망인지 */
        val sustained1h: Boolean = false
    )

    // ══ 산사태 ══
    const val LS_CTRL_CUMULATIVE = 200.0   // 통제① 누적강우 200mm 이상 &
    const val LS_CTRL_CONTINUOUS = 100.0   //        연속강우 100mm 이상 관측
    const val LS_CTRL_HOURLY = 20.0        // 통제② 시간당 최고 20mm,
    const val LS_CTRL_DAILY = 100.0        //        일강우 100mm 이상 예보
    const val LS_EVAC_CUMULATIVE = 250.0   // 대피① 누적강우 250mm 이상 &
    const val LS_EVAC_CONTINUOUS = 100.0   //        연속강우 100mm 이상 관측
    const val LS_EVAC_HOURLY = 50.0        // 대피② 시간당 50mm 이상,
    const val LS_EVAC_DAILY = 200.0        //        일강우 200mm 이상 예상
    const val LS_EVAC_NIGHT_HOURLY = 30.0  // 대피③ 취약시간 시간당 최고 30mm 예상

    // ══ 하천재해 ══
    const val RV_CTRL_CUMULATIVE = 200.0
    const val RV_CTRL_HOURLY = 30.0
    const val RV_EVAC_CUMULATIVE = 250.0
    const val RV_EVAC_HOURLY = 30.0
    const val RV_EVAC_3H = 90.0
    const val RV_EVAC_LEVEL_RATIO = 0.90
    const val RV_AFFECTED_DEPTH_CM = 15

    // ══ 지하공간 침수 ══
    const val UG_CTRL_15MIN = 20.0         // 통제① 15분 20mm 이상 & 1시간 이상 지속 예상
    const val UG_CTRL_DEPTH_CM = 5.0       // 통제② 침수심 5cm 도달 예상
    const val UG_EVAC_15MIN = 30.0         // 대피① 15분 30mm 강우 지속
    const val UG_EVAC_HOURLY = 55.0        // 대피② 시간당 55mm 강우 예상

    fun judge(input: Input): Judgement {
        val t = mutableListOf<Trigger>()

        // ── 산사태 ──
        if (input.cumulativeRainMm >= LS_EVAC_CUMULATIVE &&
            input.continuousRainMm >= LS_EVAC_CONTINUOUS) {
            t.add(Trigger(HazardType.LANDSLIDE, ActionLevel.EVACUATE, "대피①",
                "누적강우 250mm 이상 & 연속강우 100mm 이상 관측",
                "누적 ${fmt(input.cumulativeRainMm)}mm · 연속 ${fmt(input.continuousRainMm)}mm"))
        }
        val lsEvacHourly = maxOf(input.hourlyRainMm, input.forecastHourlyMm ?: 0.0)
        if (lsEvacHourly >= LS_EVAC_HOURLY || (input.forecastDailyMm ?: 0.0) >= LS_EVAC_DAILY) {
            t.add(Trigger(HazardType.LANDSLIDE, ActionLevel.EVACUATE, "대피②",
                "시간당 50mm 이상 또는 일강우 200mm 이상 예상",
                "시간당 ${fmt(lsEvacHourly)}mm · 일강우 예상 ${fmt(input.forecastDailyMm ?: 0.0)}mm"))
        }
        if (input.isVulnerableHours && lsEvacHourly >= LS_EVAC_NIGHT_HOURLY) {
            t.add(Trigger(HazardType.LANDSLIDE, ActionLevel.EVACUATE, "대피③",
                "취약시간(일몰 후~일출 전) 시간당 최고 30mm 강수 예상",
                "야간 시간대 · 시간당 ${fmt(lsEvacHourly)}mm"))
        }
        if (input.cumulativeRainMm >= LS_CTRL_CUMULATIVE &&
            input.continuousRainMm >= LS_CTRL_CONTINUOUS) {
            t.add(Trigger(HazardType.LANDSLIDE, ActionLevel.CONTROL, "통제①",
                "누적강우 200mm 이상 & 연속강우 100mm 이상 관측",
                "누적 ${fmt(input.cumulativeRainMm)}mm · 연속 ${fmt(input.continuousRainMm)}mm"))
        }
        val lsCtrlHourly = maxOf(input.hourlyRainMm, input.forecastHourlyMm ?: 0.0)
        if (lsCtrlHourly >= LS_CTRL_HOURLY || (input.forecastDailyMm ?: 0.0) >= LS_CTRL_DAILY) {
            t.add(Trigger(HazardType.LANDSLIDE, ActionLevel.CONTROL, "통제②",
                "시간당 최고 20mm 또는 일강우 100mm 이상 예보",
                "시간당 ${fmt(lsCtrlHourly)}mm · 일강우 예보 ${fmt(input.forecastDailyMm ?: 0.0)}mm"))
        }

        // ── 하천재해 ──
        if (input.cumulativeRainMm >= RV_EVAC_CUMULATIVE) {
            t.add(Trigger(HazardType.RIVER, ActionLevel.EVACUATE, "대피①",
                "누적강우 250mm 이상 관측", "누적 ${fmt(input.cumulativeRainMm)}mm"))
        }
        val rvHourly = maxOf(input.hourlyRainMm, input.forecastHourlyMm ?: 0.0)
        if (rvHourly >= RV_EVAC_HOURLY && (input.forecast3hMm ?: 0.0) >= RV_EVAC_3H) {
            t.add(Trigger(HazardType.RIVER, ActionLevel.EVACUATE, "대피②",
                "시간당 30mm 이상 & 3시간 90mm 강수 예상",
                "시간당 ${fmt(rvHourly)}mm · 3시간 ${fmt(input.forecast3hMm ?: 0.0)}mm"))
        }
        input.riverLevelRatio?.let {
            if (it >= RV_EVAC_LEVEL_RATIO) {
                t.add(Trigger(HazardType.RIVER, ActionLevel.EVACUATE, "대피③",
                    "하천제방 만수위 90% 또는 교량 수위 90% 도달",
                    "현재 ${(it * 100).toInt()}%"))
            }
        }
        if (input.floodAlertIssued) {
            t.add(Trigger(HazardType.RIVER, ActionLevel.EVACUATE, "대피④",
                "홍수경보 발령 시", "홍수경보 발령 중"))
        }
        if (input.damDischarge) {
            t.add(Trigger(HazardType.RIVER, ActionLevel.EVACUATE, "대피⑤",
                "호우 상황에서 댐 방류 협의 시", "댐 방류 협의"))
        }
        if (input.cumulativeRainMm >= RV_CTRL_CUMULATIVE) {
            t.add(Trigger(HazardType.RIVER, ActionLevel.CONTROL, "통제①",
                "누적강우 200mm 이상 관측", "누적 ${fmt(input.cumulativeRainMm)}mm"))
        }
        if (rvHourly >= RV_CTRL_HOURLY) {
            t.add(Trigger(HazardType.RIVER, ActionLevel.CONTROL, "통제②",
                "시간당 최고 30mm 예보 시", "시간당 ${fmt(rvHourly)}mm"))
        }

        // ── 지하공간 침수 ──
        // 15분 자료가 없으면 시간당의 1/4로 근사한다. 실제 집중호우는 고르게 오지 않으므로
        // 과소평가될 수 있어, 근사값임을 관측값 문구에 명시한다.
        val r15 = input.rain15minMm ?: (input.hourlyRainMm / 4.0)
        val approx = input.rain15minMm == null
        val suffix = if (approx) " (시간당에서 근사)" else ""

        if (r15 >= UG_EVAC_15MIN) {
            t.add(Trigger(HazardType.UNDERGROUND, ActionLevel.EVACUATE, "대피①",
                "15분 30mm 강우 지속 시", "15분 ${fmt(r15)}mm$suffix"))
        }
        val ugHourly = maxOf(input.hourlyRainMm, input.forecastHourlyMm ?: 0.0)
        if (ugHourly >= UG_EVAC_HOURLY) {
            t.add(Trigger(HazardType.UNDERGROUND, ActionLevel.EVACUATE, "대피②",
                "시간당 55mm 강우 예상 시", "시간당 ${fmt(ugHourly)}mm"))
        }
        if (r15 >= UG_CTRL_15MIN && input.sustained1h) {
            t.add(Trigger(HazardType.UNDERGROUND, ActionLevel.CONTROL, "통제①",
                "15분 20mm 이상 & 1시간 이상 지속 예상",
                "15분 ${fmt(r15)}mm$suffix · 지속 전망"))
        }
        input.undergroundDepthCm?.let {
            if (it >= UG_CTRL_DEPTH_CM) {
                t.add(Trigger(HazardType.UNDERGROUND, ActionLevel.CONTROL, "통제②",
                    "지하공간 침수심 5cm 도달 예상", "예상 침수심 ${fmt(it)}cm"))
            }
        }

        val byHazard = HazardType.values().associateWith { h ->
            t.filter { it.hazard == h }.maxByOrNull { it.level.rank }?.level ?: ActionLevel.NONE
        }
        val level = t.maxByOrNull { it.level.rank }?.level ?: ActionLevel.NONE

        return Judgement(
            level = level,
            triggers = t.sortedWith(
                compareByDescending<Trigger> { it.level.rank }.thenBy { it.hazard.ordinal }
            ),
            byHazard = byHazard,
            summary = buildSummary(level, byHazard),
            actions = buildActions(byHazard)
        )
    }

    private fun fmt(v: Double) = "%.0f".format(v)

    private fun buildSummary(level: ActionLevel, byHazard: Map<HazardType, ActionLevel>): String {
        if (level == ActionLevel.NONE) return "공식 통제·대피 기준에 도달하지 않았습니다."
        val parts = byHazard.filter { it.value != ActionLevel.NONE }
            .map { "${it.key.displayName} ${it.value.displayName}" }
        return "대응계획서 기준 도달 — ${parts.joinToString(" · ")}"
    }

    /**
     * 유형별 조치. 대응계획서에 명시된 내용 그대로이며 앱이 지어낸 지침이 아니다.
     */
    private fun buildActions(byHazard: Map<HazardType, ActionLevel>): List<String> {
        val a = mutableListOf<String>()

        byHazard[HazardType.UNDERGROUND]?.let { lv ->
            when (lv) {
                ActionLevel.EVACUATE -> {
                    a.add("[지하공간] 일시에 물이 차오르는 특성을 감안, 즉시대피 원칙")
                    a.add("[지하공간] 긴급재난문자 발송 및 주민대피 명령 발령 (민방위 사이렌 활용)")
                }
                ActionLevel.CONTROL -> {
                    a.add("[지하공간] 지하로 연결되는 도로·계단·엘리베이터 등 진입구간 입구 통제")
                    a.add("[지하공간] 선제적 통제와 차단 중심, 반지하주택 등 개인시설은 '대피'에 초점")
                    a.add("[지하공간] 물막이판 등 예방시설이 설치된 경우도 위험성 고려해 적극 통제")
                }
                else -> {}
            }
        }

        byHazard[HazardType.LANDSLIDE]?.let { lv ->
            when (lv) {
                ActionLevel.EVACUATE -> {
                    a.add("[산사태] 주로 거주지와 취약시간(새벽~오전)에 발생 — 사전대피 원칙 (곤란 시 즉시대피)")
                    a.add("[산사태] 산림청 산사태정보시스템 토석류 피해 예측 범위 내 전 지역·시설 대상")
                }
                ActionLevel.CONTROL -> {
                    a.add("[산사태] 산사태취약지역·우려지역, 붕괴위험사면 등 급경사지 사전통제")
                    a.add("[산사태] 차단기·접근금지 안내판 설치, 안전안내문자 발송")
                }
                else -> {}
            }
        }

        byHazard[HazardType.RIVER]?.let { lv ->
            when (lv) {
                ActionLevel.EVACUATE -> {
                    a.add("[하천] 둔치 주차장·하천변 산책로 등 대상 대피 조치 시행")
                    a.add("[하천] 안전안내문자 발송 및 차단기 설치로 재진입 차단")
                }
                ActionLevel.CONTROL -> {
                    a.add("[하천] 침수심 ${RV_AFFECTED_DEPTH_CM}cm 이상 영향 범위 사전통제")
                    a.add("[하천] 동일 하천 내 저수호안 시설(산책로·주차장)은 일괄 통제")
                }
                else -> {}
            }
        }

        return a
    }
}
