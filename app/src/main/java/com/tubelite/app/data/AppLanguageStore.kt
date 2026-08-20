package com.tubelite.app.data

import android.content.Context

object AppLanguageStore {
    private const val PREFS = "tubelite_prefs"
    private const val KEY = "app_language"

    const val BANGLA = "bn"
    const val ENGLISH = "en"

    fun get(context: Context): String = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, BANGLA) ?: BANGLA

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
    }
}
