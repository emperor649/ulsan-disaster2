package com.ulsan.disasteralert.data

/**
 * 울산 지하차도 전수 목록 (30개소).
 *
 * 출처: 2026년 여름철 자연재난 대비 재난상황 대응계획서 별첨 — 지하차도 관리현황
 *
 * ── 왜 별도 목록이 필요한가 ──
 * 인명피해 우려지역 통제지점 목록(ControlSites)에는 지하차도 일부만 실려 있습니다.
 * 하지만 침수 위험은 통제지점 지정 여부와 무관하게 **모든 지하차도**에 있습니다.
 *
 * 침수심 센서가 현장 차단기 전용이라 외부 연계가 불가능한 상황에서,
 * 앱이 할 수 있는 최선은 **강수 시 확인해야 할 지하차도 전체를 빠짐없이 제시**하는 것입니다.
 * 통제지점에 실린 것만 보여주면 나머지가 사각지대로 남습니다.
 *
 * ── 위험등급 ──
 * 대응계획서가 고/저 2단계로 분류해 두었습니다.
 * 고위험 17개소, 저위험 13개소.
 * 인력이 제한된 상황에서 순찰 우선순위를 정하는 근거가 됩니다.
 */
object Underpasses {

    enum class RiskGrade(val label: String, val colorHex: String) {
        HIGH("고위험", "#C62828"),
        LOW("저위험", "#F57C00")
    }

    data class Underpass(
        val no: Int,
        val name: String,
        /** 관리기관 — 침수센서 연계 문의처이기도 합니다 */
        val organization: String,
        val district: String,
        val grade: RiskGrade
    )

    private fun U(no: Int, name: String, org: String, district: String, grade: RiskGrade) =
        Underpass(no, name, org, district, grade)

    val all: List<Underpass> = listOf(
        U(1, "우정지하차도", "종합건설본부", "중구", RiskGrade.HIGH),
        U(2, "번영교강남지하차도", "종합건설본부", "남구", RiskGrade.HIGH),
        U(3, "번영교강북지하차도", "종합건설본부", "중구", RiskGrade.HIGH),
        U(4, "삼호지하차도", "종합건설본부", "남구", RiskGrade.HIGH),
        U(5, "학성교강남지하차도", "종합건설본부", "남구", RiskGrade.HIGH),
        U(6, "학성교강북지하차도", "종합건설본부", "중구", RiskGrade.HIGH),
        U(7, "대안지하차도", "울주군 도로과", "울주군", RiskGrade.HIGH),
        U(8, "명촌강북지하차도", "종합건설본부", "북구", RiskGrade.LOW),
        U(9, "명촌산업지하차도", "종합건설본부", "북구", RiskGrade.LOW),
        U(10, "명촌본선지하차도", "종합건설본부", "북구", RiskGrade.HIGH),
        U(11, "옥현지하차도", "종합건설본부", "남구", RiskGrade.LOW),
        U(12, "삼산지하차도", "종합건설본부", "남구", RiskGrade.HIGH),
        U(13, "명촌교강남지하차도", "종합건설본부", "남구", RiskGrade.HIGH),
        U(14, "상방지하차도", "종합건설본부", "북구", RiskGrade.HIGH),
        U(15, "동천지하차도", "종합건설본부", "중구", RiskGrade.HIGH),
        U(16, "병영성지하차도", "중구 건설과", "중구", RiskGrade.LOW),
        U(17, "여천천지하차도", "남구 건설과", "남구", RiskGrade.LOW),
        U(18, "화봉지하차도", "종합건설본부", "북구", RiskGrade.HIGH),
        U(19, "중구청지하차도", "종합건설본부", "중구", RiskGrade.LOW),
        U(20, "반천산단진입도로지하차도", "종합건설본부", "울주군", RiskGrade.HIGH),
        U(21, "원유곡지하차도", "종합건설본부", "중구", RiskGrade.LOW),
        U(22, "중산지구지하차도", "종합건설본부", "북구", RiskGrade.LOW),
        U(23, "구남지하차도", "종합건설본부", "북구", RiskGrade.LOW),
        U(24, "대송지하차도", "시 건설도로과 (울산하버브릿지)", "동구", RiskGrade.LOW),
        U(25, "애전 개방형 BOX", "시 건설도로과 (울산하버브릿지)", "동구", RiskGrade.LOW),
        U(26, "중산지하차도", "종합건설본부", "북구", RiskGrade.LOW),
        U(27, "명정지하차도", "종합건설본부", "중구", RiskGrade.HIGH),
        U(28, "신화지하차도 (교동지하차도)", "울주군 도로과", "울주군", RiskGrade.HIGH),
        U(29, "산성지하차도", "울주군 도로과", "울주군", RiskGrade.LOW),
        U(30, "울산역지하차도", "종합건설본부", "울주군", RiskGrade.HIGH),
    )

    fun inDistrict(district: String) = all.filter { it.district == district }

    /** 고위험 지하차도 — 순찰 우선순위 */
    val highRisk: List<Underpass> get() = all.filter { it.grade == RiskGrade.HIGH }

    /**
     * 관리기관별 집계.
     * 대부분 종합건설본부 소관이라, 센서 연계를 타진한다면 이곳이 1순위 창구입니다.
     */
    fun byOrganization(): Map<String, List<Underpass>> = all.groupBy { it.organization }

    /**
     * 통제지점 목록(ControlSites)에 이름이 실려 있는지.
     * 실리지 않은 지하차도는 공식 통제 기준이 따로 없으므로,
     * 강수 시 판단 근거가 더 부족합니다 — 그만큼 현장 확인이 중요합니다.
     */
    fun hasControlCriterion(u: Underpass): Boolean =
        ControlSites.all.any { site ->
            val a = site.name.replace(" ", "")
            val b = u.name.replace(" ", "").substringBefore("(")
            a.contains(b) || b.contains(a)
        }

    val totalCount get() = all.size
    val highRiskCount get() = highRisk.size
}
