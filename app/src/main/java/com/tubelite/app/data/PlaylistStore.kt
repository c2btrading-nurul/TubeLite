package com.tubelite.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PlaylistStore {
    private const val PREFS = "tubelite_prefs"
    private const val KEY = "playlists_json"

    private fun readRoot(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        return if (raw != null) JSONObject(raw) else JSONObject()
    }

    private fun writeRoot(context: Context, root: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }

    fun getPlaylistNames(context: Context): List<String> = readRoot(context).keys().asSequence().toList()

    fun createPlaylist(context: Context, name: String) {
        if (name.isBlank()) return
        val root = readRoot(context)
        if (!root.has(name)) {
            root.put(name, JSONArray())
            writeRoot(context, root)
        }
    }

    fun deletePlaylist(context: Context, name: String) {
        val root = readRoot(context)
        root.remove(name)
        writeRoot(context, root)
    }

    fun addVideo(context: Context, playlistName: String, video: VideoResult) {
        val root = readRoot(context)
        val arr = root.optJSONArray(playlistName) ?: JSONArray().also { root.put(playlistName, it) }
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("url") == video.url) {
                writeRoot(context, root)
                return
            }
        }
        val obj = JSONObject()
        obj.put("title", video.title)
        obj.put("url", video.url)
        obj.put("uploaderName", video.uploaderName)
        obj.put("thumbnailUrl", video.thumbnailUrl ?: "")
        obj.put("durationSeconds", video.durationSeconds)
        arr.put(obj)
        writeRoot(context, root)
    }

    fun removeVideo(context: Context, playlistName: String, videoUrl: String) {
        val root = readRoot(context)
        val arr = root.optJSONArray(playlistName) ?: return
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("url") != videoUrl) newArr.put(obj)
        }
        root.put(playlistName, newArr)
        writeRoot(context, root)
    }

    fun getVideos(context: Context, playlistName: String): List<VideoResult> {
        val root = readRoot(context)
        val arr = root.optJSONArray(playlistName) ?: return emptyList()
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
}
