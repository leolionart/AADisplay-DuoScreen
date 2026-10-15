package com.cva.duoscreen.panels

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import com.cva.duoscreen.core.ShizukuShell

class VirtualDisplayController(private val context: Context) {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    var mapDisplayId: Int = -1
        private set

    fun startMapPanel(
        width: Int,
        height: Int,
        dpi: Int = 200,
        packageName: String = "com.google.android.apps.maps"
    ): ImageReader? {
        stop()
        if (width <= 0 || height <= 0 || dpi <= 0) return null

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val reader = try {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        } catch (_: RuntimeException) {
            return null
        }

        val display = try {
            displayManager.createVirtualDisplay(
                "DuoScreen-Map",
                width,
                height,
                dpi,
                reader.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            )
        } catch (_: RuntimeException) {
            reader.close()
            return null
        }

        if (display == null) {
            reader.close()
            return null
        }

        imageReader = reader
        virtualDisplay = display
        mapDisplayId = display.display?.displayId ?: -1
        if (mapDisplayId < 0) {
            stop()
            return null
        }

        ShizukuShell.launchAppOnDisplay(packageName, mapDisplayId) { _ -> }
        return reader
    }

    fun tap(x: Float, y: Float) {
        if (mapDisplayId != -1) {
            ShizukuShell.injectTap(mapDisplayId, x, y)
        }
    }

    fun stop() {
        val display = virtualDisplay
        val reader = imageReader
        virtualDisplay = null
        imageReader = null
        mapDisplayId = -1

        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        reader?.close()
    }
}
