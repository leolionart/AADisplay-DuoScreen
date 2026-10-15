package com.cva.duoscreen.box

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cva.duoscreen.R
import com.cva.duoscreen.ui.MainActivity
import com.cva.duoscreen.ui.SettingsStore

class VehicleBoxService : Service() {

    companion object {
        private const val TAG = "VehicleBoxService"
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "vehicle_box_service_channel"

        fun start(context: Context) {
            val intent = Intent(context, VehicleBoxService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VehicleBoxService::class.java)
            context.stopService(intent)
        }
    }

    private val powerAndWifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            val action = intent.action ?: return

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.i(TAG, "Đã cắm nguồn xe (POWER_CONNECTED), đánh thức thiết bị và kích hoạt kết nối...")
                    VehicleBoxManager.wakeAndDismissKeyguard()
                    val store = SettingsStore.get(context)
                    if (store.isAggressiveHotspotEnabled && store.hotspotSsid.isNotBlank()) {
                        VehicleBoxManager.forceReconnect(context, store.hotspotSsid)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    val store = SettingsStore.get(context)
                    if (store.isBoxAutoUnlockEnabled) {
                        Log.i(TAG, "Màn hình tắt khi trên xe, đánh thức lại...")
                        VehicleBoxManager.wakeAndDismissKeyguard()
                    }
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    VehicleBoxManager.checkCurrentWifiState(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VehicleBoxService khởi động...")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chạy ngầm duy trì kết nối Hotspot & Tự động mở khóa"))

        // Đăng ký nhận sự kiện cắm sạc xe và Wi-Fi
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        registerReceiver(powerAndWifiReceiver, filter)

        // Khởi động watchdog kết nối Hotspot
        VehicleBoxManager.startWatchdog(this)

        // Tối ưu hóa hệ thống nếu được bật
        val store = SettingsStore.get(this)
        if (store.isBoxAutoUnlockEnabled) {
            VehicleBoxManager.applySystemBoxTweaks(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        VehicleBoxManager.checkCurrentWifiState(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VehicleBoxService hủy...")
        try {
            unregisterReceiver(powerAndWifiReceiver)
        } catch (e: Exception) {
            // ignore
        }
        VehicleBoxManager.stopWatchdog()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DuoScreen AA - Dịch vụ Chế độ Xe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối Hotspot và tối ưu hóa Android Box trên xe"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DuoScreen AA - Chế độ Android Box")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_car_app)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
