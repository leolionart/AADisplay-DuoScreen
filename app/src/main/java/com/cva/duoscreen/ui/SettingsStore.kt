package com.cva.duoscreen.ui

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "duoscreen_prefs"

        const val KEY_MAP_PACKAGE = "pkg_map"
        const val KEY_VIETMAP_PACKAGE = "pkg_vietmap"
        const val KEY_MUSIC_PACKAGE = "pkg_music"

        // Legacy compatibility keys if needed
        const val KEY_PKG_TOP = "pkg_top"
        const val KEY_PKG_BOTTOM_LEFT = "pkg_bottom_left"
        const val KEY_PKG_BOTTOM_RIGHT = "pkg_bottom_right"

        const val KEY_AUTO_START = "pref_auto_start"
        const val KEY_OVER_SPEED_WARNING = "pref_over_speed_warning"
        const val KEY_SHOW_SPEED_BUBBLE = "pref_show_speed_bubble"

        const val KEY_HOTSPOT_SSID = "pref_hotspot_ssid"
        const val KEY_HOTSPOT_PASSWORD = "pref_hotspot_password"
        const val KEY_AGGRESSIVE_HOTSPOT = "pref_aggressive_hotspot"
        const val KEY_BOX_AUTO_UNLOCK = "pref_box_auto_unlock"
        const val KEY_SUPPRESS_WARNINGS = "pref_suppress_warnings"

        const val DEFAULT_HOTSPOT_SSID = "AI Phone"
        const val DEFAULT_MAP_PKG = "com.google.android.apps.maps"
        const val DEFAULT_VIETMAP_PKG = "vn.vietmap.live"
        const val DEFAULT_MUSIC_PKG = "com.google.android.apps.youtube.music"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }

    var mapPackage: String
        get() = prefs.getString(KEY_MAP_PACKAGE, null)
            ?: prefs.getString(KEY_PKG_TOP, DEFAULT_MAP_PKG)
            ?: DEFAULT_MAP_PKG
        set(value) {
            prefs.edit()
                .putString(KEY_MAP_PACKAGE, value)
                .putString(KEY_PKG_TOP, value)
                .apply()
        }

    var vietmapPackage: String
        get() = prefs.getString(KEY_VIETMAP_PACKAGE, null)
            ?: prefs.getString(KEY_PKG_BOTTOM_LEFT, DEFAULT_VIETMAP_PKG)
            ?: DEFAULT_VIETMAP_PKG
        set(value) {
            prefs.edit()
                .putString(KEY_VIETMAP_PACKAGE, value)
                .putString(KEY_PKG_BOTTOM_LEFT, value)
                .apply()
        }

    var musicPackage: String
        get() = prefs.getString(KEY_MUSIC_PACKAGE, null)
            ?: prefs.getString(KEY_PKG_BOTTOM_RIGHT, DEFAULT_MUSIC_PKG)
            ?: DEFAULT_MUSIC_PKG
        set(value) {
            prefs.edit()
                .putString(KEY_MUSIC_PACKAGE, value)
                .putString(KEY_PKG_BOTTOM_RIGHT, value)
                .apply()
        }

    var isAutoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var isOverSpeedWarningEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVER_SPEED_WARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_OVER_SPEED_WARNING, value).apply()

    var isSpeedBubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SPEED_BUBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SPEED_BUBBLE, value).apply()

    var hotspotSsid: String
        get() = prefs.getString(KEY_HOTSPOT_SSID, DEFAULT_HOTSPOT_SSID) ?: DEFAULT_HOTSPOT_SSID
        set(value) = prefs.edit().putString(KEY_HOTSPOT_SSID, value).apply()

    var hotspotPassword: String
        get() = prefs.getString(KEY_HOTSPOT_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOTSPOT_PASSWORD, value).apply()

    var isAggressiveHotspotEnabled: Boolean
        get() = prefs.getBoolean(KEY_AGGRESSIVE_HOTSPOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AGGRESSIVE_HOTSPOT, value).apply()

    var isBoxAutoUnlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOX_AUTO_UNLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_BOX_AUTO_UNLOCK, value).apply()

    var isSuppressWarningsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUPPRESS_WARNINGS, true)
        set(value) = prefs.edit().putBoolean(KEY_SUPPRESS_WARNINGS, value).apply()
}
