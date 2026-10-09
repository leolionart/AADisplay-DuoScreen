package com.cva.duoscreen.car

import android.app.Presentation
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.cva.duoscreen.R
import com.cva.duoscreen.manager.SpeedState
import com.cva.duoscreen.manager.SpeedometerManager
import com.cva.duoscreen.model.DisplayMode
import com.cva.duoscreen.service.DuoAccessibilityService
import com.cva.duoscreen.service.DuoNotificationService
import com.cva.duoscreen.service.MediaTrackInfo
import com.cva.duoscreen.service.VietmapAlertInfo
import com.cva.duoscreen.service.VietmapParsedData
import com.cva.duoscreen.shizuku.ShizukuHelper

class DuoMainScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private val dm = carContext.getSystemService(CarContext.DISPLAY_SERVICE) as DisplayManager
    private val prefs: SharedPreferences = carContext.getSharedPreferences("duoscreen_prefs", CarContext.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Car Host VirtualDisplay & Presentation
    private var carVirtualDisplay: VirtualDisplay? = null
    private var carPresentation: Presentation? = null

    // Sub VirtualDisplays
    private var topVirtualDisplay: VirtualDisplay? = null
    private var bottomLeftVirtualDisplay: VirtualDisplay? = null
    private var bottomRightVirtualDisplay: VirtualDisplay? = null

    private var topDisplayId: Int = -1
    private var bottomLeftDisplayId: Int = -1
    private var bottomRightDisplayId: Int = -1

    private var topTexture: SurfaceTexture? = null
    private var bottomLeftTexture: SurfaceTexture? = null
    private var bottomRightTexture: SurfaceTexture? = null

    // Native Widgets Support (Mode Lite)
    private val speedometerManager = SpeedometerManager(carContext)
    private var currentSpeedState = SpeedState()
    private var currentMediaTrack = MediaTrackInfo()

    // Mode: Default to MODE_LITE for optimal performance, or read from saved settings
    private var currentMode: DisplayMode = DisplayMode.MODE_LITE

    private val speedListener: (SpeedState) -> Unit = { state ->
        currentSpeedState = state
        updateCarWidgetsUi()
    }

    private val mediaListener: (MediaTrackInfo) -> Unit = { track ->
        currentMediaTrack = track
        updateCarWidgetsUi()
    }

    private val vietmapListener: (VietmapAlertInfo) -> Unit = { alert ->
        speedometerManager.setSpeedLimit(alert.speedLimit, alert.alertMessage)
        updateCarWidgetsUi()
    }

    private val accessibilityListener: (VietmapParsedData) -> Unit = { parsed ->
        if (parsed.speedLimit != null || parsed.alertText.isNotBlank()) {
            speedometerManager.setSpeedLimit(parsed.speedLimit, parsed.alertText)
            updateCarWidgetsUi()
        }
    }

    init {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(this)

        val savedMode = prefs.getString("pref_display_mode", DisplayMode.MODE_LITE.name)
        currentMode = try {
            DisplayMode.valueOf(savedMode ?: DisplayMode.MODE_LITE.name)
        } catch (e: Exception) {
            DisplayMode.MODE_LITE
        }
    }

    override fun onGetTemplate(): Template {
        val modeTitle = if (currentMode == DisplayMode.MODE_LITE) "Chế độ: Nhẹ (Widgets)" else "Chế độ: Đa nhiệm 3 App"
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setTitle(modeTitle)
                    .setOnClickListener {
                        toggleDisplayMode()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Khởi động lại")
                    .setOnClickListener {
                        launchAppsForCurrentMode()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun toggleDisplayMode() {
        currentMode = if (currentMode == DisplayMode.MODE_LITE) {
            DisplayMode.MODE_3_PANEL
        } else {
            DisplayMode.MODE_LITE
        }
        prefs.edit().putString("pref_display_mode", currentMode.name).apply()
        invalidate() // Refresh ActionStrip title
        applyDisplayModeUi()
        launchAppsForCurrentMode()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        if (!surface.isValid) return

        val w = if (surfaceContainer.width > 0) surfaceContainer.width else 1080
        val h = if (surfaceContainer.height > 0) surfaceContainer.height else 720
        val dpi = if (surfaceContainer.dpi > 0) surfaceContainer.dpi else 160

        releaseCarDisplays()

        try {
            // 1. Tạo VirtualDisplay chính bao bọc Surface của màn hình xe hơi
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            carVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-CarHost",
                w,
                h,
                dpi,
                surface,
                flags
            )

            val display = carVirtualDisplay?.display ?: return

            // 2. Tạo Presentation chứa Layout buồng lái xe hơi
            carPresentation = Presentation(carContext, display).apply {
                setContentView(R.layout.car_presentation_layout)
                show()
            }

            setupCarPresentationViews()
            applyDisplayModeUi()

            speedometerManager.addListener(speedListener)
            speedometerManager.start()
            DuoNotificationService.addMediaListener(mediaListener)
            DuoNotificationService.addVietmapListener(vietmapListener)
            DuoAccessibilityService.addListener(accessibilityListener)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupCarPresentationViews() {
        val pres = carPresentation ?: return

        val carTvTop = pres.findViewById<TextureView>(R.id.carTvTop)
        val carTvBottomLeft = pres.findViewById<TextureView>(R.id.carTvBottomLeft)
        val carTvBottomRight = pres.findViewById<TextureView>(R.id.carTvBottomRight)

        // Setup Media Player Click Handlers on Car
        pres.findViewById<ImageButton>(R.id.carBtnMusicPrev)?.setOnClickListener {
            DuoNotificationService.skipPrevious()
        }
        pres.findViewById<ImageButton>(R.id.carBtnMusicPlayPause)?.setOnClickListener {
            DuoNotificationService.playOrPause()
        }
        pres.findViewById<ImageButton>(R.id.carBtnMusicNext)?.setOnClickListener {
            DuoNotificationService.skipNext()
        }

        carTvTop.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                topTexture = st
                checkAndCreateDisplays()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        carTvBottomLeft.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                bottomLeftTexture = st
                checkAndCreateDisplays()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        carTvBottomRight.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                bottomRightTexture = st
                checkAndCreateDisplays()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun applyDisplayModeUi() {
        val pres = carPresentation ?: return
        mainHandler.post {
            val tvLeft = pres.findViewById<TextureView>(R.id.carTvBottomLeft)
            val tvRight = pres.findViewById<TextureView>(R.id.carTvBottomRight)
            val widgetSpeed = pres.findViewById<View>(R.id.carWidgetSpeedContainer)
            val widgetMusic = pres.findViewById<View>(R.id.carWidgetMusicContainer)

            if (currentMode == DisplayMode.MODE_LITE) {
                tvLeft?.visibility = View.GONE
                tvRight?.visibility = View.GONE
                widgetSpeed?.visibility = View.VISIBLE
                widgetMusic?.visibility = View.VISIBLE
                updateCarWidgetsUi()
            } else {
                widgetSpeed?.visibility = View.GONE
                widgetMusic?.visibility = View.GONE
                tvLeft?.visibility = View.VISIBLE
                tvRight?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCarWidgetsUi() {
        val pres = carPresentation ?: return
        if (currentMode != DisplayMode.MODE_LITE) return

        mainHandler.post {
            // Speedometer & Speed Limit
            val tvSpeed = pres.findViewById<TextView>(R.id.carTvSpeedVal)
            val tvLimit = pres.findViewById<TextView>(R.id.carTvSpeedLimitVal)
            val tvAlert = pres.findViewById<TextView>(R.id.carTvVietmapAlert)

            tvSpeed?.text = currentSpeedState.speedKmh.toString()
            if (currentSpeedState.isOverSpeed) {
                tvSpeed?.setTextColor(Color.parseColor("#FF5252"))
            } else {
                tvSpeed?.setTextColor(Color.WHITE)
            }

            tvLimit?.text = (currentSpeedState.speedLimit ?: 60).toString()
            tvAlert?.text = if (currentSpeedState.alertText.isNotEmpty()) currentSpeedState.alertText else "Vietmap: Tốc độ chuẩn"

            // Media Player info
            val tvMusicTitle = pres.findViewById<TextView>(R.id.carTvMusicTitle)
            val tvMusicArtist = pres.findViewById<TextView>(R.id.carTvMusicArtist)
            val tvMusicSource = pres.findViewById<TextView>(R.id.carTvMusicSource)
            val btnPlayPause = pres.findViewById<ImageButton>(R.id.carBtnMusicPlayPause)
            val ivAlbum = pres.findViewById<ImageView>(R.id.carIvAlbumBg)

            tvMusicTitle?.text = if (currentMediaTrack.title.isNotEmpty()) currentMediaTrack.title else "Chưa phát nhạc"
            tvMusicArtist?.text = if (currentMediaTrack.artist.isNotEmpty()) currentMediaTrack.artist else "Mở Spotify / YouTube Music"
            val sourceName = if (currentMediaTrack.packageName.contains("youtube")) "YouTube Music" else if (currentMediaTrack.packageName.contains("spotify")) "Spotify" else "Đang phát"
            tvMusicSource?.text = "🎵 $sourceName"

            if (currentMediaTrack.isPlaying) {
                btnPlayPause?.setImageResource(R.drawable.ic_pause_dark)
            } else {
                btnPlayPause?.setImageResource(R.drawable.ic_play_dark)
            }

            if (currentMediaTrack.albumArt != null) {
                ivAlbum?.setImageBitmap(currentMediaTrack.albumArt)
            } else {
                ivAlbum?.setImageResource(R.drawable.ic_music_note)
            }
        }
    }

    @Synchronized
    private fun checkAndCreateDisplays() {
        val pres = carPresentation ?: return
        val tTex = topTexture ?: return

        val tvTop = pres.findViewById<TextureView>(R.id.carTvTop) ?: return
        val topW = if (tvTop.width > 0) tvTop.width else 1080
        val topH = if (tvTop.height > 0) tvTop.height else 800
        tTex.setDefaultBufferSize(topW, topH)

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        // Tạo Top Display (Google Maps)
        if (topVirtualDisplay == null) {
            topVirtualDisplay = dm.createVirtualDisplay(
                "Duo-Car-Top",
                topW,
                topH,
                220,
                android.view.Surface(tTex),
                flags
            )
            topDisplayId = topVirtualDisplay?.display?.displayId ?: -1
        }

        if (currentMode == DisplayMode.MODE_3_PANEL) {
            val blTex = bottomLeftTexture ?: return
            val brTex = bottomRightTexture ?: return

            val tvLeft = pres.findViewById<TextureView>(R.id.carTvBottomLeft) ?: return
            val tvRight = pres.findViewById<TextureView>(R.id.carTvBottomRight) ?: return

            val blW = if (tvLeft.width > 0) tvLeft.width else 480
            val blH = if (tvLeft.height > 0) tvLeft.height else 480
            val brW = if (tvRight.width > 0) tvRight.width else 600
            val brH = if (tvRight.height > 0) tvRight.height else 480

            blTex.setDefaultBufferSize(blW, blH)
            brTex.setDefaultBufferSize(brW, brH)

            if (bottomLeftVirtualDisplay == null) {
                bottomLeftVirtualDisplay = dm.createVirtualDisplay(
                    "Duo-Car-BottomLeft",
                    blW,
                    blH,
                    140,
                    android.view.Surface(blTex),
                    flags
                )
                bottomLeftDisplayId = bottomLeftVirtualDisplay?.display?.displayId ?: -1
            }

            if (bottomRightVirtualDisplay == null) {
                bottomRightVirtualDisplay = dm.createVirtualDisplay(
                    "Duo-Car-BottomRight",
                    brW,
                    brH,
                    160,
                    android.view.Surface(brTex),
                    flags
                )
                bottomRightDisplayId = bottomRightVirtualDisplay?.display?.displayId ?: -1
            }
        }

        launchAppsForCurrentMode()
    }

    private fun launchAppsForCurrentMode() {
        Thread {
            try {
                // Đảm bảo Shizuku Server sẵn sàng
                if (!ShizukuHelper.isShizukuAvailable()) {
                    ShizukuHelper.executeShell("/data/local/tmp/start_shizuku.sh")
                    Thread.sleep(1000)
                }

                // 1. Luôn bung Google Maps lên Màn trên
                if (topDisplayId != -1) {
                    ShizukuHelper.launchAppOnDisplay("com.google.android.apps.maps", topDisplayId)
                }

                // 2. Nếu ở Chế độ 3 màn hình ảo, bung tiếp Vietmap Live và YouTube Music
                if (currentMode == DisplayMode.MODE_3_PANEL) {
                    Thread.sleep(300)
                    if (bottomLeftDisplayId != -1) {
                        ShizukuHelper.launchAppOnDisplay("vn.vietmap.live", bottomLeftDisplayId)
                    }
                    Thread.sleep(300)
                    if (bottomRightDisplayId != -1) {
                        ShizukuHelper.launchAppOnDisplay("com.google.android.apps.youtube.music", bottomRightDisplayId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        speedometerManager.removeListener(speedListener)
        speedometerManager.stop()
        DuoNotificationService.removeMediaListener(mediaListener)
        DuoNotificationService.removeVietmapListener(vietmapListener)
        DuoAccessibilityService.removeListener(accessibilityListener)
        releaseCarDisplays()
    }

    private fun releaseCarDisplays() {
        try {
            carPresentation?.dismiss()
            carPresentation = null

            carVirtualDisplay?.release()
            carVirtualDisplay = null

            topVirtualDisplay?.release()
            topVirtualDisplay = null

            bottomLeftVirtualDisplay?.release()
            bottomLeftVirtualDisplay = null

            bottomRightVirtualDisplay?.release()
            bottomRightVirtualDisplay = null

            topDisplayId = -1
            bottomLeftDisplayId = -1
            bottomRightDisplayId = -1

            topTexture = null
            bottomLeftTexture = null
            bottomRightTexture = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {}
    override fun onStableAreaChanged(stableArea: Rect) {}
}
