package com.tubelite.app.data

import android.content.Context

object NowPlayingStore {
    private const val PREFS = "tubelite_prefs"
    private const val KEY = "now_playing"

    fun save(context: Context, v: VideoResult) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = listOf(
            v.title.replace("||", " "),
            v.url.replace("||", " "),
            v.uploaderName.replace("||", " "),
            (v.thumbnailUrl ?: "").replace("||", " "),
            v.durationSeconds.toString()
        ).joinToString("||")
        prefs.edit().putString(KEY, encoded).apply()
    }

    fun load(context: Context): VideoResult? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return null
        val parts = raw.split("||")
        if (parts.size < 5) return null
        return VideoResult(
            title = parts[0],
            url = parts[1],
            uploaderName = parts[2],
            thumbnailUrl = parts[3].ifBlank { null },
            durationSeconds = parts[4].toLongOrNull() ?: 0L
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
