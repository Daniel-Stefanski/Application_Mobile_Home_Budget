package com.example.homebudget.utils.settings

import com.example.homebudget.data.entity.Settings
import org.json.JSONArray
import org.json.JSONObject

// SettingsHelper.kt – funkcje wspomagające do odczytu i zapisu list (kategorie, osoby, kolory).
object SettingsHelper {
    fun getCategories(settings: Settings): MutableList<String> {
        return try {
            val cleaned = settings.categories.trim()
                .removePrefix("[")
                .removeSuffix("]")
            cleaned.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*\$)"))
                .map { it.replace("\"", "").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun getPeople(settings: Settings): MutableList<String> {
        return  try {
            val arr = JSONArray(settings.peopleList)
            MutableList(arr.length()) { arr.getString(it) }.toMutableList()
                .distinct()
                .toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun getCategoryColors(settings: Settings): JSONObject {
        return try {
            JSONObject(settings.categoryColors)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun writeCategories(list: List<String>): String =
        list.joinToString(prefix = "[", postfix = "]") { "\"$it\""}

    fun writePeople(list: List<String>): String =
        JSONArray(list).toString()

    fun getPeopleJSONArray(settings: Settings): JSONArray {
        return  try {
            JSONArray(settings.peopleList)
        } catch (e: Exception) {
            JSONArray()
        }
    }
}