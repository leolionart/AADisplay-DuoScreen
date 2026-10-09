package com.cva.duoscreen.car

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.graphics.Rect
import android.graphics.RectF
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
import com.cva.duoscreen.service.DuoNotificationService
import com.cva.duoscreen.service.MediaTrackInfo
import com.cva.duoscreen.service.VietmapAlertInfo
import com.cva.duoscreen.shizuku.ShizukuHelper

class DuoMainScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var currentSurface: Surface? = null
    private var surfaceWidth: Int = 720
    private var surfaceHeight: Int = 1280

    private val speedometerManager = SpeedometerManager(carContext)
    private var currentSpeedState = SpeedState()
    private var currentMediaTrack = MediaTrackInfo()
    private var currentVietmapAlert = VietmapAlertInfo()

    private val speedListener: (SpeedState) -> Unit = { state ->
        currentSpeedState = state
        render()
    }

    private val mediaListener: (MediaTrackInfo) -> Unit = { track ->
        currentMediaTrack = track
        render()
    }

    private val vietmapListener: (VietmapAlertInfo) -> Unit = { alert ->
        currentVietmapAlert = alert
        speedometerManager.setSpeedLimit(alert.speedLimit, alert.alertMessage)
        render()
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
                    .setTitle("Google Maps")
                    .setOnClickListener {
                        openGoogleMapsOnCar()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Vietmap")
                    .setOnClickListener {
                        ShizukuHelper.launchAppOnDisplay("vn.vietmap.live", 0)
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("YT Music")
                    .setOnClickListener {
                        ShizukuHelper.launchAppOnDisplay("com.google.android.apps.youtube.music", 0)
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        currentSurface = surfaceContainer.surface
        surfaceWidth = if (surfaceContainer.width > 0) surfaceContainer.width else 720
        surfaceHeight = if (surfaceContainer.height > 0) surfaceContainer.height else 1280

        speedometerManager.addListener(speedListener)
        speedometerManager.start()
        DuoNotificationService.addMediaListener(mediaListener)
        DuoNotificationService.addVietmapListener(vietmapListener)

        render()
        openGoogleMapsOnCar()
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
                drawCockpit(canvas, canvas.width, canvas.height)
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

    private fun drawCockpit(canvas: Canvas, w: Int, h: Int) {
        // 1. Background (Deep space navy/slate)
        canvas.drawColor(Color.parseColor("#10131A"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 2. Header Bar
        val headerTop = 8f
        val headerH = 38f
        paint.color = Color.parseColor("#1D2433")
        val badgeRect = RectF(w * 0.02f, headerTop, w * 0.98f, headerTop + headerH)
        canvas.drawRoundRect(badgeRect, 18f, 18f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#00D2D3")
        canvas.drawRoundRect(badgeRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#00D2D3")
        paint.textSize = 17f
        paint.isFakeBoldText = true
        canvas.drawText("🚗 DUOSCREEN AA • TOÀN MÀN HÌNH DỌC", w * 0.05f, headerTop + 26f, paint)

        paint.color = Color.parseColor("#4CAF50")
        paint.textSize = 13f
        paint.isFakeBoldText = false
        val gpsText = if (currentSpeedState.hasGpsFix) "GPS: Sẵn sàng" else "GPS: Đang dò..."
        canvas.drawText(gpsText, w * 0.72f, headerTop + 25f, paint)

        // 3. Top Main Panel (Navigation / Map Card)
        val topCardTop = headerTop + headerH + 8f
        val topCardBottom = h * 0.54f
        val topRect = RectF(w * 0.02f, topCardTop, w * 0.98f, topCardBottom)

        paint.color = Color.parseColor("#181D28")
        canvas.drawRoundRect(topRect, 20f, 20f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E384D")
        canvas.drawRoundRect(topRect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        // Header inside Top Card
        paint.color = Color.WHITE
        paint.textSize = 34f
        canvas.drawText("🗺️", w * 0.05f, topCardTop + 45f, paint)

        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText("BẢN ĐỒ DẪN ĐƯỜNG", w * 0.15f, topCardTop + 40f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText("Chạm để mở Google Maps / Vietmap Live:", w * 0.15f, topCardTop + 68f, paint)

        // Quick Launch Buttons inside Top Card
        val btnH = 60f
        val btn1Top = topCardTop + 85f
        val btn1Rect = RectF(w * 0.05f, btn1Top, w * 0.95f, btn1Top + btnH)
        paint.color = Color.parseColor("#00796B")
        canvas.drawRoundRect(btn1Rect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#4DB6AC")
        canvas.drawRoundRect(btn1Rect, 14f, 14f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 19f
        paint.isFakeBoldText = true
        canvas.drawText("🗺️  MỞ GOOGLE MAPS TOÀN MÀN HÌNH", w * 0.18f, btn1Top + 38f, paint)

        val btn2Top = btn1Top + btnH + 14f
        val btn2Rect = RectF(w * 0.05f, btn2Top, w * 0.95f, btn2Top + btnH)
        paint.color = Color.parseColor("#E65100")
        canvas.drawRoundRect(btn2Rect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#FFB74D")
        canvas.drawRoundRect(btn2Rect, 14f, 14f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        canvas.drawText("⚡  MỞ VIETMAP LIVE", w * 0.32f, btn2Top + 38f, paint)
        // 4. Horizontal Separator
        val divY = h * 0.56f
        paint.color = Color.parseColor("#00D2D3")
        paint.strokeWidth = 3f
        canvas.drawLine(w * 0.02f, divY, w * 0.98f, divY, paint)

        // 5. Bottom Left Card: Speedometer & Vietmap Speed Limit
        val bCardTop = h * 0.58f
        val bCardBottom = h - 14f
        val leftCardRect = RectF(w * 0.02f, bCardTop, w * 0.49f, bCardBottom)

        paint.color = Color.parseColor("#181D28")
        canvas.drawRoundRect(leftCardRect, 18f, 18f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (currentSpeedState.isOverSpeed) Color.parseColor("#E53935") else Color.parseColor("#2E384D")
        canvas.drawRoundRect(leftCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#A3BE8C")
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("⚡ TỐC ĐỘ GPS", w * 0.08f, bCardTop + 30f, paint)

        // Speedometer digits
        paint.color = if (currentSpeedState.isOverSpeed) Color.parseColor("#FF5252") else Color.WHITE
        paint.textSize = 50f
        paint.isFakeBoldText = true
        val speedStr = currentSpeedState.speedKmh.toString()
        canvas.drawText(speedStr, w * 0.08f, bCardTop + 90f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText("km/h", w * 0.32f, bCardTop + 86f, paint)

        // Vietmap Speed Limit Sign (Red circle)
        val signCenterX = w * 0.26f
        val signCenterY = bCardTop + 165f
        val signRadius = 36f
        paint.color = Color.parseColor("#D32F2F")
        canvas.drawCircle(signCenterX, signCenterY, signRadius, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(signCenterX, signCenterY, signRadius - 6f, paint)
        paint.color = Color.BLACK
        paint.textSize = 24f
        paint.isFakeBoldText = true
        val limitStr = (currentSpeedState.speedLimit ?: 60).toString()
        canvas.drawText(limitStr, signCenterX - 15f, signCenterY + 8f, paint)

        paint.color = Color.parseColor("#D8DEE9")
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val alertMsg = if (currentSpeedState.alertText.isNotEmpty()) currentSpeedState.alertText else "Vietmap: Tốc độ chuẩn"
        canvas.drawText(alertMsg, w * 0.07f, bCardBottom - 18f, paint)

        // 6. Bottom Right Card: Music Player
        val rightCardRect = RectF(w * 0.51f, bCardTop, w * 0.98f, bCardBottom)
        paint.color = Color.parseColor("#181D28")
        canvas.drawRoundRect(rightCardRect, 18f, 18f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2E384D")
        canvas.drawRoundRect(rightCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#B48EAD")
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("🎵 ĐANG PHÁT", w * 0.55f, bCardTop + 30f, paint)

        // Music track info
        paint.color = Color.WHITE
        paint.textSize = 15f
        paint.isFakeBoldText = true
        val trackTitle = if (currentMediaTrack.title.length > 14) currentMediaTrack.title.take(13) + "…" else currentMediaTrack.title
        canvas.drawText(trackTitle, w * 0.55f, bCardTop + 65f, paint)

        paint.color = Color.parseColor("#88C0D0")
        paint.textSize = 12f
        paint.isFakeBoldText = false
        val trackArtist = if (currentMediaTrack.artist.length > 18) currentMediaTrack.artist.take(17) + "…" else currentMediaTrack.artist
        canvas.drawText(trackArtist, w * 0.55f, bCardTop + 90f, paint)

        // Playback Buttons
        val mBtnY = bCardTop + 125f
        val mBtnH = 46f

        // Prev [ |< ]
        val prevRect = RectF(w * 0.55f, mBtnY, w * 0.67f, mBtnY + mBtnH)
        paint.color = Color.parseColor("#252C3D")
        canvas.drawRoundRect(prevRect, 10f, 10f, paint)
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("⏮", w * 0.58f, mBtnY + 31f, paint)

        // Play/Pause [ ▶ / ❚❚ ]
        val playRect = RectF(w * 0.69f, mBtnY, w * 0.81f, mBtnY + mBtnH)
        paint.color = Color.parseColor("#00796B")
        canvas.drawRoundRect(playRect, 10f, 10f, paint)
        paint.color = Color.WHITE
        val playIcon = if (currentMediaTrack.isPlaying) "⏸" else "▶"
        canvas.drawText(playIcon, w * 0.73f, mBtnY + 32f, paint)

        // Next [ >| ]
        val nextRect = RectF(w * 0.83f, mBtnY, w * 0.94f, mBtnY + mBtnH)
        paint.color = Color.parseColor("#252C3D")
        canvas.drawRoundRect(nextRect, 10f, 10f, paint)
        paint.color = Color.WHITE
        canvas.drawText("⏭", w * 0.86f, mBtnY + 31f, paint)
    }

    private fun openGoogleMapsOnCar() {
        try {
            val navIntent = Intent(CarContext.ACTION_NAVIGATE)
            carContext.startCarApp(navIntent)
        } catch (e: Exception) {
            try {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
                carContext.startCarApp(geoIntent)
            } catch (e2: Exception) {
                ShizukuHelper.executeShell("am start -a android.intent.action.VIEW -d geo:0,0")
            }
        }
    }

    override fun onClick(x: Float, y: Float) {
        val w = surfaceWidth.toFloat()
        val h = surfaceHeight.toFloat()

        // Check Top Card Launch Buttons
        val headerTop = 10f
        val headerH = 40f
        val topCardTop = headerTop + headerH + 10f
        val btnH = 60f
        val btn1Top = topCardTop + 85f
        val btn2Top = btn1Top + btnH + 14f

        if (x in (w * 0.05f)..(w * 0.95f)) {
            if (y in btn1Top..(btn1Top + btnH)) {
                openGoogleMapsOnCar()
                return
            }
            if (y in btn2Top..(btn2Top + btnH)) {
                ShizukuHelper.launchAppOnDisplay("vn.vietmap.live", 0)
                return
            }
        }

        // Check Bottom Right Media Player Controls
        val bCardTop = h * 0.58f
        val mBtnY = bCardTop + 125f
        val mBtnH = 46f

        if (y in mBtnY..(mBtnY + mBtnH)) {
            if (x in (w * 0.55f)..(w * 0.67f)) {
                DuoNotificationService.skipPrevious()
                render()
                return
            }
            if (x in (w * 0.69f)..(w * 0.81f)) {
                DuoNotificationService.playOrPause()
                render()
                return
            }
            if (x in (w * 0.83f)..(w * 0.94f)) {
                DuoNotificationService.skipNext()
                render()
                return
            }
        }

        // Check Bottom Left Speed Card (Tap to launch Vietmap)
        if (x in (w * 0.04f)..(w * 0.48f) && y in (h * 0.58f)..(h - 14f)) {
            ShizukuHelper.launchAppOnDisplay("vn.vietmap.live", 0)
            return
        }
    }
    override fun onScroll(distanceX: Float, distanceY: Float) {}

    override fun onFling(velocityX: Float, velocityY: Float) {}

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {}

    override fun onVisibleAreaChanged(visibleArea: Rect) {}

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        currentSurface = null
        speedometerManager.removeListener(speedListener)
        speedometerManager.stop()
        DuoNotificationService.removeMediaListener(mediaListener)
        DuoNotificationService.removeVietmapListener(vietmapListener)
    }
}
