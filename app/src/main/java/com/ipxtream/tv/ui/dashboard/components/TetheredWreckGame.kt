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
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Tethered Wreck in dp: a fragile dot follows the finger and swings heavy balls on springs into waves. */
class WreckWorld(val width: Float, val height: Float, private val random: Random) {
    var dotX = width / 2f
        private set
    var dotY = height / 2f
        private set
    var targetX = dotX
    var targetY = dotY

    val ballX = FloatArray(MAX_BALLS) { dotX + 40f }
    val ballY = FloatArray(MAX_BALLS) { dotY }
    private val ballVx = FloatArray(MAX_BALLS)
    private val ballVy = FloatArray(MAX_BALLS)
    var balls = 1
        private set
    var ballRadius = 15f
        private set

    val enemyX = FloatArray(MAX_ENEMIES)
    val enemyY = FloatArray(MAX_ENEMIES)
    var enemies = 0
        private set

    var kills = 0
        private set
    var level = 1
        private set
    var over = false
        private set
    private var nextLevelAt = 8
    private var time = 0f
    private var spawnIn = 1f

    /** Advances in fixed small steps, so a slow frame can't blow the spring up. */
    fun step(dt: Float) {
        repeat(ceil(dt / STEP).toInt().coerceAtLeast(1)) { if (!over) fixedStep(STEP) }
    }

    private fun fixedStep(h: Float) {
        time += h
        moveDot(h)
        for (b in 0 until balls) moveBall(b, h)
        spawnIn -= h
        if (spawnIn <= 0f) {
            spawn()
            spawnIn = (1.2f - time * 0.012f).coerceAtLeast(0.3f)
        }
        moveEnemies(h)
    }

    private fun moveDot(h: Float) {
        val dx = targetX - dotX
        val dy = targetY - dotY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 0.01f) return
        val move = min(distance, DOT_SPEED * h)
        dotX += dx / distance * move
        dotY += dy / distance * move
    }

    private fun moveBall(b: Int, h: Float) {
        val dx = dotX - ballX[b]
        val dy = dotY - ballY[b]
        val distance = sqrt(dx * dx + dy * dy)
        val rest = TETHER + b * 28f
        if (distance > rest) {
            val pull = SPRING * (distance - rest) / distance
            ballVx[b] += dx * pull * h
            ballVy[b] += dy * pull * h
        }
        ballVx[b] *= 1f - DAMPING * h
        ballVy[b] *= 1f - DAMPING * h
        ballX[b] += ballVx[b] * h
        ballY[b] += ballVy[b] * h
        if (ballX[b] < 0f || ballX[b] > width) ballVx[b] *= -0.6f
        if (ballY[b] < 0f || ballY[b] > height) ballVy[b] *= -0.6f
        ballX[b] = ballX[b].coerceIn(0f, width)
        ballY[b] = ballY[b].coerceIn(0f, height)
    }

    /** Enemies appear on a random edge. */
    private fun spawn() {
        if (enemies == MAX_ENEMIES) return
        val along = random.nextFloat()
        when (random.nextInt(4)) {
            0 -> { enemyX[enemies] = along * width; enemyY[enemies] = 0f }
            1 -> { enemyX[enemies] = along * width; enemyY[enemies] = height }
            2 -> { enemyX[enemies] = 0f; enemyY[enemies] = along * height }
            else -> { enemyX[enemies] = width; enemyY[enemies] = along * height }
        }
        enemies++
    }

    private fun moveEnemies(h: Float) {
        val speed = min(38f + time * 1.1f, 105f)
        var e = 0
        while (e < enemies) {
            val dx = dotX - enemyX[e]
            val dy = dotY - enemyY[e]
            val distance = sqrt(dx * dx + dy * dy)
            if (distance < DOT_RADIUS + ENEMY_RADIUS) {
                over = true
                return
            }
            enemyX[e] += dx / distance * speed * h
            enemyY[e] += dy / distance * speed * h
            if (crushed(e)) remove(e) else e++
        }
    }

    private fun crushed(e: Int): Boolean {
        for (b in 0 until balls) {
            val dx = ballX[b] - enemyX[e]
            val dy = ballY[b] - enemyY[e]
            val reach = ballRadius + ENEMY_RADIUS
            val swung = ballVx[b] * ballVx[b] + ballVy[b] * ballVy[b] > CRUSH_SPEED * CRUSH_SPEED
            if (swung && dx * dx + dy * dy < reach * reach) return true
        }
        return false
    }

    private fun remove(e: Int) {
        enemies--
        enemyX[e] = enemyX[enemies]
        enemyY[e] = enemyY[enemies]
        kills++
        if (kills >= nextLevelAt) {
            level++
            nextLevelAt += 8 + level * 4
            ballRadius = min(ballRadius + 3f, 30f)
            if (level == 3 && balls < MAX_BALLS) {
                ballX[1] = dotX
                ballY[1] = dotY + TETHER
                balls = 2
            }
        }
    }

    companion object {
        const val MAX_ENEMIES = 48
        const val MAX_BALLS = 2
        const val STEP = 1f / 120f
        const val DOT_RADIUS = 8f
        const val ENEMY_RADIUS = 9f
        const val DOT_SPEED = 700f
        const val TETHER = 62f
        const val SPRING = 70f
        const val DAMPING = 1.1f
        const val CRUSH_SPEED = 110f
    }
}

/** Survive as long as you can; touch anywhere to start again after being caught. */
@Composable
internal fun TetheredWreckScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("wreck_game")
    val density = LocalDensity.current.density
    var world by remember { mutableStateOf<WreckWorld?>(null) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var kills by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var frame by remember { mutableIntStateOf(0) }

    GameLoop(running = world != null && started && !over) { dt ->
        world?.let {
            it.step(dt)
            frame++
            kills = it.kills
            level = it.level
            if (it.over) {
                over = true
                overAt = System.currentTimeMillis()
                best.submit(it.kills)
            }
        }
    }

    fun aim(position: Offset) {
        world?.targetX = position.x / density
        world?.targetY = position.y / density
    }

    val current = world
    Box(
        modifier
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
            .onSizeChanged { if (world == null) world = WreckWorld(it.width / density, it.height / density, Random.Default) }
            .fingerPosition(
                onDown = { position ->
                    val active = world
                    if (over && active != null) {
                        if (System.currentTimeMillis() - overAt > 500) {
                            world = WreckWorld(active.width, active.height, Random.Default)
                            over = false
                            kills = 0
                            level = 1
                        }
                    } else {
                        started = true
                        aim(position)
                    }
                },
                onMove = ::aim
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame  // redraw only, no recomposition
            val w = current ?: return@Canvas
            val dot = Offset(w.dotX * density, w.dotY * density)
            for (b in 0 until w.balls) {
                val ball = Offset(w.ballX[b] * density, w.ballY[b] * density)
                drawLine(Color.White.copy(alpha = 0.35f), dot, ball, 1.5f * density)
                drawCircle(Accent, w.ballRadius * density, ball)
            }
            for (e in 0 until w.enemies) {
                drawCircle(AccentOrchid, WreckWorld.ENEMY_RADIUS * density, Offset(w.enemyX[e] * density, w.enemyY[e] * density))
            }
            drawCircle(ChipFocused, WreckWorld.DOT_RADIUS * density, dot)
        }

        GameScore(kills.toString(), best.value, extra = stringResource(R.string.game_wreck_level, level))
        Text(
            stringResource(if (over) R.string.game_wreck_over else R.string.game_wreck_hint),
            style = IpxTypography.BodyMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}
