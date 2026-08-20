package com.tubelite.app.data

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * ব্যবহারকারী Google দিয়ে সাইন-ইন করা থাকলে প্লে-লিস্ট/হিস্ট্রি/সেটিংস তার Google Drive-এর
 * "App Data" ফোল্ডারে (লুকানো, শুধু এই অ্যাপ অ্যাক্সেস করতে পারে) JSON ফাইল হিসেবে সেভ থাকে।
 */
object CloudSync {

    private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_NAME = "tubelite_backup.json"
    private val client = OkHttpClient()

    fun isSignedIn(context: Context): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    private fun accessToken(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return null
        return try {
            GoogleAuthUtil.getToken(context, account, SCOPE)
        } catch (e: Exception) {
            null
        }
    }

    fun pushAll(context: Context) {
        try {
            val token = accessToken(context) ?: return
            val json = buildBackupJson(context)
            val fileId = findFileId(token)
            if (fileId != null) updateFile(token, fileId, json) else createFile(token, json)
        } catch (e: Exception) {
            // best-effort sync — ব্যর্থ হলেও অ্যাপ চলবে, লোকাল ডেটা অক্ষত থাকবে
        }
    }

    fun pullAll(context: Context, onDone: (foundCloudData: Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val found = try {
                val token = accessToken(context)
                val fileId = token?.let { findFileId(it) }
                val json = if (token != null && fileId != null) downloadFile(token, fileId) else null
                if (json != null) {
                    applyBackupJson(context, json)
                    true
                } else false
            } catch (e: Exception) {
                false
            }
            onDone(found)
        }
    }

    fun pushIfSignedIn(context: Context) {
        if (!isSignedIn(context)) return
        CoroutineScope(Dispatchers.IO).launch { pushAll(context) }
    }

    // ---------- Google Drive REST API ----------

    private fun findFileId(token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%3D%27$FILE_NAME%27&fields=files(id)"
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return null
            val files = JSONObject(body).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).optString("id")
        }
    }

    private fun createFile(token: String, content: String) {
        val metadata = JSONObject().apply {
            put("name", FILE_NAME)
            put("parents", JSONArray().put("appDataFolder"))
        }
        val boundary = "tubelite_boundary_xyz"
        val body = "--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata.toString() +
            "\r\n--$boundary\r\n" +
            "Content-Type: application/json\r\n\r\n" +
            content +
            "\r\n--$boundary--"
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()
        client.newCall(req).execute().close()
    }

    private fun updateFile(token: String, fileId: String, content: String) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .addHeader("Authorization", "Bearer $token")
            .patch(content.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().close()
    }

    private fun downloadFile(token: String, fileId: String): String? {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(req).execute().use { resp ->
            return if (resp.isSuccessful) resp.body?.string() else null
        }
    }

    // ---------- ব্যাকআপ JSON বিল্ড/অ্যাপ্লাই ----------

    private fun buildBackupJson(context: Context): String {
        val root = JSONObject()

        val playlists = JSONObject()
        for (name in PlaylistStore.getPlaylistNames(context)) {
            val arr = JSONArray()
            for (v in PlaylistStore.getVideos(context, name)) arr.put(videoToJson(v))
            playlists.put(name, arr)
        }
        root.put("playlists", playlists)

        val historyArr = JSONArray()
        for (v in WatchHistoryStore.getAll(context)) historyArr.put(videoToJson(v))
        root.put("history", historyArr)

        root.put("darkMode", AppSettingsStore.isDarkMode(context))
        root.put("autoplayNext", AppSettingsStore.isAutoplayNextDefault(context))

        return root.toString()
    }

    private fun applyBackupJson(context: Context, jsonStr: String) {
        val root = JSONObject(jsonStr)

        root.optJSONObject("playlists")?.let { playlists ->
            val names = playlists.keys()
            for (name in names) {
                PlaylistStoreRaw.createPlaylist(context, name)
                val arr = playlists.getJSONArray(name)
                for (i in 0 until arr.length()) {
                    PlaylistStoreRaw.addVideo(context, name, jsonToVideo(arr.getJSONObject(i)))
                }
            }
        }

        root.optJSONArray("history")?.let { historyArr ->
            for (i in historyArr.length() - 1 downTo 0) {
                WatchHistoryStoreRaw.add(context, jsonToVideo(historyArr.getJSONObject(i)))
            }
        }

        if (root.has("darkMode")) AppSettingsStore.setDarkMode(context, root.getBoolean("darkMode"))
        if (root.has("autoplayNext")) AppSettingsStore.setAutoplayNextDefault(context, root.getBoolean("autoplayNext"))
    }

    private fun videoToJson(v: VideoResult): JSONObject = JSONObject().apply {
        put("title", v.title)
        put("url", v.url)
        put("uploaderName", v.uploaderName)
        put("thumbnailUrl", v.thumbnailUrl ?: "")
        put("durationSeconds", v.durationSeconds)
    }

    private fun jsonToVideo(o: JSONObject): VideoResult = VideoResult(
        title = o.optString("title"),
        url = o.optString("url"),
        uploaderName = o.optString("uploaderName"),
        thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
        durationSeconds = o.optLong("durationSeconds")
    )
}
 
