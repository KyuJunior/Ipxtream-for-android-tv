package com.ipxtream.tv.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory that creates a [XtreamApiService] bound to a specific server base URL.
 *
 * A new instance is created per-login because the base URL is user-supplied at
 * runtime and can differ between accounts. Consider caching the instance once
 * the user is authenticated (Phase 2+).
 */
object ApiClient {

    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC    = 30L
    private const val WRITE_TIMEOUT_SEC   = 30L

    /**
     * Builds and returns a [XtreamApiService] for the given [baseUrl].
     *
     * @param baseUrl  Fully-qualified base URL, e.g. "http://myserver.com:8080/"
     *                 Must end with a trailing slash (enforced by [normalizeUrl]).
     * @param debug    When true, full request/response bodies are logged via Logcat.
     *                 Should be [BuildConfig.DEBUG] in production code.
     */
    fun create(baseUrl: String, debug: Boolean = false): XtreamApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (debug)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeUrl(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }

    /**
     * Ensures the URL is properly formatted for Retrofit's [Retrofit.Builder.baseUrl]:
     * - Adds "http://" schema if missing
     * - Fixes uppercase "HTTP://" to "http://"
     * - Appends trailing "/" if missing
     */
    fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        val lower = normalized.lowercase()

        // Handle uppercase schemas or missing schemas
        normalized = when {
            lower.startsWith("http://") -> "http://" + normalized.substring(7)
            lower.startsWith("https://") -> "https://" + normalized.substring(8)
            else -> "http://$normalized" // Default to http
        }

        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }
}
