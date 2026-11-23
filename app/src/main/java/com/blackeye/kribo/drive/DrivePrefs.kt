package com.blackeye.kribo.drive

import android.content.Context

/** Non-sensitive Drive sync preferences. */
class DrivePrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("kribo_drive", Context.MODE_PRIVATE)

    var autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI, value).apply()

    private companion object {
        const val KEY_AUTO = "auto_sync_v1"
        const val KEY_WIFI = "wifi_only_v1"
    }
}
