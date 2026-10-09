package com.cva.duoscreen.car

import android.app.Presentation
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.TextureView
import android.view.View
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
import com.cva.duoscreen.shizuku.ShizukuHelper

class DuoMainScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private val dm = carContext.getSystemService(CarContext.DISPLAY_SERVICE) as DisplayManager

    // Car Host VirtualDisplay & Presentation
    private var carVirtualDisplay: VirtualDisplay? = null
    private var carPresentation: Presentation? = null

    // 3 Sub VirtualDisplays for 3 Apps
    private var topVirtualDisplay: VirtualDisplay? = null
    private var bottomLeftVirtualDisplay: VirtualDisplay? = null
    private var bottomRightVirtualDisplay: VirtualDisplay? = null

    private var topDisplayId: Int = -1
    private var bottomLeftDisplayId: Int = -1
    private var bottomRightDisplayId: Int = -1

    private var topTexture: SurfaceTexture? = null
    private var bottomLeftTexture: SurfaceTexture? = null
    private var bottomRightTexture: SurfaceTexture? = null

    init {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setTitle("Khởi động lại")
                    .setOnClickListener {
                        launchAllApps()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
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

            // 2. Tạo Presentation chứa Layout 3 ô (Google Maps trên, Vietmap & YT Music dưới)
            carPresentation = Presentation(carContext, display).apply {
                setContentView(R.layout.car_presentation_layout)
                show()
            }

            setupCarPresentationViews()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupCarPresentationViews() {
        val pres = carPresentation ?: return

        val carTvTop = pres.findViewById<TextureView>(R.id.carTvTop)
        val carTvBottomLeft = pres.findViewById<TextureView>(R.id.carTvBottomLeft)
        val carTvBottomRight = pres.findViewById<TextureView>(R.id.carTvBottomRight)

        carTvTop.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                topTexture = st
                checkAndCreateThreeDisplays(carTvTop, carTvBottomLeft, carTvBottomRight)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        carTvBottomLeft.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                bottomLeftTexture = st
                checkAndCreateThreeDisplays(carTvTop, carTvBottomLeft, carTvBottomRight)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        carTvBottomRight.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                bottomRightTexture = st
                checkAndCreateThreeDisplays(carTvTop, carTvBottomLeft, carTvBottomRight)
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    @Synchronized
    private fun checkAndCreateThreeDisplays(tvTop: View, tvLeft: View, tvRight: View) {
        val tTex = topTexture ?: return
        val blTex = bottomLeftTexture ?: return
        val brTex = bottomRightTexture ?: return

        if (topVirtualDisplay != null) return // Đã khởi tạo

        val topW = if (tvTop.width > 0) tvTop.width else 1080
        val topH = if (tvTop.height > 0) tvTop.height else 800
        val blW = if (tvLeft.width > 0) tvLeft.width else 480
        val blH = if (tvLeft.height > 0) tvLeft.height else 480
        val brW = if (tvRight.width > 0) tvRight.width else 600
        val brH = if (tvRight.height > 0) tvRight.height else 480

        tTex.setDefaultBufferSize(topW, topH)
        blTex.setDefaultBufferSize(blW, blH)
        brTex.setDefaultBufferSize(brW, brH)

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        try {
            // Màn trên: Google Maps (220 DPI)
            topVirtualDisplay = dm.createVirtualDisplay(
                "Duo-Car-Top",
                topW,
                topH,
                220,
                android.view.Surface(tTex),
                flags
            )
            topDisplayId = topVirtualDisplay?.display?.displayId ?: -1

            // Màn dưới trái: Vietmap Live (140 DPI biển báo to rõ)
            bottomLeftVirtualDisplay = dm.createVirtualDisplay(
                "Duo-Car-BottomLeft",
                blW,
                blH,
                140,
                android.view.Surface(blTex),
                flags
            )
            bottomLeftDisplayId = bottomLeftVirtualDisplay?.display?.displayId ?: -1

            // Màn dưới phải: YouTube Music (160 DPI)
            bottomRightVirtualDisplay = dm.createVirtualDisplay(
                "Duo-Car-BottomRight",
                brW,
                brH,
                160,
                android.view.Surface(brTex),
                flags
            )
            bottomRightDisplayId = bottomRightVirtualDisplay?.display?.displayId ?: -1

            // Tự động bung ngay 3 ứng dụng lên 3 màn hình ảo
            launchAllApps()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchAllApps() {
        Thread {
            try {
                // Đảm bảo Shizuku đang chạy
                if (!ShizukuHelper.isShizukuAvailable()) {
                    ShizukuHelper.executeShell("/data/local/tmp/start_shizuku.sh")
                    Thread.sleep(1000)
                }

                if (topDisplayId != -1) {
                    ShizukuHelper.launchAppOnDisplay("com.google.android.apps.maps", topDisplayId)
                }
                Thread.sleep(300)

                if (bottomLeftDisplayId != -1) {
                    ShizukuHelper.launchAppOnDisplay("vn.vietmap.live", bottomLeftDisplayId)
                }
                Thread.sleep(300)

                if (bottomRightDisplayId != -1) {
                    ShizukuHelper.launchAppOnDisplay("com.google.android.apps.youtube.music", bottomRightDisplayId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
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
