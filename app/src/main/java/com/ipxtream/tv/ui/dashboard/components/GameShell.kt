package com.ipxtream.tv.ui.dashboard.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** One finished run: its [score] and when it ended ([at], epoch ms; 0 for a best saved before history existed). */
internal data class GamePlay(val score: Int, val at: Long, val name: String = "")

/** The recent plays and the top 10 of a game, stored on this device as "score:time:name;…" (older entries have no name). */
internal object GameRecords {
    const val HISTORY = 10
    const val TOP = 10

    fun parse(text: String?): List<GamePlay> = text.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split(':')
        val score = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        when (parts.size) {
            2 -> GamePlay(score, at)
            3 -> GamePlay(score, at, parts[2])
            else -> null
        }
    }

    fun format(plays: List<GamePlay>): String =
        plays.joinToString(";") { "${it.score}:${it.at}:${GamePlayerName.clean(it.name)}" }

    fun addToHistory(history: List<GamePlay>, play: GamePlay): List<GamePlay> = (listOf(play) + history).take(HISTORY)

    /** Highest first; an equal score keeps the earlier run ahead. */
    fun addToTop(top: List<GamePlay>, play: GamePlay): List<GamePlay> =
        (top + play).sortedWith(compareByDescending<GamePlay> { it.score }.thenBy { it.at }).take(TOP)
}

/** Keeps a game's best score, its recent plays and its top 10 in the game's own local prefs. */
/** The name a player shows under on this device's leaderboards. */
internal object GamePlayerName {
    const val PREFS = com.ipxtream.tv.data.game.PlayerKeys.PREFS
    const val KEY = com.ipxtream.tv.data.game.PlayerKeys.NAME
    const val MAX = 20

    /** No separators of the stored format, no control characters, at most [MAX] characters. */
    fun clean(raw: String): String = raw.filterNot { it == ':' || it == ';' || it.isISOControl() }.trim().take(MAX)

    fun random(adjectives: List<String>, nouns: List<String>, random: kotlin.random.Random = kotlin.random.Random): String =
        clean("${adjectives.random(random)} ${nouns.random(random)} ${random.nextInt(10, 100)}")
}

internal class BestScore(
    private val prefs: android.content.SharedPreferences,
    private val player: android.content.SharedPreferences? = null,
    /** The game's id on the public leaderboard (its prefs name); empty = local only. */
    private val game: String = ""
) {
    private var stored by mutableIntStateOf(prefs.getInt("best", 0))
    val value: Int get() = stored

    fun submit(score: Int) {
        if (score <= stored) return
        stored = score
        prefs.edit().putInt("best", score).apply()
    }

    /** A run has ended: it goes into the history and, if good enough, the top 10. */
    fun played(score: Int, at: Long = System.currentTimeMillis()) {
        val play = GamePlay(score, at, player?.getString(GamePlayerName.KEY, null).orEmpty())
        prefs.edit()
            .putString("history", GameRecords.format(GameRecords.addToHistory(history(), play)))
            .putString("top", GameRecords.format(GameRecords.addToTop(top(), play)))
            .apply()
        submit(score)
        sync()
    }

    /** Sends this game's best to the public leaderboard if it isn't there yet. */
    fun sync() {
        if (game.isNotEmpty() && player != null) com.ipxtream.tv.data.game.Leaderboard.sync(game, prefs, player)
    }

    fun history(): List<GamePlay> = GameRecords.parse(prefs.getString("history", null))

    /** A best kept from before the top 10 existed shows as its first entry. */
    fun top(): List<GamePlay> = GameRecords.parse(prefs.getString("top", null))
        .ifEmpty { if (stored > 0) listOf(GamePlay(stored, 0L)) else emptyList() }
}

@Composable
internal fun rememberBestScore(name: String): BestScore {
    val context = LocalContext.current
    return remember(name) {
        BestScore(
            context.getSharedPreferences(name, Context.MODE_PRIVATE),
            context.getSharedPreferences(GamePlayerName.PREFS, Context.MODE_PRIVATE),
            name
        )
    }
}

/** The focus target every game needs. It fills the window: only Up leaves it, for the top pills. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.gameFocus(game: FocusRequester, bar: FocusRequester): Modifier = focusRequester(game)
    .focusProperties {
        start = FocusRequester.Cancel
        up = bar
        end = FocusRequester.Cancel
        down = FocusRequester.Cancel
    }
    .focusable()

/** True while How to play is open or focus sits on the top pills: every game clock stops. */
internal val LocalGamePaused = compositionLocalOf { false }

/**
 * Calls [step] while [running] with the seconds since the last call (a stall counts as 0.05 s):
 * every frame, or every second frame with reduced effects, which halves the redraws on weak devices.
 */
@Composable
internal fun GameLoop(running: Boolean, step: (Float) -> Unit) {
    val current by rememberUpdatedState(step)
    val framesPerStep = if (LocalReducedEffects.current) 2 else 1
    val active = running && !LocalGamePaused.current
    LaunchedEffect(active, framesPerStep) {
        if (!active) return@LaunchedEffect
        var last = withFrameMillis { it }
        var frames = 0
        var elapsed = 0f
        while (true) withFrameMillis { now ->
            elapsed += ((now - last) / 1000f).coerceAtMost(0.05f)
            last = now
            if (++frames % framesPerStep == 0) {
                current(elapsed)
                elapsed = 0f
            }
        }
    }
}

/** Reports where a finger is, from touching down to lifting off. */
internal fun Modifier.fingerPosition(
    onDown: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onUp: () -> Unit = {}
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            onDown(awaitFirstDown().position)
            do {
                val event = awaitPointerEvent()
                val finger = event.changes.first()
                if (finger.pressed) {
                    onMove(finger.position)
                    finger.consume()
                }
            } while (event.changes.any { it.pressed })
            onUp()
        }
    }

/** The big score and the best score, in a glass capsule so they read over any artwork. */
@Composable
internal fun GameScore(score: String, best: Int, modifier: Modifier = Modifier, extra: String? = null) {
    Box(modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                score,
                style = IpxTypography.HeadlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                stringResource(R.string.game_best, best),
                style = IpxTypography.BodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = TextSecondary
            )
            if (extra != null) {
                Text(
                    extra,
                    style = IpxTypography.BodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = TextSecondary
                )
            }
        }
    }
}

/** The one result screen every game ends on. */
@Composable
internal fun GameResultPanel(
    title: String,
    score: String,
    best: Int,
    newBest: Boolean,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(SlateDeep.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 36.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = IpxTypography.HeadlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Text(
            score,
            style = IpxTypography.HeadlineMedium.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
            color = Accent
        )
        Text(
            if (newBest) stringResource(R.string.game_new_best) else stringResource(R.string.game_best, best),
            style = IpxTypography.BodyMedium,
            color = if (newBest) AccentAmber else TextSecondary
        )
        Text(hint, style = IpxTypography.BodyMedium, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A sky or sea behind a game. Cheap: one gradient, no blur. */
internal fun DrawScope.drawGameBackdrop(top: Color, bottom: Color) {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
}

/** Pooled sparks for hits and wins. Works in whatever units the game passes in; adds no garbage per frame. */
internal class GameParticles(private val reduced: Boolean, capacity: Int = 140) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val span = FloatArray(capacity)
    private val tint = IntArray(capacity)
    private var next = 0

    fun burst(atX: Float, atY: Float, color: Color, count: Int, speed: Float, seconds: Float = 0.6f) {
        repeat(if (reduced) count / 3 + 1 else count) {
            val angle = Random.nextFloat() * 6.283f
            val pace = speed * (0.3f + Random.nextFloat() * 0.7f)
            x[next] = atX
            y[next] = atY
            vx[next] = cos(angle) * pace
            vy[next] = sin(angle) * pace
            span[next] = seconds * (0.6f + Random.nextFloat() * 0.4f)
            life[next] = span[next]
            tint[next] = color.toArgb()
            next = (next + 1) % x.size
        }
    }

    fun step(dt: Float, gravity: Float = 0f) {
        for (i in x.indices) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            vy[i] += gravity * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }

    /** [scale] and the origin map the game's units to pixels; [radius] is in pixels. */
    fun draw(scope: DrawScope, scale: Float, originX: Float, originY: Float, radius: Float) {
        for (i in x.indices) {
            if (life[i] <= 0f) continue
            val fade = (life[i] / span[i]).coerceIn(0f, 1f)
            scope.drawCircle(
                Color(tint[i]).copy(alpha = fade),
                radius * (0.4f + 0.6f * fade),
                Offset(originX + x[i] * scale, originY + y[i] * scale)
            )
        }
    }
}

@Composable
internal fun rememberGameParticles(): GameParticles {
    val reduced = LocalReducedEffects.current
    return remember(reduced) { GameParticles(reduced) }
}

/** A short decaying camera kick for big hits; nothing moves with reduced effects. */
internal class GameShake(private val reduced: Boolean) {
    private var power = 0f
    private var clock = 0f

    fun kick(strength: Float) {
        if (!reduced) power = max(power, strength)
    }

    fun step(dt: Float) {
        clock += dt
        power = max(0f, power - power * 9f * dt - dt)
    }

    val x: Float get() = sin(clock * 71f) * power
    val y: Float get() = cos(clock * 53f) * power
}

@Composable
internal fun rememberGameShake(): GameShake {
    val reduced = LocalReducedEffects.current
    return remember(reduced) { GameShake(reduced) }
}
