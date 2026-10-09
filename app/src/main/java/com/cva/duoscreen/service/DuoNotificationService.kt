package com.cva.duoscreen.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

data class MediaTrackInfo(
    val title: String = "Chưa phát nhạc",
    val artist: String = "Mở app nghe nhạc để điều khiển",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val packageName: String = ""
)

data class VietmapAlertInfo(
    val speedLimit: Int? = null,
    val alertMessage: String = "",
    val hasAlert: Boolean = false,
    val timestamp: Long = 0L
)

class DuoNotificationService : NotificationListenerService() {

    companion object {
        private var instance: DuoNotificationService? = null
        private val mediaListeners = CopyOnWriteArrayList<(MediaTrackInfo) -> Unit>()
        private val vietmapListeners = CopyOnWriteArrayList<(VietmapAlertInfo) -> Unit>()

        var currentTrack: MediaTrackInfo = MediaTrackInfo()
            private set
        var currentVietmapAlert: VietmapAlertInfo = VietmapAlertInfo()
            private set

        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, DuoNotificationService::class.java).flattenToString()
            return enabledListeners.contains(myComponent)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun addMediaListener(listener: (MediaTrackInfo) -> Unit) {
            mediaListeners.add(listener)
            listener(currentTrack)
        }

        fun removeMediaListener(listener: (MediaTrackInfo) -> Unit) {
            mediaListeners.remove(listener)
        }

        fun addVietmapListener(listener: (VietmapAlertInfo) -> Unit) {
            vietmapListeners.add(listener)
            listener(currentVietmapAlert)
        }

        fun removeVietmapListener(listener: (VietmapAlertInfo) -> Unit) {
            vietmapListeners.remove(listener)
        }

        fun playOrPause() {
            instance?.togglePlayPause()
        }

        fun skipNext() {
            instance?.next()
        }

        fun skipPrevious() {
            instance?.previous()
        }
    }

    private var activeController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaInfo()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaInfo()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onSessionsChanged(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupMediaSessionManager()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            activeController?.unregisterCallback(controllerCallback)
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        setupMediaSessionManager()
        checkActiveNotifications()
    }

    private fun setupMediaSessionManager() {
        try {
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val component = ComponentName(this, DuoNotificationService::class.java)
            val controllers = sessionManager.getActiveSessions(component)
            onSessionsChanged(controllers)
            sessionManager.removeOnActiveSessionsChangedListener(sessionListener)
            sessionManager.addOnActiveSessionsChangedListener(sessionListener, component)
        } catch (e: SecurityException) {
            // Permission not yet granted
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)
        activeController = controllers?.firstOrNull()
        activeController?.registerCallback(controllerCallback)
        updateMediaInfo()
    }

    private fun updateMediaInfo() {
        val controller = activeController
        if (controller == null) {
            currentTrack = MediaTrackInfo()
            notifyMediaChanged()
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Đang phát nhạc"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: controller.packageName

        var art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (art == null) {
            art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }

        currentTrack = MediaTrackInfo(
            title = title,
            artist = artist,
            albumArt = art,
            isPlaying = isPlaying,
            packageName = controller.packageName
        )
        notifyMediaChanged()
    }

    private fun notifyMediaChanged() {
        mainHandler.post {
            for (listener in mediaListeners) {
                listener(currentTrack)
            }
        }
    }

    fun togglePlayPause() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun next() {
        activeController?.transportControls?.skipToNext()
    }

    fun previous() {
        activeController?.transportControls?.skipToPrevious()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName.contains("vietmap", ignoreCase = true)) {
            parseVietmapNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName.contains("vietmap", ignoreCase = true)) {
            currentVietmapAlert = VietmapAlertInfo()
            notifyVietmapChanged()
        }
    }

    private fun checkActiveNotifications() {
        try {
            val notifications = activeNotifications ?: return
            for (sbn in notifications) {
                if (sbn.packageName.contains("vietmap", ignoreCase = true)) {
                    parseVietmapNotification(sbn)
                    break
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun parseVietmapNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullContent = "$title $text $bigText".trim()

        var limit: Int? = null
        // Extract speed limits like "60km/h", "giới hạn: 80", "tốc độ 50", etc.
        val speedPattern = Pattern.compile("(?i)(?:giới hạn|tốc độ|limit)?\\s*(\\d{2,3})\\s*(?:km/?h)?")
        val matcher = speedPattern.matcher(fullContent)
        while (matcher.find()) {
            val numStr = matcher.group(1)
            val num = numStr?.toIntOrNull()
            if (num != null && num in 20..140) {
                limit = num
                break
            }
        }

        // Clean up alert text
        var alert = fullContent
        if (alert.length > 80) {
            alert = alert.substring(0, 80) + "..."
        }

        currentVietmapAlert = VietmapAlertInfo(
            speedLimit = limit,
            alertMessage = if (alert.isNotBlank()) alert else "Vietmap Live đang giám sát",
            hasAlert = true,
            timestamp = System.currentTimeMillis()
        )
        notifyVietmapChanged()
    }

    private fun notifyVietmapChanged() {
        mainHandler.post {
            for (listener in vietmapListeners) {
                listener(currentVietmapAlert)
            }
        }
    }
}
