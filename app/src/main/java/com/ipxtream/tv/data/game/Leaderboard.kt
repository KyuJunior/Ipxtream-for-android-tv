package com.ipxtream.tv.data.game

import android.content.SharedPreferences
import com.ipxtream.tv.BuildConfig
import com.ipxtream.tv.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The public game leaderboard (server/leaderboard). Each player's best score per game is sent once;
 * one that couldn't be sent (offline, server busy) goes on the next finished round or Scores visit.
 * Off while [BuildConfig.LEADERBOARD_URL] is empty.
 */
object Leaderboard {
    data class Entry(val name: String, val score: Int, val you: Boolean)
    data class Board(val top: List<Entry>, val yourRank: Int?, val yourScore: Int?)

    val enabled: Boolean get() = BuildConfig.LEADERBOARD_URL.isNotEmpty()

    private val base get() = BuildConfig.LEADERBOARD_URL.trimEnd('/')
    private val http by lazy { ApiClient.sharedHttp.newBuilder().callTimeout(8, TimeUnit.SECONDS).build() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends [scores]' best if the server hasn't had it yet. [scores] is the game's prefs ("best", "sent"),
     * [player] the player prefs (id and name). Safe to call often.
     */
    fun sync(game: String, scores: SharedPreferences, player: SharedPreferences) {
        if (!enabled) return
        val best = scores.getInt("best", 0)
        if (best <= scores.getInt("sent", 0)) return
        val name = player.getString(PlayerKeys.NAME, null).orEmpty()
        if (name.isEmpty()) return
        val id = PlayerKeys.id(player)
        scope.launch {
            val body = JSONObject().put("game", game).put("player", id).put("name", name).put("score", best)
            val code = post("/v1/score", body) ?: return@launch
            // 2xx: stored. 4xx other than 429: refused for good (e.g. a blocked name), don't retry it forever.
            if (code in 200..299 || (code in 400..499 && code != 429)) scores.edit().putInt("sent", best).apply()
        }
    }

    /** Renames this player on every board. */
    fun rename(player: SharedPreferences) {
        if (!enabled) return
        val name = player.getString(PlayerKeys.NAME, null).orEmpty()
        if (name.isEmpty()) return
        val body = JSONObject().put("player", PlayerKeys.id(player)).put("name", name)
        scope.launch { post("/v1/name", body) }
    }

    /** The top of [game], or null when the server can't be reached. */
    suspend fun top(game: String, player: SharedPreferences): Board? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        runCatching {
            val request = Request.Builder().url("$base/v1/top?game=$game&player=${PlayerKeys.id(player)}").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val list = json.getJSONArray("top")
                val top = (0 until list.length()).map { i ->
                    val row = list.getJSONObject(i)
                    Entry(row.getString("name"), row.getInt("score"), row.optBoolean("you"))
                }
                val you = json.optJSONObject("you")
                Board(top, you?.optInt("rank")?.takeIf { it > 0 }, you?.optInt("score"))
            }
        }.getOrNull()
    }

    private fun post(path: String, body: JSONObject): Int? = runCatching {
        val request = Request.Builder().url(base + path).post(body.toString().toRequestBody(jsonType)).build()
        http.newCall(request).execute().use { it.code }
    }.getOrNull()
}

/** Where the player's identity lives in the "games_player" prefs. */
object PlayerKeys {
    const val PREFS = "games_player"
    const val NAME = "name"
    private const val ID = "id"

    /** A random id per install, so the board can tell players with the same name apart. */
    fun id(prefs: SharedPreferences): String =
        prefs.getString(ID, null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString(ID, it).apply()
        }
}
