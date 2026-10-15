package com.cva.duoscreen.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cva.duoscreen.R
import com.cva.duoscreen.core.ShizukuShell
import com.cva.duoscreen.model.AppInfo
import com.cva.duoscreen.service.DuoAccessibilityService
import com.cva.duoscreen.service.DuoNotificationService
import com.cva.duoscreen.source.SpeedAlert
import com.cva.duoscreen.source.SpeedAlertRepository
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHIZUKU_REQ_CODE = 1001
        private const val LOCATION_REQ_CODE = 1002
    }

    private lateinit var settingsStore: SettingsStore

    // Status UI Views
    private lateinit var dotShizukuStatus: View
    private lateinit var tvShizukuDetail: TextView
    private lateinit var btnGrantShizuku: Button

    private lateinit var dotNotificationStatus: View
    private lateinit var tvNotificationDetail: TextView
    private lateinit var btnGrantNotification: Button

    private lateinit var dotAccessibilityStatus: View
    private lateinit var tvAccessibilityDetail: TextView
    private lateinit var btnGrantAccessibility: Button

    private lateinit var dotLocationStatus: View
    private lateinit var tvLocationDetail: TextView
    private lateinit var btnGrantLocation: Button

    // Live Read-out Views
    private lateinit var tvLiveCurrentSpeed: TextView
    private lateinit var tvLiveSpeedLimit: TextView
    private lateinit var tvLiveAlertText: TextView

    // App Picker Views
    private lateinit var ivMapAppIcon: ImageView
    private lateinit var tvMapAppName: TextView
    private lateinit var btnPickMapApp: Button

    private lateinit var ivVietmapAppIcon: ImageView
    private lateinit var tvVietmapAppName: TextView
    private lateinit var btnPickVietmapApp: Button

    private lateinit var ivMusicAppIcon: ImageView
    private lateinit var tvMusicAppName: TextView
    private lateinit var btnPickMusicApp: Button

    // Android Box & Hotspot Views
    private lateinit var etHotspotSsid: EditText
    private lateinit var etHotspotPassword: EditText
    private lateinit var swAggressiveHotspot: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var swBoxAutoUnlock: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var swSuppressWarnings: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var tvHotspotStatusText: TextView
    private lateinit var btnSaveAndConnectHotspot: Button
    private lateinit var btnOneClickBoxSetup: Button

    private val boxStatusListener: (String) -> Unit = { status ->
        runOnUiThread {
            tvHotspotStatusText.text = status
        }
    }
    private lateinit var btnCheckConnection: Button

    // Installed apps cache
    private var installedApps: List<AppInfo> = emptyList()

    // Shizuku listeners
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { updateAllStatuses() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { updateAllStatuses() }
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQ_CODE) {
                runOnUiThread {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Shizuku đã được cấp quyền!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Shizuku bị từ chối cấp quyền!", Toast.LENGTH_SHORT).show()
                    }
                    updateAllStatuses()
                }
            }
        }

    // Speed alert listener
    private val speedAlertListener: (SpeedAlert) -> Unit = { alert ->
        runOnUiThread {
            updateSpeedAlertDisplay(alert)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsStore = SettingsStore.get(this)

        initViews()
        setupListeners()
        loadInstalledAppsAsync()

        // Register Shizuku listeners
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        // Start persistent vehicle box service
        com.cva.duoscreen.box.VehicleBoxService.start(this)
        com.cva.duoscreen.box.VehicleBoxManager.addStatusListener(boxStatusListener)
        com.cva.duoscreen.box.VehicleBoxManager.checkCurrentWifiState(this)
    }

    override fun onStart() {
        super.onStart()
        SpeedAlertRepository.start(this)
        SpeedAlertRepository.addListener(speedAlertListener)
    }

    override fun onResume() {
        super.onResume()
        updateAllStatuses()
        updateConfiguredAppDisplays()
    }

    override fun onStop() {
        super.onStop()
        SpeedAlertRepository.removeListener(speedAlertListener)
        // Keep repository running in background if needed by services, or stop if phone UI is solely responsible
        SpeedAlertRepository.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        com.cva.duoscreen.box.VehicleBoxManager.removeStatusListener(boxStatusListener)
    }

    private fun initViews() {
        // Status
        dotShizukuStatus = findViewById(R.id.dotShizukuStatus)
        tvShizukuDetail = findViewById(R.id.tvShizukuDetail)
        btnGrantShizuku = findViewById(R.id.btnGrantShizuku)

        dotNotificationStatus = findViewById(R.id.dotNotificationStatus)
        tvNotificationDetail = findViewById(R.id.tvNotificationDetail)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)

        dotAccessibilityStatus = findViewById(R.id.dotAccessibilityStatus)
        tvAccessibilityDetail = findViewById(R.id.tvAccessibilityDetail)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)

        dotLocationStatus = findViewById(R.id.dotLocationStatus)
        tvLocationDetail = findViewById(R.id.tvLocationDetail)
        btnGrantLocation = findViewById(R.id.btnGrantLocation)

        // Live Read-out
        tvLiveCurrentSpeed = findViewById(R.id.tvLiveCurrentSpeed)
        tvLiveSpeedLimit = findViewById(R.id.tvLiveSpeedLimit)
        tvLiveAlertText = findViewById(R.id.tvLiveAlertText)

        // App Pickers
        ivMapAppIcon = findViewById(R.id.ivMapAppIcon)
        tvMapAppName = findViewById(R.id.tvMapAppName)
        btnPickMapApp = findViewById(R.id.btnPickMapApp)

        ivVietmapAppIcon = findViewById(R.id.ivVietmapAppIcon)
        tvVietmapAppName = findViewById(R.id.tvVietmapAppName)
        btnPickVietmapApp = findViewById(R.id.btnPickVietmapApp)

        ivMusicAppIcon = findViewById(R.id.ivMusicAppIcon)
        tvMusicAppName = findViewById(R.id.tvMusicAppName)
        btnPickMusicApp = findViewById(R.id.btnPickMusicApp)


        // Android Box & Hotspot Views
        etHotspotSsid = findViewById(R.id.etHotspotSsid)
        etHotspotPassword = findViewById(R.id.etHotspotPassword)
        swAggressiveHotspot = findViewById(R.id.swAggressiveHotspot)
        swBoxAutoUnlock = findViewById(R.id.swBoxAutoUnlock)
        swSuppressWarnings = findViewById(R.id.swSuppressWarnings)
        tvHotspotStatusText = findViewById(R.id.tvHotspotStatusText)
        btnSaveAndConnectHotspot = findViewById(R.id.btnSaveAndConnectHotspot)
        btnOneClickBoxSetup = findViewById(R.id.btnOneClickBoxSetup)

        // Init values from SettingsStore
        etHotspotSsid.setText(settingsStore.hotspotSsid)
        etHotspotPassword.setText(settingsStore.hotspotPassword)
        swAggressiveHotspot.isChecked = settingsStore.isAggressiveHotspotEnabled
        swBoxAutoUnlock.isChecked = settingsStore.isBoxAutoUnlockEnabled
        swSuppressWarnings.isChecked = settingsStore.isSuppressWarningsEnabled
        btnCheckConnection = findViewById(R.id.btnCheckConnection)
    }

    private fun setupListeners() {
        btnGrantShizuku.setOnClickListener {
            handleShizukuAction()
        }

        btnGrantNotification.setOnClickListener {
            DuoNotificationService.openSettings(this)
        }

        btnGrantAccessibility.setOnClickListener {
            handleAccessibilityAction()
        }

        btnGrantLocation.setOnClickListener {
            handleLocationAction()
        }

        btnPickMapApp.setOnClickListener {
            openAppPickerDialog(
                title = getString(R.string.select_map_app),
                currentPackage = settingsStore.mapPackage
            ) { selected ->
                settingsStore.mapPackage = selected.packageName
                updateConfiguredAppDisplays()
            }
        }

        btnPickVietmapApp.setOnClickListener {
            openAppPickerDialog(
                title = getString(R.string.select_vietmap_app),
                currentPackage = settingsStore.vietmapPackage
            ) { selected ->
                settingsStore.vietmapPackage = selected.packageName
                updateConfiguredAppDisplays()
            }
        }

        btnPickMusicApp.setOnClickListener {
            openAppPickerDialog(
                title = getString(R.string.select_music_app),
                currentPackage = settingsStore.musicPackage
            ) { selected ->
                settingsStore.musicPackage = selected.packageName
                updateConfiguredAppDisplays()
            }
        }

        swAggressiveHotspot.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.isAggressiveHotspotEnabled = isChecked
        }

        swBoxAutoUnlock.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.isBoxAutoUnlockEnabled = isChecked
        }

        swSuppressWarnings.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.isSuppressWarningsEnabled = isChecked
        }

        btnSaveAndConnectHotspot.setOnClickListener {
            val ssid = etHotspotSsid.text.toString().trim()
            val pass = etHotspotPassword.text.toString().trim()
            if (ssid.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên Hotspot (SSID)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            settingsStore.hotspotSsid = ssid
            settingsStore.hotspotPassword = pass

            Toast.makeText(this, "Đang nạp cấu hình Hotspot & ép kết nối tới '$ssid'...", Toast.LENGTH_SHORT).show()
            Thread {
                com.cva.duoscreen.box.VehicleBoxManager.injectHotspotToSystem(this, ssid, pass)
                com.cva.duoscreen.box.VehicleBoxManager.forceReconnect(this, ssid)
            }.start()
        }

        btnOneClickBoxSetup.setOnClickListener {
            Toast.makeText(this, "⚡ Đang tối ưu hóa toàn bộ hệ thống cho xe...", Toast.LENGTH_SHORT).show()
            com.cva.duoscreen.box.VehicleBoxManager.applySystemBoxTweaks(this) { success ->
                if (success) {
                    Toast.makeText(this, "✅ Hệ thống đã sẵn sàng làm Android Box!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "⚠️ Đã áp dụng tối ưu (Vui lòng cấp quyền Shizuku nếu cần)", Toast.LENGTH_LONG).show()
                }
                updateAllStatuses()
            }
            if (DuoAccessibilityService.isAccessibilityEnabled(this)) {
                // already on
            } else {
                DuoAccessibilityService.enableViaShizuku(this) {
                    updateAllStatuses()
                }
            }
        }

        btnCheckConnection.setOnClickListener {
            updateAllStatuses()
            val shizukuOk = ShizukuShell.isShizukuAvailable() && ShizukuShell.hasPermission()
            val notifOk = DuoNotificationService.isPermissionGranted(this)
            val accOk = DuoAccessibilityService.isRunning() || DuoAccessibilityService.isAccessibilityEnabled(this)
            val locOk = isLocationPermissionGranted()

            if (shizukuOk && notifOk && accOk && locOk) {
                Toast.makeText(this, "✅ Mọi quyền & kết nối đã sẵn sàng cho Android Auto!", Toast.LENGTH_LONG).show()
            } else {
                val missing = mutableListOf<String>()
                if (!shizukuOk) missing.add("Shizuku")
                if (!notifOk) missing.add("Quyền thông báo")
                if (!accOk) missing.add("Trợ năng")
                if (!locOk) missing.add("Vị trí GPS")
                Toast.makeText(this, "⚠️ Cần hoàn tất: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateAllStatuses() {
        // 1. Shizuku
        val isShizukuAvail = ShizukuShell.isShizukuAvailable()
        val hasShizukuPerm = isShizukuAvail && ShizukuShell.hasPermission()
        if (hasShizukuPerm) {
            setDotState(dotShizukuStatus, true)
            tvShizukuDetail.text = "Đã kết nối ADB & cấp quyền hoạt động"
            btnGrantShizuku.visibility = View.GONE
        } else if (isShizukuAvail) {
            setDotState(dotShizukuStatus, false)
            tvShizukuDetail.text = "Shizuku đang chạy nhưng chưa cấp quyền"
            btnGrantShizuku.text = "Cấp quyền"
            btnGrantShizuku.visibility = View.VISIBLE
        } else {
            setDotState(dotShizukuStatus, false)
            tvShizukuDetail.text = "Shizuku service chưa chạy hoặc chưa cài đặt"
            btnGrantShizuku.text = "Mở Shizuku"
            btnGrantShizuku.visibility = View.VISIBLE
        }

        // 2. Notification Listener
        val notifGranted = DuoNotificationService.isPermissionGranted(this)
        if (notifGranted) {
            setDotState(dotNotificationStatus, true)
            tvNotificationDetail.text = "Đã cấp quyền đọc Media & thông báo"
            btnGrantNotification.visibility = View.GONE
        } else {
            setDotState(dotNotificationStatus, false)
            tvNotificationDetail.text = "Chưa cấp quyền truy cập thông báo"
            btnGrantNotification.visibility = View.VISIBLE
        }

        // 3. Accessibility Service
        val accRunning = DuoAccessibilityService.isRunning()
        val accEnabled = DuoAccessibilityService.isAccessibilityEnabled(this)
        if (accRunning || accEnabled) {
            setDotState(dotAccessibilityStatus, true)
            tvAccessibilityDetail.text = if (accRunning) "Dịch vụ đang chạy sẵn sàng" else "Đã bật trong cài đặt"
            btnGrantAccessibility.visibility = View.GONE
        } else {
            setDotState(dotAccessibilityStatus, false)
            tvAccessibilityDetail.text = "Chưa bật dịch vụ trợ năng đọc cảnh báo"
            btnGrantAccessibility.visibility = View.VISIBLE
        }

        // 4. Location Permission
        val locGranted = isLocationPermissionGranted()
        if (locGranted) {
            setDotState(dotLocationStatus, true)
            tvLocationDetail.text = "Đã cấp quyền vị trí chính xác (GPS)"
            btnGrantLocation.visibility = View.GONE
        } else {
            setDotState(dotLocationStatus, false)
            tvLocationDetail.text = "Chưa cấp quyền GPS đo tốc độ"
            btnGrantLocation.visibility = View.VISIBLE
        }
    }

    private fun setDotState(dot: View, active: Boolean) {
        val color = if (active) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        dot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleShizukuAction() {
        if (!ShizukuShell.isShizukuAvailable()) {
            val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Vui lòng cài đặt ứng dụng Shizuku từ Play Store!", Toast.LENGTH_LONG).show()
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(marketIntent)
                } catch (e: Exception) {
                    // ignore
                }
            }
            return
        }

        if (!ShizukuShell.hasPermission()) {
            try {
                ShizukuShell.requestPermission(SHIZUKU_REQ_CODE)
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi yêu cầu quyền Shizuku: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleAccessibilityAction() {
        if (ShizukuShell.isShizukuAvailable() && ShizukuShell.hasPermission()) {
            Toast.makeText(this, "Đang kích hoạt trợ năng qua Shizuku...", Toast.LENGTH_SHORT).show()
            DuoAccessibilityService.enableViaShizuku(this) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "✅ Đã bật Trợ năng thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Không thể tự động bật trợ năng, đang mở Cài đặt...", Toast.LENGTH_SHORT).show()
                        openAccessibilitySettings()
                    }
                    updateAllStatuses()
                }
            }
        } else {
            openAccessibilitySettings()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun handleLocationAction() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_REQ_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQ_CODE) {
            updateAllStatuses()
        }
    }

    private fun updateSpeedAlertDisplay(alert: SpeedAlert) {
        tvLiveCurrentSpeed.text = alert.speedKmh.toString()
        if (alert.isOverLimit) {
            tvLiveCurrentSpeed.setTextColor(Color.parseColor("#EF4444"))
        } else {
            tvLiveCurrentSpeed.setTextColor(Color.parseColor("#22C55E"))
        }

        val limitStr = alert.speedLimit?.let { if (it > 0) it.toString() else "--" } ?: "--"
        tvLiveSpeedLimit.text = limitStr

        val alertMsg = if (alert.alertText.isNotBlank()) alert.alertText else "Chưa có cảnh báo biển báo"
        tvLiveAlertText.text = alertMsg
    }

    private fun updateConfiguredAppDisplays() {
        // Map App
        val mapPkg = settingsStore.mapPackage
        val mapAppInfo = getAppInfoForPackage(mapPkg)
        tvMapAppName.text = "${mapAppInfo.name}\n($mapPkg)"
        if (mapAppInfo.icon != null) {
            ivMapAppIcon.setImageDrawable(mapAppInfo.icon)
        } else {
            ivMapAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // Vietmap App
        val vietmapPkg = settingsStore.vietmapPackage
        val vietmapAppInfo = getAppInfoForPackage(vietmapPkg)
        tvVietmapAppName.text = "${vietmapAppInfo.name}\n($vietmapPkg)"
        if (vietmapAppInfo.icon != null) {
            ivVietmapAppIcon.setImageDrawable(vietmapAppInfo.icon)
        } else {
            ivVietmapAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // Music App
        val musicPkg = settingsStore.musicPackage
        val musicAppInfo = getAppInfoForPackage(musicPkg)
        tvMusicAppName.text = "${musicAppInfo.name}\n($musicPkg)"
        if (musicAppInfo.icon != null) {
            ivMusicAppIcon.setImageDrawable(musicAppInfo.icon)
        } else {
            ivMusicAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
    }

    private fun getAppInfoForPackage(packageName: String): AppInfo {
        return try {
            val pm = packageManager
            val app = pm.getApplicationInfo(packageName, 0)
            AppInfo(
                name = pm.getApplicationLabel(app).toString(),
                packageName = packageName,
                icon = pm.getApplicationIcon(app)
            )
        } catch (e: Exception) {
            AppInfo(
                name = packageName,
                packageName = packageName,
                icon = null
            )
        }
    }

    private fun loadInstalledAppsAsync() {
        Thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<AppInfo>()
            for (app in apps) {
                // Filter out non-launchable system packages
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent == null && isSystem) continue

                val label = pm.getApplicationLabel(app).toString()
                val icon = try {
                    pm.getApplicationIcon(app)
                } catch (e: Exception) {
                    null
                }
                list.add(AppInfo(name = label, packageName = app.packageName, icon = icon))
            }
            list.sortBy { it.name.lowercase(Locale.getDefault()) }

            runOnUiThread {
                installedApps = list
                updateConfiguredAppDisplays()
            }
        }.start()
    }

    private fun openAppPickerDialog(
        title: String,
        currentPackage: String,
        onSelected: (AppInfo) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPickerTitle)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchApp)
        val rvList = dialogView.findViewById<RecyclerView>(R.id.rvPickerAppList)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClosePicker)

        tvTitle.text = title

        var filteredList = installedApps
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val adapter = AppListAdapter(filteredList) { appInfo ->
            onSelected(appInfo)
            dialog.dismiss()
        }

        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                filteredList = if (q.isEmpty()) {
                    installedApps
                } else {
                    installedApps.filter {
                        it.name.lowercase(Locale.getDefault()).contains(q) ||
                                it.packageName.lowercase(Locale.getDefault()).contains(q)
                    }
                }
                adapter.updateData(filteredList)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
