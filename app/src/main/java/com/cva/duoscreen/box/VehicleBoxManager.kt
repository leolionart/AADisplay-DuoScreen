package com.cva.duoscreen.box

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cva.duoscreen.core.ShizukuShell
import com.cva.duoscreen.ui.SettingsStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object VehicleBoxManager {
    private const val TAG = "VehicleBoxManager"
    private val executor = Executors.newCachedThreadPool()
    private var watchdogThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isWatchdogRunning = AtomicBoolean(false)
    private var lastScanTime = 0L
    private var lastReconnectAttemptTime = 0L

    var currentStatusText: String = "Sẵn sàng"
        private set

    private val statusListeners = mutableListOf<(String) -> Unit>()

    fun addStatusListener(listener: (String) -> Unit) {
        synchronized(statusListeners) {
            statusListeners.add(listener)
            listener(currentStatusText)
        }
    }

    fun removeStatusListener(listener: (String) -> Unit) {
        synchronized(statusListeners) {
            statusListeners.remove(listener)
        }
    }

    private fun updateStatus(text: String) {
        currentStatusText = text
        mainHandler.post {
            synchronized(statusListeners) {
                statusListeners.forEach { it(text) }
            }
        }
    }

    /**
     * Tối ưu hóa toàn bộ hệ thống VinSmart để hoạt động như một Android Box chuyên dụng:
     * - Luôn sáng màn hình khi cắm sạc/USB xe (stay_on_while_plugged_in = 7)
     * - Tắt giới hạn quét Wi-Fi (wifi_scan_throttle_enabled = 0)
     * - Luôn cho phép quét Wi-Fi nền (wifi_scan_always_enabled = 1)
     * - Wi-Fi không bao giờ ngủ (wifi_sleep_policy = 2)
     * - Tự động bật Wi-Fi khi gần mạng quen (wifi_wakeup_enabled = 1)
     * - Vô hiệu hóa màn hình khóa (lockscreen.disabled = 1, locksettings set-disabled true)
     * - Chặn triệt để kênh thông báo cảnh báo ngưng hỗ trợ Android Auto (gearhead_alerts = 0)
     * - Tự động mở khóa và dismiss keyguard ngay lập tức
     */
    fun applySystemBoxTweaks(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        executor.execute {
            Log.i(TAG, "Bắt đầu tối ưu hóa hệ thống xe (Chế độ Android Box)...")
            updateStatus("Đang thiết lập hệ thống Android Box...")

            val commands = listOf(
                "settings put global stay_on_while_plugged_in 7",
                "settings put global wifi_scan_throttle_enabled 0",
                "settings put global wifi_scan_always_enabled 1",
                "settings put global wifi_sleep_policy 2",
                "settings put global wifi_wakeup_enabled 1",
                "settings put secure lockscreen.disabled 1",
                "settings put system screen_off_timeout 2147483647",
                "locksettings set-disabled true",
                "wm dismiss-keyguard",
                "input keyevent 224",
                "cmd notification set_importance com.google.android.projection.gearhead gearhead_alerts 0",
                "setprop persist.sys.usb.config mtp,adb"
            )

            var allSuccess = true
            for (cmd in commands) {
                val res = ShizukuShell.executeShell(cmd)
                if (res.startsWith("Error")) {
                    Log.w(TAG, "Lệnh thất bại: $cmd -> $res")
                    allSuccess = false
                }
            }

            // Ghi nhận cấu hình Hotspot hiện tại vào hệ thống
            val store = SettingsStore.get(context)
            if (store.hotspotSsid.isNotBlank()) {
                injectHotspotToSystem(context, store.hotspotSsid, store.hotspotPassword)
            }

            updateStatus(if (allSuccess) "✅ Đã tối ưu hóa Android Box thành công!" else "⚠️ Đã áp dụng tối ưu (Một số quyền cần kiểm tra)")
            mainHandler.post { onComplete?.invoke(allSuccess) }
        }
    }

    /**
     * Mở khóa màn hình & dismiss keyguard chủ động
     */
    fun wakeAndDismissKeyguard() {
        executor.execute {
            try {
                ShizukuShell.executeShell("input keyevent 224") // KEYCODE_WAKEUP
                ShizukuShell.executeShell("wm dismiss-keyguard")
                ShizukuShell.executeShell("input keyevent 82")  // KEYCODE_MENU (unlock swipe)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi wakeAndDismissKeyguard: ${e.message}")
            }
        }
    }

    /**
     * Nạp cấu hình Hotspot vào tệp hệ thống WifiConfigStore.xml để Android vĩnh viễn nhớ và tự động bắt mạng
     */
    fun injectHotspotToSystem(context: Context, ssid: String, pass: String): Boolean {
        if (ssid.isBlank()) return false
        return try {
            val storeXml = ShizukuShell.executeShell("cat /data/misc/wifi/WifiConfigStore.xml")
            if (storeXml.contains("<string name=\"SSID\">&quot;$ssid&quot;</string>")) {
                // Đã có trong WifiConfigStore, kiểm tra mật khẩu
                if (pass.isNotBlank() && !storeXml.contains("<string name=\"PreSharedKey\">&quot;$pass&quot;</string>")) {
                    Log.i(TAG, "Hotspot $ssid đã tồn tại nhưng mật khẩu thay đổi, đang cập nhật...")
                } else {
                    Log.i(TAG, "Hotspot $ssid đã có sẵn trong cấu hình hệ thống.")
                    applyWifiSuggestionsFallback(context, ssid, pass)
                    return true
                }
            }

            val hasPass = pass.isNotBlank() && pass.length >= 8
            val keyMgmt = if (hasPass) "02" else "01"
            val configKey = if (hasPass) "&quot;${ssid}&quot;WPA_PSK" else "&quot;${ssid}&quot;NONE"
            val pskTag = if (hasPass) "<string name=\"PreSharedKey\">&quot;${pass}&quot;</string>" else "<null name=\"PreSharedKey\" />"

            val networkBlock = """
<Network>
<WifiConfiguration>
<string name="ConfigKey">$configKey</string>
<string name="SSID">&quot;$ssid&quot;</string>
<null name="BSSID" />
$pskTag
<null name="WEPKeys" />
<int name="WEPTxKeyIndex" value="0" />
<boolean name="HiddenSSID" value="false" />
<boolean name="RequirePMF" value="false" />
<byte-array name="AllowedKeyMgmt" num="1">$keyMgmt</byte-array>
<byte-array name="AllowedProtocols" num="1">03</byte-array>
<byte-array name="AllowedAuthAlgos" num="1">01</byte-array>
<byte-array name="AllowedGroupCiphers" num="1">0f</byte-array>
<byte-array name="AllowedPairwiseCiphers" num="1">06</byte-array>
<byte-array name="AllowedGroupMgmtCiphers" num="0"></byte-array>
<byte-array name="AllowedSuiteBCiphers" num="0"></byte-array>
<boolean name="Shared" value="true" />
<int name="WapiCertSelMode" value="0" />
<null name="WapiCertSel" />
<int name="WapiPskType" value="0" />
<null name="WapiPsk" />
<int name="Status" value="2" />
<null name="FQDN" />
<null name="ProviderFriendlyName" />
<null name="LinkedNetworksList" />
<null name="DefaultGwMacAddress" />
<boolean name="ValidatedInternetAccess" value="true" />
<boolean name="NoInternetAccessExpected" value="false" />
<int name="UserApproved" value="0" />
<boolean name="MeteredHint" value="false" />
<int name="MeteredOverride" value="0" />
<boolean name="UseExternalScores" value="false" />
<int name="NumAssociation" value="1" />
<int name="CreatorUid" value="1000" />
<string name="CreatorName">android.uid.system:1000</string>
<string name="CreationTime">time=10-11 18:00:00.000</string>
<int name="LastUpdateUid" value="1000" />
<string name="LastUpdateName">android.uid.system:1000</string>
<int name="LastConnectUid" value="1000" />
<boolean name="IsLegacyPasspointConfig" value="false" />
<long-array name="RoamingConsortiumOIs" num="0" />
<string name="RandomizedMacAddress">00:00:00:00:00:00</string>
<int name="MacRandomizationSetting" value="0" />
</WifiConfiguration>
<NetworkStatus>
<string name="SelectionStatus">NETWORK_SELECTION_ENABLED</string>
<string name="DisableReason">NETWORK_SELECTION_ENABLE</string>
<null name="ConnectChoice" />
<long name="ConnectChoiceTimeStamp" value="-1" />
<boolean name="HasEverConnected" value="true" />
</NetworkStatus>
<IpConfiguration>
<string name="IpAssignment">DHCP</string>
<string name="ProxySettings">NONE</string>
</IpConfiguration>
</Network>
""".trimIndent()

            if (storeXml.contains("</NetworkList>")) {
                val updatedXml = storeXml.replace("</NetworkList>", "$networkBlock\n</NetworkList>")
                // Ghi ra file tạm rồi ghi đè vào WifiConfigStore.xml
                val escapedXml = updatedXml.replace("'", "'\\''")
                ShizukuShell.executeShell("echo '$escapedXml' > /data/local/tmp/WifiConfigStore.xml")
                ShizukuShell.executeShell("cat /data/local/tmp/WifiConfigStore.xml > /data/misc/wifi/WifiConfigStore.xml")
                ShizukuShell.executeShell("chmod 600 /data/misc/wifi/WifiConfigStore.xml")
                ShizukuShell.executeShell("chown system:system /data/misc/wifi/WifiConfigStore.xml")
                ShizukuShell.executeShell("restorecon /data/misc/wifi/WifiConfigStore.xml")
                Log.i(TAG, "Đã ghi cấu hình Hotspot $ssid vào WifiConfigStore.xml!")

                // Nạp lại cấu hình Wi-Fi
                ShizukuShell.executeShell("svc wifi disable")
                Thread.sleep(1000)
                ShizukuShell.executeShell("svc wifi enable")
            }

            applyWifiSuggestionsFallback(context, ssid, pass)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi injectHotspotToSystem: ${e.message}", e)
            false
        }
    }

    private fun applyWifiSuggestionsFallback(context: Context, ssid: String, pass: String) {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val builder = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setIsAppInteractionRequired(false)
                    .setPriority(100)

                if (pass.isNotBlank() && pass.length >= 8) {
                    builder.setWpa2Passphrase(pass)
                }

                wm.addNetworkSuggestions(listOf(builder.build()))
                ShizukuShell.executeShell("cmd wifi network-suggestions-set-user-approved com.cva.duoscreen yes")
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyWifiSuggestionsFallback failed: ${e.message}")
        }
    }

    /**
     * Bắt buộc kết nối lại tới Hotspot mục tiêu ngay lập tức
     */
    fun forceReconnect(context: Context, targetSsid: String) {
        executor.execute {
            try {
                updateStatus("Đang ép kết nối tới '$targetSsid'...")
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                if (!wm.isWifiEnabled) {
                    wm.isWifiEnabled = true
                    ShizukuShell.executeShell("svc wifi enable")
                    Thread.sleep(1000)
                }

                // Đánh thức màn hình và xóa keyguard để Wi-Fi không bị sleep
                wakeAndDismissKeyguard()

                // Tìm trong configuredNetworks nếu có
                var foundNetId = -1
                try {
                    val networks = wm.configuredNetworks
                    if (networks != null) {
                        for (net in networks) {
                            val cleanSsid = net.SSID?.trim('\"') ?: ""
                            if (cleanSsid.equals(targetSsid, ignoreCase = true)) {
                                foundNetId = net.networkId
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // API 29+ might restrict configuredNetworks
                }

                if (foundNetId != -1) {
                    Log.i(TAG, "Tìm thấy mạng $targetSsid (ID: $foundNetId), kích hoạt kết nối...")
                    wm.disconnect()
                    wm.enableNetwork(foundNetId, true)
                    wm.reconnect()
                } else {
                    Log.i(TAG, "Gọi reconnect() và quét lại...")
                    wm.startScan()
                    wm.reconnect()
                }

                Thread.sleep(2000)
                checkCurrentWifiState(context)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi forceReconnect: ${e.message}", e)
            }
        }
    }

    /**
     * Khởi động Watchdog quét tìm và duy trì kết nối Hotspot liên tục
     */
    fun startWatchdog(context: Context) {
        if (isWatchdogRunning.getAndSet(true)) return

        watchdogThread = Thread({
            Log.i(TAG, "Khởi động Hotspot Reconnect Watchdog...")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            while (isWatchdogRunning.get()) {
                try {
                    val store = SettingsStore.get(context)
                    if (!store.isAggressiveHotspotEnabled) {
                        Thread.sleep(10000)
                        continue
                    }

                    val targetSsid = store.hotspotSsid
                    if (targetSsid.isBlank()) {
                        Thread.sleep(5000)
                        continue
                    }

                    // 1. Kiểm tra trạng thái Wi-Fi hiện tại
                    val wifiInfo = wm.connectionInfo
                    val currentSsid = wifiInfo?.ssid?.trim('\"') ?: ""
                    val isConnectedToTarget = currentSsid.equals(targetSsid, ignoreCase = true)
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val activeNet = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(activeNet)
                    val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                    if (isConnectedToTarget && hasInternet) {
                        val ip = wifiInfo?.ipAddress ?: 0
                        val ipStr = String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
                        updateStatus("🟢 Đã kết nối '$targetSsid' (IP: $ipStr, Sóng: ${wifiInfo.rssi} dBm)")
                        Thread.sleep(8000)
                        continue
                    }

                    if (isConnectedToTarget && !hasInternet) {
                        updateStatus("🟡 Đã kết nối '$targetSsid' (Đang đợi cấp Internet / 4G)...")
                        Thread.sleep(4000)
                        continue
                    }

                    // 2. Chưa kết nối tới Hotspot mục tiêu -> Bắt đầu quy trình quét & kết nối quyết liệt
                    if (!wm.isWifiEnabled) {
                        updateStatus("Bật lại Wi-Fi thiết bị...")
                        wm.isWifiEnabled = true
                        ShizukuShell.executeShell("svc wifi enable")
                        Thread.sleep(1500)
                    }

                    // Đảm bảo không bị tắt màn hình / khóa khi trên xe
                    if (store.isBoxAutoUnlockEnabled) {
                        wakeAndDismissKeyguard()
                    }

                    // Quét sóng Wi-Fi xung quanh
                    val now = System.currentTimeMillis()
                    if (now - lastScanTime > 4000) {
                        lastScanTime = now
                        wm.startScan()
                    }

                    // Kiểm tra kết quả quét sóng
                    val scanResults = try { wm.scanResults } catch (e: Exception) { null }
                    val targetResult = scanResults?.firstOrNull { it.SSID.equals(targetSsid, ignoreCase = true) }

                    if (targetResult != null) {
                        updateStatus("⚡ Phát hiện Hotspot '$targetSsid' (${targetResult.level} dBm)! Đang kết nối ngay...")
                        Log.i(TAG, "Hotspot mục tiêu $targetSsid xuất hiện với cường độ ${targetResult.level} dBm. Bắt đầu ép kết nối!")

                        wm.reconnect()

                        // Nếu sau 12 giây vẫn chưa kết nối được dù sóng có sẵn, reset giao diện wlan để xóa cache
                        if (now - lastReconnectAttemptTime > 15000) {
                            lastReconnectAttemptTime = now
                            Log.i(TAG, "Đã phát hiện sóng nhưng chưa vào được, thực hiện bounce Wi-Fi...")
                            ShizukuShell.executeShell("svc wifi disable")
                            Thread.sleep(1000)
                            ShizukuShell.executeShell("svc wifi enable")
                        }
                    } else {
                        updateStatus("🔍 Đang quét tìm '$targetSsid' (kể cả khi bạn bật trễ sau khi lên xe)...")
                        // Định kỳ 15s gọi reconnect để bắt sóng sớm nhất khi máy bạn vừa phát hotspot
                        if (now - lastReconnectAttemptTime > 15000) {
                            lastReconnectAttemptTime = now
                            wm.reconnect()
                        }
                    }

                    Thread.sleep(4000)
                } catch (e: Throwable) {
                    Log.e(TAG, "Lỗi trong vòng lặp Watchdog: ${e.message}", e)
                    Thread.sleep(5000)
                }
            }
        }, "VehicleBoxWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stopWatchdog() {
        isWatchdogRunning.set(false)
        watchdogThread?.interrupt()
        watchdogThread = null
    }

    fun checkCurrentWifiState(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        val ssid = info?.ssid?.trim('\"') ?: ""
        val store = SettingsStore.get(context)
        val text = if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
            if (ssid.equals(store.hotspotSsid, ignoreCase = true)) {
                "🟢 Đã kết nối Hotspot xe: '$ssid' (${info.rssi} dBm)"
            } else {
                "🔵 Đang kết nối Wi-Fi khác: '$ssid' (Mục tiêu: '${store.hotspotSsid}')"
            }
        } else {
            "🔴 Chưa kết nối Wi-Fi (Đang tìm '${store.hotspotSsid}')..."
        }
        updateStatus(text)
        return text
    }
}
