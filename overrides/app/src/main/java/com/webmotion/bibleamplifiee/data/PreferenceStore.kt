package com.webmotion.bibleamplifiee.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1f)
        set(value) = prefs.edit().putFloat("font_scale", value).apply()

    var displayMode: String
        get() = prefs.getString("display_mode", "FR") ?: "FR"
        set(value) = prefs.edit().putString("display_mode", value).apply()

    var activeWorkspaceId: Int
        get() = prefs.getInt("active_workspace_id", 1)
        set(value) = prefs.edit().putInt("active_workspace_id", value).apply()

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet().orEmpty()

    fun toggleFavorite(ref: VerseRef): Boolean {
        val set = favorites().toMutableSet()
        val added = if (ref.key in set) {
            set.remove(ref.key)
            false
        } else {
            set.add(ref.key)
            true
        }
        prefs.edit().putStringSet("favorites", set).apply()
        return added
    }

    fun notes(): Map<String, String> = readStringMap("notes_json")

    fun setNote(ref: VerseRef, text: String) {
        val map = notes().toMutableMap()
        if (text.isBlank()) map.remove(ref.key) else map[ref.key] = text.trim()
        writeStringMap("notes_json", map)
    }

    fun highlights(): Map<String, HighlightColor> {
        val obj = jsonObject("highlights_json")
        val result = linkedMapOf<String, HighlightColor>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = runCatching { HighlightColor.valueOf(obj.optString(key)) }.getOrDefault(HighlightColor.NONE)
            if (value != HighlightColor.NONE) result[key] = value
        }
        return result
    }

    fun setHighlight(ref: VerseRef, color: HighlightColor) {
        val obj = jsonObject("highlights_json")
        if (color == HighlightColor.NONE) obj.remove(ref.key) else obj.put(ref.key, color.name)
        prefs.edit().putString("highlights_json", obj.toString()).apply()
    }

    fun labels(): Map<String, Set<String>> {
        val obj = jsonObject("labels_json")
        val result = linkedMapOf<String, Set<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val array = obj.optJSONArray(key) ?: continue
            val values = buildSet {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
            if (values.isNotEmpty()) result[key] = values
        }
        return result
    }

    fun setLabels(ref: VerseRef, values: Set<String>) {
        val obj = jsonObject("labels_json")
        if (values.isEmpty()) {
            obj.remove(ref.key)
        } else {
            val array = JSONArray()
            values.filter { it.isNotBlank() }.map { it.trim() }.sorted().forEach(array::put)
            obj.put(ref.key, array)
        }
        prefs.edit().putString("labels_json", obj.toString()).apply()
    }

    fun history(): List<HistoryEntry> {
        val raw = prefs.getString("history_json", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val ref = VerseRef.fromKey(obj.optString("ref")) ?: continue
                    add(HistoryEntry(ref, obj.optLong("time", 0L)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addHistory(ref: VerseRef) {
        val list = history().filterNot { it.ref.key == ref.key }.toMutableList()
        list.add(0, HistoryEntry(ref, System.currentTimeMillis()))
        val array = JSONArray()
        list.take(200).forEach { entry ->
            array.put(JSONObject().put("ref", entry.ref.key).put("time", entry.timestamp))
        }
        prefs.edit().putString("history_json", array.toString()).apply()
    }

    fun clearHistory() = prefs.edit().remove("history_json").apply()

    fun workspaces(): List<WorkspaceState> {
        val raw = prefs.getString("workspaces_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(WorkspaceState(1, "Espace 1", VerseRef(1, 1, 1)))
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optInt("id", i + 1)
                    val name = obj.optString("name", "Espace $id")
                    val primary = VerseRef.fromKey(obj.optString("primary")) ?: VerseRef(1, 1, 1)
                    val secondary = VerseRef.fromKey(obj.optString("secondary"))
                    val split = obj.optBoolean("split", false)
                    add(WorkspaceState(id, name, primary, secondary, split))
                }
            }.ifEmpty { listOf(WorkspaceState(1, "Espace 1", VerseRef(1, 1, 1))) }
        }.getOrElse { listOf(WorkspaceState(1, "Espace 1", VerseRef(1, 1, 1))) }
    }

    fun saveWorkspaces(list: List<WorkspaceState>) {
        val array = JSONArray()
        list.forEach { ws ->
            val obj = JSONObject()
                .put("id", ws.id)
                .put("name", ws.name)
                .put("primary", ws.primaryRef.key)
                .put("split", ws.split)
            ws.secondaryRef?.let { obj.put("secondary", it.key) }
            array.put(obj)
        }
        prefs.edit().putString("workspaces_json", array.toString()).apply()
    }

    fun planProgress(planId: String): Set<String> =
        prefs.getStringSet("plan_progress_$planId", emptySet())?.toSet().orEmpty()

    fun togglePlanDay(planId: String, day: Int): Set<String> {
        val set = planProgress(planId).toMutableSet()
        val key = day.toString()
        if (key in set) set.remove(key) else set.add(key)
        prefs.edit().putStringSet("plan_progress_$planId", set).apply()
        return set
    }

    private fun readStringMap(key: String): Map<String, String> {
        val obj = jsonObject(key)
        val result = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val item = keys.next()
            val value = obj.optString(item)
            if (value.isNotBlank()) result[item] = value
        }
        return result
    }

    private fun writeStringMap(key: String, map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    private fun jsonObject(key: String): JSONObject {
        val raw = prefs.getString(key, null)
        return if (raw.isNullOrBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }
}
