package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Jelly Drift in world units (y down, height is fixed): each pulse lifts the jellyfish through coral gaps. */
class DriftWorld(startX: Float, private val random: Random) {
    var y = HEIGHT / 2f
        private set
    var speedY = 0f
        private set
    var score = 0
        private set
    var over = false
        private set
    var distance = 0f
        private set

    val gateX = FloatArray(GATES) { max(startX, MIN_START) + it * SPACING }
    val gateCenter = FloatArray(GATES)
    val gateGap = FloatArray(GATES)
    private val passed = BooleanArray(GATES)
    private var lastCenter = HEIGHT / 2f

    init {
        for (gate in 0 until GATES) deal(gate)
    }

    /** Each gap stays within reach of the one before it. */
    private fun deal(gate: Int) {
        val reach = (lastCenter - MAX_SHIFT)..(lastCenter + MAX_SHIFT)
        val gap = max(START_GAP - score * 1.5f, MIN_GAP)
        val low = max(reach.start, gap / 2f + MARGIN)
        val high = min(reach.endInclusive, HEIGHT - gap / 2f - MARGIN)
        lastCenter = low + random.nextFloat() * (high - low)
        gateCenter[gate] = lastCenter
        gateGap[gate] = gap
        passed[gate] = false
    }

    fun pulse() {
        if (!over) speedY = PULSE
    }

    /** Advances in fixed steps, so a TV dropping frames plays the same game as a fast phone. */
    private var pending = 0f

    fun step(dt: Float) {
        // Leftover time carries over: rounding up here ran the game 50% fast at 60 Hz.
        pending += dt
        while (pending >= STEP) {
            pending -= STEP
            if (!over) fixedStep(STEP)
        }
    }

    private fun fixedStep(h: Float) {
        speedY = min(speedY + GRAVITY * h, MAX_FALL)
        y += speedY * h
        if (y < RADIUS) {
            y = RADIUS
            speedY = 0f
        }
        if (y > HEIGHT - RADIUS) {
            over = true
            return
        }
        val forward = min(SPEED + score * 2f, MAX_SPEED) * h
        distance += forward
        val farthest = gateX.max()
        var moved = false
        for (gate in 0 until GATES) {
            gateX[gate] -= forward
            if (!passed[gate] && gateX[gate] + GATE_WIDTH / 2f < JELLY_X) {
                passed[gate] = true
                score++
            }
            if (gateX[gate] < -GATE_WIDTH && !moved) {
                moved = true
                gateX[gate] = farthest + SPACING
                deal(gate)
            }
            if (hits(gate)) over = true
        }
    }

    private fun hits(gate: Int): Boolean =
        abs(gateX[gate] - JELLY_X) < GATE_WIDTH / 2f + HIT_RADIUS &&
            abs(y - gateCenter[gate]) > gateGap[gate] / 2f - HIT_RADIUS

    companion object {
        const val HEIGHT = 360f
        const val JELLY_X = 236f  // right of the top pills at phone size, or a high jelly hides behind them
        const val RADIUS = 13f
        const val HIT_RADIUS = 10f  // smaller than the drawing, so a graze forgives
        const val GRAVITY = 800f
        const val PULSE = -280f
        const val MAX_FALL = 420f
        const val SPEED = 150f
        const val MAX_SPEED = 210f
        const val STEP = 1f / 120f
        const val GATES = 6
        const val SPACING = 230f
        const val GATE_WIDTH = 46f
        const val START_GAP = 135f
        const val MIN_GAP = 100f
        const val MAX_SHIFT = 100f
        const val MARGIN = 14f
        const val MIN_START = 400f
    }
}

private const val TILE = 900f  // scenery repeats at this world width, wider than any screen shape
private const val TRAIL = 32

private val FarReef = Color(0xFF10618A)
private val NearReef = Color(0xFF0A4068)
private val CeilingRock = Color(0xFF072B45)
private val SandCrest = Color(0xFFF3DDA8)
private val Pebble = Color(0x55624526)
private val SandPuff = Color(0xFFE2C48A)
private val Kelp = Color(0xE6062F48)
private val CoralShade = Color(0x665A1640)
private val CoralLight = Color(0x33FFFFFF)
private val Polyp = Color(0x40FFD9BC)
private val TipCore = Color(0xFFFFEFB0)
private val TipHalo = Color(0xFFFFC95E)
private val BubbleTint = Color(0xFFCFF3FF)
private val Tentacle = Color(0xFFE9C2FF)
private val JellyEye = Color(0xFF2B1452)
private val HitFlash = Color(0xFFFFE3F4)

/** All scenery is built once in world units and scaled on draw, so a frame allocates no paths or brushes. */
private object DriftArt {
    val water = Brush.verticalGradient(
        listOf(Color(0xFF1FB1BE), Color(0xFF0E7099), Color(0xFF0A3D6C), Color(0xFF06214C)),
        startY = 0f,
        endY = DriftWorld.HEIGHT
    )
    val rayBrush = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color.Transparent), startY = 0f, endY = 320f)
    val sandBrush = Brush.verticalGradient(listOf(Color(0xFFE0C085), Color(0xFF8F6E46)), startY = 342f, endY = 362f)
    val coralBrush = Brush.verticalGradient(
        listOf(Color(0xFFFFB27C), Color(0xFFF2614F), Color(0xFF8E2A5C)),
        startY = 0f,
        endY = 170f
    )
    val bellBrush = Brush.verticalGradient(
        listOf(Color(0xF7FFE0F5), Color(0xE6E98FF3), AccentOrchid.copy(alpha = 0.85f)),
        startY = -1f,
        endY = 0.65f
    )
    val jellyGlow = Brush.radialGradient(
        listOf(Color(0x99E7A0FF), Color(0x33B488FF), Color.Transparent),
        center = Offset.Zero,
        radius = 1f
    )
    val passGlow = Brush.radialGradient(
        listOf(Color(0x80FFE9A8), Color(0x28FFC95E), Color.Transparent),
        center = Offset.Zero,
        radius = 1f
    )

    val rays = Path().apply {
        for ((x, wide) in listOf(130f to 60f, 380f to 95f, 650f to 50f, 900f to 110f, 1130f to 70f)) {
            moveTo(x, 0f)
            lineTo(x + wide, 0f)
            lineTo(x + wide * 1.5f - 190f, 340f)
            lineTo(x - wide * 0.5f - 190f, 340f)
            close()
        }
    }
    val farReef = ridge(330f, 120f, 3, 7, 370f)
    val nearReef = ridge(346f, 84f, 4, 11, 370f)
    val ceiling = ridge(2f, -12f, 5, 9, -20f)
    val sand = ridge(350f, 6f, 2, 5, 380f)

    val pebbles = Path().apply {
        val dice = Random(7)
        repeat(46) {
            val x = dice.nextFloat() * TILE
            val y = 353f + dice.nextFloat() * 6f
            val r = 0.7f + dice.nextFloat() * 1.3f
            addOval(Rect(x - r * 1.6f, y - r, x + r * 1.6f, y + r))
        }
    }
    val kelp = Path().apply {
        val dice = Random(21)
        var x = 40f
        while (x < TILE - 30f) {
            val high = 20f + dice.nextFloat() * 22f
            for (blade in -1..1) {
                val root = x + blade * 7f
                moveTo(root, 366f)
                cubicTo(root - 7f, 362f - high * 0.3f, root + 7f, 362f - high * 0.6f, root + blade * 4f, 362f - high + abs(blade) * 7f)
            }
            x += 110f + dice.nextFloat() * 130f
        }
    }
    val kelpStroke = Stroke(width = 4.5f, cap = StrokeCap.Round)

    /** One coral column with its tip at y = 0, running far past any screen; flipped for the upper half. */
    val trunk = Path().apply {
        moveTo(-17f, 26f)
        cubicTo(-14f, 13f, 14f, 13f, 17f, 26f)
        var y = 26f
        var lobe = 0
        while (y < 390f) {
            val bulge = if (lobe % 2 == 0) 5f else 2f
            cubicTo(17f + bulge, y + 6f, 17f + bulge, y + 16f, 17f, y + 22f)
            y += 22f
            lobe++
        }
        while (y > 26f) {
            val bulge = if (lobe % 2 == 0) 2f else 5f
            cubicTo(-17f - bulge, y - 6f, -17f - bulge, y - 16f, -17f, y - 22f)
            y -= 22f
            lobe++
        }
        close()
    }
    val twigs = Path().apply {
        moveTo(-8f, 40f)
        cubicTo(-10f, 28f, -20f, 24f, -21f, 10f)
        moveTo(9f, 46f)
        cubicTo(12f, 30f, 20f, 26f, 22f, 14f)
        moveTo(0f, 30f)
        cubicTo(-1f, 20f, 1f, 12f, 0f, 4f)
        moveTo(-6f, 90f)
        cubicTo(-14f, 84f, -22f, 82f, -26f, 70f)
        moveTo(6f, 130f)
        cubicTo(14f, 124f, 22f, 122f, 26f, 108f)
    }
    val tips = floatArrayOf(-21f, 10f, 22f, 14f, 0f, 4f, -26f, 70f, 26f, 108f)
    val twigStroke = Stroke(width = 6.5f, cap = StrokeCap.Round)
    val shadeLine = wavyLine(11f)
    val lightLine = wavyLine(-10f)
    val shadeStroke = Stroke(width = 11f)
    val lightStroke = Stroke(width = 3.5f)
    val polyps = Path().apply {
        val dice = Random(3)
        repeat(44) {
            val x = -15f + dice.nextFloat() * 30f
            val y = 30f + dice.nextFloat() * 350f
            val r = 0.9f + dice.nextFloat() * 1.3f
            addOval(Rect(x - r, y - r, x + r, y + r))
        }
    }

    /** The bell in a -1..1 box, scaled and squashed on draw. */
    val bell = Path().apply {
        moveTo(-1f, 0.4f)
        cubicTo(-1.08f, -0.55f, -0.6f, -1f, 0f, -1f)
        cubicTo(0.6f, -1f, 1.08f, -0.55f, 1f, 0.4f)
        var x = 1f
        repeat(4) {
            cubicTo(x - 0.1f, 0.68f, x - 0.4f, 0.68f, x - 0.5f, 0.4f)
            x -= 0.5f
        }
        close()
    }
    val bellShine = Path().apply {
        moveTo(-0.74f, -0.08f)
        cubicTo(-0.72f, -0.6f, -0.36f, -0.82f, -0.06f, -0.85f)
        cubicTo(-0.3f, -0.66f, -0.5f, -0.4f, -0.52f, -0.06f)
        close()
    }

    val bubbleRing = Stroke(width = 0.8f)
    private val dice = Random(11)
    val bubbleX = FloatArray(14) { dice.nextFloat() }
    val bubbleRise = FloatArray(14) { 14f + dice.nextFloat() * 22f }
    val bubbleStart = FloatArray(14) { dice.nextFloat() * 400f }
    val bubbleSize = FloatArray(14) { 1.2f + dice.nextFloat() * 2.4f }
}

/** A rocky skyline that joins up at the tile edge: each term is a whole number of humps. */
private fun ridge(base: Float, amp: Float, k1: Int, k2: Int, bottom: Float) = Path().apply {
    moveTo(0f, bottom)
    var x = 0f
    while (x <= TILE) {
        val p = x / TILE * PI.toFloat()
        val rough = 0.55f * abs(sin(k1 * p)) + 0.35f * abs(sin(k2 * p + 0.7f)) + 0.1f * abs(sin(k1 * 7 * p))
        lineTo(x, base - amp * rough)
        x += 18f
    }
    lineTo(TILE, bottom)
    close()
}

private fun wavyLine(x: Float) = Path().apply {
    moveTo(x, 30f)
    var y = 30f
    while (y < 390f) {
        y += 12f
        lineTo(x + 2f * sin(y * 0.11f), y)
    }
}

/** Short-lived drawing state; changes every frame, so it stays out of Compose state. */
private class DriftFx {
    val trail = FloatArray(TRAIL)
    private var head = 0
    private var due = 0f
    val gateGlow = FloatArray(DriftWorld.GATES)
    var pulse = 0f
    var hit = 0f
    var bob = 0f

    fun step(dt: Float) {
        pulse = max(0f, pulse - dt * 3f)
        hit = max(0f, hit - dt * 3.5f)
        for (gate in gateGlow.indices) gateGlow[gate] = max(0f, gateGlow[gate] - dt * 2f)
    }

    /** One sample per 1/60 s whatever the frame rate, so the tentacles look the same on a slow TV. */
    fun record(y: Float, dt: Float) {
        due += dt
        while (due >= 1f / 60f) {
            due -= 1f / 60f
            head = (head + 1) % TRAIL
            trail[head] = y
        }
    }

    fun trailAt(samplesAgo: Int): Float = trail[(head - samplesAgo + TRAIL) % TRAIL]
}

private fun lastPassedGate(world: DriftWorld): Int {
    var found = -1
    for (gate in 0 until DriftWorld.GATES) {
        val behind = world.gateX[gate] + DriftWorld.GATE_WIDTH / 2f < DriftWorld.JELLY_X
        if (behind && (found < 0 || world.gateX[gate] > world.gateX[found])) found = gate
    }
    return found
}

/** One button: OK or a tap starts the swim and every press pulses up. A hit ends the run. */
@Composable
internal fun JellyDriftScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.DRIFT.prefs)
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val reduced = LocalReducedEffects.current
    val paused = LocalGamePaused.current
    val fx = remember { DriftFx() }
    var world by remember { mutableStateOf<DriftWorld?>(null) }
    var worldWidth by remember { mutableStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var newBest by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }  // the burst plays out after the run ends
    var settleLeft by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var clock by remember { mutableFloatStateOf(0f) }
    var scorePop by remember { mutableFloatStateOf(0f) }

    GameLoop(running = !over || settling) { dt ->
        clock += dt
        particles.step(dt, gravity = -30f)
        shake.step(dt)
        fx.step(dt)
        if (scorePop > 0f) scorePop = max(0f, scorePop - dt * 5f)
        val w = world
        if (w != null && !over) {
            if (started) {
                fx.bob -= fx.bob * min(1f, dt * 8f)
                val before = w.score
                w.step(dt)
                if (w.score != before) {
                    score = w.score
                    scorePop = 1f
                    val gate = lastPassedGate(w)
                    if (gate >= 0) {
                        fx.gateGlow[gate] = 1f
                        particles.burst(w.gateX[gate], w.gateCenter[gate], TipHalo, 8, 60f)
                    }
                }
                if (w.over) {
                    newBest = w.score > best.value
                    best.played(w.score)
                    over = true
                    overAt = System.currentTimeMillis()
                    settling = true
                    settleLeft = 1.4f
                    fx.hit = 1f
                    particles.burst(DriftWorld.JELLY_X, w.y, AccentOrchid, 22, 120f, 0.9f)
                    particles.burst(DriftWorld.JELLY_X, w.y, ChipFocused, 12, 80f, 0.7f)
                    if (w.y > DriftWorld.HEIGHT - DriftWorld.RADIUS) {
                        particles.burst(DriftWorld.JELLY_X, 350f, SandPuff, 14, 70f)
                    }
                    shake.kick(5f)
                }
                fx.record(w.y + fx.bob, dt)
            } else {
                fx.bob = sin(clock * 2f) * 6f
                fx.trail.fill(w.y + fx.bob)
            }
        }
        if (settling) {
            settleLeft -= dt
            if (settleLeft <= 0f) settling = false
        }
    }

    val act by rememberUpdatedState {
        when {
            // The How to play sheet doesn't take touches, so a tap on it lands here.
            paused -> Unit
            // A held or double press right after losing must not throw the result screen away.
            over -> if (System.currentTimeMillis() - overAt > 500) {
                world = DriftWorld(worldWidth, Random.Default)
                fx.gateGlow.fill(0f)
                settling = false
                over = false
                started = false
                score = 0
            }
            else -> {
                started = true
                fx.pulse = 1f
                world?.let { particles.burst(DriftWorld.JELLY_X, it.y + 8f, BubbleTint, 4, 34f, 0.8f) }
                world?.pulse()
            }
        }
    }

    Box(
        modifier
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .onKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0) act()
                    true
                } else false
            }
            .onSizeChanged {
                if (it.height > 0) {
                    worldWidth = it.width * DriftWorld.HEIGHT / it.height
                    // Hiding a phone's system bars resizes us: a waiting world must keep its first gate at the edge.
                    if (world == null || !started) world = DriftWorld(worldWidth, Random.Default)
                }
            }
            .fingerPosition(onDown = { act() }, onMove = {})
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val t = clock
            val w = world ?: return@Canvas
            val s = size.height / DriftWorld.HEIGHT
            val worldW = size.width / s
            scale(s, Offset.Zero) {
                drawRect(DriftArt.water, Offset.Zero, Size(worldW + 1f, DriftWorld.HEIGHT + 1f))
                if (!reduced) {
                    translate(left = sin(t * 0.3f) * 14f) {
                        drawPath(DriftArt.rays, DriftArt.rayBrush, alpha = 0.75f + 0.25f * sin(t * 0.6f))
                    }
                }
                tiled(w.distance * 0.12f, worldW) { drawPath(DriftArt.farReef, FarReef) }
                if (!reduced) tiled(w.distance * 0.3f, worldW) { drawPath(DriftArt.nearReef, NearReef) }
                drawBubbles(t, w.distance, worldW, if (reduced) 5 else DriftArt.bubbleX.size)

                translate(shake.x, shake.y) {
                    for (gate in 0 until DriftWorld.GATES) {
                        val x = w.gateX[gate]
                        if (x < -40f || x > worldW + 40f) continue
                        val center = w.gateCenter[gate]
                        val halfGap = w.gateGap[gate] / 2f
                        val glow = fx.gateGlow[gate]
                        if (glow > 0f && !reduced) {
                            translate(x, center) {
                                scale(halfGap * 1.4f, Offset.Zero) {
                                    drawCircle(DriftArt.passGlow, 1f, Offset.Zero, alpha = glow)
                                }
                            }
                        }
                        val flip = if (gate % 2 == 0) 1f else -1f
                        drawCoral(x, center - halfGap, flip, -1f, glow, t, reduced)
                        drawCoral(x, center + halfGap, -flip, 1f, glow, t, reduced)
                    }

                    tiled(w.distance, worldW) {
                        translate(top = -2.5f) { drawPath(DriftArt.sand, SandCrest) }
                        drawPath(DriftArt.sand, DriftArt.sandBrush)
                        drawPath(DriftArt.pebbles, Pebble)
                        drawPath(DriftArt.ceiling, CeilingRock)
                    }
                    if (!over) drawJelly(w.y + fx.bob, w.speedY, t, fx, reduced)
                    if (!reduced) {
                        tiled(w.distance * 1.25f, worldW) {
                            translate(left = sin(t * 0.9f) * 2f) {
                                drawPath(DriftArt.kelp, Kelp, style = DriftArt.kelpStroke)
                            }
                        }
                    }
                    particles.draw(this, 1f, 0f, 0f, 2.4f)
                }
            }
            if (fx.hit > 0f) drawRect(HitFlash, alpha = fx.hit * 0.45f)
        }

        GameScore(
            score.toString(),
            best.value,
            Modifier.graphicsLayer {
                val grow = 1f + scorePop * 0.18f
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        )
        if (over) {
            GameResultPanel(
                title = stringResource(R.string.game_drift_title),
                score = score.toString(),
                best = best.value,
                newBest = newBest,
                hint = stringResource(R.string.game_drift_over, score),
                modifier = Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 480.dp)
            )
        } else if (!started) {
            Text(
                stringResource(R.string.game_drift_hint),
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

/** Repeats one [TILE] of scenery across the window, moved left by [travel]. */
private inline fun DrawScope.tiled(travel: Float, worldW: Float, block: DrawScope.() -> Unit) {
    // Tiles overlap by one unit: butted edges left a hairline seam where the anti-aliasing met.
    val stride = TILE - 1f
    var left = -(travel % stride)
    while (left < worldW) {
        translate(left, 0f) { block() }
        left += stride
    }
}

private fun DrawScope.drawBubbles(t: Float, distance: Float, worldW: Float, count: Int) {
    val wrap = worldW + 30f
    for (i in 0 until count) {
        val y = DriftWorld.HEIGHT - (t * DriftArt.bubbleRise[i] + DriftArt.bubbleStart[i]) % 400f
        val x = (DriftArt.bubbleX[i] * wrap - distance * 0.6f).mod(wrap) - 15f + sin(t * 1.3f + i) * 4f
        val r = DriftArt.bubbleSize[i]
        drawCircle(BubbleTint.copy(alpha = 0.45f), r, Offset(x, y), style = DriftArt.bubbleRing)
        drawCircle(BubbleTint.copy(alpha = 0.7f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
}

private fun DrawScope.drawCoral(
    x: Float,
    tipY: Float,
    flipX: Float,
    flipY: Float,
    glow: Float,
    t: Float,
    reduced: Boolean
) {
    translate(x, tipY) {
        scale(flipX, flipY, Offset.Zero) {
            drawPath(DriftArt.trunk, DriftArt.coralBrush)
            if (!reduced) {
                drawPath(DriftArt.shadeLine, CoralShade, style = DriftArt.shadeStroke)
                drawPath(DriftArt.lightLine, CoralLight, style = DriftArt.lightStroke)
                drawPath(DriftArt.polyps, Polyp)
            }
            drawPath(DriftArt.twigs, DriftArt.coralBrush, style = DriftArt.twigStroke)
            val tips = DriftArt.tips
            for (i in 0 until tips.size / 2) {
                val tip = Offset(tips[i * 2], tips[i * 2 + 1])
                val halo = 0.3f + 0.15f * sin(t * 3f + i + x * 0.01f) + glow * 0.5f
                drawCircle(TipHalo.copy(alpha = halo), 6f + glow * 5f, tip)
                drawCircle(TipCore, 3f, tip)
            }
        }
    }
}

private fun DrawScope.drawJelly(y: Float, speedY: Float, t: Float, fx: DriftFx, reduced: Boolean) {
    val cx = DriftWorld.JELLY_X
    // The bell flattens on the way up and rounds out while it sinks.
    val lean = (speedY / DriftWorld.MAX_FALL).coerceIn(-1f, 1f)
    val bellW = 16f * (1f - lean * 0.22f)
    val bellH = 15f * (1f + lean * 0.22f)
    val rimY = y + bellH * 0.45f

    if (reduced) {
        drawCircle(Tentacle.copy(alpha = 0.14f + fx.pulse * 0.14f), 26f, Offset(cx, y))
    } else {
        translate(cx, y) {
            scale(36f + fx.pulse * 14f, Offset.Zero) {
                drawCircle(DriftArt.jellyGlow, 1f, Offset.Zero, alpha = 0.6f + fx.pulse * 0.4f)
            }
        }
    }

    // Each tentacle point sits where the bell was a moment ago, so they stream behind every move.
    val strands = if (reduced) 3 else 5
    val segments = if (reduced) 5 else 8
    val spread = (strands - 1) / 2f
    for (strand in 0 until strands) {
        val rootX = cx + (strand - spread) / spread * bellW * 0.72f
        var fromX = rootX
        var fromY = rimY
        for (k in 1..segments) {
            val step = k * 8 / segments
            val lag = ((fx.trailAt(step * 2) - y) * 0.7f).coerceIn(-30f, 30f)
            val toX = rootX - step * 2.6f + sin(t * 4f + step * 0.8f + strand) * step * 0.32f
            val toY = rimY + lag + step * 3.2f
            drawLine(
                color = Tentacle.copy(alpha = 0.85f - step * 0.07f),
                start = Offset(fromX, fromY),
                end = Offset(toX, toY),
                strokeWidth = max(0.8f, 2.6f - step * 0.2f),
                cap = StrokeCap.Round
            )
            fromX = toX
            fromY = toY
        }
    }

    rotate(lean * 10f, Offset(cx, y)) {
        translate(cx, y) {
            scale(bellW, bellH, Offset.Zero) {
                drawPath(DriftArt.bell, DriftArt.bellBrush)
                drawPath(DriftArt.bellShine, Color.White.copy(alpha = 0.42f))
                for (dot in 0..2) {
                    val twinkle = 0.55f + 0.35f * sin(t * 3f + dot * 2f)
                    drawCircle(ChipFocused.copy(alpha = twinkle), 0.09f, Offset(-0.42f + dot * 0.42f, 0.26f - (dot % 2) * 0.1f))
                }
                drawCircle(JellyEye, 0.11f, Offset(0.2f, -0.22f))
                drawCircle(JellyEye, 0.11f, Offset(0.62f, -0.22f))
                drawCircle(Color.White, 0.04f, Offset(0.23f, -0.26f))
                drawCircle(Color.White, 0.04f, Offset(0.65f, -0.26f))
            }
        }
    }
}
