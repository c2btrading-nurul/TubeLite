package com.tubelite.app.data

import android.content.Context

object SearchHistoryStore {
    private const val PREFS = "tubelite_prefs"
    private const val KEY = "search_history"
    private const val MAX_HISTORY = 15

    fun add(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY, "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
        val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_HISTORY)
        prefs.edit().putString(KEY, updated.joinToString("||")).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }

    fun getRecent(context: Context, limit: Int = 5): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, "")?.split("||")?.filter { it.isNotBlank() }?.take(limit) ?: emptyList()
    }
}
