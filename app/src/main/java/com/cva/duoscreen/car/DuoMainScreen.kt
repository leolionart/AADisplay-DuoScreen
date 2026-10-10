package com.cva.duoscreen.car

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dashboardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var renderPending = false
    private val renderRunnable = Runnable {
        renderPending = false
        renderNow()
    }

    private var currentSurface: Surface? = null
    private var surfaceWidth: Int = 699
    private var surfaceHeight: Int = 633

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
        // Thanh Action trên đỉnh màn hình xe: Bấm chuẩn 100% Android Auto
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🗺️ Google Maps")
                    .setOnClickListener {
                        openGoogleMaps()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⚡ Vietmap Live")
                    .setOnClickListener {
                        openVietmapLive()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🎵 YT Music")
                    .setOnClickListener {
                        openYouTubeMusic()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun openGoogleMaps() {
        try {
            val navIntent = Intent(CarContext.ACTION_NAVIGATE)
            carContext.startCarApp(navIntent)
        } catch (e: Exception) {
            try {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
                carContext.startCarApp(geoIntent)
            } catch (e2: Exception) {
                ShizukuHelper.launchAppOnDisplay("com.google.android.apps.maps", 0)
            }
        }
    }

    private fun openVietmapLive() {
        // Kích hoạt Vietmap Live (chạy nổi hoặc mở app)
        ShizukuHelper.launchAppOnDisplay("vn.vietmap.live", 0)
    }

    private fun openYouTubeMusic() {
        ShizukuHelper.launchAppOnDisplay("com.google.android.apps.youtube.music", 0)
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

        render()
    }

    private fun render() {
        if (renderPending) return
        renderPending = true
        mainHandler.removeCallbacks(renderRunnable)
        mainHandler.post(renderRunnable)
    }

    @Synchronized
    private fun renderNow() {
        val surface = currentSurface ?: return
        if (!surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            if (canvas != null) {
                surfaceWidth = canvas.width
                surfaceHeight = canvas.height
                drawDashboard(canvas, canvas.width, canvas.height)
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
    private fun drawDashboard(canvas: Canvas, w: Int, h: Int) {
        // 1. Background (Deep navy space)
        canvas.drawColor(Color.parseColor("#0F141C"))
        val paint = dashboardPaint
        paint.reset()
        paint.isAntiAlias = true
        // 2. Header Status
        val headerH = 36f
        paint.color = Color.parseColor("#1B2230")
        val badgeRect = RectF(w * 0.02f, 8f, w * 0.98f, 8f + headerH)
        canvas.drawRoundRect(badgeRect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#00D2D3")
        canvas.drawRoundRect(badgeRect, 14f, 14f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#00D2D3")
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("🚗 DUOSCREEN AA • COCKPIT DẪN ĐƯỜNG", w * 0.05f, 32f, paint)

        paint.color = Color.parseColor("#4CAF50")
        paint.textSize = 12f
        paint.isFakeBoldText = false
        val gpsText = if (currentSpeedState.hasGpsFix) "GPS: Sẵn sàng" else "GPS: Đang dò..."
        canvas.drawText(gpsText, w * 0.74f, 31f, paint)

        // 3. Top Large Card: Hướng dẫn mở nhanh bản đồ
        val topCardTop = 8f + headerH + 10f
        val topCardH = h * 0.52f
        val topRect = RectF(w * 0.02f, topCardTop, w * 0.98f, topCardTop + topCardH)

        paint.color = Color.parseColor("#161D2A")
        canvas.drawRoundRect(topRect, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E3A4E")
        canvas.drawRoundRect(topRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 36f
        canvas.drawText("🗺️", w * 0.46f, topCardTop + 65f, paint)

        paint.textSize = 20f
        paint.isFakeBoldText = true
        paint.color = Color.WHITE
        canvas.drawText("BẢN ĐỒ DẪN ĐƯỜNG", w * 0.28f, topCardTop + 115f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#88C0D0")
        canvas.drawText("Chọn các nút trên thanh Menu đỉnh màn hình:", w * 0.16f, topCardTop + 150f, paint)

        // Nút hướng dẫn trực quan
        val btnGuideTop = topCardTop + 175f
        val btnGuideH = 46f
        val btn1Rect = RectF(w * 0.08f, btnGuideTop, w * 0.92f, btnGuideTop + btnGuideH)
        paint.color = Color.parseColor("#00796B")
        canvas.drawRoundRect(btn1Rect, 12f, 12f, paint)
        paint.color = Color.WHITE
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("🗺️  MỞ GOOGLE MAPS (MENU TRÊN)", w * 0.20f, btnGuideTop + 29f, paint)

        val btn2Top = btnGuideTop + btnGuideH + 12f
        val btn2Rect = RectF(w * 0.08f, btn2Top, w * 0.92f, btn2Top + btnGuideH)
        paint.color = Color.parseColor("#E65100")
        canvas.drawRoundRect(btn2Rect, 12f, 12f, paint)
        paint.color = Color.WHITE
        canvas.drawText("⚡  MỞ VIETMAP LIVE (MENU TRÊN)", w * 0.22f, btn2Top + 29f, paint)

        // 4. Đường phân chia ngang
        val divY = topCardTop + topCardH + 10f
        paint.color = Color.parseColor("#00D2D3")
        paint.strokeWidth = 2f
        canvas.drawLine(w * 0.02f, divY, w * 0.98f, divY, paint)

        // 5. Widget Dưới Trái: Tốc độ GPS & Biển báo giới hạn Vietmap
        val bCardTop = divY + 8f
        val bCardBottom = h - 10f
        val leftCardRect = RectF(w * 0.02f, bCardTop, w * 0.48f, bCardBottom)

        paint.color = Color.parseColor("#161D2A")
        canvas.drawRoundRect(leftCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (currentSpeedState.isOverSpeed) Color.parseColor("#FF5252") else Color.parseColor("#2E3A4E")
        canvas.drawRoundRect(leftCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#A3BE8C")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("⚡ TỐC ĐỘ GPS", w * 0.06f, bCardTop + 26f, paint)

        // Số tốc độ
        paint.color = if (currentSpeedState.isOverSpeed) Color.parseColor("#FF5252") else Color.WHITE
        paint.textSize = 46f
        paint.isFakeBoldText = true
        val speedStr = currentSpeedState.speedKmh.toString()
        canvas.drawText(speedStr, w * 0.06f, bCardTop + 80f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("km/h", w * 0.28f, bCardTop + 76f, paint)

        // Biển báo tốc độ giới hạn Vietmap (Vòng tròn đỏ viền trắng)
        val signCenterX = w * 0.38f
        val signCenterY = bCardTop + 54f
        val signRadius = 26f
        paint.color = Color.parseColor("#D32F2F")
        canvas.drawCircle(signCenterX, signCenterY, signRadius, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(signCenterX, signCenterY, signRadius - 4f, paint)
        paint.color = Color.BLACK
        paint.textSize = 18f
        paint.isFakeBoldText = true
        val limitStr = (currentSpeedState.speedLimit ?: 60).toString()
        canvas.drawText(limitStr, signCenterX - 11f, signCenterY + 6f, paint)

        // Vietmap Alert Banner
        paint.color = Color.parseColor("#D8DEE9")
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val alertMsg = if (currentSpeedState.alertText.isNotEmpty()) currentSpeedState.alertText else "Vietmap: Tốc độ chuẩn"
        canvas.drawText(alertMsg, w * 0.05f, bCardBottom - 14f, paint)

        // 6. Widget Dưới Phải: Trình phát nhạc
        val rightCardRect = RectF(w * 0.52f, bCardTop, w * 0.98f, bCardBottom)
        paint.color = Color.parseColor("#161D2A")
        canvas.drawRoundRect(rightCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E3A4E")
        canvas.drawRoundRect(rightCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#B48EAD")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("🎵 ĐANG PHÁT", w * 0.56f, bCardTop + 26f, paint)

        // Tên bài hát & Nghệ sĩ
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = true
        val title = if (currentMediaTrack.title.length > 15) currentMediaTrack.title.take(14) + "…" else currentMediaTrack.title
        canvas.drawText(title, w * 0.56f, bCardTop + 54f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val artist = if (currentMediaTrack.artist.length > 18) currentMediaTrack.artist.take(17) + "…" else currentMediaTrack.artist
        canvas.drawText(artist, w * 0.56f, bCardTop + 76f, paint)

        // Trạng thái phát
        paint.color = Color.parseColor("#A3BE8C")
        paint.textSize = 11f
        val playStateText = if (currentMediaTrack.isPlaying) "▶ Đang chạy" else "⏸ Tạm dừng"
        canvas.drawText(playStateText, w * 0.56f, bCardBottom - 14f, paint)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mainHandler.removeCallbacks(renderRunnable)
        renderPending = false
        currentSurface = null
        speedometerManager.removeListener(speedListener)
        speedometerManager.stop()
        DuoNotificationService.removeMediaListener(mediaListener)
        DuoNotificationService.removeVietmapListener(vietmapListener)
        DuoAccessibilityService.removeListener(accessibilityListener)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {}
    override fun onStableAreaChanged(stableArea: Rect) {}
}
