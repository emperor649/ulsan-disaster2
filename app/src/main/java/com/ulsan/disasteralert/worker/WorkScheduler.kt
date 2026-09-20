package com.ulsan.disasteralert.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val WORK_NAME_PREFIX = "risk_polling_"

    /**
     * 관심 지역을 등록하면 15분 주기로 위험도를 갱신하는 워커를 예약한다.
     * (WorkManager PeriodicWork 최소 주기가 15분이라 그보다 빠른 긴급 알림은
     *  서버 → FCM 푸시 경로(DisasterFcmService)로 보완한다.)
     */
    fun scheduleRegion(
        context: Context,
        regionName: String,
        nx: Int,
        ny: Int,
        district: String = regionName
    ) {
        val input = workDataOf(
            RiskPollingWorker.KEY_REGION_NAME to regionName,
            RiskPollingWorker.KEY_NX to nx,
            RiskPollingWorker.KEY_NY to ny,
            RiskPollingWorker.KEY_DISTRICT to district
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RiskPollingWorker>(15, TimeUnit.MINUTES)
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_PREFIX + regionName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelRegion(context: Context, regionName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + regionName)
    }
}
