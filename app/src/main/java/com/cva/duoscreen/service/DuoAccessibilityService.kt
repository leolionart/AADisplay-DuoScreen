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
import com.cva.duoscreen.core.ShizukuShell
import com.cva.duoscreen.source.VietmapSource

class DuoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DuoAccessibility"
        private const val VIETMAP_PKG = "vn.vietmap.live"

        private var instance: DuoAccessibilityService? = null
        private val mainHandler = Handler(Looper.getMainLooper())

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
            if (!ShizukuShell.isShizukuAvailable() || !ShizukuShell.hasPermission()) {
                onComplete(false)
                return
            }

            Thread {
                val serviceComponent = ComponentName(context, DuoAccessibilityService::class.java).flattenToString()
                val currentServices = ShizukuShell.executeShell("settings get secure enabled_accessibility_services").trim()

                val newServices = if (currentServices.isEmpty() || currentServices == "null") {
                    serviceComponent
                } else if (!currentServices.contains(serviceComponent)) {
                    "$currentServices:$serviceComponent"
                } else {
                    currentServices
                }

                ShizukuShell.executeShell("settings put secure enabled_accessibility_services $newServices")
                ShizukuShell.executeShell("settings put secure accessibility_enabled 1")

                mainHandler.postDelayed({
                    val enabled = isAccessibilityEnabled(context)
                    onComplete(enabled)
                }, 500)
            }.start()
        }

        fun dumpCurrentVietmapTree(): List<String> {
            val service = instance ?: return listOf("AccessibilityService chưa chạy!")
            return service.dumpAllVietmapNodes()
        }
    }

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
        if (pkg == VIETMAP_PKG) {
            parseAllInteractiveWindows()
            return
        }
        handleAndroidBoxAutomations(event)
    }

    private fun handleAndroidBoxAutomations(event: AccessibilityEvent) {
        val store = com.cva.duoscreen.ui.SettingsStore.get(this)
        if (!store.isBoxAutoUnlockEnabled) return

        val pkg = event.packageName?.toString() ?: ""
        val targetPackages = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.gms",
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )

        if (pkg !in targetPackages) return

        val root = rootInActiveWindow ?: event.source ?: return
        try {
            findAndClickAutoApprovals(root)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun findAndClickAutoApprovals(node: AccessibilityNodeInfo?) {
        if (node == null) return

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val lower = "$text $desc".lowercase().trim()

        // Auto-unlock if "Mở khóa để tiếp tục" is shown
        if (lower.contains("mở khóa để tiếp tục") || lower.contains("unlock to continue") || lower.contains("mở khóa điện thoại")) {
            Log.i(TAG, "DuoAccessibility: Phát hiện nhắc mở khóa, kích hoạt dismiss keyguard...")
            com.cva.duoscreen.box.VehicleBoxManager.wakeAndDismissKeyguard()
        }

        val isConfirmButton = lower == "ok" ||
                lower == "tiếp tục" || lower == "continue" ||
                lower == "cho phép" || lower == "allow" ||
                lower == "đồng ý" || lower == "agree" || lower == "i agree" ||
                lower == "bắt đầu ngay" || lower == "start now" ||
                lower == "luôn cho phép" || lower == "always allow" ||
                lower == "luôn luôn" || lower == "always" ||
                lower == "bỏ qua" || lower == "skip" ||
                lower == "đóng" || lower == "close" ||
                lower == "hoàn tất" || lower == "done" ||
                lower == "xác nhận" || lower == "confirm"

        if (isConfirmButton && (node.isClickable || node.parent?.isClickable == true)) {
            Log.i(TAG, "DuoAccessibility: Tự động click phê duyệt/bỏ qua dialog xe: '$text'")
            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            return
        }

        for (i in 0 until node.childCount) {
            findAndClickAutoApprovals(node.getChild(i))
        }
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
                        extractNodeTexts(root, collectedTexts, collectedNodesInfo)
                    }
                }
            } else {
                rootInActiveWindow?.let { root ->
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg.contains("vietmap", ignoreCase = true)) {
                        extractNodeTexts(root, collectedTexts, collectedNodesInfo)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing accessibility windows: ${e.message}")
        }

        if (collectedTexts.isNotEmpty()) {
            VietmapSource.processParsedTexts(collectedTexts)
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