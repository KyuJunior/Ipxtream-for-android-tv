package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

enum class NovaKind { NORMAL, MAGNET, LASER, HOLE }

/** One level of One-Tap Nova, in dp: one tap makes a blast, and every particle it touches blasts too. */
class NovaWorld(val level: Int, val width: Float, val height: Float, random: Random) {
    val count = min(20 + level * 4, MAX_PARTICLES)
    val x = FloatArray(count) { random.nextFloat() * width }
    val y = FloatArray(count) { random.nextFloat() * height }
    private val vx = FloatArray(count) { (random.nextFloat() - 0.5f) * 70f }
    private val vy = FloatArray(count) { (random.nextFloat() - 0.5f) * 70f }
    val kind = Array(count) { i ->
        when {
            level >= 5 && i < min(level - 3, 4) -> NovaKind.HOLE
            level >= 4 && i % 9 == 5 -> NovaKind.LASER
            level >= 3 && i % 7 == 3 -> NovaKind.MAGNET
            else -> NovaKind.NORMAL
        }
    }
    val popped = BooleanArray(count)
    private val poppable = kind.count { it != NovaKind.HOLE }
    val target = ceil(poppable * min(0.3f + 0.03f * level, 0.8f)).toInt()

    val blastX = FloatArray(count + 1)
    val blastY = FloatArray(count + 1)
    val blastAge = FloatArray(count + 1)
    private val blastKind = Array(count + 1) { NovaKind.NORMAL }
    var blasts = 0
        private set
    var poppedCount = 0
        private set
    private var tapped = false
    private val reach = min(width, height) * 0.26f

    val finished: Boolean get() = tapped && (0 until blasts).none { blastAge[it] < BLAST_LIFE }
    val won: Boolean get() = poppedCount >= target

    fun blastRadius(index: Int): Float = reach * min(blastAge[index] / GROW_TIME, 1f)

    fun tap(tapX: Float, tapY: Float) {
        if (tapped) return
        tapped = true
        addBlast(tapX, tapY, NovaKind.NORMAL)
    }

    private fun addBlast(atX: Float, atY: Float, type: NovaKind) {
        blastX[blasts] = atX
        blastY[blasts] = atY
        blastAge[blasts] = 0f
        blastKind[blasts] = type
        blasts++
    }

    fun step(dt: Float) {
        for (i in 0 until count) {
            if (popped[i]) continue
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (x[i] < 0f || x[i] > width) vx[i] = -vx[i]
            if (y[i] < 0f || y[i] > height) vy[i] = -vy[i]
            x[i] = x[i].coerceIn(0f, width)
            y[i] = y[i].coerceIn(0f, height)
        }
        var b = 0
        while (b < blasts) {  // pop() adds blasts, and this loop reaches them in the same frame
            if (blastAge[b] < BLAST_LIFE) {
                blastAge[b] += dt
                if (blastKind[b] == NovaKind.MAGNET) pull(blastX[b], blastY[b], dt)
                touchParticles(b)
            }
            b++
        }
    }

    private fun touchParticles(b: Int) {
        val radius = blastRadius(b)
        for (i in 0 until count) {
            if (popped[i]) continue
            val dx = x[i] - blastX[b]
            val dy = y[i] - blastY[b]
            if (dx * dx + dy * dy > (radius + PARTICLE_RADIUS) * (radius + PARTICLE_RADIUS)) continue
            if (kind[i] == NovaKind.HOLE) {
                blastAge[b] = BLAST_LIFE  // swallowed
                return
            }
            pop(i)
        }
    }

    private fun pop(index: Int) {
        popped[index] = true
        poppedCount++
        addBlast(x[index], y[index], kind[index])
        if (kind[index] != NovaKind.LASER) return
        for (i in 0 until count) {
            if (popped[i] || kind[i] == NovaKind.HOLE) continue
            if (abs(x[i] - x[index]) < LASER_WIDTH || abs(y[i] - y[index]) < LASER_WIDTH) pop(i)
        }
    }

    private fun pull(atX: Float, atY: Float, dt: Float) {
        for (i in 0 until count) {
            if (popped[i] || kind[i] == NovaKind.HOLE) continue
            val dx = atX - x[i]
            val dy = atY - y[i]
            val distance = sqrt(dx * dx + dy * dy)
            if (distance < 1f || distance > reach * 3f) continue
            x[i] += dx / distance * PULL_SPEED * dt
            y[i] += dy / distance * PULL_SPEED * dt
        }
    }

    companion object {
        const val MAX_PARTICLES = 60
        const val PARTICLE_RADIUS = 6f
        const val BLAST_LIFE = 1.2f
        const val GROW_TIME = 0.3f
        const val LASER_WIDTH = 8f
        const val PULL_SPEED = 90f
    }
}

/** Tap once per level and hope the chain reaches the target. Three failed levels end the run. */
@Composable
internal fun NovaGameScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("nova_game")
    val reduced = LocalReducedEffects.current
    val density = LocalDensity.current.density
    var world by remember { mutableStateOf<NovaWorld?>(null) }
    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var finished by remember { mutableStateOf(false) }
    var popped by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun newWorld() {
        if (size.width == 0) return
        world = NovaWorld(level, size.width / density, size.height / density, Random.Default)
        finished = false
        popped = 0
    }

    GameLoop(running = world != null && !finished) { dt ->
        world?.let {
            it.step(dt)
            frame++
            popped = it.poppedCount
            if (it.finished) {
                finished = true
                if (it.won) best.submit(level) else lives--
            }
        }
    }

    val current = world
    Box(
        modifier
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
            .onSizeChanged {
                size = it
                if (world == null) newWorld()
            }
            .fingerPosition(
                onDown = { position ->
                    val active = world
                    when {
                        active == null -> newWorld()
                        !finished -> active.tap(position.x / density, position.y / density)
                        active.won -> { level++; newWorld() }
                        lives <= 0 -> { level = 1; lives = 3; newWorld() }
                        else -> newWorld()
                    }
                },
                onMove = {}
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame  // redraw only, no recomposition
            val w = current ?: return@Canvas
            val scale = density
            for (b in 0 until w.blasts) {
                if (w.blastAge[b] >= NovaWorld.BLAST_LIFE) continue
                val fade = 1f - w.blastAge[b] / NovaWorld.BLAST_LIFE
                val center = Offset(w.blastX[b] * scale, w.blastY[b] * scale)
                val radius = w.blastRadius(b) * scale
                if (reduced) drawCircle(Accent.copy(alpha = fade), radius, center, style = Stroke(2f * scale))
                else drawCircle(Accent.copy(alpha = 0.45f * fade), radius, center)
            }
            for (i in 0 until w.count) {
                if (w.popped[i]) continue
                val center = Offset(w.x[i] * scale, w.y[i] * scale)
                val radius = NovaWorld.PARTICLE_RADIUS * scale
                when (w.kind[i]) {
                    NovaKind.NORMAL -> drawCircle(ChipFocused, radius, center)
                    NovaKind.MAGNET -> drawCircle(AccentOrchid, radius * 1.3f, center)
                    NovaKind.LASER -> drawCircle(Color(0xFF58D6A9), radius * 1.3f, center)
                    NovaKind.HOLE -> {
                        drawCircle(Color.Black, radius * 2f, center)
                        drawCircle(AccentOrchid, radius * 2f, center, style = Stroke(1.5f * scale))
                    }
                }
            }
        }

        GameScore(
            score = stringResource(R.string.game_nova_progress, popped, current?.target ?: 0),
            best = best.value,
            extra = stringResource(R.string.game_nova_level, level, lives)
        )
        val message = when {
            current == null -> null
            !finished -> stringResource(if (level >= 5) R.string.game_nova_hole_hint else R.string.game_nova_hint)
            current.won -> stringResource(R.string.game_nova_clear)
            lives <= 0 -> stringResource(R.string.game_nova_over, level)
            else -> stringResource(R.string.game_nova_retry)
        }
        if (message != null) {
            Text(
                message,
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp)
            )
        }
    }
}
