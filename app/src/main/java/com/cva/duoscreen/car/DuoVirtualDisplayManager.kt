package com.cva.duoscreen.car

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.MotionEvent
import android.view.Surface
import com.cva.duoscreen.shizuku.ShizukuHelper
class DuoVirtualDisplayManager(private val context: Context) {

    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    var topVirtualDisplay: VirtualDisplay? = null
        private set
    var bottomLeftVirtualDisplay: VirtualDisplay? = null
        private set
    var bottomRightVirtualDisplay: VirtualDisplay? = null
        private set

    var topDisplayId: Int = -1
        private set
    var bottomLeftDisplayId: Int = -1
        private set
    var bottomRightDisplayId: Int = -1
        private set

    private var topSurface: Surface? = null
    private var bottomLeftSurface: Surface? = null
    private var bottomRightSurface: Surface? = null

    fun createThreeDisplays(
        topTexture: SurfaceTexture, topW: Int, topH: Int,
        bottomLeftTexture: SurfaceTexture, bLeftW: Int, bLeftH: Int,
        bottomRightTexture: SurfaceTexture, bRightW: Int, bRightH: Int
    ) {
        release()

        topSurface = Surface(topTexture)
        bottomLeftSurface = Surface(bottomLeftTexture)
        bottomRightSurface = Surface(bottomRightTexture)

        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            // 1. Top Wide Panel (Google Maps: 220 DPI for broad view)
            topVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-Top",
                if (topW > 0) topW else 1080,
                if (topH > 0) topH else 720,
                220,
                topSurface,
                flags
            )
            topDisplayId = topVirtualDisplay?.display?.displayId ?: -1

            // 2. Bottom Left Panel (Vietmap Live Speed Warning Widget: 140 DPI to make text, signs and icons big & readable!)
            bottomLeftVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-BottomLeft",
                if (bLeftW > 0) bLeftW else 540,
                if (bLeftH > 0) bLeftH else 720,
                140, // Low DPI = UI elements, speed limit circles and warning text scale up dramatically!
                bottomLeftSurface,
                flags
            )
            bottomLeftDisplayId = bottomLeftVirtualDisplay?.display?.displayId ?: -1

            // 3. Bottom Right Panel (YouTube Music player: 160 DPI)
            bottomRightVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-BottomRight",
                if (bRightW > 0) bRightW else 540,
                if (bRightH > 0) bRightH else 720,
                160,
                bottomRightSurface,
                flags
            )
            bottomRightDisplayId = bottomRightVirtualDisplay?.display?.displayId ?: -1

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createSingleTopDisplay(
        topTexture: SurfaceTexture,
        topW: Int,
        topH: Int,
        defaultDpi: Int = 220
    ) {
        release()
        topSurface = Surface(topTexture)
        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            topVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-Top",
                if (topW > 0) topW else 1080,
                if (topH > 0) topH else 720,
                defaultDpi,
                topSurface,
                flags
            )
            topDisplayId = topVirtualDisplay?.display?.displayId ?: -1
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createCarDisplay(
        carSurface: Surface,
        width: Int,
        height: Int,
        dpi: Int = 160
    ) {
        release()
        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            topVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-Car",
                if (width > 0) width else 1280,
                if (height > 0) height else 720,
                if (dpi > 0) dpi else 160,
                carSurface,
                flags
            )
            topDisplayId = topVirtualDisplay?.display?.displayId ?: -1
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun handleCarTap(x: Float, y: Float) {
        if (topDisplayId != -1) {
            ShizukuHelper.injectTap(topDisplayId, x, y)
        }
    }

    fun handleTouch(displayId: Int, event: MotionEvent, viewW: Int, viewH: Int, displayW: Int, displayH: Int) {
        if (displayId == -1 || viewW == 0 || viewH == 0) return
        val scaleX = displayW.toFloat() / viewW.toFloat()
        val scaleY = displayH.toFloat() / viewH.toFloat()
        val targetX = event.x * scaleX
        val targetY = event.y * scaleY

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                ShizukuHelper.injectTap(displayId, targetX, targetY)
            }
        }
    }

    fun launchApp(packageName: String, displayId: Int) {
        if (displayId == -1) return
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                val options = ActivityOptions.makeBasic()
                options.setLaunchDisplayId(displayId)
                context.startActivity(intent, options.toBundle())
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ShizukuHelper.launchAppOnDisplay(packageName, displayId)
    }

    fun launchTopApp(packageName: String) {
        launchApp(packageName, topDisplayId)
    }

    fun launchBottomLeftApp(packageName: String) {
        launchApp(packageName, bottomLeftDisplayId)
    }

    fun launchBottomRightApp(packageName: String) {
        launchApp(packageName, bottomRightDisplayId)
    }

    fun release() {
        try {
            topVirtualDisplay?.release()
            bottomLeftVirtualDisplay?.release()
            bottomRightVirtualDisplay?.release()

            topSurface?.release()
            bottomLeftSurface?.release()
            bottomRightSurface?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        topVirtualDisplay = null
        bottomLeftVirtualDisplay = null
        bottomRightVirtualDisplay = null

        topSurface = null
        bottomLeftSurface = null
        bottomRightSurface = null

        topDisplayId = -1
        bottomLeftDisplayId = -1
        bottomRightDisplayId = -1
    }
}
