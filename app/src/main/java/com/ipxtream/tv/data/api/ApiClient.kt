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
    /**
     * The app's one HTTP client. Other clients (fixtures, beIN guide) derive from it with
     * [OkHttpClient.newBuilder], which shares its connection pool and threads.
     */
    val sharedHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            // Play build only: answers the demo sign-in's address inside the app.
            .apply { DemoCatalog.interceptor?.let { addInterceptor(it) } }
            .build()
    }

    fun create(baseUrl: String, debug: Boolean = false): XtreamApiService {
        // Request lines only, and only in debug builds. BODY logging would copy multi-MB
        // catalogs into logcat, and every request URL carries the account password.
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            android.util.Log.d("OkHttp", redactCredentials(message))
        }.apply {
            level = if (debug)
                HttpLoggingInterceptor.Level.BASIC
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = sharedHttp.newBuilder()
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeUrl(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }

    private val CREDENTIAL_PARAM = Regex("""(?i)((?:password|username)=)[^&\s]*""")
    /** `/movie/<user>/<pass>/…`, `/series/…`, `/live/…`, `/timeshift/…` stream paths. */
    private val TYPED_PATH = Regex("""(?i)(/(?:live|movie|series|timeshift)/)[^/\s]+/[^/\s]+/""")
    /** Live streams have no type segment: `http://host:port/<user>/<pass>/<id>.m3u8`. */
    private val LIVE_PATH = Regex("""(?i)(https?://[^/\s]+/)[^/\s]+/[^/\s]+/(\d+\.(?:m3u8|ts)\b)""")

    /**
     * Masks the login in any text that may hold a provider URL (logs, crash log, error messages):
     * `username=` / `password=` query parameters and the `<user>/<pass>` segments of stream paths.
     */
    fun redactCredentials(text: String): String = text
        .replace(CREDENTIAL_PARAM, "$1***")
        .replace(TYPED_PATH, "$1***/***/")
        .replace(LIVE_PATH, "$1***/***/$2")

    /**
     * The HTTPS address of [server] on [httpsPort], or null when the provider offers none
     * ("", "0", not a number) or [server] already uses HTTPS. Keeps the host, drops any path.
     */
    fun httpsVariant(server: String, httpsPort: String?): String? {
        val port = httpsPort?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val normalized = normalizeUrl(server)
        if (!normalized.startsWith("http://")) return null
        val host = runCatching { java.net.URI(normalized).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return "https://$host:$port/"
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
