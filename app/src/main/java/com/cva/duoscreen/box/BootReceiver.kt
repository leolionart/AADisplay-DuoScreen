package com.cva.duoscreen.box

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cva.duoscreen.ui.SettingsStore

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        Log.i(TAG, "Nhận sự kiện hệ thống: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_POWER_CONNECTED -> {
                val store = SettingsStore.get(context)
                if (store.isBoxAutoUnlockEnabled || store.isAggressiveHotspotEnabled) {
                    Log.i(TAG, "Tự động kích hoạt VehicleBoxService sau khởi động...")
                    VehicleBoxService.start(context)
                }
            }
        }
    }
}
