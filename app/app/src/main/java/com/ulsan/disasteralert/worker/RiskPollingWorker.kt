package com.ulsan.disasteralert.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ulsan.disasteralert.BuildConfig
import com.ulsan.disasteralert.data.*
import com.ulsan.disasteralert.network.ApiClient
import com.ulsan.disasteralert.notification.NotificationHelper
import com.ulsan.disasteralert.util.RainAccumulator
import com.ulsan.disasteralert.util.districtOf

import java.text.SimpleDateFormat
import java.util.*

/**
 * 등록된 관심 지역에 대해 기상특보 + 강수량 + 재난문자를 주기적으로(예: 10분) 조회하고
 * RiskCalculator로 위험도를 산출한 뒤, 이전 상태보다 위험도가 상승했으면 알림을 발송한다.
 *
 * 실제 배포 시 WorkManager 최소 주기는 15분이므로, 그보다 촘촘한 실시간성이 필요하면
 * FCM(DisasterFcmService)을 긴급 채널로 병행 사용한다.
 */
class RiskPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val region = inputData.getString(KEY_REGION_NAME) ?: return Result.failure()
            // 저장된 값이 "울산 남구" 형태일 수 있으므로 정규화한다
            val district = districtOf(inputData.getString(KEY_DISTRICT) ?: region)
            val nx = inputData.getInt(KEY_NX, -1)
            val ny = inputData.getInt(KEY_NY, -1)
            if (nx < 0 || ny < 0) return Result.failure()

            val now = Calendar.getInstance()
            val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
            val timeFmt = SimpleDateFormat("HHmm", Locale.KOREA)
            val baseDate = dateFmt.format(now.time)
            val baseTime = timeFmt.format(now.time)

            // 1) 기상특보 조회
            val warningResp = ApiClient.kmaApi.getWeatherWarnings(
                serviceKey = BuildConfig.KMA_SERVICE_KEY
            )
            val warnings = warningResp.response.body?.items?.item.orEmpty()
                .filter { it.regName.contains(region) }
                .map {
                    WeatherWarning(
                        regionCode = it.regId,
                        regionName = it.regName,
                        warningType = it.warnVar,
                        warningLevel = mapWarningLevel(it.warnStress),
                        announcedAt = it.tmFc,
                        command = it.command
                    )
                }

            // 2) 강수량 조회
            //    1순위: AWS 실측 (지점값, 국지 극값을 잡아냄)
            //    2순위: 초단기실황 (5km 격자 보간값)
            val awsRainfall = if (BuildConfig.KMA_APIHUB_KEY.isNotBlank()) {
                runCatching {
                    AwsRepository.getDistrictRainfall(BuildConfig.KMA_APIHUB_KEY, district)
                }.getOrNull()
            } else null

            // 15분 실측 강수량 — 지하공간 침수 기준 판정에 필수.
            // 강수량이 가장 많은 지점을 기준으로 조회한다 (최댓값 원칙).
            val rain15min = if (BuildConfig.KMA_APIHUB_KEY.isNotBlank()) {
                val targetStation = awsRainfall?.maxStation?.stationId
                    ?: UlsanAwsStations.inDistrict(district).firstOrNull()?.id
                targetStation?.let {
                    runCatching {
                        AwsRepository.getRecentMinuteRain(BuildConfig.KMA_APIHUB_KEY, it, 15)
                    }.getOrNull()
                }
            } else null

            // 초단기실황(폴백)
            val nowcastResp = ApiClient.kmaApi.getUltraShortNowcast(
                serviceKey = BuildConfig.KMA_SERVICE_KEY,
                baseDate = baseDate,
                baseTime = baseTime,
                nx = nx,
                ny = ny
            )
            val rainItem = nowcastResp.response.body?.items?.item.orEmpty()
                .find { it.category == "RN1" }
            // AWS 값이 있으면 그것을 쓴다 — 격자 보간값보다 정확하다.
            // 지역 내 여러 관측소 중 **최댓값**을 쓰는 것이 핵심:
            // 평균을 쓰면 국지 호우가 희석되는데, 피해는 최댓값 지점에서 난다.
            val precipitation = when {
                awsRainfall != null && awsRainfall.readings.isNotEmpty() ->
                    PrecipitationObservation(
                        stationId = awsRainfall.maxStation?.stationId ?: "AWS",
                        regionName = awsRainfall.maxStation?.stationName ?: region,
                        hourlyRainMm = awsRainfall.maxHourlyRainMm,
                        cumulativeRainMm = awsRainfall.maxDailyRainMm,
                        observedAt = ""
                    )
                rainItem != null ->
                    PrecipitationObservation(
                        stationId = "$nx,$ny",
                        regionName = region,
                        hourlyRainMm = rainItem.obsrValue.toDoubleOrNull() ?: 0.0,
                        cumulativeRainMm = 0.0,
                        observedAt = "${rainItem.baseDate}${rainItem.baseTime}"
                    )
                else -> null
            }

            // 3) 재난문자/위기경보 조회
            // 지역명으로 서버에서 미리 걸러 호출량을 아낀다 (일일 1,000건 한도)
            val disasterResp = ApiClient.disasterApi.getDisasterMessages(
                serviceKey = BuildConfig.DISASTER_SERVICE_KEY,
                crtDt = baseDate,
                regionName = "울산"
            )
            val disasterAlerts = disasterResp.body.orEmpty()
                .filter { (it.RCPTN_RGN_NM ?: "").contains(district) || (it.RCPTN_RGN_NM ?: "").contains("울산") }
                .map {
                    DisasterAlert(
                        alertId = it.SN ?: "",
                        disasterType = it.DST_SE_NM ?: "기타",
                        crisisLevel = mapCrisisLevel(it.EMRG_STEP_NM),
                        regionName = it.RCPTN_RGN_NM ?: "",
                        message = it.MSG_CN ?: "",
                        issuedAt = it.CRT_DT ?: "",
                        issuingAgency = "행정안전부"
                    )
                }

            // 4) 강수량 누적 기록 (과거 사례 비교에 필요)
            precipitation?.let {
                RainAccumulator.record(applicationContext, region, it.hourlyRainMm)
            }
            val rainSummary = RainAccumulator.summarize(applicationContext, region)

            // 5) 실시간 기본 위험도 산출
            val baseStatus = RiskCalculator.calculate(
                regionName = region,
                warnings = warnings,
                precipitation = precipitation,
                disasterAlerts = disasterAlerts,
                updatedAt = "$baseDate$baseTime"
            )

            // 6) 하천 수위 조회 — 강수량보다 직접적인 침수 예측 지표
            val riverStatuses = fetchRiverLevels(district)

            // 7) 조위 조회 — 하구 배수 가능 여부를 좌우하는 변수
            val tideStatus = if (BuildConfig.TIDE_SERVICE_KEY.isNotBlank()) {
                if (!TideRepository.stationCodeVerified(applicationContext)) {
                    runCatching {
                        TideRepository.verifyStationCode(applicationContext, BuildConfig.TIDE_SERVICE_KEY)
                    }
                }
                // 태풍 영향권이면 폭풍해일 여유를 반영해 조위를 높게 잡습니다
                val surgeExpected = TyphoonRepository.hasActiveThreat(applicationContext)
                runCatching {
                    TideRepository.getTideStatus(
                        applicationContext, BuildConfig.TIDE_SERVICE_KEY, surgeExpected
                    )
                }.getOrNull()
            } else null

            // 8) 과거 이력 + 하천 수위 + 조위 연계 종합 판단
            val composite = CompositeRiskCalculator.evaluate(
                baseStatus = baseStatus,
                district = district,
                cumulativeRainMm = rainSummary.cumulative24h,
                rainDurationHours = rainSummary.rainDurationHours,
                tideStatus = tideStatus,
                riverStatuses = riverStatuses,
                officialInput = OfficialCriteria.Input(
                    cumulativeRainMm = rainSummary.cumulative24h,
                    // 연속강우: 비가 끊기지 않고 이어진 구간의 누적. 산사태 기준의 필수 조건
                    continuousRainMm = rainSummary.cumulative24h,
                    hourlyRainMm = precipitation?.hourlyRainMm ?: 0.0,
                    rain15minMm = rain15min,
                    dailyRainMm = awsRainfall?.maxDailyRainMm,
                    riverLevelRatio = riverStatuses.maxOfOrNull {
                        it.currentLevelM / it.station.dangerLevel
                    },
                    floodAlertIssued = riverStatuses.any { it.stage >= RiverStage.ALERT },
                    // 산사태 대피③ 취약시간: 일몰 후 ~ 일출 전 (약 19시~07시)
                    isVulnerableHours = now.get(Calendar.HOUR_OF_DAY).let { it >= 19 || it < 7 },
                    // 15분 20mm가 1시간 지속되는지는 예보가 필요. 보수적으로 강우 지속 중이면 true
                    sustained1h = rainSummary.rainDurationHours >= 1
                ),
                underpassInput = UnderpassStage.Input(
                    forecastDailyMm = awsRainfall?.maxDailyRainMm,
                    cumulative3hMm = rainSummary.cumulative3h,
                    cumulative12hMm = rainSummary.cumulative12h,
                    heavyRainAdvisory = warnings.any {
                        it.warningType.contains("호우") && it.warningLevel >= WarningLevel.ADVISORY
                    },
                    heavyRainWarning = warnings.any {
                        it.warningType.contains("호우") && it.warningLevel == WarningLevel.WARNING
                    },
                    typhoonAdvisory = warnings.any { it.warningType.contains("태풍") },
                    // 홍수 특보는 하천 수위 단계에서 역산 (홍수통제소 특보 API 연계 전)
                    floodAdvisory = riverStatuses.any { it.stage == RiverStage.WARNING },
                    floodWarning = riverStatuses.any { it.stage >= RiverStage.ALERT }
                    // floodDepthCm, pumpFailureOrOverflow, agencyControlRequest 는
                    // 현장 정보라 앱이 알 수 없음 — null/false 유지
                ),
                siteObservation = SiteControlEvaluator.Observation(
                    hourlyRainMm = precipitation?.hourlyRainMm ?: 0.0,
                    cumulativeRainMm = rainSummary.cumulative24h,
                    rain15minMm = rain15min,
                    sustainedHeavyRate = (precipitation?.hourlyRainMm ?: 0.0) >= 50.0 &&
                            rainSummary.rainDurationHours >= 1,
                    typhoonOrHeavyRainWarning = warnings.any {
                        it.warningLevel == WarningLevel.WARNING
                    } || TyphoonRepository.hasActiveThreat(applicationContext),
                    // 중구·남구 기준에 필요한 시간별 누적
                    cumulative3hMm = rainSummary.cumulative3h,
                    cumulative12hMm = rainSummary.cumulative12h,
                    dailyRainMm = awsRainfall?.maxDailyRainMm ?: rainSummary.cumulative24h,
                    continuousRainMm = rainSummary.cumulative24h,
                    // 호우경보와 주의보를 구분한다 — 중구·동구 기준이 이를 나눠 쓴다
                    heavyRainWarning = warnings.any {
                        it.warningType.contains("호우") && it.warningLevel == WarningLevel.WARNING
                    },
                    heavyRainAdvisory = warnings.any {
                        it.warningType.contains("호우") && it.warningLevel >= WarningLevel.ADVISORY
                    }
                    // 아래는 외부 시스템 연계 전까지 null — 추정하지 않는다
                    //   soilMoistureIndex  : 산림청 산사태정보시스템
                    //   roadFloodDepthCm   : 지하차도 침수센서·CCTV
                    //   bridgeClearanceCm  : 하천 교량상부 수위 계측
                    //   surgeHeightM       : 해일 특보
                    //   situationMeetingHeld : 담당자 입력
                )
            )

            // 9) 주의 단계 이상이면 알림 (SAFE는 알림 생략)
            val alertSent = composite.finalLevel != RiskLevel.SAFE
            if (alertSent) {
                NotificationHelper.notifyCompositeRisk(
                    applicationContext,
                    composite,
                    eventNotice = Events.noticeFor(applicationContext, district)
                )
            }

            // 10) 관측 스냅샷 기록 — 임계값을 실측 데이터로 보정하기 위한 근거
            ObservationLog.record(
                applicationContext,
                ObservationLog.Snapshot(
                    timestampMillis = System.currentTimeMillis(),
                    district = district,
                    hourlyRainMm = precipitation?.hourlyRainMm ?: 0.0,
                    cumulative24hMm = rainSummary.cumulative24h,
                    riverLevelM = riverStatuses.firstOrNull()?.currentLevelM,
                    riverStage = riverStatuses.firstOrNull()?.stage?.name,
                    tideLevelCm = tideStatus?.currentLevelCm,
                    nearHighTide = tideStatus?.isNearHighTide ?: false,
                    riskScore = composite.finalScore,
                    riskLevel = composite.finalLevel.name,
                    alertSent = alertSent
                )
            )

            // 11) 태풍 대비 단계 — 위험도 점수와 분리된 별도 경로
            //     시간 척도가 달라(일 단위 vs 분 단위) 같은 점수에 합치지 않는다
            if (TyphoonRepository.shouldRefresh(applicationContext) &&
                BuildConfig.KMA_APIHUB_KEY.isNotBlank()) {
                runCatching {
                    val approaches = TyphoonRepository.getApproaches(
                        applicationContext,
                        BuildConfig.KMA_APIHUB_KEY,
                        BuildConfig.TIDE_SERVICE_KEY
                    )
                    TyphoonRepository.markRefreshed(applicationContext)

                    TyphoonRepository.setActiveThreat(
                        applicationContext,
                        approaches.any { it.preparednessLevel >= PreparednessLevel.PREPARE }
                    )

                    TyphoonAnalyzer.mostThreatening(approaches)?.let { top ->
                        if (top.preparednessLevel >= PreparednessLevel.PREPARE) {
                            NotificationHelper.notifyTyphoonPreparedness(applicationContext, top)
                        }
                    }
                }
            }

            // 12) 마지막 정상 상태 캐시 — API 장애 시 폴백용
            LastKnownState.clearFailures(applicationContext, district)
            LastKnownState.save(
                applicationContext,
                LastKnownState.Cached(
                    district = district,
                    riskScore = composite.finalScore,
                    riskLevel = composite.finalLevel.displayName,
                    hourlyRainMm = precipitation?.hourlyRainMm ?: 0.0,
                    cumulative24hMm = rainSummary.cumulative24h,
                    summary = composite.actionGuidance.firstOrNull() ?: "",
                    timestampMillis = System.currentTimeMillis()
                )
            )

            Result.success()
        } catch (e: Exception) {
            // 조용한 실패가 가장 위험하다 — 연속 실패가 이어지면 사용자에게 알린다
            val district = inputData.getString(KEY_DISTRICT)
                ?: inputData.getString(KEY_REGION_NAME) ?: "지역"
            val failures = LastKnownState.recordFailure(applicationContext, district)

            if (failures >= LastKnownState.FAILURE_ALERT_THRESHOLD) {
                NotificationHelper.notifyDataFailure(
                    applicationContext,
                    district,
                    failures,
                    LastKnownState.load(applicationContext, district)
                )
            }
            Result.retry()
        }
    }

    /**
     * 관할 구역의 수위관측소를 조회하고 최근 1시간 관측값으로 상승률까지 분석한다.
     * 수위 API가 실패해도 전체 판단은 계속되어야 하므로 예외를 삼킨다.
     */
    private suspend fun fetchRiverLevels(district: String): List<RiverLevelStatus> {
        val apiKey = BuildConfig.FLOOD_SERVICE_KEY
        if (apiKey.isBlank()) return emptyList()

        // 관측소 제원이 아직 동기화되지 않았으면 먼저 받아온다
        if (StationSyncHelper.needsSync(applicationContext)) {
            runCatching { StationSyncHelper.syncUlsanStations(applicationContext, apiKey) }
        }

        val stations = StationSyncHelper.effectiveStations(applicationContext, district)
            .filterNot { it.code.startsWith("TODO_") }

        return stations.mapNotNull { station ->
            runCatching {
                val resp = ApiClient.floodApi.getWaterLevels(apiKey, station.code)
                val readings = resp.content.orEmpty().mapNotNull { item ->
                    val level = item.wl.toDoubleOrNull() ?: return@mapNotNull null
                    WaterLevelReading(
                        stationCode = item.wlobscd,
                        waterLevelM = level,
                        flowRate = item.fw?.toDoubleOrNull(),
                        observedAtMillis = parseYmdhm(item.ymdhm)
                    )
                }
                RiverLevelAnalyzer.analyze(station, readings)
            }.getOrNull()
        }
    }

    private fun parseYmdhm(ymdhm: String): Long {
        return runCatching {
            SimpleDateFormat("yyyyMMddHHmm", Locale.KOREA).parse(ymdhm)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun mapWarningLevel(code: String): WarningLevel = when (code) {
        "2" -> WarningLevel.WARNING   // 경보 코드 (기관 명세에 맞춰 조정)
        "1" -> WarningLevel.ADVISORY  // 주의보 코드
        else -> WarningLevel.NONE
    }

    /**
     * 긴급재난문자 V2의 긴급단계명을 위기경보 단계로 매핑한다.
     *
     * V2는 위기경보 4단계(관심/주의/경계/심각)가 아니라
     * 문자 등급 3단계(위급재난/긴급재난/안전안내)로 내려온다.
     * 둘은 다른 체계이므로 아래처럼 대응시킨다.
     *   위급재난 = 공습·규모6.0 이상 지진 등 → 심각
     *   긴급재난 = 태풍·호우 등 기상 재난 → 경계
     *   안전안내 = 주의 환기 목적 → 주의
     */
    private fun mapCrisisLevel(name: String?): CrisisLevel = when {
        name == null -> CrisisLevel.INTEREST
        name.contains("위급") -> CrisisLevel.SERIOUS
        name.contains("긴급") -> CrisisLevel.ALERT
        name.contains("안전안내") -> CrisisLevel.CAUTION
        // 구버전 위기경보 표기가 섞여 올 경우 대비
        name.contains("심각") -> CrisisLevel.SERIOUS
        name.contains("경계") -> CrisisLevel.ALERT
        name.contains("주의") -> CrisisLevel.CAUTION
        else -> CrisisLevel.INTEREST
    }

    companion object {
        const val KEY_REGION_NAME = "region_name"
        const val KEY_NX = "nx"
        const val KEY_NY = "ny"
        const val KEY_DISTRICT = "district"  // 중구/남구/동구/북구/울주군
    }
}
