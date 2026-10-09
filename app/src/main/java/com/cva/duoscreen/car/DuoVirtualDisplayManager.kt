package com.cva.duoscreen.car

import android.content.Context
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
        bottomRightTexture: SurfaceTexture, bRightW: Int, bRightH: Int,
        dpi: Int = 240
    ) {
        release()

        topSurface = Surface(topTexture)
        bottomLeftSurface = Surface(bottomLeftTexture)
        bottomRightSurface = Surface(bottomRightTexture)

        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY

            // 1. Top Wide Panel
            topVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-Top",
                if (topW > 0) topW else 1080,
                if (topH > 0) topH else 720,
                dpi,
                topSurface,
                flags
            )
            topDisplayId = topVirtualDisplay?.display?.displayId ?: -1

            // 2. Bottom Left Panel
            bottomLeftVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-BottomLeft",
                if (bLeftW > 0) bLeftW else 540,
                if (bLeftH > 0) bLeftH else 720,
                dpi,
                bottomLeftSurface,
                flags
            )
            bottomLeftDisplayId = bottomLeftVirtualDisplay?.display?.displayId ?: -1

            // 3. Bottom Right Panel
            bottomRightVirtualDisplay = dm.createVirtualDisplay(
                "DuoScreen-BottomRight",
                if (bRightW > 0) bRightW else 540,
                if (bRightH > 0) bRightH else 720,
                dpi,
                bottomRightSurface,
                flags
            )
            bottomRightDisplayId = bottomRightVirtualDisplay?.display?.displayId ?: -1

        } catch (e: Exception) {
            e.printStackTrace()
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

    fun launchTopApp(packageName: String) {
        if (topDisplayId != -1) {
            ShizukuHelper.launchAppOnDisplay(packageName, topDisplayId)
        }
    }

    fun launchBottomLeftApp(packageName: String) {
        if (bottomLeftDisplayId != -1) {
            ShizukuHelper.launchAppOnDisplay(packageName, bottomLeftDisplayId)
        }
    }

    fun launchBottomRightApp(packageName: String) {
        if (bottomRightDisplayId != -1) {
            ShizukuHelper.launchAppOnDisplay(packageName, bottomRightDisplayId)
        }
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
