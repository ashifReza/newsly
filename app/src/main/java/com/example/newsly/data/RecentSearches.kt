package com.example.newsly.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last few searches, newest first, kept in SharedPreferences. */
class RecentSearches(context: Context) {
    private val prefs = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun add(query: String) {
        val updated = (listOf(query) + _items.value.filterNot { it.equals(query, ignoreCase = true) }).take(MAX)
        save(updated)
    }

    fun remove(query: String) = save(_items.value - query)

    fun clear() = save(emptyList())

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private fun save(items: List<String>) {
        prefs.edit().putString(KEY, items.joinToString(SEPARATOR)).apply()
        _items.value = items
    }

    private companion object {
        const val KEY = "items"
        const val SEPARATOR = "\n"
        const val MAX = 8
    }
}
