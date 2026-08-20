package com.tubelite.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ব্যবহারকারী সাইন-ইন করা থাকলে প্লে-লিস্ট/হিস্ট্রি/সেটিংস Firestore-এ (ক্লাউডে) সেভ ও রিস্টোর করে।
 * সাধারণ নীতি: সাইন-ইনের সময় ক্লাউডে ডেটা থাকলে সেটাই নেওয়া হয়, না থাকলে লোকাল ডেটা আপলোড হয়।
 */
object CloudSync {

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    fun isSignedIn(): Boolean = uid() != null

    fun pushAll(context: Context) {
        val u = uid() ?: return
        val playlistsMap = HashMap<String, Any>()
        for (name in PlaylistStore.getPlaylistNames(context)) {
            playlistsMap[name] = PlaylistStore.getVideos(context, name).map { videoToMap(it) }
        }
        val doc = hashMapOf<String, Any>(
            "playlists" to playlistsMap,
            "history" to WatchHistoryStore.getAll(context).map { videoToMap(it) },
            "darkMode" to AppSettingsStore.isDarkMode(context),
            "autoplayNext" to AppSettingsStore.isAutoplayNextDefault(context)
        )
        FirebaseFirestore.getInstance().collection("users").document(u).set(doc)
    }

    fun pullAll(context: Context, onDone: (foundCloudData: Boolean) -> Unit) {
        val u = uid()
        if (u == null) { onDone(false); return }
        FirebaseFirestore.getInstance().collection("users").document(u).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    onDone(false)
                    return@addOnSuccessListener
                }
                @Suppress("UNCHECKED_CAST")
                val playlistsMap = snap.get("playlists") as? Map<String, List<Map<String, Any>>> ?: emptyMap()
                for ((name, videos) in playlistsMap) {
                    PlaylistStoreRaw.createPlaylist(context, name)
                    for (v in videos) PlaylistStoreRaw.addVideo(context, name, mapToVideo(v))
                }

                @Suppress("UNCHECKED_CAST")
                val historyList = snap.get("history") as? List<Map<String, Any>> ?: emptyList()
                for (v in historyList.reversed()) WatchHistoryStoreRaw.add(context, mapToVideo(v))

                snap.getBoolean("darkMode")?.let { AppSettingsStore.setDarkMode(context, it) }
                snap.getBoolean("autoplayNext")?.let { AppSettingsStore.setAutoplayNextDefault(context, it) }

                onDone(true)
            }
            .addOnFailureListener { onDone(false) }
    }

    fun pushIfSignedIn(context: Context) {
        if (isSignedIn()) pushAll(context)
    }

    private fun videoToMap(v: VideoResult): Map<String, Any> = mapOf(
        "title" to v.title,
        "url" to v.url,
        "uploaderName" to v.uploaderName,
        "thumbnailUrl" to (v.thumbnailUrl ?: ""),
        "durationSeconds" to v.durationSeconds
    )

    private fun mapToVideo(m: Map<String, Any>): VideoResult = VideoResult(
        title = m["title"] as? String ?: "",
        url = m["url"] as? String ?: "",
        uploaderName = m["uploaderName"] as? String ?: "",
        thumbnailUrl = (m["thumbnailUrl"] as? String)?.ifBlank { null },
        durationSeconds = (m["durationSeconds"] as? Long) ?: 0L
    )
}
