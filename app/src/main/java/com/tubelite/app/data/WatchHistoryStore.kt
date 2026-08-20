package com.tubelite.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WatchHistoryStoreRaw {
    private const val PREFS = "tubelite_prefs"
    private const val KEY = "watch_history_json"
    private const val MAX = 100

    fun add(context: Context, video: VideoResult) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        val arr = if (raw != null) JSONArray(raw) else JSONArray()

        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("url") != video.url) filtered.put(obj)
        }

        val newObj = JSONObject()
        newObj.put("title", video.title)
        newObj.put("url", video.url)
        newObj.put("uploaderName", video.uploaderName)
        newObj.put("thumbnailUrl", video.thumbnailUrl ?: "")
        newObj.put("durationSeconds", video.durationSeconds)

        val result = JSONArray()
        result.put(newObj)
        for (i in 0 until minOf(filtered.length(), MAX - 1)) {
            result.put(filtered.getJSONObject(i))
        }
        prefs.edit().putString(KEY, result.toString()).apply()
    }
}

object WatchHistoryStore {

    fun add(context: Context, video: VideoResult) {
        WatchHistoryStoreRaw.add(context, video)
        CloudSync.pushIfSignedIn(context)
    }

    fun getAll(context: Context): List<VideoResult> {
        val prefs = context.getSharedPreferences("tubelite_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("watch_history_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            VideoResult(
                title = obj.optString("title"),
                url = obj.optString("url"),
                uploaderName = obj.optString("uploaderName"),
                thumbnailUrl = obj.optString("thumbnailUrl").ifBlank { null },
                durationSeconds = obj.optLong("durationSeconds")
            )
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences("tubelite_prefs", Context.MODE_PRIVATE).edit().remove("watch_history_json").apply()
        CloudSync.pushIfSignedIn(context)
    }
}
