package com.ipxtream.tv.data.local

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * MD5-keyed JSON disk cache stored in the app's private cache directory.
 *
 * ## Design
 * - Cache key  = MD5 hex digest of any arbitrary String (typically a URL + params).
 * - Cache file = `{cacheDir}/{md5}.json` containing a [CacheEntry] JSON wrapper.
 * - Each entry stores a [CacheEntry.timestamp] so callers can check staleness.
 *
 * ## Thread safety
 * File I/O runs on whatever dispatcher the caller uses. Always call from
 * `Dispatchers.IO` (enforced by the suspend functions in [ContentRepository]).
 *
 * @param cacheDir Base directory for JSON files. Pass `context.cacheDir` for
 *                 system-managed ephemeral storage (cleared by "Clear Cache" in
 *                 device settings) or `context.filesDir` for persistent storage.
 */
class CacheManager(private val cacheDir: File) {

    private val gson = Gson()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Serialises [data] to JSON and writes it to disk under a key derived from
     * [cacheKey]. Silently swallows I/O errors (degraded-mode: next call will
     * just go to the network again).
     *
     * @param cacheKey Arbitrary string (URL + params recommended) used to
     *                 generate the filename.
     * @param data     Any Gson-serialisable object.
     */
    fun <T> save(cacheKey: String, data: T) {
        try {
            val entry   = CacheEntry(data = gson.toJson(data))
            val file    = fileFor(cacheKey)
            file.writeText(gson.toJson(entry))
            Log.d(TAG, "Saved cache → ${file.name}")
        } catch (e: IOException) {
            Log.w(TAG, "Cache write failed for key='$cacheKey': ${e.message}")
        }
    }

    /**
     * Loads and deserialises a cached entry.
     *
     * @param cacheKey   Same key used in [save].
     * @param type       Gson [java.lang.reflect.Type] for deserialisation.
     *                   Use `object : TypeToken<List<YourModel>>() {}.type`.
     * @param maxAgeMs   Maximum acceptable age in milliseconds. Entries older
     *                   than this are treated as missing (returns null).
     *                   Default is [DEFAULT_MAX_AGE_MS] (6 hours).
     * @return Deserialised object of type [T], or `null` if not found / stale
     *         / corrupt.
     */
    fun <T> load(
        cacheKey: String,
        type:     java.lang.reflect.Type,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): T? {
        val file = fileFor(cacheKey)
        if (!file.exists()) return null

        return try {
            val entry = gson.fromJson(file.readText(), CacheEntry::class.java)
            val ageMs = System.currentTimeMillis() - entry.timestamp

            if (ageMs > maxAgeMs) {
                Log.d(TAG, "Cache stale (${ageMs / 1000}s old) → ${file.name}")
                null
            } else {
                Log.d(TAG, "Cache hit (${ageMs / 1000}s old) → ${file.name}")
                gson.fromJson<T>(entry.data, type)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache corrupt for key='$cacheKey': ${e.message}")
            file.delete() // purge bad entry
            null
        }
    }

    /**
     * Deletes the cache entry for [cacheKey] if it exists.
     */
    fun invalidate(cacheKey: String) {
        val file = fileFor(cacheKey)
        if (file.delete()) Log.d(TAG, "Invalidated cache → ${file.name}")
    }

    /**
     * Deletes **all** `.json` cache files in [cacheDir].
     * Call this on logout or "Clear Cache" in app settings.
     */
    fun clearAll() {
        val deleted = cacheDir.listFiles { f -> f.extension == "json" }
            ?.count { it.delete() } ?: 0
        Log.d(TAG, "Cleared $deleted cache entries")
    }

    /**
     * Returns the total size of all cache files in bytes.
     * Useful for a "Cache size: X MB" settings entry.
     */
    fun totalCacheSizeBytes(): Long =
        cacheDir.listFiles { f -> f.extension == "json" }
            ?.sumOf { it.length() } ?: 0L

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun fileFor(cacheKey: String): File =
        File(cacheDir, "${cacheKey.md5()}.json")

    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "CacheManager"

        /** Default max cache age: 6 hours in milliseconds. */
        const val DEFAULT_MAX_AGE_MS: Long = 6 * 60 * 60 * 1000L

        /** Short cache window for data that changes frequently (e.g. live channels). */
        const val LIVE_MAX_AGE_MS: Long = 30 * 60 * 1000L   // 30 minutes

        /** Longer cache window for slow-changing data (series catalogue). */
        const val SERIES_MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L // 24 hours
    }
}

/**
 * Internal wrapper stored as the root JSON object in each cache file.
 *
 * Keeping the [data] as a raw JSON String (not a nested object) means the
 * [CacheManager] itself is type-agnostic and doesn't need to know the schema
 * of every cached response type.
 *
 * @param timestamp Unix epoch milliseconds at the time of write.
 * @param data      Serialised JSON string of the actual content.
 */
private data class CacheEntry(
    @SerializedName("timestamp") val timestamp: Long   = System.currentTimeMillis(),
    @SerializedName("data")      val data:      String
)
