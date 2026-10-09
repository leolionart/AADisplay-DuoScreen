package com.cva.duoscreen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.cva.duoscreen.shizuku.ShizukuHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

data class VietmapParsedData(
    val speedLimit: Int? = null,
    val currentSpeed: Int? = null,
    val alertText: String = "",
    val rawTexts: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class DuoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DuoAccessibility"
        private const val VIETMAP_PKG = "vn.vietmap.live"

        private var instance: DuoAccessibilityService? = null
        private val listeners = CopyOnWriteArrayList<(VietmapParsedData) -> Unit>()
        private val mainHandler = Handler(Looper.getMainLooper())

        var latestData: VietmapParsedData = VietmapParsedData()
            private set

        fun isRunning(): Boolean = instance != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, DuoAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(expectedComponentName)
        }

        fun enableViaShizuku(context: Context, onComplete: (Boolean) -> Unit) {
            if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.hasPermission()) {
                onComplete(false)
                return
            }

            Thread {
                val serviceComponent = ComponentName(context, DuoAccessibilityService::class.java).flattenToString()
                val currentServices = ShizukuHelper.executeShell("settings get secure enabled_accessibility_services").trim()

                val newServices = if (currentServices.isEmpty() || currentServices == "null") {
                    serviceComponent
                } else if (!currentServices.contains(serviceComponent)) {
                    "$currentServices:$serviceComponent"
                } else {
                    currentServices
                }

                ShizukuHelper.executeShell("settings put secure enabled_accessibility_services $newServices")
                ShizukuHelper.executeShell("settings put secure accessibility_enabled 1")

                mainHandler.postDelayed({
                    val enabled = isAccessibilityEnabled(context)
                    onComplete(enabled)
                }, 500)
            }.start()
        }

        fun addListener(listener: (VietmapParsedData) -> Unit) {
            listeners.add(listener)
            listener(latestData)
        }

        fun removeListener(listener: (VietmapParsedData) -> Unit) {
            listeners.remove(listener)
        }

        fun dumpCurrentVietmapTree(): List<String> {
            val service = instance ?: return listOf("AccessibilityService chưa chạy!")
            return service.dumpAllVietmapNodes()
        }
    }

    private val speedRegex = Pattern.compile("(?i)(?:giới hạn|tốc độ|limit)?\\s*(\\d{2,3})\\s*(?:km/?h)?")
    private val validLimits = setOf(20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

    private val periodicRunnable = object : Runnable {
        override fun run() {
            parseAllInteractiveWindows()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "DuoAccessibilityService connected and ready to parse Vietmap Live!")

        serviceInfo = serviceInfo?.apply {
            packageNames = null
            flags = flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        mainHandler.post(periodicRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        if (pkg != VIETMAP_PKG) return

        parseAllInteractiveWindows()
    }

    override fun onInterrupt() {
        Log.w(TAG, "DuoAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(periodicRunnable)
        instance = null
    }
    fun parseAllInteractiveWindows() {
        val collectedTexts = mutableListOf<String>()
        val collectedNodesInfo = mutableListOf<String>()

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (window in windowList) {
                    val root = window.root
                    val pkg = root?.packageName?.toString() ?: ""

                    // Skip SystemUI and our own app
                    if (pkg == "com.android.systemui" || pkg == "com.cva.duoscreen") continue

                    if (pkg.contains("vietmap", ignoreCase = true)) {
                        Log.d(TAG, "FOUND VIETMAP WINDOW! ID=${window.id}, type=${window.type}, layer=${window.layer}")
                        extractNodeTexts(root, collectedTexts, collectedNodesInfo)
                        Log.d(TAG, "Vietmap nodes extracted: $collectedNodesInfo")
                    }
                }
            } else {
                rootInActiveWindow?.let { root ->
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg.contains("vietmap", ignoreCase = true)) {
                        Log.d(TAG, "FOUND VIETMAP in rootInActiveWindow!")
                        extractNodeTexts(root, collectedTexts, collectedNodesInfo)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing accessibility windows: ${e.message}")
        }

        if (collectedTexts.isNotEmpty()) {
            Log.d(TAG, "Nodes collected from overlays: $collectedTexts")
            processParsedTexts(collectedTexts)
        }
    }

    private fun extractNodeTexts(
        node: AccessibilityNodeInfo?,
        outTexts: MutableList<String>,
        outDebugList: MutableList<String>
    ) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName ?: "no_id"
        val cls = node.className ?: "null"

        outDebugList.add("[$cls, id=$viewId, childCount=${node.childCount}, text='$text', desc='$desc']")

        if (!text.isNullOrEmpty()) {
            outTexts.add(text)
        }
        if (!desc.isNullOrEmpty() && desc != text) {
            outTexts.add(desc)
        }

        for (i in 0 until node.childCount) {
            extractNodeTexts(node.getChild(i), outTexts, outDebugList)
        }
    }

    private fun processParsedTexts(texts: List<String>) {
        var detectedLimit: Int? = null
        var detectedSpeed: Int? = null
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
                // If it's a standard speed limit number (e.g. 40, 50, 60, 70, 80, 90, 100, 120...)
                if (validLimits.contains(num)) {
                    detectedLimit = num
                } else if (num in 0..199) {
                    detectedSpeed = num
                }
                continue
            }

            // Check pattern e.g. "60 km/h" or "giới hạn 80"
            val matcher = speedRegex.matcher(t)
            if (matcher.find()) {
                val foundNum = matcher.group(1)?.toIntOrNull()
                if (foundNum != null && validLimits.contains(foundNum)) {
                    detectedLimit = foundNum
                }
            }

            // Check alert keywords
            if (lower.contains("camera") || lower.contains("phạt nguội") ||
                lower.contains("dân cư") || lower.contains("giới hạn") ||
                lower.contains("vượt") || lower.contains("tốc độ") ||
                lower.contains("m") || lower.contains("km")
            ) {
                alerts.add(t)
            }
        }

        val limitToUse = detectedLimit ?: latestData.speedLimit
        val alertString = if (alerts.isNotEmpty()) {
            alerts.distinct().joinToString(" • ")
        } else {
            latestData.alertText
        }

        if (limitToUse != latestData.speedLimit || alertString != latestData.alertText || detectedSpeed != latestData.currentSpeed) {
            latestData = VietmapParsedData(
                speedLimit = limitToUse,
                currentSpeed = detectedSpeed ?: latestData.currentSpeed,
                alertText = alertString,
                rawTexts = texts,
                timestamp = System.currentTimeMillis()
            )
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        mainHandler.post {
            for (listener in listeners) {
                listener(latestData)
            }
        }
    }

    fun dumpAllVietmapNodes(): List<String> {
        val debugList = mutableListOf<String>()
        val texts = mutableListOf<String>()
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (w in windowList) {
                    val root = w.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg == "com.cva.duoscreen") continue

                    val isCandidate = pkg.contains("vietmap", ignoreCase = true) ||
                            w.type == AccessibilityWindowInfo.TYPE_SYSTEM
                    if (isCandidate) {
                        debugList.add("--- Cửa sổ [ID: ${w.id}, Type: ${w.type}, Pkg: $pkg] ---")
                        extractNodeTexts(root, texts, debugList)
                    }
                }
            }
        } catch (e: Exception) {
            debugList.add("Lỗi quét: ${e.message}")
        }
        if (debugList.isEmpty()) {
            debugList.add("Không tìm thấy cửa sổ nào của Vietmap Live (hãy chắc chắn bạn đã bật Bong bóng nổi)")
        }
        return debugList
    }
}
