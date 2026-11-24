package com.ipxtream.tv.data.api

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ipxtream.tv.BuildConfig
import com.ipxtream.tv.ui.gate.BlockedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One flavor's entry in docs/app-config.json. */
data class GateRule(
    @SerializedName("min_version") val minVersion: String = "",
    @SerializedName("disabled") val disabled: Boolean = false,
    @SerializedName("message") val message: String = ""
)

data class GateConfig(
    @SerializedName("github") val github: GateRule? = null,
    @SerializedName("play") val play: GateRule? = null
)

enum class GateBlock { OUTDATED, DISABLED, UNVERIFIED }

data class GateVerdict(val block: GateBlock, val message: String)

/**
 * Remote switch: docs/app-config.json (GitHub Pages) can require a newer version or stop the app.
 * Unreachable file: the last saved rule applies; after [OFFLINE_LIMIT_MS] with no successful check
 * the app stops too, so blocking the address doesn't get around it.
 */
object AppGate {
    const val CONFIG_URL = "https://kyujunior.github.io/Ipxtream-for-android-tv/app-config.json"
    private const val PREFS = "app_gate"
    private const val CHECK_EVERY_MS = 60 * 60_000L
    const val OFFLINE_LIMIT_MS = 24 * 60 * 60_000L

    private val gson = Gson()
    // Login and Dashboard both check at start-up; the second waits for the first's fetch and its saved result.
    private val lock = Mutex()
    private val http by lazy { ApiClient.sharedHttp.newBuilder().callTimeout(8, TimeUnit.SECONDS).build() }

    /** Pure decision, tested in AppGateTest. [lastOkMs] = last successful fetch (or first launch). */
    fun decide(rule: GateRule?, lastOkMs: Long, nowMs: Long, version: String): GateVerdict? {
        val message = rule?.message.orEmpty()
        if (nowMs - lastOkMs > OFFLINE_LIMIT_MS) return GateVerdict(GateBlock.UNVERIFIED, "")
        if (rule == null) return null
        if (rule.disabled) return GateVerdict(GateBlock.DISABLED, message)
        if (rule.minVersion.isNotBlank() && isOlder(version, rule.minVersion)) return GateVerdict(GateBlock.OUTDATED, message)
        return null
    }

    /** "2.0.9" < "2.0.10"; missing parts count as 0, non-numbers are ignored. */
    fun isOlder(current: String, min: String): Boolean {
        val a = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = min.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    fun parseRule(json: String): GateRule? {
        val config = gson.fromJson(json, GateConfig::class.java) ?: error("empty config")
        return if (BuildConfig.FLAVOR == "play") config.play else config.github
    }

    /** [force]: skip the hourly throttle (the block screen's Retry). */
    suspend fun check(context: Context, force: Boolean = false): GateVerdict? = lock.withLock { withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!prefs.contains("first")) prefs.edit().putLong("first", now).apply()
        if (force || now - prefs.getLong("tried", 0) > CHECK_EVERY_MS) {
            prefs.edit().putLong("tried", now).apply()
            runCatching {
                http.newCall(Request.Builder().url(CONFIG_URL).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body?.string().orEmpty()
                    val rule = parseRule(body)
                    prefs.edit().putString("rule", rule?.let { gson.toJson(it) }).putLong("ok", now).apply()
                }
            }
        }
        val rule = prefs.getString("rule", null)?.let { runCatching { gson.fromJson(it, GateRule::class.java) }.getOrNull() }
        val lastOk = prefs.getLong("ok", 0).takeIf { it > 0 } ?: prefs.getLong("first", now)
        decide(rule, lastOk, now, BuildConfig.VERSION_NAME)
    } }

    /** Called from each screen's onStart: a block replaces the whole task with the block screen. */
    fun enforce(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            val verdict = check(activity) ?: return@launch
            activity.startActivity(
                Intent(activity, BlockedActivity::class.java)
                    .putExtra(BlockedActivity.EXTRA_BLOCK, verdict.block.name)
                    .putExtra(BlockedActivity.EXTRA_MESSAGE, verdict.message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            activity.finish()
        }
    }
}
