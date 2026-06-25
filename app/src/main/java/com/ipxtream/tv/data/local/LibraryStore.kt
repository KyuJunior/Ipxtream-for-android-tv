package com.ipxtream.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ipxtream.tv.data.model.AuthCredentials

/**
 * Scoped data model representing an item in the Favorites or Watch History library.
 */
data class LibraryItem(
    val id: String,                  // streamId, seriesId, or episodeId
    val parentId: String? = null,    // For episodes, this is the seriesId. Otherwise null
    val name: String,
    val type: String,                // "live" | "movie" | "series" | "episode"
    val iconUrl: String?,
    val categoryId: String?,
    val rating: String? = null,
    val containerExtension: String? = null,
    val lastWatchedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thread-safe local storage for Favorites and Watch History.
 *
 * Scopes the shared preferences to the specific server and username to ensure
 * multi-user isolation on shared TV hardware.
 */
class LibraryStore(
    context: Context,
    private val credentials: AuthCredentials
) {
    private val gson = Gson()
    private val serverSlug = credentials.server.replace(Regex("[^a-zA-Z0-9]"), "_")
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "ipxtream_library_${serverSlug}_${credentials.username}",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_ITEMS = 50
    }

    // ─── Favorites API ────────────────────────────────────────────────────────

    @Synchronized
    fun getFavorites(): List<LibraryItem> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LibraryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addFavorite(item: LibraryItem) {
        val current = getFavorites().toMutableList()
        current.removeAll { it.id == item.id && it.type == item.type }
        current.add(0, item) // Insert at top (most recently favorited)
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(current)).apply()
    }

    @Synchronized
    fun removeFavorite(id: String, type: String) {
        val current = getFavorites().toMutableList()
        val removed = current.removeAll { it.id == id && it.type == type }
        if (removed) {
            prefs.edit().putString(KEY_FAVORITES, gson.toJson(current)).apply()
        }
    }

    @Synchronized
    fun isFavorite(id: String, type: String): Boolean {
        return getFavorites().any { it.id == id && it.type == type }
    }

    // ─── Watch History API ───────────────────────────────────────────────────

    @Synchronized
    fun getHistory(): List<LibraryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LibraryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveHistoryItem(item: LibraryItem) {
        val current = getHistory().toMutableList()
        current.removeAll { it.id == item.id && it.type == item.type }
        // Insert at top with current timestamp
        current.add(0, item.copy(timestamp = System.currentTimeMillis()))
        
        // Cap history length
        if (current.size > MAX_HISTORY_ITEMS) {
            current.removeAt(current.lastIndex)
        }
        prefs.edit().putString(KEY_HISTORY, gson.toJson(current)).apply()
    }

    @Synchronized
    fun removeFromHistory(id: String, type: String) {
        val current = getHistory().toMutableList()
        val removed = current.removeAll { it.id == id && it.type == type }
        if (removed) {
            prefs.edit().putString(KEY_HISTORY, gson.toJson(current)).apply()
        }
    }
}
