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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.ceil
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
    fun step(dt: Float) {
        repeat(ceil(dt / STEP).toInt().coerceAtLeast(1)) { if (!over) fixedStep(STEP) }
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
        const val JELLY_X = 140f
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

private val CoralColor = Color(0xFF2FB8A6)
private val CoralTipColor = Color(0xFF7FE3D1)

/** One button: OK or a tap starts the swim and every press pulses up. A hit ends the run. */
@Composable
internal fun JellyDriftScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("drift_game")
    var world by remember { mutableStateOf<DriftWorld?>(null) }
    var worldWidth by remember { mutableStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var score by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }

    GameLoop(running = !over) { dt ->
        frame++  // also keeps the waiting jellyfish bobbing
        val w = world
        if (w != null && started) {
            w.step(dt)
            score = w.score
            if (w.over) {
                over = true
                overAt = System.currentTimeMillis()
                best.submit(w.score)
            }
        }
    }

    val act by rememberUpdatedState {
        when {
            // A held or double press right after losing must not throw the result screen away.
            over -> if (System.currentTimeMillis() - overAt > 500) {
                world = DriftWorld(worldWidth, Random.Default)
                over = false
                started = false
                score = 0
            }
            else -> {
                started = true
                world?.pulse()
            }
        }
    }

    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
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
                    if (world == null) world = DriftWorld(worldWidth, Random.Default)
                }
            }
            .fingerPosition(onDown = { act() }, onMove = {})
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val tick = frame * 0.09f
            val w = world ?: return@Canvas
            val s = size.height / DriftWorld.HEIGHT
            if (focused) drawRect(Color.White.copy(alpha = 0.04f))

            val halfGate = DriftWorld.GATE_WIDTH / 2f * s
            for (gate in 0 until DriftWorld.GATES) {
                val x = w.gateX[gate] * s
                if (x < -halfGate * 2f || x > size.width + halfGate * 2f) continue
                val gapTop = (w.gateCenter[gate] - w.gateGap[gate] / 2f) * s
                val gapBottom = (w.gateCenter[gate] + w.gateGap[gate] / 2f) * s
                val round = CornerRadius(halfGate * 0.6f)
                drawRoundRect(CoralColor, Offset(x - halfGate, -halfGate), Size(halfGate * 2f, gapTop + halfGate), round)
                drawRoundRect(CoralColor, Offset(x - halfGate, gapBottom), Size(halfGate * 2f, size.height - gapBottom + halfGate), round)
                drawCircle(CoralTipColor, halfGate * 0.32f, Offset(x - halfGate * 0.4f, gapTop - halfGate * 0.5f))
                drawCircle(CoralTipColor, halfGate * 0.24f, Offset(x + halfGate * 0.5f, gapBottom + halfGate * 0.6f))
            }

            val centerX = DriftWorld.JELLY_X * s
            val centerY = (if (started) w.y else w.y + sin(tick) * 6f) * s
            val radius = DriftWorld.RADIUS * s
            // The bell flattens on the way up and rounds out while it sinks.
            val squash = (w.speedY / DriftWorld.MAX_FALL).coerceIn(-1f, 1f) * 0.25f
            val bellW = radius * (1f - squash)
            val bellH = radius * (1f + squash)
            for (strand in -1..1) {
                val rootX = centerX + strand * bellW * 0.6f
                val sway = sin(tick + strand) * radius * 0.35f
                drawLine(
                    AccentOrchid.copy(alpha = 0.8f),
                    Offset(rootX, centerY),
                    Offset(rootX + sway, centerY + radius * 1.5f),
                    1.6f * s
                )
            }
            drawArc(
                ChipFocused, 180f, 180f, true,
                Offset(centerX - bellW, centerY - bellH),
                Size(bellW * 2f, bellH * 2f)
            )
            drawCircle(AccentOrchid, radius * 0.18f, Offset(centerX + bellW * 0.35f, centerY - bellH * 0.4f))
        }

        GameScore(score.toString(), best.value)
        Text(
            when {
                over -> stringResource(R.string.game_drift_over, score)
                else -> stringResource(R.string.game_drift_hint)
            },
            style = IpxTypography.BodyMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, bottom = 8.dp)
        )
    }
}
