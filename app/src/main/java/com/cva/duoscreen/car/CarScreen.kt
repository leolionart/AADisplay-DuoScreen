package com.cva.duoscreen.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.cva.duoscreen.core.ShizukuShell
import com.cva.duoscreen.panels.VirtualDisplayController
import com.cva.duoscreen.source.SpeedAlert
import com.cva.duoscreen.source.SpeedAlertRepository
import com.cva.duoscreen.ui.SettingsStore

class CarScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private val controller = VirtualDisplayController(carContext)
    private var surface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private var renderPending = false
    private var lastRenderTime = 0L

    private var mapBitmap: Bitmap? = null
    private var imageReader: ImageReader? = null
    private var mapImageWidth: Int = 0
    private var mapImageHeight: Int = 0
    private val dstRect = Rect()
    private val srcRect = Rect()
    
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val speedAlertListener: (SpeedAlert) -> Unit = { scheduleRender() }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {
        val settings = SettingsStore.get(carContext)
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Maps")
                    .setOnClickListener {
                        val id = controller.mapDisplayId
                        if (id != -1) ShizukuShell.launchAppOnDisplay(settings.mapPackage, id) {}
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Vietmap")
                    .setOnClickListener {
                        val id = controller.mapDisplayId
                        if (id != -1) ShizukuShell.launchAppOnDisplay(settings.vietmapPackage, id) {}
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Music")
                    .setOnClickListener {
                        val id = controller.mapDisplayId
                        if (id != -1) ShizukuShell.launchAppOnDisplay(settings.musicPackage, id) {}
                    }
                    .build()
            )
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val s = surfaceContainer.surface ?: return
        surface = s
        surfaceWidth = surfaceContainer.width
        surfaceHeight = surfaceContainer.height
        handler.removeCallbacks(renderRunnable)
        renderPending = false
        mapBitmap?.recycle()
        mapBitmap = null
        mapImageWidth = 0
        mapImageHeight = 0

        SpeedAlertRepository.start(carContext)
        SpeedAlertRepository.addListener(speedAlertListener)

        // Render the canvas and bubble before Maps produces its first frame.
        renderInternal()

        val mapPkg = SettingsStore.get(carContext).mapPackage
        val reader = controller.startMapPanel(surfaceWidth, surfaceHeight, packageName = mapPkg)
        imageReader = reader
        reader?.setOnImageAvailableListener({ ir ->
            if (ir !== imageReader || surface == null) {
                ir.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                if (pixelStride <= 0 || rowStride <= 0 || image.width <= 0 || image.height <= 0) return@setOnImageAvailableListener

                val imageWidth = image.width
                val imageHeight = image.height
                val bitmapWidth = rowStride / pixelStride
                if (bitmapWidth < imageWidth) return@setOnImageAvailableListener

                if (mapBitmap == null || mapBitmap!!.width != bitmapWidth || mapBitmap!!.height != imageHeight) {
                    val oldBitmap = mapBitmap
                    mapBitmap = Bitmap.createBitmap(bitmapWidth, imageHeight, Bitmap.Config.ARGB_8888)
                    oldBitmap?.recycle()
                }

                plane.buffer.rewind()
                mapBitmap?.copyPixelsFromBuffer(plane.buffer)
                mapImageWidth = imageWidth
                mapImageHeight = imageHeight
                scheduleRender()
            } catch (_: RuntimeException) {
                // ImageReader frames can be invalid while the virtual display is stopping.
            } finally {
                image.close()
            }
        }, handler)
    }

    private val renderRunnable = Runnable {
        renderPending = false
        renderInternal()
    }

    private fun scheduleRender() {
        if (!renderPending) {
            renderPending = true
            val now = SystemClock.elapsedRealtime()
            val delay = (33L - (now - lastRenderTime)).coerceAtLeast(0L)
            handler.postDelayed(renderRunnable, delay)
        }
    }

    @Synchronized
    private fun renderInternal() {
        val currentSurface = surface ?: return
        if (!currentSurface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return

        val canvas = currentSurface.lockCanvas(null) ?: return
        try {
            canvas.drawColor(Color.BLACK)

            val bmp = mapBitmap
            if (bmp != null && mapImageWidth > 0 && mapImageHeight > 0) {
                srcRect.set(0, 0, mapImageWidth, mapImageHeight)
                val scale = minOf(
                    surfaceWidth.toFloat() / mapImageWidth,
                    surfaceHeight.toFloat() / mapImageHeight
                )
                val scaledW = (mapImageWidth * scale).toInt()
                val scaledH = (mapImageHeight * scale).toInt()
                val dx = (surfaceWidth - scaledW) / 2
                val dy = (surfaceHeight - scaledH) / 2
                dstRect.set(dx, dy, dx + scaledW, dy + scaledH)
                canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
            }

            BubbleRenderer.draw(canvas, SpeedAlertRepository.current, surfaceWidth, surfaceHeight)
            lastRenderTime = SystemClock.elapsedRealtime()
        } finally {
            currentSurface.unlockCanvasAndPost(canvas)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        imageReader = null
        controller.stop()
        SpeedAlertRepository.stop()
        SpeedAlertRepository.removeListener(speedAlertListener)
        surface = null
        surfaceWidth = 0
        surfaceHeight = 0
        mapImageWidth = 0
        mapImageHeight = 0
        mapBitmap?.recycle()
        mapBitmap = null
        renderPending = false
        handler.removeCallbacks(renderRunnable)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {}

    override fun onStableAreaChanged(stableArea: Rect) {}

    private var isDraggingBubble = false

    override fun onClick(x: Float, y: Float) {
        // 1. Check if user tapped directly on the speed bubble -> Cycle position to next corner!
        if (BubbleRenderer.contains(x, y)) {
            BubbleRenderer.cycleCorner(surfaceWidth, surfaceHeight)
            scheduleRender()
            return
        }

        // 2. Otherwise pass tap to the underlying Google Maps on the VirtualDisplay
        if (mapImageWidth <= 0 || mapImageHeight <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return
        val scale = minOf(
            surfaceWidth.toFloat() / mapImageWidth,
            surfaceHeight.toFloat() / mapImageHeight
        )
        val scaledW = mapImageWidth * scale
        val scaledH = mapImageHeight * scale
        val dx = (surfaceWidth - scaledW) / 2f
        val dy = (surfaceHeight - scaledH) / 2f
        if (x < dx || x >= dx + scaledW || y < dy || y >= dy + scaledH) return
        controller.tap((x - dx) / scale, (y - dy) / scale)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        // If dragging bubble or dragging across the screen, allow moving bubble
        if (BubbleRenderer.cardRect.width() > 0) {
            // distanceX and distanceY are deltas in Android Auto Car App Library (dx = previous - current)
            val targetX = BubbleRenderer.currentX - distanceX
            val targetY = BubbleRenderer.currentY - distanceY
            BubbleRenderer.moveTo(targetX, targetY, surfaceWidth, surfaceHeight)
            scheduleRender()
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {}
    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {}
}
