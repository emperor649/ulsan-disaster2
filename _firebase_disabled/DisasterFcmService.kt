package com.ulsan.disasteralert.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ulsan.disasteralert.data.*

/**
 * 서버 측에서 신규 위기경보/특보를 감지하면 FCM으로 즉시 push.
 * 폴링 주기(예: 10분)보다 빠르게 긴급 경보를 전달하기 위한 채널.
 *
 * 서버는 data payload로 아래 키를 담아 전송한다고 가정:
 *  region, riskLevel, riskScore, warningsJson, disasterAlertsJson, hourlyRainMm, updatedAt
 */
class DisasterFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val region = data["region"] ?: return

        // 실제 구현에서는 Gson으로 warningsJson/disasterAlertsJson을 파싱
        val status = RegionRiskStatus(
            regionName = region,
            riskScore = data["riskScore"]?.toIntOrNull() ?: 0,
            riskLevel = runCatching { RiskLevel.valueOf(data["riskLevel"] ?: "CAUTION") }
                .getOrDefault(RiskLevel.CAUTION),
            activeWarnings = emptyList(),   // TODO: warningsJson 파싱
            latestPrecipitation = null,     // TODO: hourlyRainMm 파싱
            activeDisasterAlerts = emptyList(), // TODO: disasterAlertsJson 파싱
            updatedAt = data["updatedAt"] ?: ""
        )

        NotificationHelper.notifyRiskStatus(applicationContext, status)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: 서버에 토큰 등록 (지역 구독 정보와 함께)
    }
}
