package com.ulsan.disasteralert.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ulsan.disasteralert.util.RegionPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // 기기 재부팅 시 PeriodicWork가 유지되지 않을 수 있어 재등록
        RegionPrefs.getRegisteredRegions(context).forEach { region ->
            WorkScheduler.scheduleRegion(context, region.name, region.nx, region.ny, region.district)
        }
    }
}
