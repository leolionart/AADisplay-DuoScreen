package com.cva.duoscreen.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.cva.duoscreen.source.SpeedAlert
import android.text.TextPaint
import android.text.TextUtils

object BubbleRenderer {
    private val red = Color.rgb(239, 68, 68)
    private val muted = Color.rgb(153, 168, 190)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B1018")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#253247")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val speedLimitRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val speedLimitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }
    private val currentSpeedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
        textSize = 48f
    }
    private val currentSpeedUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.LEFT
        textSize = 20f
    }
    private val alertTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 22f
    }

    val cardRect = RectF()
    private val ellipsisPaint = TextPaint(alertTextPaint)

    // Current position of the bubble (top-left by default, supports custom X/Y or snapping)
    var currentX: Float = -1f
    var currentY: Float = -1f

    fun contains(x: Float, y: Float): Boolean {
        return cardRect.contains(x, y)
    }

    fun moveTo(x: Float, y: Float, viewW: Int, viewH: Int) {
        val w = cardRect.width()
        val h = cardRect.height()
        currentX = x.coerceIn(12f, (viewW - w - 12f).coerceAtLeast(12f))
        currentY = y.coerceIn(12f, (viewH - h - 12f).coerceAtLeast(12f))
    }

    fun cycleCorner(viewW: Int, viewH: Int) {
        val w = if (cardRect.width() > 0) cardRect.width() else 260f
        val h = if (cardRect.height() > 0) cardRect.height() else 100f
        val margin = 20f

        val left = margin
        val right = (viewW - w - margin).coerceAtLeast(margin)
        val top = margin
        val bottom = (viewH - h - margin).coerceAtLeast(margin)

        // Cycle: Top-Left -> Top-Right -> Bottom-Right -> Bottom-Left -> Top-Left
        val isRight = currentX > (viewW / 2f) - (w / 2f)
        val isBottom = currentY > (viewH / 2f) - (h / 2f)

        when {
            !isRight && !isBottom -> { currentX = right; currentY = top }       // to Top-Right
            isRight && !isBottom  -> { currentX = right; currentY = bottom }    // to Bottom-Right
            isRight && isBottom   -> { currentX = left; currentY = bottom }     // to Bottom-Left
            else                  -> { currentX = left; currentY = top }        // back to Top-Left
        }
    }

    fun draw(canvas: Canvas, alert: SpeedAlert, viewW: Int, viewH: Int) {
        val margin = 20f
        val padding = 16f
        
        // Keep the speed, unit and warning inside the card at 800x480.
        val cardWidth = (viewW * 0.36f).coerceIn(240f, 320f).coerceAtMost(viewW - 2f * margin)
        if (cardWidth < 180f || viewH < 2f * margin + 90f) return
        val cardHeight = 96f

        if (currentX < 0 || currentY < 0) {
            currentX = margin
            currentY = margin
        } else {
            currentX = currentX.coerceIn(margin, (viewW - cardWidth - margin).coerceAtLeast(margin))
            currentY = currentY.coerceIn(margin, (viewH - cardHeight - margin).coerceAtLeast(margin))
        }

        cardRect.set(currentX, currentY, currentX + cardWidth, currentY + cardHeight)
        
        // Draw card background and border
        canvas.drawRoundRect(cardRect, 16f, 16f, cardPaint)
        canvas.drawRoundRect(cardRect, 16f, 16f, borderPaint)
        // 1. Draw Speed Limit Sign (Left)
        val signRadius = 28f
        val signCx = cardRect.left + padding + signRadius
        val signCy = cardRect.top + cardHeight / 2f
        
        val textY = signCy - (speedLimitTextPaint.descent() + speedLimitTextPaint.ascent()) / 2f
        if (alert.speedLimit != null && alert.speedLimit > 0) {
            speedLimitRingPaint.color = red
            canvas.drawCircle(signCx, signCy, signRadius, speedLimitRingPaint)
            speedLimitTextPaint.color = Color.WHITE
            canvas.drawText(alert.speedLimit.toString(), signCx, textY, speedLimitTextPaint)
        } else {
            speedLimitRingPaint.color = borderPaint.color
            canvas.drawCircle(signCx, signCy, signRadius, speedLimitRingPaint)
            speedLimitTextPaint.color = muted
            canvas.drawText("--", signCx, textY, speedLimitTextPaint)
        }
        
        // 2. Draw Current Speed (Middle-Right)
        val speedX = signCx + signRadius + 16f
        val topHalfY = cardRect.top + padding + 36f
        
        currentSpeedPaint.color = if (alert.isOverLimit) red else Color.rgb(125, 211, 252)
        
        val speedStr = alert.speedKmh.toString()
        val availableWidth = (cardRect.right - padding - speedX).coerceAtLeast(0f)
        val unitWidth = currentSpeedUnitPaint.measureText("km/h") + 6f
        currentSpeedPaint.textSize = 48f
        val speedWidth = currentSpeedPaint.measureText(speedStr)
        val speedScale = if (speedWidth + unitWidth > availableWidth && speedWidth > 0f) {
            ((availableWidth - unitWidth) / speedWidth).coerceIn(0.3f, 1f)
        } else 1f
        currentSpeedPaint.textSize = 48f * speedScale
        canvas.drawText(speedStr, speedX, topHalfY, currentSpeedPaint)
        canvas.drawText("km/h", speedX + currentSpeedPaint.measureText(speedStr) + 6f, topHalfY, currentSpeedUnitPaint)

        val cleanAlert = if (alert.alertText.matches(Regex("(?i)^\\s*\\d+\\s*(?:km/?h)?\\s*$"))) {
            ""
        } else {
            alert.alertText
        }
        val warning = when {
            cleanAlert.isNotEmpty() -> cleanAlert
            !alert.hasGps -> "Đang tìm tín hiệu GPS..."
            alert.speedKmh == 0 -> "Đang dừng xe"
            else -> "Giám sát an toàn"
        }
        alertTextPaint.color = if (alert.isOverLimit) red else muted
        ellipsisPaint.color = alertTextPaint.color
        val text = TextUtils.ellipsize(warning, ellipsisPaint, availableWidth, TextUtils.TruncateAt.END)
        canvas.drawText(text, 0, text.length, speedX, cardRect.bottom - padding - 4f, alertTextPaint)
    }
}
