package com.ipxtream.tv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.reflect.TypeToken
import com.ipxtream.tv.data.api.ApiClient
import com.ipxtream.tv.data.api.XtreamApiService
import com.ipxtream.tv.data.local.CacheManager
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import retrofit2.HttpException

/**
 * Single source of truth for all Xtream Codes content data.
 *
 * ## Cache Strategy — Network-First with Offline Fallback
 * ```
 * ┌─────────────────────────────────────────────┐
 * │  Is device online?                          │
 * │       YES                  NO               │
 * │        │                   │                │
 * │  Fetch from API      Load from cache        │
 * │        │                   │                │
 * │  Save to cache      Return cache data       │
 * │        │            (or error if empty)     │
 * │  Return data                                │
 * └─────────────────────────────────────────────┘
 * ```
 * Additionally, if the network call **fails** (e.g., server unreachable) but a
 * cache entry exists, the stale cache is returned rather than surfacing an error.
 * This is the same behaviour as the WPF app's local JSON file fallback.
 *
 * ## Thread Safety
 * All public functions are `suspend` and switch to [Dispatchers.IO] internally
 * for both network I/O (Retrofit) and disk I/O (CacheManager). Safe to call
 * from any CoroutineScope.
 *
 * @param context      Application context — used only for connectivity checks.
 * @param credentials  Authenticated user credentials.
 * @param apiService   Retrofit service instance (from [ApiClient.create]).
 * @param cacheManager Disk cache backend.
 */
class ContentRepository(
    private val context:      Context,
    private val credentials:  AuthCredentials,
    private val apiService:   XtreamApiService,
    private val cacheManager: CacheManager
) {

    // =========================================================================
    //  Live TV
    // =========================================================================

    /**
     * Fetches all Live TV categories.
     * Cache TTL: [CacheManager.DEFAULT_MAX_AGE_MS] (6 hours).
     */
    suspend fun getLiveCategories(forceRefresh: Boolean = false): Result<List<Category>> =
        fetchWithCache(
            cacheKey = buildKey("live_categories"),
            maxAgeMs = CacheManager.DEFAULT_MAX_AGE_MS,
            type     = object : TypeToken<List<Category>>() {}.type,
            forceRefresh = forceRefresh
        ) {
            apiService.getLiveCategories(credentials.username, credentials.password)
        }

    /**
     * Fetches Live TV streams, optionally filtered by [categoryId].
     * Cache TTL: [CacheManager.LIVE_MAX_AGE_MS] (30 minutes — channels update frequently).
     */
    suspend fun getLiveStreams(categoryId: String? = null, forceRefresh: Boolean = false): Result<List<StreamItem>> {
        val key = buildKey("live_streams", categoryId)
        return fetchWithCache(
            cacheKey = key,
            maxAgeMs = CacheManager.LIVE_MAX_AGE_MS,
            type     = object : TypeToken<List<StreamItem>>() {}.type,
            forceRefresh = forceRefresh
        ) {
            apiService.getLiveStreams(credentials.username, credentials.password, categoryId = categoryId)
        }
    }

    // =========================================================================
    //  VOD (Movies)
    // =========================================================================

    /**
     * Fetches all VOD/Movie categories.
     * Cache TTL: [CacheManager.DEFAULT_MAX_AGE_MS] (6 hours).
     */
    suspend fun getVodCategories(forceRefresh: Boolean = false): Result<List<Category>> =
        fetchWithCache(
            cacheKey = buildKey("vod_categories"),
            maxAgeMs = CacheManager.DEFAULT_MAX_AGE_MS,
            type     = object : TypeToken<List<Category>>() {}.type,
            forceRefresh = forceRefresh
        ) {
            apiService.getVodCategories(credentials.username, credentials.password)
        }

    /**
     * Fetches VOD streams, optionally filtered by [categoryId].
     * Cache TTL: [CacheManager.DEFAULT_MAX_AGE_MS] (6 hours).
     */
    suspend fun getVodStreams(categoryId: String? = null, forceRefresh: Boolean = false): Result<List<StreamItem>> {
        val key = buildKey("vod_streams", categoryId)
        return fetchWithCache(
            cacheKey = key,
            maxAgeMs = CacheManager.DEFAULT_MAX_AGE_MS,
            type     = object : TypeToken<List<StreamItem>>() {}.type,
            forceRefresh = forceRefresh
        ) {
            apiService.getVodStreams(credentials.username, credentials.password, categoryId = categoryId)
        }
    }

    // =========================================================================
    //  Series
    // =========================================================================

    /**
     * Fetches all TV Series categories.
     * Cache TTL: [CacheManager.SERIES_MAX_AGE_MS] (24 hours).
     */
    suspend fun getSeriesCategories(forceRefresh: Boolean = false): Result<List<Category>> =
        fetchWithCache(
            cacheKey = buildKey("series_categories"),
            maxAgeMs = CacheManager.SERIES_MAX_AGE_MS,
            type     = object : TypeToken<List<Category>>() {}.type,
            forceRefresh = forceRefresh
        ) {
            apiService.getSeriesCategories(credentials.username, credentials.password)
        }

    /**
     * Fetches the Series index list, optionally filtered by [categoryId].
     * Cache TTL: [CacheManager.SERIES_MAX_AGE_MS] (24 hours).
     */
    suspend fun getSeries(categoryId: String? = null, forceRefresh: Boolean = false): Result<List<SeriesItem>> {
        val key = buildKey("series", categoryId)
        return fetchWithCache(
            cacheKey = key,
            maxAgeMs = CacheManager.SERIES_MAX_AGE_MS,
            type     = object : TypeToken<List<SeriesItem>>() {}.type,
            forceRefresh = forceRefresh
        ) {
            apiService.getSeries(credentials.username, credentials.password, categoryId = categoryId)
        }
    }

    /**
     * Fetches full season + episode detail for a specific series.
     * Cache TTL: [CacheManager.SERIES_MAX_AGE_MS] (24 hours).
     *
     * The returned [SeriesInfoResponse] contains [SeriesInfoResponse.toSeasons]
     * for UI-ready consumption.
     *
     * @param seriesId [SeriesItem.seriesId] from the index list.
     */
    suspend fun getSeriesInfo(seriesId: Int, forceRefresh: Boolean = false): Result<SeriesInfoResponse> =
        fetchWithCache(
            cacheKey = buildKey("series_info_$seriesId"),
            maxAgeMs = CacheManager.SERIES_MAX_AGE_MS,
            type     = SeriesInfoResponse::class.java,
            forceRefresh = forceRefresh
        ) {
            apiService.getSeriesInfo(credentials.username, credentials.password, seriesId = seriesId)
        }

    // =========================================================================
    //  Cache Management
    // =========================================================================

    /**
     * Clears the entire disk cache for the current user's server.
     * Call on logout or when the user requests a manual refresh.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheManager.clearAll()
    }

    /**
     * Returns the total disk cache size in bytes.
     * Use in a settings screen: "Cache size: ${bytes / 1024 / 1024} MB".
     */
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheManager.totalCacheSizeBytes()
    }

    // =========================================================================
    //  Core fetch-or-cache engine
    // =========================================================================

    /**
     * Generic Cache-First fallback fetch.
     *
     * 1. Check local disk cache. If fresh & valid, return [Result.success] (unless forced).
     * 2. If forced, missing, or stale, attempt network fetch.
     * 3. Network success: write to cache and return new data.
     * 4. Network failure: fallback to serving stale/expired cache (if any exists).
     *
     * All I/O is performed on [Dispatchers.IO].
     */
    private suspend inline fun <reified T> fetchWithCache(
        cacheKey: String,
        maxAgeMs: Long,
        type:     java.lang.reflect.Type,
        forceRefresh: Boolean = false,
        crossinline fetch: suspend () -> T
    ): Result<T> = withContext(Dispatchers.IO) {

        // 1. Check cache first (happy fast path)
        if (!forceRefresh) {
            val cached = loadFromCache<T>(cacheKey, maxAgeMs, type)
            if (cached != null) {
                Log.d(TAG, "Cache HIT -> '$cacheKey'")
                return@withContext cached
            }
        }

        // 2. Cache miss, stale, or explicitly forced. Try network.
        if (isOnline()) {
            var lastException: Exception? = null
            var attempt = 1
            val maxAttempts = 3
            var successData: T? = null
            var isSuccess = false

            while (attempt <= maxAttempts) {
                try {
                    val data = fetch()
                    successData = data
                    isSuccess = true
                    break
                } catch (e: Exception) {
                    val mappedException = mapException(e)
                    lastException = mappedException

                    if (mappedException is AuthenticationException) {
                        break // Don't retry authentication failures (401/403)
                    }

                    if (attempt < maxAttempts) {
                        val delayMs = if (attempt == 1) 1000L else 2000L
                        Log.d(TAG, "Retry attempt $attempt failed for '$cacheKey', retrying in ${delayMs}ms. Error: ${mappedException.message}")
                        kotlinx.coroutines.delay(delayMs)
                    }
                    attempt++
                }
            }

            if (isSuccess && successData != null) {
                cacheManager.save(cacheKey, successData)
                Log.d(TAG, "Network FETCH -> '$cacheKey'")
                return@withContext Result.success(successData)
            } else {
                val finalException = lastException ?: IOException("Unknown network error")
                Log.w(TAG, "Network FAILED for '$cacheKey' after $maxAttempts attempts: ${finalException.message}")
                return@withContext loadFromCache<T>(cacheKey, maxAgeMs = Long.MAX_VALUE, type)
                    ?: Result.failure(finalException)
            }
        } else {
            Log.d(TAG, "Device OFFLINE -> trying stale cache '$cacheKey'")
            return@withContext loadFromCache<T>(cacheKey, maxAgeMs = Long.MAX_VALUE, type)
                ?: Result.failure(
                    IOException("Device is offline and no cached data exists for this content.")
                )
        }
    }

    fun mapException(e: Exception): Exception {
        if (e is HttpException) {
            return when (e.code()) {
                401, 403 -> AuthenticationException("Invalid credentials or expired subscription.")
                429 -> RateLimitException("Too many requests. Please wait a moment before trying again.")
                in 500..599 -> ServerOfflineException("The IPTV provider's server is currently offline.")
                else -> e
            }
        }
        return e
    }

    /**
     * Attempts to load a typed value from the cache.
     * Returns null if the entry is missing, stale, or corrupt.
     */
    private fun <T> loadFromCache(
        cacheKey: String,
        maxAgeMs: Long,
        type:     java.lang.reflect.Type
    ): Result<T>? {
        val data: T? = cacheManager.load(cacheKey, type, maxAgeMs)
        return data?.let { Result.success(it) }
    }

    // =========================================================================
    //  Connectivity
    // =========================================================================

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    /**
     * Builds a scoped cache key that includes the server and username so that
     * different accounts on the same device never share cache entries.
     */
    private fun buildKey(base: String, suffix: String? = null): String {
        val host = credentials.server.replace(Regex("[^a-zA-Z0-9]"), "_")
        val user = credentials.username
        return if (suffix != null) "${host}_${user}_${base}_$suffix"
        else                       "${host}_${user}_${base}"
    }

    // =========================================================================
    //  Companion / Factory
    // =========================================================================

    companion object {
        private const val TAG = "ContentRepository"

        /**
         * Convenience factory that wires all dependencies from a [Context] and
         * [AuthCredentials]. Use this until Hilt is introduced.
         *
         * ```kotlin
         * val repo = ContentRepository.create(applicationContext, credentials)
         * ```
         */
        fun create(context: Context, credentials: AuthCredentials): ContentRepository {
            val apiService   = ApiClient.create(credentials.server, debug = true)
            val cacheManager = CacheManager(context.cacheDir)
            return ContentRepository(context.applicationContext, credentials, apiService, cacheManager)
        }
    }
}

class AuthenticationException(message: String) : Exception(message)
class RateLimitException(message: String) : Exception(message)
class ServerOfflineException(message: String) : Exception(message)
