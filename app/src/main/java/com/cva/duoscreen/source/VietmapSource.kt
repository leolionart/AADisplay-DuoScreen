package com.cva.duoscreen.source

import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.Pattern

object VietmapSource {
    private const val TAG = "VietmapSource"

    private val validLimits = setOf(20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)
    private val distanceRegex = Pattern.compile("\\b\\d+\\s*m\\b")
    private val speedRegex = Pattern.compile("(?i)(?:giới hạn|tốc độ|limit)?\\s*(\\d{2,3})\\s*(?:km/?h)?")

    fun processParsedTexts(texts: List<String>) {
        var detectedLimit: Int? = null
        val alerts = mutableListOf<String>()

        for (t in texts) {
            val lower = t.lowercase()
            if (lower.contains("%") || lower.contains("pin") || lower.contains("sạc") ||
                lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("usb") ||
                lower.contains("thông báo") || lower.contains("hệ thống")) {
                continue
            }

            val num = t.toIntOrNull()
            if (num != null) {
                if (validLimits.contains(num)) {
                    detectedLimit = num
                }
                continue
            }
            val isPureSpeed = lower.matches(Regex("(?i)^\\d+\\s*(?:km/?h)?$"))
            if (isPureSpeed) {
                continue
            }

            val matcher = speedRegex.matcher(t)
            if (matcher.find()) {
                val foundNum = matcher.group(1)?.toIntOrNull()
                if (foundNum != null && validLimits.contains(foundNum)) {
                    detectedLimit = foundNum
                }
            }

            if (lower.contains("camera") || lower.contains("phạt nguội") ||
                lower.contains("dân cư") || lower.contains("giới hạn") ||
                lower.contains("vượt") || lower.contains("tốc độ") ||
                distanceRegex.matcher(lower).find()
            ) {
                alerts.add(t)
            }
        }

        val currentAlert = SpeedAlertRepository.current

        val limitToUse = detectedLimit ?: currentAlert.speedLimit
        val alertString = if (alerts.isNotEmpty()) {
            alerts.distinct().joinToString(" • ")
        } else {
            ""
        }

        // We forward {speedLimit, alertText} to repository.
        // We can pass detectedSpeed as well? The repo says:
        // "Merges GpsSpeedSource (speed, hasGps) with VietmapSource (speedLimit, alertText)"
        // But the previous AccessibilityService also detected current speed from Vietmap.
        // Actually the prompt says: "forwards {speedLimit, alertText} to SpeedAlertRepository".
        // I will just push limitToUse and alertString.
        SpeedAlertRepository.updateVietmapData(limitToUse, alertString)
    }

    fun parseVietmapNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullContent = "$title $text $bigText".trim()

        var limit: Int? = null
        val matcher = speedRegex.matcher(fullContent)
        while (matcher.find()) {
            val numStr = matcher.group(1)
            val num = numStr?.toIntOrNull()
            if (num != null && validLimits.contains(num)) {
                limit = num
                break
            }
        }

        var alert = fullContent
        if (alert.length > 80) {
            alert = alert.substring(0, 80) + "..."
        }
        
        val isPureSpeed = alert.matches(Regex("(?i)^\\d+\\s*(?:km/?h)?$"))
        val alertString = if (alert.isNotBlank() && !isPureSpeed) alert else ""
        SpeedAlertRepository.updateVietmapData(limit, alertString)
    }
    
    fun clearNotificationData() {
        SpeedAlertRepository.updateVietmapData(null, "")
    }
}