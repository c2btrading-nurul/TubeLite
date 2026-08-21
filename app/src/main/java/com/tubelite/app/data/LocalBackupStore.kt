package com.tubelite.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject

object LocalBackupStore {

    private const val PREFS = "tubelite_prefs"
    private const val VERSION = 1

    fun export(context: Context, uri: Uri): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val root = JSONObject()
            val values = JSONObject()

            prefs.all.forEach { (key, value) ->
                val item = JSONObject()

                when (value) {
                    is Boolean -> {
                        item.put("type", "boolean")
                        item.put("value", value)
                    }
                    is Int -> {
                        item.put("type", "int")
                        item.put("value", value)
                    }
                    is Long -> {
                        item.put("type", "long")
                        item.put("value", value)
                    }
                    is Float -> {
                        item.put("type", "float")
                        item.put("value", value.toDouble())
                    }
                    is String -> {
                        item.put("type", "string")
                        item.put("value", value)
                    }
                }

                values.put(key, item)
            }

            root.put("app", "TubeLite")
            root.put("version", VERSION)
            root.put("preferences", values)

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return false

            true
        } catch (_: Exception) {
            false
        }
    }

    fun import(context: Context, uri: Uri): Boolean {
        return try {
            val json = context.contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return false

            val root = JSONObject(json)

            if (root.optString("app") != "TubeLite") {
                return false
            }

            val values = root.optJSONObject("preferences") ?: return false

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()

            val keys = values.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val item = values.optJSONObject(key) ?: continue

                when (item.optString("type")) {
                    "boolean" ->
                        editor.putBoolean(key, item.optBoolean("value"))

                    "int" ->
                        editor.putInt(key, item.optInt("value"))

                    "long" ->
                        editor.putLong(key, item.optLong("value"))

                    "float" ->
                        editor.putFloat(key, item.optDouble("value").toFloat())

                    "string" ->
                        editor.putString(key, item.optString("value"))
                }
            }

            editor.commit()
        } catch (_: Exception) {
            false
        }
    }
}
