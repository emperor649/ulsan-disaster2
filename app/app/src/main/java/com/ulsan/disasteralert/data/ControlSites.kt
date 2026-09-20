package com.ulsan.disasteralert.data

import com.ulsan.disasteralert.data.OfficialCriteria.HazardType.*

/**
 * 울산시 여름철 자연재난 대비 재난상황 대응계획서 — 인명피해 우려지역 사전통제 지점.
 *
 * 출처: 2026년 여름철 자연재난 대비 재난상황 대응계획서 (울산시)
 *
 * ── 왜 이 데이터가 결정적인가 ──
 * 이전까지는 언론 보도에서 역산한 추정 지점 18곳을 썼습니다.
 * 이제는 시가 공식 지정한 지점과 그 지점별 통제 발동 기준을 그대로 씁니다.
 * 앱이 자체 판단을 내리는 것이 아니라, 정해진 기준 도달을 먼저 알리는 도구가 됩니다.
 *
 * ── 구·군마다 기준 체계가 다릅니다 ──
 * 북구는 **시우량**(시간당), 울주군은 **누적강우**를 주로 씁니다.
 * 하나로 통일하면 한쪽 지점들이 통째로 누락되므로, 지점별 기준을 그대로 보존합니다.
 */
object ControlSites {

    /**
     * 지점별 통제 발동 기준.
     * 대응계획서 표의 '통제 기준' 열을 그대로 코드로 옮긴 것입니다.
     */
    enum class Criterion(val label: String) {
        /** 시우량 20mm 이상 (북구 산사태) */
        HOURLY20("시우량 20mm 이상"),
        /** 시우량 30mm 이상 (북구 하천재해) */
        HOURLY30("시우량 30mm 이상"),
        /** 시우량 30mm 이상 + 하천수위 교량상부 기준 30cm 도달 */
        HOURLY30_BRIDGE30("시우량 30mm 이상 또는 하천수위 교량상부 기준 30cm 도달"),
        /** 누적강우 200mm 이상 관측 (울주군 다수) */
        CUM200("누적강우 200mm 이상 관측"),
        /** 도로침수 및 50mm/h 이상 지속 */
        RATE50("도로침수 및 50mm/h 이상 지속"),
        /** 도로중심 노면으로부터 5cm 침수 (지하차도) */
        ROAD5CM("도로중심 노면으로부터 5cm 침수"),
        /** 산사태주의보 발령 또는 토양함수지수 80% 도달 */
        SOIL80("산사태주의보 발령 또는 토양함수지수 80% 도달"),
        /** 태풍·호우경보 발령 시 (야영장) */
        TYPHOON_ALERT("태풍·호우경보 발령 시"),

        // ── 중구 ──
        /** 호우경보 및 침수심 15cm 이상 (기상상황·현장여건에 따라 조정) */
        WARN_DEPTH15("호우경보 및 침수심 15cm 이상"),
        /** 시간당 30mm 이상 또는 일강우량 100mm 이상 */
        HOURLY30_DAILY100("시간당 30mm 이상 · 일강우량 100mm 이상"),
        /** 기상특보 시 상황판단회의 → 돌발성 집중호우로 인한 내수배제 불량 시 */
        SITUATION_MEETING("기상특보 시 상황판단회의 (돌발성 집중호우 내수배제 불량)"),
        /** 산사태주의보(토양함수 80%) / 산사태경보(토양함수 100%) */
        SOIL80_100("산사태주의보 80% / 경보 100% 도달"),
        /** 호우주의보 및 3시간 강우량 60mm 이상 */
        ADVISORY_3H60("호우주의보 및 3시간 강우량 60mm 이상"),

        // ── 남구 ──
        /** 침수심 15cm 이상 */
        DEPTH15("침수심 15cm 이상"),
        /** 침수심 15cm 미만 비상대기, 15cm 이상 통제 */
        DEPTH15_STAGED("침수심 15cm 미만 비상대기 · 15cm 이상 통제"),
        /** 누적강수 60mm/3시간 또는 110mm/12시간 */
        CUM60_3H("누적강수 60mm/3시간 · 110mm/12시간"),
        /** 누적강수 90mm/3시간 또는 180mm/12시간 */
        CUM90_3H("누적강수 90mm/3시간 · 180mm/12시간"),
        /** 1시간 강우량 25mm 이상 */
        HOURLY25("1시간 강우량 25mm 이상"),
        /** 해일높이 0.5m 이상 */
        SURGE_05M("해일높이 0.5m 이상"),
        /** 연속 100~200 미만 & 시강우량 20~30 미만 & 일강우량 80~150 미만 */
        LANDSLIDE_BAND("연속100~200 · 시강우20~30 · 일강우80~150 구간"),

        // ── 동구 ──
        /** 호우경보 상황 지속되고 시우량 40mm 이상 */
        WARN_HOURLY40("호우경보 지속 & 시우량 40mm 이상")
    }

    data class Site(
        val no: Int,
        val name: String,
        val address: String,
        val district: String,
        val hazard: OfficialCriteria.HazardType,
        val criterion: Criterion,
        /** 통제 범위 */
        val scope: String
    ) {
        /** 인명피해 직결 지점인지 — 지하차도는 최우선 */
        val isCritical: Boolean
            get() = hazard == UNDERGROUND ||
                    criterion == Criterion.ROAD5CM ||
                    criterion == Criterion.WARN_DEPTH15 ||
                    criterion == Criterion.DEPTH15 ||
                    criterion == Criterion.DEPTH15_STAGED
    }

    private fun S(
        no: Int, name: String, address: String, district: String,
        hazard: OfficialCriteria.HazardType, criterion: Criterion, scope: String
    ) = Site(no, name, address, district, hazard, criterion, scope)

    /** 북구 — 시우량 기준 체계 */
    val bukgu: List<Site> = listOf(
        S(1, "평해사 일원", "매곡동 산120-1", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(2, "청룡암 일원", "매곡동 산68", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(3, "평해사 인근 산단", "매곡동 산65", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(4, "호봉사 인근", "호계동 산38", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(5, "달천동 산132", "달천동 산132", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(6, "천곡동 산99-3", "천곡동 산99-3", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(7, "천곡동 170", "천곡동 170", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(8, "대안동 산359", "대안동 산359", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(9, "옥천암 일원", "연암동 산78-1", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(10, "연암동 산92", "연암동 산92", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(11, "현대자동차 사택 일원", "양정동 556-31", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(12, "양정동 786-12", "양정동 786-12", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(13, "현대아파트 3차 절개지", "염포동 산75", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(14, "염포예술창작소", "염포동 508-23", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(15, "성내마을", "염포동 산179-21", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(16, "숲속의 더유엘", "염포동 산63", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(17, "송정박상진호수공원", "송정동 산14-1", "북구", LANDSLIDE, Criterion.HOURLY20, "공원 전체"),
        S(18, "양정동 산100-5", "양정동 산100-5", "북구", LANDSLIDE, Criterion.HOURLY20, "등산로 및 계류구역"),
        S(19, "구유동 35", "구유동 35", "북구", LANDSLIDE, Criterion.HOURLY20, "위험사면 인접 통행구간"),
        S(20, "상방지하차도", "연암동 989-2", "북구", UNDERGROUND, Criterion.ROAD5CM, "지하차도 진입로"),
        S(21, "명촌교 북단 지하차도", "명촌동 930-1", "북구", UNDERGROUND, Criterion.ROAD5CM, "지하차도 진입로"),
        S(22, "화봉지하차도", "화봉동 957-1", "북구", UNDERGROUND, Criterion.ROAD5CM, "지하차도 진입로"),
        S(23, "대성레미콘 앞", "신천동 745-118", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(24, "이화천 하부 진입로", "중산동 1166-43", "북구", RIVER, Criterion.HOURLY30, "하천변 산책로 진입구"),
        S(25, "속심이보", "중산동 1166-29", "북구", RIVER, Criterion.HOURLY30_BRIDGE30, "하천 횡단시설"),
        S(26, "재전보", "중산동 1166-29", "북구", RIVER, Criterion.HOURLY30_BRIDGE30, "하천 횡단시설"),
        S(27, "약수교 하부 하상도로", "중산동 1166-29", "북구", RIVER, Criterion.HOURLY30, "교량하부 저지대"),
        S(28, "시례잠수교", "시례동 7-54", "북구", RIVER, Criterion.HOURLY30_BRIDGE30, "하천 횡단시설"),
        S(29, "상안잠수교", "호계동 94.3-1", "북구", RIVER, Criterion.HOURLY30_BRIDGE30, "하천 횡단시설"),
        S(30, "천곡문화센터 일원", "천곡동 579-2", "북구", RIVER, Criterion.HOURLY30, "상습침수구역 전체"),
        S(31, "신명해변", "신명동 286", "북구", RIVER, Criterion.HOURLY30, "해안도로 전체"),
        S(32, "신명천 교각", "신명동 282-1", "북구", RIVER, Criterion.HOURLY30, "하천변도로 전체"),
        S(33, "산하해변", "산하동 1", "북구", RIVER, Criterion.HOURLY30, "해안도로 전체"),
        S(34, "정자해변", "정자동 637-27", "북구", RIVER, Criterion.HOURLY30, "해안도로 전체"),
        S(35, "판지해변", "구유동 324-1", "북구", RIVER, Criterion.HOURLY30, "해안도로 전체"),
        S(36, "복성 제전해변", "구유동 40", "북구", RIVER, Criterion.HOURLY30, "해안도로 전체"),
        S(37, "무룡천 달문사지 밑", "무룡동 935-203", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(38, "정자방파제", "정자동 638", "북구", RIVER, Criterion.HOURLY30, "해안가 저지대"),
        S(39, "당사항", "당사동 378-5", "북구", RIVER, Criterion.HOURLY30, "해안가 저지대"),
        S(40, "우가해상관람데크", "당사동 378-5", "북구", RIVER, Criterion.HOURLY30, "해안가 산책로"),
        S(41, "당사항 낚시공원", "당사동 508", "북구", RIVER, Criterion.HOURLY30, "낚시터 전체"),
        S(42, "우가어촌체험마을", "당사동 143-5", "북구", RIVER, Criterion.HOURLY30, "어촌체험마을 전체"),
        S(43, "신명천 세월교", "신명동 284", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(44, "무룡천 세월교", "신현동 934-1", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(45, "산맥천", "어물동 330", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(46, "어물천", "어물동 1240-54", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(47, "정자천", "신명동 284", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(48, "무룡동 264-2", "무룡동 264-2", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(49, "대안천", "대안동 산368-1", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(50, "신명천", "대안동 1059-1", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(51, "구암공영주차장", "어물동 272-1", "북구", RIVER, Criterion.HOURLY30, "하천 횡단시설"),
        S(52, "명촌천 부근 제방도로", "효문동 905-5", "북구", RIVER, Criterion.HOURLY30, "하천 제방도로"),
        S(53, "명촌둔치주차장", "명촌동 794-13", "북구", RIVER, Criterion.HOURLY30, "둔치주차장"),
        S(54, "심청골 등산로 일대", "양정동 산18-1", "북구", RIVER, Criterion.HOURLY30, "하천변 산책로"),
        S(55, "오치골 등산로 일대", "양정동 산31-1", "북구", RIVER, Criterion.HOURLY30, "하천변 산책로"),
        S(56, "양정경로당 인근", "양정동 산109-3", "북구", RIVER, Criterion.HOURLY30, "침수우려도로 전체"),
        S(57, "염포운동장 옆 산책로", "염포동 382-2", "북구", RIVER, Criterion.HOURLY30, "산책로 전체"),
    )

    /** 울주군 — 누적강우 기준 체계 */
    val ulju: List<Site> = listOf(
        S(1, "반용지구", "범서읍 척과리 134-1", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(2, "사일지구", "범서읍 사연리 120-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(3, "구영지구", "범서읍 구영리 684", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(4, "천상지구1", "범서읍 천상리 412-10", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(5, "망성지구1", "범서읍 망성리 454-3", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(6, "진목지구", "범서읍 입암리 1141-78", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(7, "천상지구2", "범서읍 천상리 1041-82", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(8, "굴화지구", "범서읍 굴화리 678-23", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(9, "천상지구3", "범서읍 천상리 786", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(10, "망성지구2", "범서읍 망성리 454-65", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(11, "입암지구", "범서읍 입암리 443-16", "울주군", RIVER, Criterion.RATE50, "우려지역 내 하천"),
        S(12, "덕신지구1", "온산읍 덕신리 405-13", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(13, "덕신지구2", "온산읍 덕신리 872-5", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(14, "강양지구", "온산읍 강양리 85", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(15, "학남지구", "온산읍 학남리 790-1", "울주군", LANDSLIDE, Criterion.CUM200, "급경사지"),
        S(16, "원산사거리", "온산읍 화산리 861", "울주군", RIVER, Criterion.RATE50, "우려지역 내 도로"),
        S(17, "당월로", "온산읍 원산리 250", "울주군", RIVER, Criterion.RATE50, "우려지역 내 도로"),
        S(18, "반천지구", "언양읍 반천강변길 51", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천, 주차장"),
        S(19, "무동지구", "언양읍 무동길 20-6", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천 전체"),
        S(20, "대암지구", "언양읍 대암둔기로 105", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천 전체"),
        S(21, "미연1리지구", "언양읍 곰재길 16~72", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천 전체"),
        S(22, "옹태지구", "언양읍 태기리 265-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천 전체"),
        S(23, "대곡지구", "언양읍 반구대안길 307", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천 전체"),
        S(24, "남부1리지구", "언양읍 남부리 336-1", "울주군", RIVER, Criterion.CUM200, "둔치주차장"),
        S(25, "방천5리지구", "언양읍 서부리 395-143", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(26, "남천둑길(하상도로)", "언양읍 남부리 336-93", "울주군", UNDERGROUND, Criterion.RATE50, "해당 하상도로"),
        S(27, "반천지하차도", "언양읍 반송리 640-2", "울주군", UNDERGROUND, Criterion.RATE50, "해당 지하차도"),
        S(28, "능골마을", "언양읍 송대리 23-2", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(29, "내곡마을", "언양읍 송대리 207", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(30, "신화마을", "언양읍 직동리 산37-4", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(31, "대운지구", "온양읍 운화리 1405", "울주군", RIVER, Criterion.CUM200, "물놀이 하천·계곡"),
        S(32, "남창지구1", "온양읍 남창리 298-2", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(33, "삼광지구", "온양읍 삼광리 624", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(34, "내광지구", "온양읍 내광리 36-4", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(35, "운화지구1", "온양읍 운화리 1155", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(36, "남창지구2", "온양읍 남창리 336", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(37, "운화지구2", "온양읍 운화리 849", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(38, "동상지구", "온양읍 동상리 322", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(39, "남창지구3", "온양읍 남창리 94-2", "울주군", RIVER, Criterion.CUM200, "둔치주차장"),
        S(40, "대안지구", "온양읍 대안리 101", "울주군", RIVER, Criterion.RATE50, "우려지역 내 도로 전체"),
        S(41, "대안지하차도", "온양읍 대안리 190-1", "울주군", UNDERGROUND, Criterion.RATE50, "해당 지하차도"),
        S(42, "귀지마을", "온양읍 외광리 산6", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(43, "외광마을", "온양읍 외광리 산37-5", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(44, "양동지구", "청량읍 동천리 472-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(45, "상남지구1", "청량읍 상남리 29", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(46, "삼정지구", "청량읍 삼정리 755", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(47, "안산지구", "청량읍 용암리 588-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(48, "상남지구2", "청량읍 상남리 969-2", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(49, "죽전지구", "청량읍 문죽리 1255-49", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(50, "덕정지구1", "청량읍 덕하리 415-11", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(51, "신덕하지구", "청량읍 상남리 591-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(52, "덕정지구2", "청량읍 덕하리 472-1", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(53, "작천지구", "삼남읍 교동리 산111-3", "울주군", RIVER, Criterion.CUM200, "하천변 산책로"),
        S(54, "교동지구", "삼남읍 교동리 1522-45", "울주군", RIVER, Criterion.CUM200, "물놀이 하천·계곡"),
        S(55, "신화지구1", "삼남읍 신화리 322", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(56, "신화지구2", "삼남읍 신화리 377", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(57, "작천정달빛야영장", "삼남읍 교동리 1522-1", "울주군", RIVER, Criterion.TYPHOON_ALERT, "야영장"),
        S(58, "울산역지하차도", "삼남읍 교동리 278-3", "울주군", UNDERGROUND, Criterion.RATE50, "해당 지하차도"),
        S(59, "신암지구1", "서생면 신암리 291-52", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(60, "신리지구", "서생면 신암리 315-13", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(61, "나사지구", "서생면 나사리 434-5", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(62, "대송지구", "서생면 대송리 1-4", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(63, "송정지구", "서생면 대송리 339-19", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(64, "평동지구", "서생면 대송리 51-16", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(65, "진하지구", "서생면 진하리 76-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(66, "연산교", "서생면 신암리 1607-26", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(67, "신암지구2", "서생면 신암리 332-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(68, "브니엘교회 앞 삼거리", "서생면 신암리 174-6", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(69, "깨목천 옆 도로", "서생면 진하리 315-2", "울주군", RIVER, Criterion.RATE50, "우려지역 내 도로 전체"),
        S(70, "서생지구", "서생면 화산리 1027-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(71, "초천지구", "웅촌면 초천리 74", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(72, "곡천지구1", "웅촌면 곡천리 136-7", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(73, "고연지구", "웅촌면 고연리 1728-39", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(74, "오복지구", "웅촌면 대복리 668-6", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(75, "곡천지구2", "웅촌면 곡천리 846-100", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(76, "대대지구", "웅촌면 대대리 966-6", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
        S(77, "대현마을", "두동면 천전리 596", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(78, "내와지구1", "두서면 내와리 762-1", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(79, "외와마을", "두서면 내와리 1002", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(80, "선필마을1", "두서면 인보리 산120-1", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(81, "전읍마을", "두서면 전읍리 44-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(82, "노서마을", "두서면 인보리 211-2", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(83, "정토지구", "두서면 활천리 257-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(84, "선필마을2", "두서면 인보리 산84-3", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(85, "내와지구2", "두서면 내와리 618-5", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
        S(86, "덕현지구3", "상북면 덕현리 1225-18", "울주군", RIVER, Criterion.CUM200, "소교량 전체"),
        S(87, "덕현지구1", "상북면 덕현리 332", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(88, "등억지구2", "상북면 등억알프스리 701", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(89, "이천지구1", "상북면 이천리 737", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(90, "철구소지구", "상북면 이천리 산86-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(91, "이천지구2", "상북면 이천리 617", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(92, "등억지구1", "상북면 등억알프스리 743", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(93, "천전1교", "상북면 천전리 314-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(95, "이천지구3", "상북면 이천리 산59", "울주군", LANDSLIDE, Criterion.CUM200, "우려지역 내 산사태취약지"),
        S(96, "덕현지구2", "상북면 덕현리 산21-6", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
        S(97, "거리지구", "상북면 거리 산89", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
        S(98, "소호지구", "상북면 소호리 산45", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
        S(99, "등억알프스야영장", "상북면 등억알프스리 309-1", "울주군", RIVER, Criterion.TYPHOON_ALERT, "야영장"),
        S(100, "작천정별빛야영장", "상북면 등억알프스리 17", "울주군", RIVER, Criterion.TYPHOON_ALERT, "야영장"),
        S(101, "금곡지구", "삼동면 금곡리 311-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(102, "출강지구", "삼동면 출강리 711-15", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(103, "금곡교", "삼동면 하잠리 582-7", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(104, "삼동교", "삼동면 하잠리 953-3", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(105, "하잠지구1", "삼동면 하잠리 396-2", "울주군", RIVER, Criterion.CUM200, "하천변산책로"),
        S(106, "하잠지구2", "삼동면 하잠리 893-2", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(107, "보은지구", "삼동면 보은리 958-1", "울주군", RIVER, Criterion.CUM200, "우려지역 내 하천"),
        S(108, "조일지구", "삼동면 조일리 산274", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
        S(109, "금곡마을", "삼동면 금곡리 439-1", "울주군", LANDSLIDE, Criterion.SOIL80, "우려지역 내 산사태취약지"),
    )


    /** 중구 — 호우경보+침수심 15cm 체계 (지하차도 다수) */
    val junggu: List<Site> = listOf(
        S(1, "동천지하차도", "남외동 690-4", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하차도 전체"),
        S(2, "우정지하차도", "성남동 316-3", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하차도 전체"),
        S(3, "번영교 하부도로", "옥교동 72-3", "중구", RIVER, Criterion.WARN_DEPTH15, "하부도로 출입방면"),
        S(4, "삼호교 하부도로", "다운동 467", "중구", RIVER, Criterion.WARN_DEPTH15, "하부도로 출입방면"),
        S(5, "성남지하보도", "성남동 220-2", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하보도 출입방면"),
        S(6, "우정지하보도", "성남동 321-3", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하보도 출입방면"),
        S(7, "다운지구 (태화강·척과천 징검다리)", "태화강방면-다운12길 24 인근", "중구", RIVER, Criterion.HOURLY30_DAILY100, "징검다리·산책로 진입로"),
        S(8, "다운(척과천)지구", "다운동 794-4 인근", "중구", RIVER, Criterion.HOURLY30_DAILY100, "징검다리·산책로 진입로"),
        S(9, "남외(동천)지구", "반구동 929 (약사천-동천 합류지점)", "중구", RIVER, Criterion.HOURLY30_DAILY100, "징검다리 진입로"),
        S(10, "태화지구", "태화동 20-3, 42", "중구", RIVER, Criterion.SITUATION_MEETING, "태화지구 일원"),
        S(11, "우정지구", "우정동 285-1", "중구", RIVER, Criterion.SITUATION_MEETING, "우정지구 일원"),
        S(12, "성안지구1", "성안동 산60", "중구", LANDSLIDE, Criterion.SOIL80_100, "위험지역 사전대피"),
        S(13, "성안지구2", "성안동 산160", "중구", LANDSLIDE, Criterion.SOIL80_100, "위험지역 사전대피"),
        S(14, "성안지구3", "성안동 산264-2", "중구", LANDSLIDE, Criterion.SOIL80_100, "위험지역 사전대피"),
        S(15, "학성강북지하차도", "반구동 425-2", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하차도 출입방면"),
        S(16, "번영교강북지하차도", "옥교동 74-1", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하차도 출입방면"),
        S(17, "명정지하차도", "태화동 681-1", "중구", UNDERGROUND, Criterion.WARN_DEPTH15, "지하차도 출입방면"),
        S(18, "강북공영주차장", "옥교동 72-3", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(19, "동천공영주차장", "남외동 964", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(20, "성남둔치공영주차장", "성남동 216", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(21, "신삼호교공영주차장", "다운동 486", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(22, "태화강국가정원1공영주차장", "다운동 467", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(23, "태화강국가정원2공영주차장", "태화동 800", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(24, "태화강국가정원3공영주차장", "태화동 800", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(25, "태화강국가정원4공영주차장", "태화동 800", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
        S(26, "태화강국가정원5공영주차장", "태화동 800", "중구", RIVER, Criterion.ADVISORY_3H60, "둔치주차장 출입통제 및 주차차량 대피"),
    )

    /** 남구 — 누적강수 3시간/12시간 체계 */
    val namgu: List<Site> = listOf(
        S(1, "매암사거리", "매암동 392-12", "남구", RIVER, Criterion.CUM60_3H, "매암사거리 전체"),
        S(2, "학성교강남지하차도", "삼산동 1043-2", "남구", UNDERGROUND, Criterion.DEPTH15, "지하차도 전체"),
        S(3, "삼호지하차도", "무거동 212", "남구", UNDERGROUND, Criterion.DEPTH15, "지하차도 전체"),
        S(4, "삼산차하차도", "삼산동 19-2", "남구", UNDERGROUND, Criterion.DEPTH15, "지하차도 전체"),
        S(5, "번영교강남지하차도", "삼산동 1390-2", "남구", UNDERGROUND, Criterion.DEPTH15, "지하차도 전체"),
        S(6, "상개동 산사태취약지구", "상개동 산53", "남구", LANDSLIDE, Criterion.LANDSLIDE_BAND, "산사태취약지구 전체"),
        S(7, "신정동 산사태취약지구", "신정동 산109-17", "남구", LANDSLIDE, Criterion.LANDSLIDE_BAND, "산사태취약지구 전체"),
        S(8, "두왕동 산사태취약지구", "두왕동 산15-3", "남구", LANDSLIDE, Criterion.LANDSLIDE_BAND, "산사태취약지구 전체"),
        S(9, "선암동 산사태취약지구", "선암동 산125", "남구", LANDSLIDE, Criterion.LANDSLIDE_BAND, "산사태취약지구 전체"),
        S(10, "명촌강남지하차도", "삼산동 108-5", "남구", UNDERGROUND, Criterion.DEPTH15_STAGED, "지하차도 전체"),
        S(11, "태화교 하부도로", "신정동 1406-4", "남구", RIVER, Criterion.CUM60_3H, "하부도로 전체"),
        S(12, "와와교 자로 하상도로", "무거동 1312-1", "남구", RIVER, Criterion.CUM60_3H, "하부도로 전체"),
        S(13, "신삼호교 하상도로", "무거동 122-9", "남구", RIVER, Criterion.CUM60_3H, "하부도로 전체"),
        S(14, "신정3지구", "신정동 517", "남구", RIVER, Criterion.HOURLY25, "신정동 517 일원 전체"),
        S(15, "태화강둔치공영주차장", "신정동 1513", "남구", RIVER, Criterion.CUM60_3H, "둔치공영주차장 전체"),
        S(16, "여천천지구", "여천천 산책로 (달동 일원)", "남구", RIVER, Criterion.CUM60_3H, "산책로 전체"),
        S(17, "장생포 해안지구", "장생포동 41-4", "남구", RIVER, Criterion.SURGE_05M, "장생포해안지구 전체"),
        S(18, "두왕지구", "두왕동 460-16", "남구", RIVER, Criterion.HOURLY25, "두왕동 460-16 일원 전체"),
        S(19, "삼호연안 다목적광장 공영주차장", "무거동 105-1", "남구", RIVER, Criterion.CUM60_3H, "둔치공영주차장 전체"),
        S(20, "태화강지구", "태화강 산책로 (삼호동·신정동 일원)", "남구", RIVER, Criterion.CUM60_3H, "산책로 전체"),
        S(21, "무거천지구", "무거천 산책로 (삼호동·무거동 일원)", "남구", RIVER, Criterion.CUM90_3H, "산책로 전체"),
    )

    /** 동구 — 호우경보 지속 + 시우량 40mm, 즉시통제 원칙 */
    val donggu: List<Site> = listOf(
        S(1, "새납마을", "새납길 49", "동구", LANDSLIDE, Criterion.WARN_HOURLY40, "새납마을 전체"),
        S(2, "일산진마을 고늘지구", "일산진11길 221", "동구", UNDERGROUND, Criterion.WARN_HOURLY40, "일산진마을 해안가 일원"),
        S(3, "성끝마을", "성끝4길 61", "동구", UNDERGROUND, Criterion.WARN_HOURLY40, "성끝마을 전체"),
        S(4, "주전지구", "주전해안길 264", "동구", UNDERGROUND, Criterion.WARN_HOURLY40, "주전해안길 264 해안가 일원"),
    )

    /**
     * 전체 지점.
     *
     * 울산 5개 구·군 전체가 반영되었습니다.
     */
    val all: List<Site> get() = junggu + namgu + donggu + bukgu + ulju

    fun inDistrict(district: String) = all.filter { it.district == district }

    fun byHazard(hazard: OfficialCriteria.HazardType) = all.filter { it.hazard == hazard }

    /** 지하차도 등 인명피해 직결 지점 */
    val criticalSites: List<Site> get() = all.filter { it.isCritical }

    fun hasData(district: String) = all.any { it.district == district }
}
