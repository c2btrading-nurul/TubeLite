package com.tubelite.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object LocalBackupStore {
    private const val PREFS = "tubelite_prefs"
    private const val SEARCH_KEY = "search_history"
    private const val BACKUP_VERSION = 1

    fun exportTo(context: Context, uri: Uri) {
        val root = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("darkMode", AppSettingsStore.isDarkMode(context))
            put("autoplayNext", AppSettingsStore.isAutoplayNextDefault(context))

            val search = JSONArray()
            SearchHistoryStore.getRecent(context, 15).forEach { search.put(it) }
            put("searchHistory", search)

            val playlists = JSONObject()
            PlaylistStore.getPlaylistNames(context).forEach { name ->
                val videos = JSONArray()
                PlaylistStore.getVideos(context, name).forEach { videos.put(videoToJson(it)) }
                playlists.put(name, videos)
            }
            put("playlists", playlists)

            val history = JSONArray()
            WatchHistoryStore.getAll(context).forEach { history.put(videoToJson(it)) }
            put("watchHistory", history)
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Backup file could not be opened")
    }

    fun importFrom(context: Context, uri: Uri) {
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Backup file could not be opened")

        val root = JSONObject(json)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (root.has("darkMode")) editor.putBoolean("setting_dark_mode", root.getBoolean("darkMode"))
        if (root.has("autoplayNext")) editor.putBoolean("setting_autoplay_next", root.getBoolean("autoplayNext"))

        root.optJSONArray("searchHistory")?.let { arr ->
            val values = (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }.take(15)
            editor.putString(SEARCH_KEY, values.joinToString("||"))
        }
        editor.apply()

        root.optJSONObject("playlists")?.let { playlists ->
            val names = playlists.keys().asSequence().toList()
            names.forEach { name ->
                PlaylistStoreRaw.createPlaylist(context, name)
                val videos = playlists.optJSONArray(name) ?: return@forEach
                for (i in 0 until videos.length()) {
                    PlaylistStoreRaw.addVideo(context, name, jsonToVideo(videos.getJSONObject(i)))
                }
            }
        }

        root.optJSONArray("watchHistory")?.let { history ->
            for (i in history.length() - 1 downTo 0) {
                WatchHistoryStoreRaw.add(context, jsonToVideo(history.getJSONObject(i)))
            }
        }
    }

    private fun videoToJson(video: VideoResult) = JSONObject().apply {
        put("title", video.title)
        put("url", video.url)
        put("uploaderName", video.uploaderName)
        put("thumbnailUrl", video.thumbnailUrl ?: "")
        put("durationSeconds", video.durationSeconds)
    }

    private fun jsonToVideo(obj: JSONObject) = VideoResult(
        title = obj.optString("title"),
        url = obj.optString("url"),
        uploaderName = obj.optString("uploaderName"),
        thumbnailUrl = obj.optString("thumbnailUrl").ifBlank { null },
        durationSeconds = obj.optLong("durationSeconds")
    )
}
