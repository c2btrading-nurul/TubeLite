package com.tubelite.app.data

import android.content.Context

object AppSettingsStore {
    private const val PREFS = "tubelite_prefs"
    private const val KEY_DARK = "setting_dark_mode"
    private const val KEY_AUTOPLAY = "setting_autoplay_next"
    private const val KEY_SHORTS_ENABLED = "setting_shorts_enabled"

    fun isDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK, true)

    fun setDarkMode(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, value).apply()
        CloudSync.pushIfSignedIn(context)
    }

    fun isShortsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHORTS_ENABLED, false)

    fun setShortsEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHORTS_ENABLED, value).apply()
        CloudSync.pushIfSignedIn(context)
    }

    fun isAutoplayNextDefault(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTOPLAY, true)

    fun setAutoplayNextDefault(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTOPLAY, value).apply()
        CloudSync.pushIfSignedIn(context)
    }
}
