package com.ulsan.disasteralert.data

/**
 * 특보 + 실측 강수량 + 재난 위기경보를 조합해 지역별 위험도 스코어를 산출한다.
 *
 * 점수 구성 (조정 가능한 가중치):
 *  - 기상특보: 주의보 2점, 경보 4점 (복수 특보 시 합산)
 *  - 강수량: 시간당 15mm↑ 2점, 30mm↑ 4점, 50mm↑ 6점 가산
 *  - 재난 위기경보: 관심1 / 주의3 / 경계6 / 심각10 (최고 단계 기준)
 *
 * 최종 스코어 구간으로 5단계 RiskLevel 매핑
 */
object RiskCalculator {

    fun calculate(
        regionName: String,
        warnings: List<WeatherWarning>,
        precipitation: PrecipitationObservation?,
        disasterAlerts: List<DisasterAlert>,
        updatedAt: String
    ): RegionRiskStatus {

        var score = 0

        // 1) 기상특보 합산
        score += warnings.sumOf { it.warningLevel.score }

        // 2) 실측 강수량 가산
        precipitation?.let {
            score += when {
                it.hourlyRainMm >= 50 -> 6
                it.hourlyRainMm >= 30 -> 4
                it.hourlyRainMm >= 15 -> 2
                else -> 0
            }
        }

        // 3) 재난 위기경보 (최고 단계만 반영, 중복 가산 방지)
        val maxCrisis = disasterAlerts.maxByOrNull { it.crisisLevel.score }
        score += maxCrisis?.crisisLevel?.score ?: 0

        val level = when {
            score >= 14 -> RiskLevel.SEVERE
            score >= 9  -> RiskLevel.DANGER
            score >= 5  -> RiskLevel.WARNING
            score >= 2  -> RiskLevel.CAUTION
            else        -> RiskLevel.SAFE
        }

        return RegionRiskStatus(
            regionName = regionName,
            riskScore = score,
            riskLevel = level,
            activeWarnings = warnings,
            latestPrecipitation = precipitation,
            activeDisasterAlerts = disasterAlerts,
            updatedAt = updatedAt
        )
    }
}
