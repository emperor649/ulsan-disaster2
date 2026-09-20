package com.ulsan.disasteralert.data

/**
 * 실시간 위험도(RiskCalculator) + 과거 이력 기반 취약도를 결합한 종합 판단.
 *
 * 최종 점수 = (실시간 기본점수 × 지역 취약계수 × 만조계수) + 상습침수지 임박 가산
 *
 * 이 방식의 장점: 같은 강수량이라도 과거 피해가 잦았던 지역은 더 일찍 경보가 뜬다.
 * 차바 사례처럼 "만조와 겹쳐 배수 불가" 조건도 계수로 반영된다.
 */
object CompositeRiskCalculator {

    data class CompositeResult(
        val baseStatus: RegionRiskStatus,
        val finalScore: Int,
        val finalLevel: RiskLevel,
        val vulnerability: VulnerabilityProfile,
        val hotspotsAtRisk: List<Pair<FloodHotspot, Int>>,
        val similarEvents: List<SimilarEventMatch>,
        val riverStatuses: List<RiverLevelStatus>,
        val upstreamWarning: String?,
        val tideStatus: TideStatus?,
        val backwaterRisk: BackwaterRisk,
        /**
         * 울산시 대응계획서 공식 통제·대피 기준 판정.
         * 앱 자체 위험도와 **분리**해서 보관한다 — 섞으면 공식 기준을 덮어쓰게 된다.
         */
        val officialJudgement: OfficialCriteria.Judgement,
        /** 대응계획서 지점별 통제 판정 — 현장에서 바로 쓰는 정보 */
        val siteStatuses: List<SiteControlEvaluator.SiteStatus>,
        /**
         * 자동 판정이 불가능해 현장 확인이 필요한 지점.
         * 지하차도 침수심 센서가 외부 연계되지 않아 생긴 공백을 메우기 위한 것.
         */
        val needsFieldCheck: List<SiteControlEvaluator.SiteStatus>,
        /**
         * 강수 시 확인해야 할 지하차도 전수 목록.
         * 통제지점에 실리지 않은 곳까지 포함합니다 — 사각지대를 남기지 않기 위해.
         */
        val underpassesToCheck: List<Underpasses.Underpass>,
        /**
         * 지하차도 단계 판정 (관심→주의→경계→심각).
         * 침수심 센서 없이도 경계 단계까지는 강수·특보로 자동 판정됩니다.
         */
        val underpassStage: UnderpassStage.Judgement,
        val actionGuidance: List<String>
    )

    fun evaluate(
        baseStatus: RegionRiskStatus,
        district: String,
        cumulativeRainMm: Double,
        rainDurationHours: Int,
        tideStatus: TideStatus? = null,
        riverStatuses: List<RiverLevelStatus> = emptyList(),
        officialInput: OfficialCriteria.Input? = null,
        siteObservation: SiteControlEvaluator.Observation? = null,
        underpassInput: UnderpassStage.Input? = null
    ): CompositeResult {

        val profile = VulnerabilityAnalyzer.profileFor(district)
        val hourlyRain = baseStatus.latestPrecipitation?.hourlyRainMm ?: 0.0

        // 1) 실시간 점수에 지역 취약계수 적용
        var score = baseStatus.riskScore * profile.vulnerabilityMultiplier

        // 2) 조위 보정 (하구 지역에서만 유효)
        // 만조 근접도 + 대조기 여부 + 배수차단 임계 초과를 종합한 계수
        if (tideStatus != null && profile.tideSensitive) {
            score *= tideStatus.tideMultiplier
        }

        // 3) 상습 침수지 임박 가산
        val atRisk = VulnerabilityAnalyzer.hotspotsAtRisk(district, hourlyRain, cumulativeRainMm)
        atRisk.forEach { (spot, percent) ->
            // baseWeight는 Int, 나머지 분기는 Double이라 타입을 맞춰야 한다
            score += when {
                percent >= 100 -> spot.hotspotType.baseWeight.toDouble()
                percent >= 90 -> spot.hotspotType.baseWeight * 0.6
                else -> spot.hotspotType.baseWeight * 0.3
            }
        }

        // 4) 하천 수위 반영 — 강수량보다 직접적인 침수 지표이므로 가중치가 크다
        riverStatuses.forEach { river ->
            score += river.stage.score

            // 급상승 중이면 추가 가산 (도달 시간이 짧다 = 대응 여유가 없다)
            if (river.riseRateMPerHour >= RiverLevelAnalyzer.RAPID_RISE_THRESHOLD_M_PER_HOUR) {
                score += 4
            }
            // 다음 단계까지 1시간 이내면 사실상 임박으로 간주
            river.minutesToNextStage?.let { minutes ->
                if (minutes <= 60) score += 3
                else if (minutes <= 120) score += 1.5
            }
        }

        // 5) 배수 위험 — 조위와 하천 수위의 결합 판단
        val backwater = TideRepository.assessBackwater(tideStatus, riverStatuses)
        score += backwater.level.score

        // 6) 과거 사례 유사도 매칭
        val similar = VulnerabilityAnalyzer.matchSimilarEvents(
            hourlyRain, cumulativeRainMm, rainDurationHours,
            tideStatus?.isNearHighTide ?: false
        )
        // 심각도 높은 과거 사례의 진행률이 높으면 추가 가산
        similar.firstOrNull()?.let { match ->
            if (match.progressPercent >= 70 && match.event.severityScore >= 8) {
                score += 3
            }
        }

        val finalScore = score.toInt()
        val finalLevel = when {
            finalScore >= 18 -> RiskLevel.SEVERE
            finalScore >= 12 -> RiskLevel.DANGER
            finalScore >= 7 -> RiskLevel.WARNING
            finalScore >= 3 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }

        val siteObs = siteObservation ?: SiteControlEvaluator.Observation(
            hourlyRainMm = hourlyRain,
            cumulativeRainMm = cumulativeRainMm
        )
        val siteEval = SiteControlEvaluator.evaluate(district, siteObs)

        return CompositeResult(
            baseStatus = baseStatus,
            finalScore = finalScore,
            finalLevel = finalLevel,
            vulnerability = profile,
            hotspotsAtRisk = atRisk,
            similarEvents = similar,
            riverStatuses = riverStatuses,
            upstreamWarning = RiverLevelAnalyzer.upstreamWarning(riverStatuses),
            tideStatus = tideStatus,
            backwaterRisk = backwater,
            officialJudgement = OfficialCriteria.judge(
                officialInput ?: OfficialCriteria.Input(
                    cumulativeRainMm = cumulativeRainMm,
                    continuousRainMm = cumulativeRainMm,
                    hourlyRainMm = hourlyRain,
                    riverLevelRatio = riverStatuses.maxOfOrNull {
                        it.currentLevelM / it.station.dangerLevel
                    },
                    floodAlertIssued = riverStatuses.any { it.stage >= RiverStage.ALERT }
                )
            ),
            siteStatuses = siteEval,
            needsFieldCheck = SiteControlEvaluator.needsFieldCheck(siteEval, siteObs),
            underpassesToCheck = SiteControlEvaluator.underpassesToCheck(district, siteObs),
            underpassStage = UnderpassStage.judge(
                underpassInput ?: UnderpassStage.Input(
                    cumulative3hMm = siteObs.cumulative3hMm,
                    cumulative12hMm = siteObs.cumulative12hMm,
                    heavyRainAdvisory = siteObs.heavyRainAdvisory,
                    heavyRainWarning = siteObs.heavyRainWarning,
                    floodDepthCm = siteObs.roadFloodDepthCm
                )
            ),
            actionGuidance = buildGuidance(finalLevel, atRisk, riverStatuses, tideStatus, backwater, profile)
        )
    }

    /**
     * 위험 단계와 임박한 지점 유형에 맞춘 구체적 행동요령.
     * 일반적인 "안전에 유의하세요" 대신 지점 특성에 맞춘 지시를 낸다.
     */
    private fun buildGuidance(
        level: RiskLevel,
        atRisk: List<Pair<FloodHotspot, Int>>,
        riverStatuses: List<RiverLevelStatus>,
        tideStatus: TideStatus?,
        backwater: BackwaterRisk,
        profile: VulnerabilityProfile
    ): List<String> {
        val guidance = mutableListOf<String>()

        if (level == RiskLevel.SAFE) return listOf("현재 특이사항 없음")

        // 하천 수위 관련 조치를 최우선으로 — 범람은 되돌릴 수 없다
        riverStatuses
            .filter { it.stage >= RiverStage.ATTENTION }
            .sortedByDescending { it.stage.score }
            .forEach { river ->
                val lead = river.minutesToNextStage
                when (river.stage) {
                    RiverStage.DANGER -> guidance.add(
                        "[${river.station.name}] 계획홍수위 초과 — 제방 범람 위험. " +
                        "${river.station.protectedAreas.joinToString(", ")} 즉시 대피 발령"
                    )
                    RiverStage.ALERT -> guidance.add(
                        "[${river.station.name}] 홍수경보 수위 도달 — " +
                        "${river.station.protectedAreas.joinToString(", ")} 대피 준비 및 하천변 전면 통제" +
                        (if (lead != null && lead <= 120) " (계획홍수위까지 약 ${lead}분)" else "")
                    )
                    RiverStage.WARNING -> guidance.add(
                        "[${river.station.name}] 홍수주의보 수위 — 하천변 산책로·둔치 통제, " +
                        "저지대 주민 사전 안내" +
                        (if (lead != null && lead <= 120) " (경보수위까지 약 ${lead}분)" else "")
                    )
                    RiverStage.ATTENTION -> guidance.add(
                        "[${river.station.name}] 관심수위 도달 — 수위 추이 주시 및 배수문 점검"
                    )
                    else -> {}
                }
            }

        // 지점 유형별 우선 조치
        atRisk.forEach { (spot, percent) ->
            val urgency = if (percent >= 100) "즉시" else "선제적으로"
            when (spot.hotspotType) {
                HotspotType.UNDERPASS ->
                    guidance.add("[${spot.name}] $urgency 진입 차단 필요 — 지하차도 침수는 인명피해로 직결됩니다")
                HotspotType.RIVERSIDE_ROAD ->
                    guidance.add("[${spot.name}] $urgency 통제 및 산책로 이용객 대피 안내")
                HotspotType.LOWLAND_MARKET ->
                    guidance.add("[${spot.name}] $urgency 배수펌프 가동 및 상인 대상 사전 고지")
                HotspotType.LANDSLIDE_ZONE ->
                    guidance.add("[${spot.name}] $urgency 인근 주민 대피 검토 — 산사태·낙석 이력 구역")
                HotspotType.BRIDGE_UNDERPASS ->
                    guidance.add("[${spot.name}] $urgency 하부 도로 차량 통행 통제")
                HotspotType.RESIDENTIAL ->
                    guidance.add("[${spot.name}] $urgency 저지대 주택 침수 대비 및 주민 연락")
                HotspotType.INDUSTRIAL ->
                    guidance.add("[${spot.name}] $urgency 차량 이동 조치 및 도로 통제 준비")
            }
        }

        // 배수 위험을 하천 조치 바로 다음 순위로
        if (backwater.level != BackwaterLevel.NONE && backwater.message.isNotBlank()) {
            guidance.add(backwater.message)
        }

        // 만조가 아직 오지 않았다면 선제 대응 리드타임을 알린다
        if (tideStatus != null && profile.tideSensitive && !tideStatus.isNearHighTide) {
            tideStatus.minutesToNextHighTide?.let { minutes ->
                if (minutes in 1..240) {
                    guidance.add(
                        "약 ${minutes}분 후 만조(${tideStatus.nextHighTide?.levelCm ?: "-"}cm)입니다. " +
                        "그 전에 배수 작업을 마치고, 만조 시간대에는 배수 지연을 전제로 대응하세요." +
                        (if (tideStatus.isSpringTide) " 대조기라 만조 조위가 평소보다 높습니다." else "")
                    )
                }
            }
        }

        when (level) {
            RiskLevel.SEVERE ->
                guidance.add(0, "즉시 대피 및 통제 조치 단계입니다. 비상근무 체계 격상을 검토하세요")
            RiskLevel.DANGER ->
                guidance.add(0, "위험 단계입니다. 상습 침수지 사전 통제와 취약계층 연락을 시작하세요")
            RiskLevel.WARNING ->
                guidance.add(0, "경고 단계입니다. 배수시설 점검과 통제 준비를 갖추세요")
            RiskLevel.CAUTION ->
                guidance.add(0, "주의 단계입니다. 기상 추이를 주시하세요")
            else -> {}
        }

        return guidance
    }
}
