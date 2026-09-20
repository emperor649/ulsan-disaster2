package com.ulsan.disasteralert

import android.app.Application
import com.ulsan.disasteralert.notification.NotificationHelper

class DisasterAlertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
