package com.tubelite.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Subscription(
    val channelUrl: String,
    val channelName: String,
    val avatarUrl: String?
)

object SubscriptionStore {
    private const val PREFS = "tubelite_prefs"
    private const val KEY = "subscriptions_json"

    fun getAll(context: Context): List<Subscription> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("channelUrl")
                if (url.isBlank()) return@mapNotNull null
                Subscription(
                    channelUrl = url,
                    channelName = o.optString("channelName"),
                    avatarUrl = o.optString("avatarUrl").ifBlank { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isSubscribed(context: Context, channelUrl: String): Boolean =
        getAll(context).any { it.channelUrl == channelUrl }

    fun subscribe(context: Context, subscription: Subscription) {
        val current = getAll(context).filterNot { it.channelUrl == subscription.channelUrl }
        write(context, current + subscription)
    }

    fun unsubscribe(context: Context, channelUrl: String) {
        write(context, getAll(context).filterNot { it.channelUrl == channelUrl })
    }

    private fun write(context: Context, items: List<Subscription>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("channelUrl", item.channelUrl)
                put("channelName", item.channelName)
                put("avatarUrl", item.avatarUrl ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
        CloudSync.pushIfSignedIn(context)
    }
}
