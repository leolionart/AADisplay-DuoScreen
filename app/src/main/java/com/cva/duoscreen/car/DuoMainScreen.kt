package com.cva.duoscreen.car

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.cva.duoscreen.manager.SpeedState
import com.cva.duoscreen.manager.SpeedometerManager
import com.cva.duoscreen.service.DuoAccessibilityService
import com.cva.duoscreen.service.DuoNotificationService
import com.cva.duoscreen.service.MediaTrackInfo
import com.cva.duoscreen.service.VietmapAlertInfo
import com.cva.duoscreen.service.VietmapParsedData
import com.cva.duoscreen.shizuku.ShizukuHelper

class DuoMainScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private val dm = carContext.getSystemService(CarContext.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSurface: Surface? = null
    private var surfaceWidth: Int = 699
    private var surfaceHeight: Int = 633

    // VirtualDisplay & ImageReader for Map streaming onto Canvas
    private var mapVirtualDisplay: VirtualDisplay? = null
    private var mapImageReader: ImageReader? = null
    private var mapDisplayId: Int = -1
    private var latestMapBitmap: Bitmap? = null

    // Native Widgets Support
    private val speedometerManager = SpeedometerManager(carContext)
    private var currentSpeedState = SpeedState()
    private var currentMediaTrack = MediaTrackInfo()

    private val speedListener: (SpeedState) -> Unit = { state ->
        currentSpeedState = state
        render()
    }

    private val mediaListener: (MediaTrackInfo) -> Unit = { track ->
        currentMediaTrack = track
        render()
    }

    private val vietmapListener: (VietmapAlertInfo) -> Unit = { alert ->
        speedometerManager.setSpeedLimit(alert.speedLimit, alert.alertMessage)
        render()
    }

    private val accessibilityListener: (VietmapParsedData) -> Unit = { parsed ->
        if (parsed.speedLimit != null || parsed.alertText.isNotBlank()) {
            speedometerManager.setSpeedLimit(parsed.speedLimit, parsed.alertText)
            render()
        }
    }

    init {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(this)
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setTitle("Google Maps Toàn Màn Hình")
                    .setOnClickListener {
                        openGoogleMapsFullScreen()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Khởi động lại")
                    .setOnClickListener {
                        startMapDisplay()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun openGoogleMapsFullScreen() {
        try {
            val navIntent = Intent(CarContext.ACTION_NAVIGATE)
            carContext.startCarApp(navIntent)
        } catch (e: Exception) {
            try {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
                carContext.startCarApp(geoIntent)
            } catch (e2: Exception) {
                // ignore
            }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        currentSurface = surfaceContainer.surface
        surfaceWidth = if (surfaceContainer.width > 0) surfaceContainer.width else 699
        surfaceHeight = if (surfaceContainer.height > 0) surfaceContainer.height else 633

        speedometerManager.addListener(speedListener)
        speedometerManager.start()
        DuoNotificationService.addMediaListener(mediaListener)
        DuoNotificationService.addVietmapListener(vietmapListener)
        DuoAccessibilityService.addListener(accessibilityListener)

        startMapDisplay()
        render()
    }

    @Synchronized
    private fun startMapDisplay() {
        releaseMapDisplay()

        val mapW = surfaceWidth
        val mapH = (surfaceHeight * 0.60f).toInt().coerceAtLeast(300)

        try {
            mapImageReader = ImageReader.newInstance(mapW, mapH, PixelFormat.RGBA_8888, 2).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * mapW

                        var bmp = latestMapBitmap
                        if (bmp == null || bmp.width != mapW || bmp.height != mapH) {
                            bmp = Bitmap.createBitmap(mapW + rowPadding / pixelStride, mapH, Bitmap.Config.ARGB_8888)
                        }
                        bmp.copyPixelsFromBuffer(buffer)
                        image.close()

                        latestMapBitmap = bmp
                        render()
                    } catch (e: Exception) {
                        // ignore dropped frames
                    }
                }, mainHandler)
            }

            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            mapVirtualDisplay = dm.createVirtualDisplay(
                "Duo-Car-MapTop",
                mapW,
                mapH,
                200,
                mapImageReader!!.surface,
                flags
            )
            mapDisplayId = mapVirtualDisplay?.display?.displayId ?: -1

            // Tự động bung Google Maps vào màn hình ảo này
            Thread {
                try {
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        ShizukuHelper.executeShell("/data/local/tmp/start_shizuku.sh")
                        Thread.sleep(1000)
                    }
                    if (mapDisplayId != -1) {
                        ShizukuHelper.launchAppOnDisplay("com.google.android.apps.maps", mapDisplayId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun render() {
        val surface = currentSurface ?: return
        if (!surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            if (canvas != null) {
                surfaceWidth = canvas.width
                surfaceHeight = canvas.height
                drawSmartCockpit(canvas, canvas.width, canvas.height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun drawSmartCockpit(canvas: Canvas, w: Int, h: Int) {
        // 1. Background
        canvas.drawColor(Color.parseColor("#10131A"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 2. Header Status
        val headerH = 34f
        paint.color = Color.parseColor("#1D2433")
        val badgeRect = RectF(w * 0.02f, 4f, w * 0.98f, 4f + headerH)
        canvas.drawRoundRect(badgeRect, 12f, 12f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.parseColor("#00D2D3")
        canvas.drawRoundRect(badgeRect, 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#00D2D3")
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("🚗 DUOSCREEN • BẢN ĐỒ & TIỆN ÍCH", w * 0.05f, 26f, paint)

        paint.color = Color.parseColor("#4CAF50")
        paint.textSize = 12f
        paint.isFakeBoldText = false
        val gpsText = if (currentSpeedState.hasGpsFix) "GPS: Sẵn sàng" else "GPS: Đang dò..."
        canvas.drawText(gpsText, w * 0.74f, 25f, paint)

        // 3. Top Panel: Live Map Display (Streamed from Google Maps VirtualDisplay)
        val topMapY = 4f + headerH + 6f
        val topMapH = h * 0.58f
        val mapRect = RectF(w * 0.02f, topMapY, w * 0.98f, topMapY + topMapH)

        val bmp = latestMapBitmap
        if (bmp != null) {
            val srcRect = Rect(0, 0, (w * 0.96f).toInt(), topMapH.toInt())
            canvas.drawBitmap(bmp, srcRect, mapRect, null)
        } else {
            // Placeholder while map is loading
            paint.color = Color.parseColor("#181D28")
            canvas.drawRoundRect(mapRect, 16f, 16f, paint)
            paint.color = Color.WHITE
            paint.textSize = 28f
            canvas.drawText("🗺️", w * 0.46f, topMapY + topMapH * 0.45f, paint)
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("Đang tải Bản đồ Google Maps...", w * 0.28f, topMapY + topMapH * 0.62f, paint)
        }

        // Viền neon bao quanh bản đồ
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E384D")
        canvas.drawRoundRect(mapRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        // 4. Đường phân chia ngang
        val divY = topMapY + topMapH + 6f
        paint.color = Color.parseColor("#00D2D3")
        paint.strokeWidth = 2f
        canvas.drawLine(w * 0.02f, divY, w * 0.98f, divY, paint)

        // 5. Widget Dưới Trái: Tốc độ GPS & Biển báo Vietmap
        val bCardTop = divY + 6f
        val bCardBottom = h - 8f
        val leftCardRect = RectF(w * 0.02f, bCardTop, w * 0.48f, bCardBottom)

        paint.color = Color.parseColor("#181D28")
        canvas.drawRoundRect(leftCardRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (currentSpeedState.isOverSpeed) Color.parseColor("#FF5252") else Color.parseColor("#2E384D")
        canvas.drawRoundRect(leftCardRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#A3BE8C")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("⚡ TỐC ĐỘ GPS", w * 0.06f, bCardTop + 24f, paint)

        // Số tốc độ
        paint.color = if (currentSpeedState.isOverSpeed) Color.parseColor("#FF5252") else Color.WHITE
        paint.textSize = 44f
        paint.isFakeBoldText = true
        val speedStr = currentSpeedState.speedKmh.toString()
        canvas.drawText(speedStr, w * 0.06f, bCardTop + 74f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("km/h", w * 0.28f, bCardTop + 70f, paint)

        // Biển báo tốc độ giới hạn Vietmap (Vòng tròn đỏ viền trắng)
        val signCenterX = w * 0.38f
        val signCenterY = bCardTop + 48f
        val signRadius = 26f
        paint.color = Color.parseColor("#D32F2F")
        canvas.drawCircle(signCenterX, signCenterY, signRadius, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(signCenterX, signCenterY, signRadius - 4f, paint)
        paint.color = Color.BLACK
        paint.textSize = 18f
        paint.isFakeBoldText = true
        val limitStr = (currentSpeedState.speedLimit ?: 60).toString()
        canvas.drawText(limitStr, signCenterX - 10f, signCenterY + 6f, paint)

        // Vietmap Alert text
        paint.color = Color.parseColor("#D8DEE9")
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val alertMsg = if (currentSpeedState.alertText.isNotEmpty()) currentSpeedState.alertText else "Vietmap: Tốc độ chuẩn"
        canvas.drawText(alertMsg, w * 0.05f, bCardBottom - 12f, paint)

        // 6. Widget Dưới Phải: Trình phát nhạc (Coolwalk style)
        val rightCardRect = RectF(w * 0.52f, bCardTop, w * 0.98f, bCardBottom)
        paint.color = Color.parseColor("#181D28")
        canvas.drawRoundRect(rightCardRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E384D")
        canvas.drawRoundRect(rightCardRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#B48EAD")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("🎵 ĐANG PHÁT", w * 0.56f, bCardTop + 24f, paint)

        // Tên bài hát & Nghệ sĩ
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = true
        val title = if (currentMediaTrack.title.length > 15) currentMediaTrack.title.take(14) + "…" else currentMediaTrack.title
        canvas.drawText(title, w * 0.56f, bCardTop + 50f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val artist = if (currentMediaTrack.artist.length > 18) currentMediaTrack.artist.take(17) + "…" else currentMediaTrack.artist
        canvas.drawText(artist, w * 0.56f, bCardTop + 70f, paint)

        // Nút điều khiển nhạc
        val mBtnY = bCardTop + 84f
        val mBtnH = 34f

        // Prev
        val prevRect = RectF(w * 0.56f, mBtnY, w * 0.67f, mBtnY + mBtnH)
        paint.color = Color.parseColor("#252C3D")
        canvas.drawRoundRect(prevRect, 8f, 8f, paint)
        paint.color = Color.WHITE
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("⏮", w * 0.59f, mBtnY + 23f, paint)

        // Play/Pause
        val playRect = RectF(w * 0.70f, mBtnY, w * 0.81f, mBtnY + mBtnH)
        paint.color = Color.parseColor("#00796B")
        canvas.drawRoundRect(playRect, 8f, 8f, paint)
        paint.color = Color.WHITE
        val playIcon = if (currentMediaTrack.isPlaying) "⏸" else "▶"
        canvas.drawText(playIcon, w * 0.74f, mBtnY + 24f, paint)

        // Next
        val nextRect = RectF(w * 0.84f, mBtnY, w * 0.95f, mBtnY + mBtnH)
        paint.color = Color.parseColor("#252C3D")
        canvas.drawRoundRect(nextRect, 8f, 8f, paint)
        paint.color = Color.WHITE
        canvas.drawText("⏭", w * 0.87f, mBtnY + 23f, paint)
    }

    override fun onClick(x: Float, y: Float) {
        val w = surfaceWidth.toFloat()
        val h = surfaceHeight.toFloat()
        val divY = 4f + 34f + 6f + h * 0.58f + 6f
        val bCardTop = divY + 6f
        val mBtnY = bCardTop + 84f
        val mBtnH = 34f

        // Nhấn nút điều khiển nhạc
        if (y in mBtnY..(mBtnY + mBtnH)) {
            if (x in (w * 0.56f)..(w * 0.67f)) {
                DuoNotificationService.skipPrevious()
                render()
                return
            }
            if (x in (w * 0.70f)..(w * 0.81f)) {
                DuoNotificationService.playOrPause()
                render()
                return
            }
            if (x in (w * 0.84f)..(w * 0.95f)) {
                DuoNotificationService.skipNext()
                render()
                return
            }
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        currentSurface = null
        speedometerManager.removeListener(speedListener)
        speedometerManager.stop()
        DuoNotificationService.removeMediaListener(mediaListener)
        DuoNotificationService.removeVietmapListener(vietmapListener)
        DuoAccessibilityService.removeListener(accessibilityListener)
        releaseMapDisplay()
    }

    private fun releaseMapDisplay() {
        try {
            mapVirtualDisplay?.release()
            mapVirtualDisplay = null
            mapImageReader?.close()
            mapImageReader = null
            mapDisplayId = -1
            latestMapBitmap = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {}
    override fun onStableAreaChanged(stableArea: Rect) {}
}
