package com.ipxtream.tv.data.local

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * MD5-keyed JSON disk cache stored in the app's private cache directory.
 *
 * ## Design
 * - Cache key  = MD5 hex digest of any arbitrary String (typically a URL + params).
 * - Cache file = `{cacheDir}/{md5}.v2.json`: the data's own JSON, streamed to and from disk.
 *   Catalogs are 10+ MB; never holding them as one String keeps a weak TV out of GC pauses.
 * - Age comes from the file's modification time.
 * - Files from older versions (`{md5}.json`, a [CacheEntry] wrapper holding the data as an
 *   escaped string) are read once, rewritten in the new format and deleted.
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
        val file = fileFor(cacheKey)
        val tmp  = File(cacheDir, file.name + ".tmp")
        try {
            // Stream straight to a temp file, then rename, so a crash or full disk mid-write
            // never leaves a truncated cache file behind.
            tmp.bufferedWriter(Charsets.UTF_8).use { gson.toJson(data, it) }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("rename failed")
            }
            Log.d(TAG, "Saved cache → ${file.name}")
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "Cache write failed for key='$cacheKey': ${e.message}")
        } catch (e: OutOfMemoryError) {
            // A huge catalog can exhaust the heap while serialising; skip caching it this time.
            tmp.delete()
            Log.w(TAG, "Cache write skipped (out of memory) for key='$cacheKey'")
        }
    }

    /**
     * Age of the entry in milliseconds, from the file's modification time (no parsing —
     * catalog files are many megabytes). Null when the entry doesn't exist.
     */
    fun ageMs(cacheKey: String): Long? {
        val file = fileFor(cacheKey).takeIf { it.exists() } ?: legacyFileFor(cacheKey)
        if (!file.exists()) return null
        return (System.currentTimeMillis() - file.lastModified()).coerceAtLeast(0L)
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
        if (!file.exists()) return loadLegacy(cacheKey, type, maxAgeMs)

        val ageMs = System.currentTimeMillis() - file.lastModified()
        if (ageMs > maxAgeMs) {
            Log.d(TAG, "Cache stale (${ageMs / 1000}s old) → ${file.name}")
            return null
        }
        return try {
            Log.d(TAG, "Cache hit (${ageMs / 1000}s old) → ${file.name}")
            JsonReader(file.bufferedReader(Charsets.UTF_8)).use { gson.fromJson<T>(it, type) }
        } catch (e: Exception) {
            Log.w(TAG, "Cache corrupt for key='$cacheKey': ${e.message}")
            file.delete() // purge bad entry
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Cache too large to load for key='$cacheKey'")
            null
        }
    }

    /** One-time read of a pre-v2 file; rewrites it in the streaming format. */
    private fun <T> loadLegacy(cacheKey: String, type: java.lang.reflect.Type, maxAgeMs: Long): T? {
        val legacy = legacyFileFor(cacheKey)
        if (!legacy.exists()) return null
        return try {
            val entry = gson.fromJson(legacy.readText(), CacheEntry::class.java)
            val data = gson.fromJson<T>(entry.data, type)
            legacy.delete()
            if (data != null) {
                save(cacheKey, data)
                fileFor(cacheKey).setLastModified(entry.timestamp)  // keep its real age
            }
            if (System.currentTimeMillis() - entry.timestamp > maxAgeMs) null else data
        } catch (e: Throwable) {
            legacy.delete()
            null
        }
    }

    /**
     * Deletes the cache entry for [cacheKey] if it exists.
     */
    fun invalidate(cacheKey: String) {
        val file = fileFor(cacheKey)
        legacyFileFor(cacheKey).delete()
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
        File(cacheDir, "${cacheKey.md5()}.v2.json")

    private fun legacyFileFor(cacheKey: String): File =
        File(cacheDir, "${cacheKey.md5()}.json")

    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns true if cache entry exists and is older than [maxAgeMs], or does not exist.
     */
    fun isStale(cacheKey: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean {
        val age = ageMs(cacheKey) ?: return true
        return age > maxAgeMs
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "CacheManager"

        /** Default max cache age: 12 hours in milliseconds. */
        const val DEFAULT_MAX_AGE_MS: Long = 12 * 60 * 60 * 1000L

        /** Short cache window for data that changes frequently (e.g. live channels: 2 hours). */
        const val LIVE_MAX_AGE_MS: Long = 2 * 60 * 60 * 1000L

        /** Longer cache window for slow-changing data (series catalogue: 24 hours). */
        const val SERIES_MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L
    }
}

/**
 * Pre-v2 wrapper, kept only to read old cache files once.
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
