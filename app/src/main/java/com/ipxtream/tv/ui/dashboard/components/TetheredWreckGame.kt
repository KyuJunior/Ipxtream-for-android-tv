package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Tethered Wreck in dp: a fragile dot follows the finger and swings heavy balls on springs into waves. */
class WreckWorld(width: Float, height: Float, private val random: Random) {
    var width = width
        private set
    var height = height
        private set
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
    var time = 0f
        private set

    /** Tells the screen where an enemy was crushed, in dp. */
    var onCrush: ((Float, Float) -> Unit)? = null

    private var nextLevelAt = 8
    private var spawnIn = 1f
    private var pending = 0f

    fun ballSpeed(b: Int): Float = sqrt(ballVx[b] * ballVx[b] + ballVy[b] * ballVy[b])

    fun tetherLength(b: Int): Float = TETHER + b * 28f

    /** Follows the game area when it changes size, so a run survives the side menu moving. */
    fun resize(newWidth: Float, newHeight: Float) {
        width = newWidth
        height = newHeight
        dotX = dotX.coerceIn(0f, width)
        dotY = dotY.coerceIn(0f, height)
        for (b in 0 until balls) {
            ballX[b] = ballX[b].coerceIn(0f, width)
            ballY[b] = ballY[b].coerceIn(0f, height)
        }
    }

    /** Advances in fixed small steps, so a slow frame can't blow the spring up. Leftover time carries over. */
    fun step(dt: Float) {
        pending += dt
        while (pending >= STEP && !over) {
            fixedStep(STEP)
            pending -= STEP
        }
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
        // A finger can drag past the game area; enemies would never reach a dot out there.
        val dx = targetX.coerceIn(0f, width) - dotX
        val dy = targetY.coerceIn(0f, height) - dotY
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
        val rest = tetherLength(b)
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
        // An edge right beside the dot would catch it with no warning, so those use the far side.
        val dx = enemyX[enemies] - dotX
        val dy = enemyY[enemies] - dotY
        if (dx * dx + dy * dy < SPAWN_CLEARANCE * SPAWN_CLEARANCE) {
            enemyX[enemies] = width - enemyX[enemies]
            enemyY[enemies] = height - enemyY[enemies]
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
        onCrush?.invoke(enemyX[e], enemyY[e])
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
        const val SPAWN_CLEARANCE = 70f
    }
}

private const val TAU = (2.0 * PI).toFloat()
private const val POPS = 10
private const val POP_LIFE = 0.55f
private const val TRAIL = 12
private const val DANGER_RANGE = 64f

private val FloorWarm = Color(0xFF52201A)
private val FloorMid = Color(0xFF2E120F)
private val FloorDeep = Color(0xFF140807)
private val StoneDark = Color(0xFF2A1512)
private val StoneLight = Color(0xFF43241C)
private val StoneEdge = Color(0xFF6A3A2A)
private val LavaDeep = Color(0xFFD8340F)
private val LavaMid = Color(0xFFFF7A1F)
private val LavaBright = Color(0xFFFFD36B)
private val IronLight = Color(0xFF9AA3AE)
private val IronMid = Color(0xFF4A5059)
private val IronDark = Color(0xFF1E2126)
private val MoltenLight = Color(0xFFFFF0B0)
private val MoltenMid = Color(0xFFFF9A2E)
private val MoltenEdge = Color(0xFFB82D10)
private val ChainCold = Color(0xFF8C93A0)
private val CreatureBody = Color(0xFF6B3FC4)
private val CreatureBack = Color(0xFF3F2185)
private val CreaturePupil = Color(0xFF17082E)
private val IdleCreatures = floatArrayOf(0.2f, 0.72f, 0.8f, 0.7f, 0.88f, 0.35f, 0.12f, 0.4f, 0.62f, 0.84f)

/** The decoration of a run: nothing here changes the rules. */
private class WreckFx {
    var clock = 0f
    var levelFlash = 0f
    var hurtFlash = 0f
    val popX = FloatArray(POPS)
    val popY = FloatArray(POPS)
    val popAge = FloatArray(POPS) { POP_LIFE }
    private var nextPop = 0
    val spin = FloatArray(WreckWorld.MAX_BALLS)
    val heat = FloatArray(WreckWorld.MAX_BALLS)
    val trailX = Array(WreckWorld.MAX_BALLS) { FloatArray(TRAIL) }
    val trailY = Array(WreckWorld.MAX_BALLS) { FloatArray(TRAIL) }
    val trailCount = IntArray(WreckWorld.MAX_BALLS)

    fun pop(x: Float, y: Float) {
        popX[nextPop] = x
        popY[nextPop] = y
        popAge[nextPop] = 0f
        nextPop = (nextPop + 1) % POPS
    }

    fun advance(dt: Float, world: WreckWorld) {
        clock += dt
        levelFlash = max(0f, levelFlash - dt / 1.2f)
        hurtFlash = max(0f, hurtFlash - dt / 0.6f)
        for (i in 0 until POPS) popAge[i] += dt
        for (b in 0 until world.balls) {
            val speed = world.ballSpeed(b)
            spin[b] += speed / world.ballRadius * dt
            heat[b] += ((if (speed > WreckWorld.CRUSH_SPEED) 1f else 0f) - heat[b]) * min(1f, dt * 16f)
            val slot = trailCount[b] % TRAIL
            trailX[b][slot] = world.ballX[b]
            trailY[b][slot] = world.ballY[b]
            trailCount[b]++
        }
    }

    fun reset() {
        levelFlash = 0f
        hurtFlash = 0f
        popAge.fill(POP_LIFE)
        heat.fill(0f)
        trailCount.fill(0)
    }
}

/** Survive as long as you can; touch anywhere to start again after being caught. */
@Composable
internal fun TetheredWreckScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("wreck_game")
    val reduced = LocalReducedEffects.current
    val paused by rememberUpdatedState(LocalGamePaused.current)
    val density = LocalDensity.current.density
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val fx = remember { WreckFx() }
    val spikes = remember { Path() }
    val textPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    var world by remember { mutableStateOf<WreckWorld?>(null) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var kills by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var frame by remember { mutableIntStateOf(0) }
    val cracks = remember(stage) { wreckCracks(stage.width.toFloat(), stage.height.toFloat()) }

    fun newWorld(width: Float, height: Float): WreckWorld {
        val fresh = WreckWorld(width, height, Random.Default)
        fresh.onCrush = { x, y ->
            fx.pop(x, y)
            particles.burst(x, y, AccentOrchid, 12, 170f, 0.5f)
            particles.burst(x, y, AccentAmber, 8, 230f, 0.4f)
            shake.kick(3f)
        }
        return fresh
    }

    // Keeps running a moment after the end, so the last burst plays out behind the result.
    GameLoop(running = world != null && !settled) { dt ->
        world?.let {
            if (started && !over) {
                it.step(dt)
                kills = it.kills
                if (it.level != level) {
                    level = it.level
                    fx.levelFlash = 1f
                    shake.kick(6f)
                    for (b in 0 until it.balls) particles.burst(it.ballX[b], it.ballY[b], AccentAmber, 18, 200f)
                }
                if (it.over) {
                    over = true
                    overAt = System.currentTimeMillis()
                    newBest = it.kills > best.value
                    best.submit(it.kills)
                    fx.hurtFlash = 1f
                    shake.kick(12f)
                    particles.burst(it.dotX, it.dotY, ChipFocused, 26, 240f, 0.8f)
                    particles.burst(it.dotX, it.dotY, AccentRed, 26, 170f, 0.8f)
                }
            }
            fx.advance(dt, it)
            particles.step(dt)
            shake.step(dt)
            if (over && System.currentTimeMillis() - overAt > 1500) settled = true
            frame++
        }
    }

    fun aim(position: Offset) {
        world?.targetX = position.x / density
        world?.targetY = position.y / density
    }

    val current = world
    val levelText = stringResource(R.string.game_wreck_level, level)
    Box(
        modifier
            .clipToBounds()
            .background(FloorDeep)
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .onSizeChanged {
                stage = it
                val active = world
                // Until the first touch the dot stays centred, even if the bars hide and the window grows.
                if (active == null || !started) world = newWorld(it.width / density, it.height / density)
                else active.resize(it.width / density, it.height / density)
            }
            .fingerPosition(
                onDown = { position ->
                    val active = world
                    when {
                        paused -> Unit
                        over && active != null -> if (System.currentTimeMillis() - overAt > 500) {
                            world = newWorld(active.width, active.height)
                            fx.reset()
                            over = false
                            settled = false
                            newBest = false
                            kills = 0
                            level = 1
                            aim(position)
                        }
                        else -> {
                            started = true
                            aim(position)
                        }
                    }
                },
                onMove = ::aim
            )
    ) {
        // Its own layer, so the moving pieces never re-record the floor.
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                frame  // moves the layer only
                translationX = shake.x * density
                translationY = shake.y * density
            }
        ) { drawWreckFloor(cracks, reduced) }

        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                frame  // moves the layer only
                translationX = shake.x * density
                translationY = shake.y * density
            }
        ) {
            frame  // redraw only, no recomposition
            val w = current ?: return@Canvas
            val d = density
            val dot = Offset(w.dotX * d, w.dotY * d)
            if (!reduced) drawFloorLife(cracks, fx.clock)
            if (!started) drawStartScene(dot, fx.clock, reduced)

            var nearest = Float.MAX_VALUE
            for (e in 0 until w.enemies) {
                val dx = w.dotX - w.enemyX[e]
                val dy = w.dotY - w.enemyY[e]
                val distance = max(sqrt(dx * dx + dy * dy), 0.01f)
                nearest = min(nearest, distance)
                drawCreature(
                    Offset(w.enemyX[e] * d, w.enemyY[e] * d),
                    WreckWorld.ENEMY_RADIUS * d * 1.15f,
                    dx / distance,
                    dy / distance,
                    fx.clock * 9f + e,
                    reduced
                )
            }

            val link = Stroke(1.4f * d)
            val ring = Stroke(2.5f * d, cap = StrokeCap.Round)
            for (b in 0 until w.balls) {
                val ball = Offset(w.ballX[b] * d, w.ballY[b] * d)
                val heat = fx.heat[b]
                if (!reduced && heat > 0.02f) drawTrail(fx, b, w.ballRadius * d, heat)
                drawChain(dot, ball, w.tetherLength(b) * d, heat, link, reduced)
                drawWrecker(
                    ball,
                    w.ballRadius * d,
                    fx.spin[b],
                    heat,
                    min(1f, w.ballSpeed(b) / WreckWorld.CRUSH_SPEED),
                    spikes,
                    ring,
                    reduced
                )
            }

            if (!w.over) {
                if (nearest < DANGER_RANGE) {
                    drawCircle(
                        AccentRed.copy(alpha = (1f - nearest / DANGER_RANGE) * 0.85f),
                        (WreckWorld.DOT_RADIUS + 11f) * d,
                        dot,
                        style = ring
                    )
                }
                drawSpark(dot, fx.clock, reduced)
            }

            particles.draw(this, d, 0f, 0f, 2.6f * d)

            textPaint.textSize = 17f * d
            for (i in 0 until POPS) {
                val age = fx.popAge[i] / POP_LIFE
                if (age >= 1f) continue
                val at = Offset(fx.popX[i] * d, fx.popY[i] * d)
                drawCircle(LavaBright.copy(alpha = 1f - age), (10f + 34f * age) * d, at, style = ring)
                textPaint.color = ChipFocused.copy(alpha = 1f - age).toArgb()
                drawContext.canvas.nativeCanvas.drawText("+1", at.x, at.y - (16f + 26f * age) * d, textPaint)
            }

            if (fx.levelFlash > 0f) {
                drawRect(AccentAmber.copy(alpha = 0.2f * fx.levelFlash * fx.levelFlash))
                textPaint.textSize = 34f * d
                textPaint.color = LavaBright.copy(alpha = min(1f, fx.levelFlash * 2.5f)).toArgb()
                drawContext.canvas.nativeCanvas.drawText(levelText, size.width / 2f, size.height * 0.42f, textPaint)
            }
            if (fx.hurtFlash > 0f) drawRect(AccentRed.copy(alpha = 0.35f * fx.hurtFlash))
        }

        GameScore(kills.toString(), best.value, extra = levelText)

        if (!started) {
            Text(
                stringResource(R.string.game_wreck_hint),
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        if (over) {
            GameResultPanel(
                title = stringResource(R.string.game_wreck_title),
                score = kills.toString(),
                best = best.value,
                newBest = newBest,
                hint = stringResource(R.string.game_wreck_over),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** Lava cracks that wander and fork across the floor, in pixels. */
private fun wreckCracks(width: Float, height: Float): Path {
    val path = Path()
    val random = Random(11)
    fun walk(startX: Float, startY: Float, startAngle: Float, length: Int, forks: Int) {
        var x = startX
        var y = startY
        var angle = startAngle
        path.moveTo(x, y)
        repeat(length) {
            angle += (random.nextFloat() - 0.5f) * 1.1f
            val stride = (0.03f + random.nextFloat() * 0.04f) * width
            x += cos(angle) * stride
            y += sin(angle) * stride
            path.lineTo(x, y)
            if (forks > 0 && random.nextFloat() < 0.3f) {
                walk(x, y, angle + if (random.nextBoolean()) 0.9f else -0.9f, length / 2, forks - 1)
                path.moveTo(x, y)
            }
        }
    }
    repeat(9) { walk(random.nextFloat() * width, random.nextFloat() * height, random.nextFloat() * TAU, 7, 2) }
    return path
}

/** Everything that stays still; drawn again only when the window changes. */
private fun DrawScope.drawWreckFloor(cracks: Path, reduced: Boolean) {
    if (size.minDimension <= 0f) return
    val d = density
    val w = size.width
    val h = size.height
    val middle = Offset(w / 2f, h / 2f)
    drawRect(Brush.radialGradient(listOf(FloorWarm, FloorMid, FloorDeep), middle, max(w, h) * 0.62f))

    val random = Random(5)
    repeat(if (reduced) 18 else 70) { i ->
        val stoneW = (18f + random.nextFloat() * 34f) * d
        val stoneH = stoneW * (0.55f + random.nextFloat() * 0.35f)
        val at = Offset(random.nextFloat() * w - stoneW / 2f, random.nextFloat() * h - stoneH / 2f)
        val stone = Size(stoneW, stoneH)
        drawOval(Color.Black.copy(alpha = 0.3f), at + Offset(0f, 3f * d), stone)
        drawOval(if (i % 3 == 0) StoneLight else StoneDark, at, stone)
        drawOval(StoneEdge.copy(alpha = 0.35f), at + Offset(stoneW * 0.2f, stoneH * 0.12f), Size(stoneW * 0.45f, stoneH * 0.3f))
    }

    if (!reduced) {
        repeat(6) {
            val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
            val glow = (50f + random.nextFloat() * 70f) * d
            drawCircle(Brush.radialGradient(listOf(LavaDeep.copy(alpha = 0.32f), Color.Transparent), at, glow), glow, at)
        }
        drawPath(cracks, LavaDeep.copy(alpha = 0.3f), style = Stroke(9f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawPath(cracks, LavaMid, style = Stroke(3.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(cracks, LavaBright, style = Stroke(1.2f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))

    drawRect(
        Brush.radialGradient(
            0.45f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.72f),
            center = middle,
            radius = sqrt(w * w + h * h) / 2f
        )
    )
}

/** The cracks breathe and embers rise; both skipped on weak devices. */
private fun DrawScope.drawFloorLife(cracks: Path, clock: Float) {
    val d = density
    drawPath(
        cracks,
        LavaBright.copy(alpha = 0.17f + 0.12f * sin(clock * 1.7f)),
        style = Stroke(3f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    for (i in 0 until 16) {
        val rise = clock * (0.07f + 0.02f * (i % 4)) + i * 0.37f
        val life = rise - floor(rise)
        val along = i * 0.618f
        drawCircle(
            LavaBright.copy(alpha = sin(life * PI.toFloat()) * 0.7f),
            (1f + (i % 3) * 0.5f) * d,
            Offset((along - floor(along)) * size.width + sin(clock * 0.7f + i) * 14f * d, size.height * (1f - life))
        )
    }
}

/** Before the first touch: the swing path, a ball running round it, and creatures waiting. */
private fun DrawScope.drawStartScene(dot: Offset, clock: Float, reduced: Boolean) {
    val d = density
    val orbit = WreckWorld.TETHER * d
    for (i in 0 until 28) {
        val angle = i * TAU / 28f + clock * 0.4f
        drawCircle(ChipFocused.copy(alpha = 0.3f), 1.7f * d, dot + Offset(cos(angle) * orbit, sin(angle) * orbit))
    }
    val swing = clock * 2.6f
    val ghost = dot + Offset(cos(swing) * orbit, sin(swing) * orbit)
    drawCircle(MoltenMid.copy(alpha = 0.25f), 20f * d, ghost)
    drawCircle(AccentAmber.copy(alpha = 0.85f), 6f * d, ghost)
    val rise = clock * 0.7f
    val pulse = rise - floor(rise)
    drawCircle(ChipFocused.copy(alpha = 0.6f * (1f - pulse)), (12f + 34f * pulse) * d, dot, style = Stroke(2f * d))

    for (i in IdleCreatures.indices step 2) {
        val at = Offset(size.width * IdleCreatures[i], size.height * IdleCreatures[i + 1])
        val dx = dot.x - at.x
        val dy = dot.y - at.y
        val distance = max(sqrt(dx * dx + dy * dy), 0.01f)
        drawCreature(at, WreckWorld.ENEMY_RADIUS * d * 1.15f, dx / distance, dy / distance, clock * 3f + i, reduced)
    }
}

/** A cinder-horned violet critter; [lookX], [lookY] is the unit direction to the dot. */
private fun DrawScope.drawCreature(at: Offset, r: Float, lookX: Float, lookY: Float, phase: Float, reduced: Boolean) {
    val look = Offset(lookX, lookY)
    val side = Offset(-lookY, lookX)
    if (!reduced) {
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.05f, at + Offset(2f * density, 4f * density))
        val stride = sin(phase) * r * 0.45f
        drawCircle(CreatureBack, r * 0.34f, at + side * (r * 0.85f) + look * stride)
        drawCircle(CreatureBack, r * 0.34f, at - side * (r * 0.85f) - look * stride)
        val hornBase = at - look * (r * 0.25f)
        drawLine(LavaBright, hornBase + side * (r * 0.6f), hornBase + side * (r * 1.05f) - look * (r * 0.55f), 1.8f * density, StrokeCap.Round)
        drawLine(LavaBright, hornBase - side * (r * 0.6f), hornBase - side * (r * 1.05f) - look * (r * 0.55f), 1.8f * density, StrokeCap.Round)
    }
    drawCircle(CreatureBack, r, at)
    drawCircle(CreatureBody, r * 0.8f, at + look * (r * 0.14f))
    val eye = at + look * (r * 0.46f)
    val left = eye + side * (r * 0.4f)
    val right = eye - side * (r * 0.4f)
    drawCircle(Color.White, r * 0.33f, left)
    drawCircle(Color.White, r * 0.33f, right)
    drawCircle(CreaturePupil, r * 0.17f, left + look * (r * 0.13f))
    drawCircle(CreaturePupil, r * 0.17f, right + look * (r * 0.13f))
}

private fun DrawScope.drawTrail(fx: WreckFx, b: Int, radius: Float, heat: Float) {
    val count = fx.trailCount[b]
    for (k in 0 until min(count, TRAIL)) {
        val slot = (count - 1 - k) % TRAIL
        val fade = 1f - k.toFloat() / TRAIL
        drawCircle(
            MoltenMid.copy(alpha = 0.3f * fade * heat),
            radius * (0.45f + 0.5f * fade),
            Offset(fx.trailX[b][slot] * density, fx.trailY[b][slot] * density)
        )
    }
}

/** Rings and bars along the tether; it hangs slack when the ball is closer than its length. */
private fun DrawScope.drawChain(dot: Offset, ball: Offset, rest: Float, heat: Float, ringStyle: Stroke, reduced: Boolean) {
    val d = density
    val dx = ball.x - dot.x
    val dy = ball.y - dot.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val color = lerp(ChainCold, MoltenMid, heat)
    if (!reduced) drawLine(color.copy(alpha = 0.25f + 0.25f * heat), dot, ball, 5f * d, StrokeCap.Round)
    val links = (length / (8f * d)).toInt().coerceIn(3, 20)
    val sag = max(0f, rest - length) * 0.35f
    val bendX = -dy / length
    val bendY = dx / length
    for (i in 1 until links) {
        val t = i.toFloat() / links
        val bend = sin(t * PI.toFloat()) * sag
        val at = Offset(dot.x + dx * t + bendX * bend, dot.y + dy * t + bendY * bend)
        if (i % 2 == 0) drawCircle(color, 3.2f * d, at, style = ringStyle) else drawCircle(color, 1.7f * d, at)
    }
}

/** The ball is cold iron until it moves fast enough to crush; the ring round it fills up to that speed. */
private fun DrawScope.drawWrecker(
    at: Offset,
    r: Float,
    spin: Float,
    heat: Float,
    gauge: Float,
    spikes: Path,
    ringStyle: Stroke,
    reduced: Boolean
) {
    val d = density
    drawCircle(Color.Black.copy(alpha = 0.38f), r * 1.12f, at + Offset(3f * d, 6f * d))
    if (heat > 0.02f) drawCircle(MoltenMid.copy(alpha = 0.32f * heat), r * 2f, at)

    val edge = lerp(IronDark, MoltenEdge, heat)
    spikes.reset()
    for (i in 0 until 8) {
        val angle = spin + i * TAU / 8f
        spikes.moveTo(at.x + cos(angle - 0.2f) * r * 0.9f, at.y + sin(angle - 0.2f) * r * 0.9f)
        spikes.lineTo(at.x + cos(angle) * r * 1.38f, at.y + sin(angle) * r * 1.38f)
        spikes.lineTo(at.x + cos(angle + 0.2f) * r * 0.9f, at.y + sin(angle + 0.2f) * r * 0.9f)
        spikes.close()
    }
    drawPath(spikes, edge)

    val body = lerp(IronMid, MoltenMid, heat)
    if (reduced) {
        drawCircle(body, r, at)
    } else {
        drawCircle(
            Brush.radialGradient(
                listOf(lerp(IronLight, MoltenLight, heat), body, edge),
                Offset(at.x - r * 0.35f, at.y - r * 0.4f),
                r * 1.6f
            ),
            r,
            at
        )
    }
    for (i in 0 until 3) {
        val angle = spin + i * TAU / 3f
        drawCircle(edge, r * 0.11f, Offset(at.x + cos(angle) * r * 0.58f, at.y + sin(angle) * r * 0.58f))
    }
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.2f, Offset(at.x - r * 0.38f, at.y - r * 0.42f))

    val gaugeRadius = r * 1.38f + 5f * d
    val corner = Offset(at.x - gaugeRadius, at.y - gaugeRadius)
    val box = Size(gaugeRadius * 2f, gaugeRadius * 2f)
    drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, corner, box, style = ringStyle)
    drawArc(
        if (gauge >= 1f) AccentAmber else ChipFocused.copy(alpha = 0.75f),
        -90f,
        360f * gauge,
        false,
        corner,
        box,
        style = ringStyle
    )
}

private fun DrawScope.drawSpark(at: Offset, clock: Float, reduced: Boolean) {
    val d = density
    val pulse = 1f + 0.12f * sin(clock * 7f)
    val halo = 26f * d * pulse
    if (reduced) {
        drawCircle(Accent.copy(alpha = 0.3f), halo * 0.6f, at)
    } else {
        drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha = 0.6f), Color.Transparent), at, halo), halo, at)
    }
    val ray = Offset(cos(clock * 2f), sin(clock * 2f)) * (13f * d * pulse)
    val cross = Offset(-ray.y, ray.x)
    drawLine(ChipFocused.copy(alpha = 0.85f), at - ray, at + ray, 1.6f * d, StrokeCap.Round)
    drawLine(ChipFocused.copy(alpha = 0.85f), at - cross, at + cross, 1.6f * d, StrokeCap.Round)
    drawCircle(Accent, WreckWorld.DOT_RADIUS * d * 0.85f, at)
    drawCircle(ChipFocused, WreckWorld.DOT_RADIUS * d * 0.55f, at)
}
