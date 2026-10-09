package com.cva.duoscreen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cva.duoscreen.adapter.AppListAdapter
import com.cva.duoscreen.car.DuoVirtualDisplayManager
import com.cva.duoscreen.manager.SpeedState
import com.cva.duoscreen.manager.SpeedometerManager
import com.cva.duoscreen.model.AppInfo
import com.cva.duoscreen.model.DisplayMode
import com.cva.duoscreen.service.DuoNotificationService
import com.cva.duoscreen.service.MediaTrackInfo
import com.cva.duoscreen.service.VietmapAlertInfo
import com.cva.duoscreen.shizuku.ShizukuHelper
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    enum class TargetSlot { TOP, BOTTOM_LEFT, BOTTOM_RIGHT }

    private lateinit var displayManager: DuoVirtualDisplayManager
    private lateinit var speedometerManager: SpeedometerManager
    private lateinit var prefs: SharedPreferences

    private var currentMode: DisplayMode = DisplayMode.MODE_LITE
    private var currentSlot: TargetSlot = TargetSlot.TOP

    private var topTexture: SurfaceTexture? = null
    private var bottomLeftTexture: SurfaceTexture? = null
    private var bottomRightTexture: SurfaceTexture? = null

    private lateinit var tvTopDisplay: TextureView
    private lateinit var tvBottomLeftDisplay: TextureView
    private lateinit var tvBottomRightDisplay: TextureView

    private lateinit var frameTop: View
    private lateinit var layoutBottomRow: View
    private lateinit var frameBottomLeft: View
    private lateinit var frameBottomRight: View

    private lateinit var dividerHorizontal: View
    private lateinit var dividerVertical: View

    private lateinit var btnFloatingTrigger: View
    private lateinit var panelControlOverlay: View

    private lateinit var btnSlotTop: Button
    private lateinit var btnSlotBottomLeft: Button
    private lateinit var btnSlotBottomRight: Button
    private lateinit var tvSlotLabel: TextView
    private lateinit var rvAppList: RecyclerView

    private lateinit var placeholderTop: View
    private lateinit var placeholderBottomLeft: View
    private lateinit var placeholderBottomRight: View

    private lateinit var tvPlaceholderTopStatus: TextView
    private lateinit var tvPlaceholderBottomLeftStatus: TextView
    private lateinit var tvPlaceholderBottomRightStatus: TextView

    private lateinit var layoutShizukuStatus: View
    private lateinit var viewShizukuDot: View
    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnGrantShizuku: Button

    private lateinit var swAutoStart: androidx.appcompat.widget.SwitchCompat
    private lateinit var btnMenuStartDisplays: Button
    private lateinit var btnMenuStopDisplays: Button
    private lateinit var btnPlaceholderStart: Button

    // Mode Selector Buttons
    private lateinit var btnModeLite: Button
    private lateinit var btnMode3Panel: Button

    // Mode Lite Native Widgets: Speed & Alerts
    private lateinit var widgetSpeedContainer: LinearLayout
    private lateinit var tvSpeedVal: TextView
    private lateinit var tvSpeedLimitVal: TextView
    private lateinit var layoutSpeedLimitSign: FrameLayout
    private lateinit var tvVietmapAlert: TextView
    private lateinit var btnQuickVietmap: TextView

    // Mode Lite Native Widgets: Media Player Controller
    private lateinit var widgetMusicContainer: LinearLayout
    private lateinit var ivMusicArt: ImageView
    private lateinit var tvMusicTitle: TextView
    private lateinit var tvMusicArtist: TextView
    private lateinit var btnGrantNotification: Button
    private lateinit var btnMusicPrev: ImageButton
    private lateinit var btnMusicPlayPause: ImageButton
    private lateinit var btnMusicNext: ImageButton

    private var isDisplaysRunning = false
    private var hasAutoCreated = false

    private val statusHandler = Handler(Looper.getMainLooper())
    private val hideStatusRunnable = Runnable {
        if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.hasPermission()) {
            layoutShizukuStatus.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    layoutShizukuStatus.visibility = View.GONE
                    layoutShizukuStatus.alpha = 1f
                }
                .start()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            checkAndRequestShizukuPermission()
            if (ShizukuHelper.hasPermission()) {
                tryAutoStartDisplays()
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            updateShizukuStatusUi()
        }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        runOnUiThread {
            updateShizukuStatusUi()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                tryAutoStartDisplays()
            }
        }
    }

    private val mediaListener: (MediaTrackInfo) -> Unit = { track ->
        runOnUiThread {
            tvMusicTitle.text = track.title
            tvMusicArtist.text = track.artist
            if (track.albumArt != null) {
                ivMusicArt.setImageBitmap(track.albumArt)
            } else {
                ivMusicArt.setImageResource(R.drawable.ic_music_note)
            }
            if (track.isPlaying) {
                btnMusicPlayPause.setImageResource(R.drawable.ic_pause)
            } else {
                btnMusicPlayPause.setImageResource(R.drawable.ic_play)
            }
        }
    }

    private val vietmapAlertListener: (VietmapAlertInfo) -> Unit = { alert ->
        runOnUiThread {
            speedometerManager.setSpeedLimit(alert.speedLimit, alert.alertMessage)
        }
    }

    private val speedStateListener: (SpeedState) -> Unit = { state ->
        runOnUiThread {
            tvSpeedVal.text = state.speedKmh.toString()
            if (state.speedLimit != null) {
                tvSpeedLimitVal.text = state.speedLimit.toString()
                layoutSpeedLimitSign.visibility = View.VISIBLE
            } else {
                tvSpeedLimitVal.text = "--"
            }

            if (state.isOverSpeed) {
                widgetSpeedContainer.setBackgroundResource(R.drawable.bg_cockpit_card_alert)
                tvSpeedVal.setTextColor(Color.parseColor("#E02424"))
            } else {
                widgetSpeedContainer.setBackgroundResource(R.drawable.bg_cockpit_card)
                tvSpeedVal.setTextColor(Color.parseColor("#ECEFF4"))
            }

            if (state.alertText.isNotBlank()) {
                tvVietmapAlert.text = state.alertText
            } else {
                tvVietmapAlert.text = if (state.hasGpsFix) "GPS sẵn sàng. Vietmap đang theo dõi..." else "Đang tìm tín hiệu GPS..."
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("duoscreen_prefs", Context.MODE_PRIVATE)

        // Make truly fullscreen immersive
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        displayManager = DuoVirtualDisplayManager(this)
        speedometerManager = SpeedometerManager(this)

        // Bind Views
        tvTopDisplay = findViewById(R.id.tvTopDisplay)
        tvBottomLeftDisplay = findViewById(R.id.tvBottomLeftDisplay)
        tvBottomRightDisplay = findViewById(R.id.tvBottomRightDisplay)

        frameTop = findViewById(R.id.frameTop)
        layoutBottomRow = findViewById(R.id.layoutBottomRow)
        frameBottomLeft = findViewById(R.id.frameBottomLeft)
        frameBottomRight = findViewById(R.id.frameBottomRight)

        placeholderTop = findViewById(R.id.placeholderTop)
        placeholderBottomLeft = findViewById(R.id.placeholderBottomLeft)
        placeholderBottomRight = findViewById(R.id.placeholderBottomRight)

        tvPlaceholderTopStatus = findViewById(R.id.tvPlaceholderTopStatus)
        tvPlaceholderBottomLeftStatus = findViewById(R.id.tvPlaceholderBottomLeftStatus)
        tvPlaceholderBottomRightStatus = findViewById(R.id.tvPlaceholderBottomRightStatus)

        layoutShizukuStatus = findViewById(R.id.layoutShizukuStatus)
        viewShizukuDot = findViewById(R.id.viewShizukuDot)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        btnGrantShizuku = findViewById(R.id.btnGrantShizuku)

        btnPlaceholderStart = findViewById(R.id.btnPlaceholderStart)
        swAutoStart = findViewById(R.id.swAutoStart)
        btnMenuStartDisplays = findViewById(R.id.btnMenuStartDisplays)
        btnMenuStopDisplays = findViewById(R.id.btnMenuStopDisplays)

        dividerHorizontal = findViewById(R.id.dividerHorizontal)
        dividerVertical = findViewById(R.id.dividerVertical)

        btnFloatingTrigger = findViewById(R.id.btnFloatingTrigger)
        panelControlOverlay = findViewById(R.id.panelControlOverlay)

        val btnClose = findViewById<Button>(R.id.btnCloseMenu)
        btnSlotTop = findViewById(R.id.btnSlotTop)
        btnSlotBottomLeft = findViewById(R.id.btnSlotBottomLeft)
        btnSlotBottomRight = findViewById(R.id.btnSlotBottomRight)
        tvSlotLabel = findViewById(R.id.tvSlotLabel)
        rvAppList = findViewById(R.id.rvAppList)

        // Bind Mode Buttons
        btnModeLite = findViewById(R.id.btnModeLite)
        btnMode3Panel = findViewById(R.id.btnMode3Panel)

        // Bind Speed Widget
        widgetSpeedContainer = findViewById(R.id.widgetSpeedContainer)
        tvSpeedVal = findViewById(R.id.tvSpeedVal)
        tvSpeedLimitVal = findViewById(R.id.tvSpeedLimitVal)
        layoutSpeedLimitSign = findViewById(R.id.layoutSpeedLimitSign)
        tvVietmapAlert = findViewById(R.id.tvVietmapAlert)
        btnQuickVietmap = findViewById(R.id.btnQuickVietmap)

        // Bind Music Widget
        widgetMusicContainer = findViewById(R.id.widgetMusicContainer)
        ivMusicArt = findViewById(R.id.ivMusicArt)
        tvMusicTitle = findViewById(R.id.tvMusicTitle)
        tvMusicArtist = findViewById(R.id.tvMusicArtist)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)
        btnMusicPrev = findViewById(R.id.btnMusicPrev)
        btnMusicPlayPause = findViewById(R.id.btnMusicPlayPause)
        btnMusicNext = findViewById(R.id.btnMusicNext)

        val btnPresetMaps = findViewById<Button>(R.id.btnPresetMaps)
        val btnPresetVietmap = findViewById<Button>(R.id.btnPresetVietmap)
        val btnPresetMusic = findViewById<Button>(R.id.btnPresetMusic)
        val btnPresetFermata = findViewById<Button>(R.id.btnPresetFermata)

        val autoStart = prefs.getBoolean("pref_auto_start", false)
        swAutoStart.isChecked = autoStart
        swAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_auto_start", isChecked).apply()
        }

        btnPlaceholderStart.setOnClickListener {
            startDisplays(force = true)
        }

        btnMenuStartDisplays.setOnClickListener {
            startDisplays(force = true)
            hideMenu()
        }

        btnMenuStopDisplays.setOnClickListener {
            stopDisplays()
            hideMenu()
        }

        layoutShizukuStatus.setOnClickListener {
            checkAndRequestShizukuPermission()
        }
        btnGrantShizuku.setOnClickListener {
            checkAndRequestShizukuPermission()
        }

        placeholderTop.setOnClickListener {
            startDisplays(force = true)
        }
        placeholderBottomLeft.setOnClickListener {
            startDisplays(force = true)
        }
        placeholderBottomRight.setOnClickListener {
            startDisplays(force = true)
        }

        // App List
        rvAppList.layoutManager = LinearLayoutManager(this)
        loadAllInstalledAppsAsync()

        // Slot Selectors
        selectSlot(TargetSlot.TOP)
        btnSlotTop.setOnClickListener { selectSlot(TargetSlot.TOP) }
        btnSlotBottomLeft.setOnClickListener { selectSlot(TargetSlot.BOTTOM_LEFT) }
        btnSlotBottomRight.setOnClickListener { selectSlot(TargetSlot.BOTTOM_RIGHT) }

        // Presets
        btnPresetMaps.setOnClickListener { assignAppToCurrentSlot("com.google.android.apps.maps", "Google Maps") }
        btnPresetVietmap.setOnClickListener { assignAppToCurrentSlot("vn.vietmap.live", "Vietmap Live") }
        btnPresetMusic.setOnClickListener { assignAppToCurrentSlot("com.google.android.apps.youtube.music", "YT Music") }
        btnPresetFermata.setOnClickListener { assignAppToCurrentSlot("me.aap.fermata.auto.dear.google.why", "Fermata Auto") }

        // Mode Selector Listeners
        btnModeLite.setOnClickListener {
            selectDisplayMode(DisplayMode.MODE_LITE, shouldRestart = isDisplaysRunning)
        }
        btnMode3Panel.setOnClickListener {
            selectDisplayMode(DisplayMode.MODE_3_PANEL, shouldRestart = isDisplaysRunning)
        }

        // Music Controls Listeners
        btnMusicPlayPause.setOnClickListener {
            DuoNotificationService.playOrPause()
        }
        btnMusicNext.setOnClickListener {
            DuoNotificationService.skipNext()
        }
        btnMusicPrev.setOnClickListener {
            DuoNotificationService.skipPrevious()
        }
        btnGrantNotification.setOnClickListener {
            DuoNotificationService.openSettings(this)
        }

        // Quick Vietmap Launch
        btnQuickVietmap.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage("vn.vietmap.live")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Chưa cài đặt Vietmap Live trên máy", Toast.LENGTH_SHORT).show()
            }
        }

        // Draggable Dividers & Proportions
        setupDraggableDividers()
        restoreProportions()

        // SurfaceTexture Listeners
        tvTopDisplay.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                topTexture = surface
                if (width > 0 && height > 0) {
                    surface.setDefaultBufferSize(width, height)
                }
                tryAutoStartDisplays()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    surface.setDefaultBufferSize(width, height)
                    if (isDisplaysRunning) {
                        displayManager.resizeTopDisplay(surface, width, height)
                    }
                }
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        tvBottomLeftDisplay.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                bottomLeftTexture = surface
                if (width > 0 && height > 0) {
                    surface.setDefaultBufferSize(width, height)
                }
                tryAutoStartDisplays()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    surface.setDefaultBufferSize(width, height)
                }
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        tvBottomRightDisplay.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                bottomRightTexture = surface
                if (width > 0 && height > 0) {
                    surface.setDefaultBufferSize(width, height)
                }
                tryAutoStartDisplays()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    surface.setDefaultBufferSize(width, height)
                }
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        // Touch Listeners on Displays for Mode 3
        tvTopDisplay.setOnTouchListener { _, event ->
            if (!isDisplaysRunning) return@setOnTouchListener false
            displayManager.handleTouch(
                displayManager.topDisplayId, event,
                tvTopDisplay.width, tvTopDisplay.height,
                tvTopDisplay.width, tvTopDisplay.height
            )
            true
        }

        tvBottomLeftDisplay.setOnTouchListener { _, event ->
            if (!isDisplaysRunning || currentMode == DisplayMode.MODE_LITE) return@setOnTouchListener false
            displayManager.handleTouch(
                displayManager.bottomLeftDisplayId, event,
                tvBottomLeftDisplay.width, tvBottomLeftDisplay.height,
                tvBottomLeftDisplay.width, tvBottomLeftDisplay.height
            )
            true
        }

        tvBottomRightDisplay.setOnTouchListener { _, event ->
            if (!isDisplaysRunning || currentMode == DisplayMode.MODE_LITE) return@setOnTouchListener false
            displayManager.handleTouch(
                displayManager.bottomRightDisplayId, event,
                tvBottomRightDisplay.width, tvBottomRightDisplay.height,
                tvBottomRightDisplay.width, tvBottomRightDisplay.height
            )
            true
        }

        // Menu triggers
        btnFloatingTrigger.alpha = 0.3f
        btnFloatingTrigger.setOnClickListener { toggleMenu() }
        btnClose.setOnClickListener { hideMenu() }

        // Register Shizuku listeners
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // Register Service Listeners
        DuoNotificationService.addMediaListener(mediaListener)
        DuoNotificationService.addVietmapListener(vietmapAlertListener)
        speedometerManager.addListener(speedStateListener)

        // Restore saved Display Mode (default: MODE_LITE)
        val savedModeStr = prefs.getString("pref_display_mode", DisplayMode.MODE_LITE.name)
        val savedMode = try {
            DisplayMode.valueOf(savedModeStr ?: DisplayMode.MODE_LITE.name)
        } catch (e: Exception) {
            DisplayMode.MODE_LITE
        }
        selectDisplayMode(savedMode, shouldRestart = false)

        checkAndRequestShizukuPermission()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestShizukuPermission()
        checkNotificationPermission()
        if (currentMode == DisplayMode.MODE_LITE) {
            checkLocationPermission()
            speedometerManager.start()
        }
        if (ShizukuHelper.hasPermission()) {
            tryAutoStartDisplays()
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentMode == DisplayMode.MODE_LITE) {
            speedometerManager.stop()
        }
    }

    private fun checkLocationPermission() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                2001
            )
        }
    }

    private fun checkNotificationPermission() {
        val granted = DuoNotificationService.isPermissionGranted(this)
        btnGrantNotification.visibility = if (granted) View.GONE else View.VISIBLE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speedometerManager.start()
            }
        }
    }

    private fun selectDisplayMode(mode: DisplayMode, shouldRestart: Boolean = true) {
        currentMode = mode
        prefs.edit().putString("pref_display_mode", mode.name).apply()

        val activeBg = Color.parseColor("#00796B")
        val inactiveBg = Color.parseColor("#424242")

        if (mode == DisplayMode.MODE_LITE) {
            btnModeLite.setBackgroundColor(activeBg)
            btnMode3Panel.setBackgroundColor(inactiveBg)

            widgetSpeedContainer.visibility = View.VISIBLE
            widgetMusicContainer.visibility = View.VISIBLE

            tvBottomLeftDisplay.visibility = View.GONE
            tvBottomRightDisplay.visibility = View.GONE
            placeholderBottomLeft.visibility = View.GONE
            placeholderBottomRight.visibility = View.GONE

            btnSlotBottomLeft.visibility = View.GONE
            btnSlotBottomRight.visibility = View.GONE
            selectSlot(TargetSlot.TOP)

            checkLocationPermission()
            speedometerManager.start()
            checkNotificationPermission()
        } else {
            btnModeLite.setBackgroundColor(inactiveBg)
            btnMode3Panel.setBackgroundColor(activeBg)

            widgetSpeedContainer.visibility = View.GONE
            widgetMusicContainer.visibility = View.GONE

            tvBottomLeftDisplay.visibility = View.VISIBLE
            tvBottomRightDisplay.visibility = View.VISIBLE

            if (!isDisplaysRunning) {
                placeholderBottomLeft.visibility = View.VISIBLE
                placeholderBottomRight.visibility = View.VISIBLE
            }

            btnSlotBottomLeft.visibility = View.VISIBLE
            btnSlotBottomRight.visibility = View.VISIBLE

            speedometerManager.stop()
        }

        if (shouldRestart && isDisplaysRunning) {
            startDisplays(force = true)
        }
    }

    private fun checkAndRequestShizukuPermission() {
        if (ShizukuHelper.isShizukuAvailable()) {
            if (!ShizukuHelper.hasPermission()) {
                ShizukuHelper.requestPermission(1001)
            }
        }
        updateShizukuStatusUi()
    }

    private fun updateShizukuStatusUi() {
        runOnUiThread {
            when {
                !ShizukuHelper.isShizukuAvailable() -> {
                    statusHandler.removeCallbacks(hideStatusRunnable)
                    layoutShizukuStatus.animate().cancel()
                    layoutShizukuStatus.alpha = 1f
                    layoutShizukuStatus.visibility = View.VISIBLE
                    tvShizukuStatus.text = "Chờ khởi chạy Shizuku..."
                    tvShizukuStatus.setTextColor(Color.parseColor("#BF616A"))
                    viewShizukuDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BF616A"))
                    btnGrantShizuku.visibility = View.GONE
                    updatePlaceholderStatus("Chờ Shizuku khởi chạy...")
                    if (::btnPlaceholderStart.isInitialized) {
                        btnPlaceholderStart.isEnabled = false
                        btnPlaceholderStart.text = "⏳ Đang chờ Shizuku..."
                    }
                }
                !ShizukuHelper.hasPermission() -> {
                    statusHandler.removeCallbacks(hideStatusRunnable)
                    layoutShizukuStatus.animate().cancel()
                    layoutShizukuStatus.alpha = 1f
                    layoutShizukuStatus.visibility = View.VISIBLE
                    tvShizukuStatus.text = "Chờ cấp quyền Shizuku"
                    tvShizukuStatus.setTextColor(Color.parseColor("#EBCB8B"))
                    viewShizukuDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EBCB8B"))
                    btnGrantShizuku.visibility = View.VISIBLE
                    updatePlaceholderStatus("Chờ cấp quyền Shizuku...")
                    if (::btnPlaceholderStart.isInitialized) {
                        btnPlaceholderStart.isEnabled = true
                        btnPlaceholderStart.text = "🔑 Cấp quyền Shizuku để chạy"
                    }
                }
                else -> {
                    tvShizukuStatus.text = "Shizuku sẵn sàng"
                    tvShizukuStatus.setTextColor(Color.parseColor("#A3BE8C"))
                    viewShizukuDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A3BE8C"))
                    btnGrantShizuku.visibility = View.GONE
                    updatePlaceholderStatus("Sẵn sàng khởi động...")
                    if (::btnPlaceholderStart.isInitialized) {
                        btnPlaceholderStart.isEnabled = true
                        btnPlaceholderStart.text = if (currentMode == DisplayMode.MODE_LITE) "🚀 Khởi chạy Bản đồ" else "🚀 Khởi chạy 3 ứng dụng"
                    }
                    statusHandler.removeCallbacks(hideStatusRunnable)
                    statusHandler.postDelayed(hideStatusRunnable, 2500)
                }
            }
        }
    }

    private fun updatePlaceholderStatus(status: String) {
        tvPlaceholderTopStatus.text = status
        tvPlaceholderBottomLeftStatus.text = status
        tvPlaceholderBottomRightStatus.text = status
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableDividers() {
        var startY = 0f
        var startTopHeight = 0
        var startBottomHeight = 0

        dividerHorizontal.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startTopHeight = frameTop.height
                    startBottomHeight = layoutBottomRow.height
                    dividerHorizontal.setBackgroundColor(Color.parseColor("#88C0D0"))
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    val totalH = startTopHeight + startBottomHeight
                    val newTopH = (startTopHeight + deltaY).coerceIn(totalH * 0.2f, totalH * 0.8f)
                    val newBottomH = totalH - newTopH

                    val topWeight = (newTopH / totalH) * 100f
                    val bottomWeight = (newBottomH / totalH) * 100f

                    val topParams = frameTop.layoutParams as LinearLayout.LayoutParams
                    topParams.weight = topWeight
                    frameTop.layoutParams = topParams

                    val bottomParams = layoutBottomRow.layoutParams as LinearLayout.LayoutParams
                    bottomParams.weight = bottomWeight
                    layoutBottomRow.layoutParams = bottomParams

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dividerHorizontal.setBackgroundColor(Color.parseColor("#2E3440"))
                    val topParams = frameTop.layoutParams as LinearLayout.LayoutParams
                    val bottomParams = layoutBottomRow.layoutParams as LinearLayout.LayoutParams
                    prefs.edit()
                        .putFloat("weight_top", topParams.weight)
                        .putFloat("weight_bottom", bottomParams.weight)
                        .apply()
                    tvTopDisplay.post {
                        val tw = tvTopDisplay.width
                        val th = tvTopDisplay.height
                        if (tw > 0 && th > 0 && isDisplaysRunning) {
                            topTexture?.setDefaultBufferSize(tw, th)
                            displayManager.resizeTopDisplay(topTexture, tw, th)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        var startX = 0f
        var startLeftWidth = 0
        var startRightWidth = 0

        dividerVertical.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startLeftWidth = frameBottomLeft.width
                    startRightWidth = frameBottomRight.width
                    dividerVertical.setBackgroundColor(Color.parseColor("#88C0D0"))
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    val totalW = startLeftWidth + startRightWidth
                    val newLeftW = (startLeftWidth + deltaX).coerceIn(totalW * 0.2f, totalW * 0.8f)
                    val newRightW = totalW - newLeftW

                    val leftWeight = (newLeftW / totalW) * 100f
                    val rightWeight = (newRightW / totalW) * 100f

                    val leftParams = frameBottomLeft.layoutParams as LinearLayout.LayoutParams
                    leftParams.weight = leftWeight
                    frameBottomLeft.layoutParams = leftParams

                    val rightParams = frameBottomRight.layoutParams as LinearLayout.LayoutParams
                    rightParams.weight = rightWeight
                    frameBottomRight.layoutParams = rightParams

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dividerVertical.setBackgroundColor(Color.parseColor("#2E3440"))
                    val leftParams = frameBottomLeft.layoutParams as LinearLayout.LayoutParams
                    val rightParams = frameBottomRight.layoutParams as LinearLayout.LayoutParams
                    prefs.edit()
                        .putFloat("weight_bottom_left", leftParams.weight)
                        .putFloat("weight_bottom_right", rightParams.weight)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun restoreProportions() {
        val topW = prefs.getFloat("weight_top", 65f)
        val bottomW = prefs.getFloat("weight_bottom", 35f)
        val leftW = prefs.getFloat("weight_bottom_left", 40f)
        val rightW = prefs.getFloat("weight_bottom_right", 60f)

        val topParams = frameTop.layoutParams as LinearLayout.LayoutParams
        topParams.weight = topW
        frameTop.layoutParams = topParams

        val bottomParams = layoutBottomRow.layoutParams as LinearLayout.LayoutParams
        bottomParams.weight = bottomW
        layoutBottomRow.layoutParams = bottomParams

        val leftParams = frameBottomLeft.layoutParams as LinearLayout.LayoutParams
        leftParams.weight = leftW
        frameBottomLeft.layoutParams = leftParams

        val rightParams = frameBottomRight.layoutParams as LinearLayout.LayoutParams
        rightParams.weight = rightW
        frameBottomRight.layoutParams = rightParams
    }

    private fun assignAppToCurrentSlot(packageName: String, label: String) {
        when (currentSlot) {
            TargetSlot.TOP -> {
                prefs.edit().putString("pkg_top", packageName).apply()
                placeholderTop.visibility = View.GONE
                displayManager.launchTopApp(packageName)
                Toast.makeText(this, "Đã gán $label vào Màn Trên", Toast.LENGTH_SHORT).show()
            }
            TargetSlot.BOTTOM_LEFT -> {
                prefs.edit().putString("pkg_bottom_left", packageName).apply()
                placeholderBottomLeft.visibility = View.GONE
                displayManager.launchBottomLeftApp(packageName)
                Toast.makeText(this, "Đã gán $label vào Dưới Trái", Toast.LENGTH_SHORT).show()
            }
            TargetSlot.BOTTOM_RIGHT -> {
                prefs.edit().putString("pkg_bottom_right", packageName).apply()
                placeholderBottomRight.visibility = View.GONE
                displayManager.launchBottomRightApp(packageName)
                Toast.makeText(this, "Đã gán $label vào Dưới Phải", Toast.LENGTH_SHORT).show()
            }
        }
        hideMenu()
    }

    private fun selectSlot(slot: TargetSlot) {
        currentSlot = slot
        val activeBg = Color.parseColor("#00796B")
        val inactiveBg = Color.parseColor("#424242")

        btnSlotTop.setBackgroundColor(if (slot == TargetSlot.TOP) activeBg else inactiveBg)
        btnSlotBottomLeft.setBackgroundColor(if (slot == TargetSlot.BOTTOM_LEFT) activeBg else inactiveBg)
        btnSlotBottomRight.setBackgroundColor(if (slot == TargetSlot.BOTTOM_RIGHT) activeBg else inactiveBg)
    }

    private fun loadAllInstalledAppsAsync() {
        Thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(mainIntent, 0)
            val appMap = mutableMapOf<String, AppInfo>()

            for (resolveInfo in launcherApps) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == packageName) continue
                val name = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                appMap[pkg] = AppInfo(name, pkg, icon)
            }

            val keyPackages = listOf(
                "com.google.android.apps.maps",
                "vn.vietmap.live",
                "com.google.android.apps.youtube.music",
                "me.aap.fermata.auto.dear.google.why",
                "com.android.chrome",
                "com.spotify.music",
                "com.zing.zalo"
            )

            for (pkg in keyPackages) {
                if (!appMap.containsKey(pkg)) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val name = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        appMap[pkg] = AppInfo(name, pkg, icon)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            val resultList = appMap.values.toMutableList()
            resultList.sortBy { it.name.lowercase() }

            runOnUiThread {
                rvAppList.adapter = AppListAdapter(resultList) { selectedApp ->
                    assignAppToCurrentSlot(selectedApp.packageName, selectedApp.name)
                }
            }
        }.start()
    }

    private fun tryAutoStartDisplays() {
        val autoStart = prefs.getBoolean("pref_auto_start", false)
        if (!autoStart) return
        startDisplays(force = false)
    }

    private fun startDisplays(force: Boolean = false) {
        if (!force && isDisplaysRunning) return

        if (!ShizukuHelper.isShizukuAvailable()) {
            Toast.makeText(this, "Shizuku chưa chạy trên thiết bị!", Toast.LENGTH_SHORT).show()
            updateShizukuStatusUi()
            return
        }

        if (!ShizukuHelper.hasPermission()) {
            checkAndRequestShizukuPermission()
            Toast.makeText(this, "Vui lòng cấp quyền Shizuku!", Toast.LENGTH_SHORT).show()
            return
        }

        val tTex = topTexture
        if (tTex == null) {
            Toast.makeText(this, "Đang chuẩn bị màn hình hiển thị...", Toast.LENGTH_SHORT).show()
            return
        }

        tvTopDisplay.post {
            val tw = tvTopDisplay.width
            val th = tvTopDisplay.height
            if (tw <= 0 || th <= 0) return@post

            val topPkg = prefs.getString("pkg_top", "com.google.android.apps.maps") ?: "com.google.android.apps.maps"

            if (currentMode == DisplayMode.MODE_LITE) {
                isDisplaysRunning = true
                hasAutoCreated = true
                displayManager.createSingleTopDisplay(tTex, tw, th, 220)
                placeholderTop.visibility = View.GONE

                widgetSpeedContainer.visibility = View.VISIBLE
                widgetMusicContainer.visibility = View.VISIBLE
                placeholderBottomLeft.visibility = View.GONE
                placeholderBottomRight.visibility = View.GONE

                checkLocationPermission()
                speedometerManager.start()
                checkNotificationPermission()

                if (::btnMenuStartDisplays.isInitialized) {
                    btnMenuStartDisplays.text = "🔄 Khởi động lại Bản đồ"
                    btnMenuStopDisplays.isEnabled = true
                }

                Thread {
                    displayManager.launchTopApp(topPkg)
                }.start()
            } else {
                val blTex = bottomLeftTexture
                val brTex = bottomRightTexture

                if (blTex == null || brTex == null) {
                    Toast.makeText(this, "Đang chuẩn bị màn hình hiển thị...", Toast.LENGTH_SHORT).show()
                    return@post
                }

                val blw = tvBottomLeftDisplay.width
                val blh = tvBottomLeftDisplay.height
                val brw = tvBottomRightDisplay.width
                val brh = tvBottomRightDisplay.height

                if (blw > 0 && blh > 0 && brw > 0 && brh > 0) {
                    isDisplaysRunning = true
                    hasAutoCreated = true
                    displayManager.createThreeDisplays(
                        tTex, tw, th,
                        blTex, blw, blh,
                        brTex, brw, brh
                    )
                    val bLeftPkg = prefs.getString("pkg_bottom_left", "vn.vietmap.live") ?: "vn.vietmap.live"
                    val bRightPkg = prefs.getString("pkg_bottom_right", "com.google.android.apps.youtube.music") ?: "com.google.android.apps.youtube.music"

                    placeholderTop.visibility = View.GONE
                    placeholderBottomLeft.visibility = View.GONE
                    placeholderBottomRight.visibility = View.GONE

                    if (::btnMenuStartDisplays.isInitialized) {
                        btnMenuStartDisplays.text = "🔄 Khởi động lại 3 app"
                        btnMenuStopDisplays.isEnabled = true
                    }

                    Thread {
                        displayManager.launchTopApp(topPkg)
                        displayManager.launchBottomLeftApp(bLeftPkg)
                        displayManager.launchBottomRightApp(bRightPkg)
                    }.start()
                }
            }
        }
    }

    private fun stopDisplays() {
        displayManager.release()
        isDisplaysRunning = false
        placeholderTop.visibility = View.VISIBLE
        if (currentMode == DisplayMode.MODE_3_PANEL) {
            placeholderBottomLeft.visibility = View.VISIBLE
            placeholderBottomRight.visibility = View.VISIBLE
        }
        if (::btnMenuStartDisplays.isInitialized) {
            btnMenuStartDisplays.text = "🚀 Khởi chạy ngay"
            btnMenuStopDisplays.isEnabled = false
        }
        Toast.makeText(this, "Đã dừng màn hình", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMenu() {
        if (panelControlOverlay.visibility == View.VISIBLE) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        panelControlOverlay.visibility = View.VISIBLE
        btnFloatingTrigger.alpha = 1.0f
        if (::btnMenuStartDisplays.isInitialized) {
            if (isDisplaysRunning) {
                btnMenuStartDisplays.text = if (currentMode == DisplayMode.MODE_LITE) "🔄 Khởi động lại Bản đồ" else "🔄 Khởi động lại 3 app"
                btnMenuStopDisplays.isEnabled = true
            } else {
                btnMenuStartDisplays.text = "🚀 Khởi chạy ngay"
                btnMenuStopDisplays.isEnabled = false
            }
        }
    }

    private fun hideMenu() {
        panelControlOverlay.visibility = View.GONE
        btnFloatingTrigger.alpha = 0.25f
    }

    override fun onDestroy() {
        super.onDestroy()
        statusHandler.removeCallbacks(hideStatusRunnable)
        displayManager.release()
        speedometerManager.stop()
        speedometerManager.removeListener(speedStateListener)
        DuoNotificationService.removeMediaListener(mediaListener)
        DuoNotificationService.removeVietmapListener(vietmapAlertListener)

        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
