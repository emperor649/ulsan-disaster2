package com.ulsan.disasteralert.data

/**
 * 지점별 통제 발동 판정.
 *
 * 대응계획서의 지점별 '통제 기준'을 현재 관측값과 대조해
 * **어느 지점이 지금 통제 대상인지** 구체적으로 알려줍니다.
 *
 * 이것이 앱의 가장 실용적인 기능입니다.
 * "중구 위험도 14점"보다 "우정동 지하차도 통제 기준 도달"이
 * 현장에서 바로 쓸 수 있는 정보이기 때문입니다.
 */
object SiteControlEvaluator {

    data class SiteStatus(
        val site: ControlSites.Site,
        val triggered: Boolean,
        /** 기준 대비 도달률 (%) — 임박 여부 판단용 */
        val progressPercent: Int?,
        val observed: String,
        val reason: String
    )

    /**
     * 판정에 필요한 관측 입력.
     *
     * 일부 기준(도로침수, 하천수위 교량상부, 토양함수지수)은 현장 확인이나
     * 별도 시스템이 필요합니다. 값이 없으면 해당 지점은 '판정 불가'로 두고,
     * 추정으로 넘겨짚지 않습니다 — 잘못된 확신이 미판정보다 위험합니다.
     */
    data class Observation(
        val hourlyRainMm: Double,
        val cumulativeRainMm: Double,
        /** 15분 강수량 — AWS 매분자료에서 산출. 지하공간 기준 판정에 사용 */
        val rain15minMm: Double? = null,
        /** 50mm/h 이상이 지속되고 있는지 */
        val sustainedHeavyRate: Boolean = false,
        /** 산림청 토양함수지수 (%) — 산사태정보시스템 연계 필요 */
        val soilMoistureIndex: Double? = null,
        /** 산사태주의보 발령 여부 */
        val landslideAdvisory: Boolean = false,
        /** 태풍 또는 호우경보 발령 여부 */
        val typhoonOrHeavyRainWarning: Boolean = false,
        /** 하천수위 교량상부 기준 도달 높이 (cm) — 현장 계측 필요 */
        val bridgeClearanceCm: Double? = null,
        /** 지하차도 노면 침수 깊이 (cm) — CCTV·센서 필요 */
        val roadFloodDepthCm: Double? = null,

        // ── 중구·남구·동구 기준에 필요한 추가 관측값 ──
        /** 3시간 누적 강수량 */
        val cumulative3hMm: Double? = null,
        /** 12시간 누적 강수량 */
        val cumulative12hMm: Double? = null,
        /** 일 강수량 */
        val dailyRainMm: Double? = null,
        /** 연속강우량 (비가 끊기지 않고 이어진 누적) */
        val continuousRainMm: Double? = null,
        /** 호우경보 발령 여부 (주의보와 구분) */
        val heavyRainWarning: Boolean = false,
        /** 호우주의보 발령 여부 */
        val heavyRainAdvisory: Boolean = false,
        /** 산사태경보 발령 여부 (주의보보다 상위) */
        val landslideWarning: Boolean = false,
        /** 폭풍해일 높이 (m) — 해일 특보 또는 조위 관측 */
        val surgeHeightM: Double? = null,
        /**
         * 기상특보 시 상황판단회의 개최 여부.
         * 이 기준은 자동 판정이 불가능하다 — 사람이 회의에서 결정하는 것이기 때문.
         */
        val situationMeetingHeld: Boolean = false
    )

    fun evaluate(district: String, obs: Observation): List<SiteStatus> =
        ControlSites.inDistrict(district).map { evaluateSite(it, obs) }

    fun evaluateAll(obs: Observation): List<SiteStatus> =
        ControlSites.all.map { evaluateSite(it, obs) }

    private fun evaluateSite(site: ControlSites.Site, obs: Observation): SiteStatus {
        return when (site.criterion) {
            ControlSites.Criterion.HOURLY20 -> byHourly(site, obs, 20.0)
            ControlSites.Criterion.HOURLY30 -> byHourly(site, obs, 30.0)

            ControlSites.Criterion.HOURLY30_BRIDGE30 -> {
                val rainHit = obs.hourlyRainMm >= 30.0
                val bridgeHit = (obs.bridgeClearanceCm ?: Double.MAX_VALUE) <= 30.0
                val pct = ((obs.hourlyRainMm / 30.0) * 100).toInt()
                SiteStatus(
                    site, rainHit || bridgeHit, pct,
                    "시우량 ${"%.1f".format(obs.hourlyRainMm)}mm" +
                        (obs.bridgeClearanceCm?.let { " · 교량상부 여유 ${it}cm" } ?: ""),
                    when {
                        bridgeHit -> "하천수위가 교량상부 기준 30cm에 도달했습니다"
                        rainHit -> "시우량 30mm 기준을 넘었습니다"
                        else -> "기준 미달 (교량상부 수위는 현장 확인 필요)"
                    }
                )
            }

            ControlSites.Criterion.CUM200 -> {
                val hit = obs.cumulativeRainMm >= 200.0
                val pct = ((obs.cumulativeRainMm / 200.0) * 100).toInt()
                SiteStatus(
                    site, hit, pct,
                    "누적강우 ${"%.0f".format(obs.cumulativeRainMm)}mm",
                    if (hit) "누적강우 200mm 기준을 넘었습니다"
                    else "기준의 ${pct}% 수준"
                )
            }

            ControlSites.Criterion.RATE50 -> {
                val hit = obs.sustainedHeavyRate || obs.hourlyRainMm >= 50.0
                val pct = ((obs.hourlyRainMm / 50.0) * 100).toInt()
                SiteStatus(
                    site, hit, pct,
                    "시간당 ${"%.1f".format(obs.hourlyRainMm)}mm",
                    if (hit) "50mm/h 기준에 도달했습니다 (도로침수 여부는 현장 확인)"
                    else "기준의 ${pct}% 수준"
                )
            }

            ControlSites.Criterion.ROAD5CM -> {
                val depth = obs.roadFloodDepthCm
                SiteStatus(
                    site,
                    triggered = (depth ?: 0.0) >= 5.0,
                    progressPercent = depth?.let { ((it / 5.0) * 100).toInt() },
                    observed = depth?.let { "노면 침수 ${it}cm" } ?: "침수 계측값 없음",
                    reason = when {
                        depth == null ->
                            "노면 침수 깊이를 알 수 없습니다. CCTV·침수센서 확인이 필요합니다. " +
                            "참고: 현재 시간당 ${"%.1f".format(obs.hourlyRainMm)}mm"
                        depth >= 5.0 -> "노면 5cm 침수 기준에 도달했습니다"
                        else -> "기준 미달"
                    }
                )
            }

            ControlSites.Criterion.SOIL80 -> {
                val idx = obs.soilMoistureIndex
                val hit = obs.landslideAdvisory || (idx ?: 0.0) >= 80.0
                SiteStatus(
                    site, hit, idx?.toInt(),
                    idx?.let { "토양함수지수 ${it}%" } ?: "토양함수지수 미연계",
                    when {
                        obs.landslideAdvisory -> "산사태주의보가 발령되었습니다"
                        idx == null ->
                            "토양함수지수를 확인할 수 없습니다. 산림청 산사태정보시스템 연계가 필요합니다"
                        idx >= 80.0 -> "토양함수지수 80% 기준에 도달했습니다"
                        else -> "기준의 ${idx.toInt()}% 수준"
                    }
                )
            }

            // ── 중구 ──
            ControlSites.Criterion.WARN_DEPTH15 -> {
                val depth = obs.roadFloodDepthCm
                val warnHit = obs.heavyRainWarning
                val depthHit = (depth ?: 0.0) >= 15.0
                SiteStatus(
                    site,
                    triggered = warnHit && depthHit,
                    progressPercent = depth?.let { ((it / 15.0) * 100).toInt() },
                    observed = buildString {
                        append(if (warnHit) "호우경보 발령" else "호우경보 없음")
                        append(" · ")
                        append(depth?.let { "침수심 ${it}cm" } ?: "침수심 계측값 없음")
                    },
                    reason = when {
                        depth == null ->
                            "침수심을 알 수 없습니다. CCTV·침수센서 확인이 필요합니다. " +
                            "기상상황·현장여건에 따라 기준이 조정될 수 있습니다"
                        warnHit && depthHit -> "호우경보 + 침수심 15cm 기준에 모두 도달했습니다"
                        depthHit -> "침수심은 기준을 넘었으나 호우경보가 발령되지 않았습니다 (현장 판단 필요)"
                        else -> "기준 미달"
                    }
                )
            }

            ControlSites.Criterion.HOURLY30_DAILY100 -> {
                val daily = obs.dailyRainMm ?: 0.0
                val hit = obs.hourlyRainMm >= 30.0 || daily >= 100.0
                val pct = maxOf(obs.hourlyRainMm / 30.0, daily / 100.0).let { (it * 100).toInt() }
                SiteStatus(
                    site, hit, pct,
                    "시간당 ${"%.1f".format(obs.hourlyRainMm)}mm · 일강우 ${"%.0f".format(daily)}mm",
                    if (hit) "시간당 30mm 또는 일강우 100mm 기준에 도달했습니다"
                    else "기준의 ${pct}% 수준"
                )
            }

            ControlSites.Criterion.SITUATION_MEETING -> SiteStatus(
                site,
                triggered = obs.situationMeetingHeld,
                progressPercent = null,
                observed = if (obs.situationMeetingHeld) "상황판단회의 개최" else "회의 미개최",
                reason = if (obs.situationMeetingHeld)
                    "상황판단회의 결정에 따라 통제합니다"
                else
                    "이 지점은 기상특보 시 상황판단회의에서 결정합니다. " +
                    "자동 판정이 불가능하므로 담당자 확인이 필요합니다. " +
                    "참고: 시간당 ${"%.1f".format(obs.hourlyRainMm)}mm · 누적 ${"%.0f".format(obs.cumulativeRainMm)}mm"
            )

            ControlSites.Criterion.SOIL80_100 -> {
                val idx = obs.soilMoistureIndex
                val hit = obs.landslideAdvisory || obs.landslideWarning || (idx ?: 0.0) >= 80.0
                SiteStatus(
                    site, hit, idx?.toInt(),
                    idx?.let { "토양함수지수 ${it}%" } ?: "토양함수지수 미연계",
                    when {
                        obs.landslideWarning -> "산사태경보(토양함수 100%)가 발령되었습니다 — 즉시 대피"
                        obs.landslideAdvisory -> "산사태주의보(토양함수 80%)가 발령되었습니다"
                        idx == null -> "산림청 산사태정보시스템 연계가 필요합니다"
                        idx >= 100.0 -> "토양함수지수 100% — 경보 수준입니다"
                        idx >= 80.0 -> "토양함수지수 80% — 주의보 수준입니다"
                        else -> "기준의 ${idx.toInt()}% 수준"
                    }
                )
            }

            ControlSites.Criterion.ADVISORY_3H60 -> {
                val c3 = obs.cumulative3hMm ?: 0.0
                val hit = obs.heavyRainAdvisory && c3 >= 60.0
                val pct = ((c3 / 60.0) * 100).toInt()
                SiteStatus(
                    site, hit, pct,
                    "${if (obs.heavyRainAdvisory) "호우주의보 발령" else "주의보 없음"} · 3시간 ${"%.0f".format(c3)}mm",
                    if (hit) "호우주의보 + 3시간 60mm 기준에 도달했습니다"
                    else if (c3 >= 60.0) "강우는 기준을 넘었으나 호우주의보가 없습니다"
                    else "기준의 ${pct}% 수준"
                )
            }

            // ── 남구 ──
            ControlSites.Criterion.DEPTH15 -> byDepth(site, obs, 15.0, staged = false)
            ControlSites.Criterion.DEPTH15_STAGED -> byDepth(site, obs, 15.0, staged = true)

            ControlSites.Criterion.CUM60_3H -> byCumulative(site, obs, 60.0, 110.0)
            ControlSites.Criterion.CUM90_3H -> byCumulative(site, obs, 90.0, 180.0)

            ControlSites.Criterion.HOURLY25 -> byHourly(site, obs, 25.0)

            ControlSites.Criterion.SURGE_05M -> {
                val h = obs.surgeHeightM
                SiteStatus(
                    site,
                    triggered = (h ?: 0.0) >= 0.5,
                    progressPercent = h?.let { ((it / 0.5) * 100).toInt() },
                    observed = h?.let { "해일높이 ${it}m" } ?: "해일높이 정보 없음",
                    reason = when {
                        h == null -> "해일 특보 또는 조위 관측값 연계가 필요합니다"
                        h >= 0.5 -> "해일높이 0.5m 기준에 도달했습니다 — 대피 안내 대상"
                        else -> "기준의 ${((h / 0.5) * 100).toInt()}% 수준"
                    }
                )
            }

            ControlSites.Criterion.LANDSLIDE_BAND -> {
                // 구간 기준: 연속 100~200 & 시강우 20~30 & 일강우 80~150
                // 하한 도달만으로 통제 대상으로 본다 (상한은 상위 단계로 넘어가는 경계)
                val cont = obs.continuousRainMm ?: obs.cumulativeRainMm
                val daily = obs.dailyRainMm ?: 0.0
                val contHit = cont >= 100.0
                val hourlyHit = obs.hourlyRainMm >= 20.0
                val dailyHit = daily >= 80.0
                val hit = contHit || hourlyHit || dailyHit
                val pct = maxOf(cont / 100.0, obs.hourlyRainMm / 20.0, daily / 80.0)
                    .let { (it * 100).toInt() }
                SiteStatus(
                    site, hit, pct,
                    "연속 ${"%.0f".format(cont)}mm · 시강우 ${"%.1f".format(obs.hourlyRainMm)}mm · 일강우 ${"%.0f".format(daily)}mm",
                    if (hit) "구간 기준 하한(연속100·시강우20·일강우80)에 도달했습니다"
                    else "기준의 ${pct}% 수준"
                )
            }

            // ── 동구 ──
            ControlSites.Criterion.WARN_HOURLY40 -> {
                val hit = obs.heavyRainWarning && obs.hourlyRainMm >= 40.0
                val pct = ((obs.hourlyRainMm / 40.0) * 100).toInt()
                SiteStatus(
                    site, hit, pct,
                    "${if (obs.heavyRainWarning) "호우경보 지속" else "호우경보 없음"} · 시우량 ${"%.1f".format(obs.hourlyRainMm)}mm",
                    if (hit) "호우경보 지속 + 시우량 40mm — 즉시통제 대상입니다"
                    else if (obs.hourlyRainMm >= 40.0) "시우량은 기준을 넘었으나 호우경보가 없습니다"
                    else "기준의 ${pct}% 수준"
                )
            }

            ControlSites.Criterion.TYPHOON_ALERT -> SiteStatus(
                site, obs.typhoonOrHeavyRainWarning, null,
                if (obs.typhoonOrHeavyRainWarning) "특보 발령 중" else "특보 없음",
                if (obs.typhoonOrHeavyRainWarning) "태풍·호우경보 발령으로 폐쇄 대상입니다"
                else "기준 미달"
            )
        }
    }

    /** 침수심 기준 판정 */
    private fun byDepth(
        site: ControlSites.Site, obs: Observation, threshold: Double, staged: Boolean
    ): SiteStatus {
        val depth = obs.roadFloodDepthCm
        return SiteStatus(
            site,
            triggered = (depth ?: 0.0) >= threshold,
            progressPercent = depth?.let { ((it / threshold) * 100).toInt() },
            observed = depth?.let { "침수심 ${it}cm" } ?: "침수심 계측값 없음",
            reason = when {
                depth == null ->
                    "침수심을 알 수 없습니다. CCTV·침수센서 확인이 필요합니다. " +
                    "참고: 시간당 ${"%.1f".format(obs.hourlyRainMm)}mm"
                depth >= threshold -> "침수심 ${threshold.toInt()}cm 기준에 도달했습니다"
                staged -> "침수심 ${threshold.toInt()}cm 미만 — 비상대기 및 통제 준비 단계입니다"
                else -> "기준 미달"
            }
        )
    }

    /** 3시간/12시간 누적 기준 판정 — 둘 중 하나만 넘어도 발동 */
    private fun byCumulative(
        site: ControlSites.Site, obs: Observation, t3h: Double, t12h: Double
    ): SiteStatus {
        val c3 = obs.cumulative3hMm
        val c12 = obs.cumulative12hMm

        if (c3 == null && c12 == null) {
            return SiteStatus(
                site, false, null,
                "3시간·12시간 누적값 없음",
                "시간별 누적 강수량이 필요합니다. " +
                "참고: 24시간 누적 ${"%.0f".format(obs.cumulativeRainMm)}mm"
            )
        }

        val hit3 = (c3 ?: 0.0) >= t3h
        val hit12 = (c12 ?: 0.0) >= t12h
        val pct = maxOf((c3 ?: 0.0) / t3h, (c12 ?: 0.0) / t12h).let { (it * 100).toInt() }

        return SiteStatus(
            site, hit3 || hit12, pct,
            "3시간 ${"%.0f".format(c3 ?: 0.0)}mm · 12시간 ${"%.0f".format(c12 ?: 0.0)}mm",
            when {
                hit3 -> "3시간 누적 ${t3h.toInt()}mm 기준에 도달했습니다"
                hit12 -> "12시간 누적 ${t12h.toInt()}mm 기준에 도달했습니다"
                else -> "기준의 ${pct}% 수준"
            }
        )
    }

    private fun byHourly(
        site: ControlSites.Site, obs: Observation, threshold: Double
    ): SiteStatus {
        val hit = obs.hourlyRainMm >= threshold
        val pct = ((obs.hourlyRainMm / threshold) * 100).toInt()
        return SiteStatus(
            site, hit, pct,
            "시우량 ${"%.1f".format(obs.hourlyRainMm)}mm",
            if (hit) "시우량 ${threshold.toInt()}mm 기준을 넘었습니다"
            else "기준의 ${pct}% 수준"
        )
    }

    /** 통제 발동 지점만 */
    fun triggered(statuses: List<SiteStatus>) =
        statuses.filter { it.triggered }
            .sortedByDescending { it.site.isCritical }

    /** 임박 지점 (기준의 80% 이상, 아직 미발동) */
    fun imminent(statuses: List<SiteStatus>) =
        statuses.filter { !it.triggered && (it.progressPercent ?: 0) >= 80 }
            .sortedByDescending { it.progressPercent ?: 0 }

    /**
     * 판정 불가 지점 — 외부 데이터가 없어 확인할 수 없는 곳.
     * 이걸 숨기면 "통제 대상 없음"으로 오해하게 되므로 반드시 드러냅니다.
     */
    fun undeterminable(statuses: List<SiteStatus>) =
        statuses.filter { it.progressPercent == null && !it.triggered }

    /**
     * 판정 불가 지점 중 **강수 여건상 주시가 필요한 곳**.
     *
     * 침수심 센서 연계가 불가능해 지하차도는 자동 판정이 안 됩니다.
     * 그렇다고 침묵하면 "이상 없음"으로 오해하게 되므로,
     * 강수량이 일정 수준을 넘으면 **현장 확인을 요청**합니다.
     *
     * 판정이 아니라 환기입니다. 앱이 "통제하라"고 말하지 않고
     * "이 지점을 지금 확인하라"고만 말합니다.
     *
     * 기준: 시간당 20mm 또는 15분 10mm — 대응계획서 통제기준의
     * 하한선 언저리로, 지하공간 침수가 시작될 수 있는 수준.
     */
    fun needsFieldCheck(
        statuses: List<SiteStatus>, obs: Observation
    ): List<SiteStatus> {
        if (!rainConcernFor(obs)) return emptyList()
        return undeterminable(statuses)
            .filter { it.site.isCritical }
            .sortedBy { it.site.district }
    }

    /** 현장 확인이 필요한 강수 여건인지 */
    fun rainConcernFor(obs: Observation): Boolean =
        obs.hourlyRainMm >= 20.0 ||
        (obs.rain15minMm ?: 0.0) >= 10.0 ||
        obs.sustainedHeavyRate

    /**
     * 강수 시 확인해야 할 지하차도 — **전수 기준**.
     *
     * 통제지점 목록에 실린 것만 보면 사각지대가 생깁니다.
     * 침수 위험은 통제지점 지정 여부와 무관하게 모든 지하차도에 있고,
     * 센서 연계가 불가능한 이상 앱이 할 수 있는 건 빠짐없이 제시하는 것뿐입니다.
     *
     * 고위험 등급을 앞에 둡니다 — 인력이 제한된 상황의 순찰 우선순위입니다.
     */
    fun underpassesToCheck(
        district: String?, obs: Observation
    ): List<Underpasses.Underpass> {
        if (!rainConcernFor(obs)) return emptyList()
        val list = district?.let { Underpasses.inDistrict(it) } ?: Underpasses.all
        return list.sortedWith(
            compareBy<Underpasses.Underpass> { it.grade.ordinal }.thenBy { it.no }
        )
    }

    /** 요약 문장 */
    fun summarize(statuses: List<SiteStatus>, obs: Observation? = null): String {
        val t = triggered(statuses)
        val i = imminent(statuses)
        val u = undeterminable(statuses)
        val check = obs?.let { needsFieldCheck(statuses, it) } ?: emptyList()

        return buildString {
            if (t.isEmpty() && i.isEmpty()) {
                append("통제 기준에 도달한 지점이 없습니다")
            } else {
                if (t.isNotEmpty()) {
                    append("통제 대상 ${t.size}개소")
                    val crit = t.count { it.site.isCritical }
                    if (crit > 0) append(" (인명피해 직결 ${crit}개소)")
                }
                if (i.isNotEmpty()) {
                    if (t.isNotEmpty()) append(" · ")
                    append("임박 ${i.size}개소")
                }
            }
            if (check.isNotEmpty()) {
                appendLine()
                append("⚠ 현장 확인 필요 ${check.size}개소 (지하차도 등 — 자동 판정 불가)")
            } else if (u.isNotEmpty()) {
                appendLine()
                append("※ ${u.size}개소는 외부 데이터 미연계로 판정할 수 없습니다")
            }
        }
    }
}
