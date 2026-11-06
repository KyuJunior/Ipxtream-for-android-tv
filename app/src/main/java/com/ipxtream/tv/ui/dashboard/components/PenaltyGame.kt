package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.AccentOrchidDeep
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class ShotResult { GOAL, SAVED, WIDE }

/**
 * The rules of the penalty shootout. Positions run 0..1 across the goal mouth: the keeper guesses
 * at random, so the corners are the safest shots, but the outer [POST] of the sweep goes wide.
 */
object PenaltyRules {
    const val SHOTS = 5
    const val POST = 0.05f
    const val KEEPER_REACH = 0.2f

    /** Where the sweeping aim is after [elapsedMs]; each shot of the round sweeps faster. */
    fun aimAt(elapsedMs: Long, shotIndex: Int): Float {
        val period = (1800L - 220L * shotIndex).coerceAtLeast(700L)
        val t = (elapsedMs % period).toFloat() / period
        return if (t < 0.5f) t * 2f else 2f - t * 2f
    }

    fun resultOf(aim: Float, keeper: Float): ShotResult = when {
        aim < POST || aim > 1f - POST -> ShotResult.WIDE
        abs(aim - keeper) <= KEEPER_REACH -> ShotResult.SAVED
        else -> ShotResult.GOAL
    }
}

private class Shot(val aim: Float, val keeper: Float, val result: ShotResult, val target: Offset)

private const val FLIGHT = 0.6f
private const val HOLD = 0.9f
private const val SETTLE = 1.8f
private const val PI_F = 3.1415927f
private const val DIVE_LEAN = 20f
private const val LANDED_LEAN = 78f
private const val NET_COLS = 16
private const val NET_ROWS = 7

private val SkyTop = Color(0xFF050A22)
private val SkyLow = Color(0xFF1B2C63)
private val StandDark = Color(0xFF0C1433)
private val StandLit = Color(0xFF1A2650)
private val PitchFar = Color(0xFF12683F)
private val PitchNear = Color(0xFF0B4A2C)
private val BoardNavy = Color(0xFF142A6E)
private val FloodWarm = Color(0xFFFFF4D6)
private val KeeperKit = Color(0xFF7B46DC)
private val KeeperKitDark = Color(0xFF5A2CB0)
private val KeeperSkin = Color(0xFFE2AE86)
private val KeeperHair = Color(0xFF2A1B16)
private val BootDark = Color(0xFF14131C)
private val BallPatch = Color(0xFF1C2230)

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

/** Where everything stands, in pixels. Proportions come from the height so a phone and a TV match. */
private class PenaltyStage(val w: Float, val h: Float) {
    val u = h / 440f
    val boardsTop = h * 0.45f
    val pitchTop = h * 0.515f
    val goalTop = h * 0.2f
    val groundY = h * 0.6f
    val goalH = groundY - goalTop
    val goalW = min(w * 0.64f, goalH * 3.1f)
    val goalL = (w - goalW) / 2f
    val goalR = goalL + goalW
    val backL = goalL + goalW * 0.035f
    val backR = goalR - goalW * 0.035f
    val backTop = goalTop + goalH * 0.06f
    val backBottom = groundY - goalH * 0.1f
    val reach = PenaltyRules.KEEPER_REACH * goalW / (1f - 2f * PenaltyRules.POST)
    val shoulderY = groundY - goalH * 0.57f
    val spot = Offset(w / 2f, h * 0.84f)
    val flashX = FloatArray(14)
    val flashY = FloatArray(14)

    init {
        val rng = Random(3)
        for (i in flashX.indices) {
            flashX[i] = rng.nextFloat() * w
            flashY[i] = bowlEdge(flashX[i]) + rng.nextFloat() * (boardsTop - bowlEdge(flashX[i]))
        }
    }

    /** The outer POST of the sweep lies outside the posts, so a wide shot looks wide. */
    fun xOf(position: Float) = goalL + (position - PenaltyRules.POST) / (1f - 2f * PenaltyRules.POST) * goalW

    fun bowlEdge(x: Float): Float {
        val across = x / w
        return 4f * across * (1f - across) * h * 0.13f
    }

    /** Each shot of a round flies at its own height. */
    fun aimY(shotIndex: Int) = goalTop + goalH * AIM_HEIGHTS[shotIndex.coerceIn(0, AIM_HEIGHTS.lastIndex)]

    /** -1 to 1: how far the keeper dives from the centre. */
    fun dive(keeper: Float) = (keeper - 0.5f) / (0.5f - PenaltyRules.POST)

    /** Where the ball ends its flight. A save lands on the arm itself, wherever the dive tilted it. */
    fun targetOf(aim: Float, keeper: Float, result: ShotResult, shotIndex: Int): Offset {
        val x = xOf(aim)
        if (result != ShotResult.SAVED) return Offset(x, aimY(shotIndex))
        val tilt = dive(keeper) * DIVE_LEAN * PI_F / 180f
        return Offset(x, shoulderY + (x - xOf(keeper)) / cos(tilt) * sin(tilt))
    }

    fun flightPoint(target: Offset, eased: Float) = Offset(
        mix(spot.x, target.x, eased),
        mix(spot.y, target.y, eased) - sin(PI_F * eased) * h * 0.1f
    )

    /** A net knot pulled toward the ball, as if pushed away from the viewer. */
    fun bulged(x: Float, y: Float, hit: Offset, amount: Float): Offset {
        if (amount == 0f) return Offset(x, y)
        val dx = hit.x - x
        val dy = hit.y - y
        val pull = amount / (1f + (dx * dx + dy * dy) / (goalH * goalH * 0.12f))
        return Offset(x + dx * pull, y + dy * pull)
    }

    private companion object {
        val AIM_HEIGHTS = floatArrayOf(0.3f, 0.52f, 0.38f, 0.6f, 0.46f)
    }
}

/** Penalty shootout on a floodlit pitch: OK (or a touch) shoots, then starts a new round. */
@Composable
fun PenaltyGameScreen(
    gameFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.PENALTY.prefs)
    val reduced = LocalReducedEffects.current
    val paused = LocalGamePaused.current
    val density = LocalDensity.current.density
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val ballPath = remember { Path() }
    val backdrop = remember(reduced) { stadiumBackdrop(reduced) }
    var stage by remember { mutableStateOf<PenaltyStage?>(null) }
    var results by remember { mutableStateOf(emptyList<Boolean>()) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var caption by remember { mutableStateOf<ShotResult?>(null) }
    var finished by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(true) }
    var newBest by remember { mutableStateOf(false) }
    var finishedAt by remember { mutableStateOf(0L) }
    var finishedClock by remember { mutableFloatStateOf(0f) }
    var aim by remember { mutableFloatStateOf(0f) }
    var sweep by remember { mutableFloatStateOf(0f) }
    var shotTime by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }

    fun arrive(current: Shot) {
        results = results + (current.result == ShotResult.GOAL)
        caption = current.result
        val at = current.target
        when (current.result) {
            ShotResult.GOAL -> {
                listOf(Accent, AccentAmber, ChipFocused, AccentOrchid).forEach {
                    particles.burst(at.x, at.y, it, 16, 340f * density, 1.4f)
                }
                shake.kick(4f)
            }
            ShotResult.SAVED -> {
                particles.burst(at.x, at.y, ChipFocused, 14, 170f * density, 0.5f)
                shake.kick(3f)
            }
            ShotResult.WIDE -> particles.burst(at.x, at.y, TextSecondary, 6, 70f * density, 0.4f)
        }
    }

    GameLoop(running = !finished || !settled) { dt ->
        time += dt
        particles.step(dt, 420f * density)
        shake.step(dt)
        val current = shot
        when {
            current != null -> {
                val before = shotTime
                shotTime += dt
                if (before < FLIGHT && shotTime >= FLIGHT) arrive(current)
                if (shotTime >= FLIGHT + HOLD) {
                    shot = null
                    caption = null
                    sweep = 0f
                    if (results.size == PenaltyRules.SHOTS) {
                        val score = results.count { it }
                        newBest = score > best.value
                        best.played(score)
                        finished = true
                        settled = false
                        finishedAt = System.currentTimeMillis()
                        finishedClock = time
                    }
                }
            }
            finished -> if (time - finishedClock > SETTLE) settled = true
            else -> {
                sweep += dt
                aim = PenaltyRules.aimAt((sweep * 1000f).toLong(), results.size)
            }
        }
    }

    val act by rememberUpdatedState<() -> Unit> {
        when {
            paused -> Unit  // How to play sits over the game and lets touches through
            finished -> if (System.currentTimeMillis() - finishedAt > 500) {  // a double press must not skip the result
                results = emptyList()
                finished = false
                settled = true
                newBest = false
                sweep = 0f
                aim = 0f
            }
            shot == null -> stage?.let { s ->
                // Stays between the posts: a dive past the post would save nothing and look wrong.
                val keeper = PenaltyRules.POST + Random.nextFloat() * (1f - 2f * PenaltyRules.POST)
                val result = PenaltyRules.resultOf(aim, keeper)
                shot = Shot(aim, keeper, result, s.targetOf(aim, keeper, result, results.size))
                shotTime = 0f
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .gameFocus(gameFocusRequester, upFocusRequester)
            .onKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0) act()
                    true
                } else false
            }
            .onSizeChanged { stage = PenaltyStage(it.width.toFloat(), it.height.toFloat()) }
            .fingerPosition(onDown = { act() }, onMove = {})
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    time  // follows the clock without recomposing
                    if (!reduced) {
                        // Slightly oversized so a camera kick never shows the window's edge.
                        scaleX = 1.02f
                        scaleY = 1.02f
                        translationX = shake.x * density
                        translationY = shake.y * density
                    }
                }
        ) {
            Spacer(Modifier.fillMaxSize().then(backdrop))
            Canvas(Modifier.fillMaxSize()) {
                val s = stage ?: return@Canvas
                val now = time
                val current = shot
                val t = if (current != null) shotTime else 0f
                val after = t - FLIGHT
                val linear = (t / FLIGHT).coerceIn(0f, 1f)
                val eased = 1f - (1f - linear) * (1f - linear)
                val scored = current?.result == ShotResult.GOAL && after > 0f

                if (!reduced) drawCameraFlashes(s, now)

                val bulge = if (scored) 0.55f * exp(-4.5f * after) * cos(after * 15f) else 0f
                drawNet(s, current?.target ?: Offset.Zero, if (abs(bulge) < 0.01f) 0f else bulge)
                drawGoalFrame(s)

                val keeper = current?.keeper ?: 0.5f
                val dive = s.dive(keeper)
                val moved = ((t - 0.06f) / (FLIGHT - 0.1f)).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
                val landed = ((t - FLIGHT) / 0.35f).coerceIn(0f, 1f)
                val keeperX = mix(s.xOf(0.5f), s.xOf(keeper), moved)
                val keeperY = s.shoulderY - sin(PI_F * moved) * s.goalH * 0.12f +
                    (s.groundY - 18f * s.u - s.shoulderY) * abs(dive) * landed +
                    (if (current == null) sin(now * 2.2f) * 1.5f * s.u else 0f)
                val lean = dive * mix(DIVE_LEAN * moved, LANDED_LEAN, landed)
                drawKeeper(s, keeperX, keeperY, lean)

                if (current == null && !finished) {
                    val wide = aim < PenaltyRules.POST || aim > 1f - PenaltyRules.POST
                    drawReticle(s, Offset(s.xOf(aim), s.aimY(results.size)), wide, now)
                }

                val big = 15f * s.u
                val small = 7.5f * s.u
                var ball = s.spot
                var radius = big
                var spin = 0f
                var visible = true
                if (current != null) {
                    val target = current.target
                    val side = if (target.x >= s.w / 2f) 1f else -1f
                    spin = eased * 9f + max(after, 0f) * 6f
                    when {
                        after <= 0f -> {
                            ball = s.flightPoint(target, eased)
                            radius = mix(big, small, eased)
                            if (!reduced) for (i in 1..4) {
                                val back = (eased - i * 0.07f).coerceAtLeast(0f)
                                drawCircle(
                                    Color.White.copy(alpha = 0.2f - i * 0.04f),
                                    mix(big, small, back) * (1f - i * 0.12f),
                                    s.flightPoint(target, back)
                                )
                            }
                        }
                        current.result == ShotResult.GOAL -> {
                            ball = Offset(target.x, min(target.y + s.h * 1.1f * after * after, s.backBottom))
                            radius = small
                        }
                        current.result == ShotResult.SAVED -> {
                            ball = Offset(
                                target.x + side * s.h * 0.34f * after,
                                min(target.y - s.h * 0.3f * after + s.h * 1.6f * after * after, s.groundY + s.h * 0.06f)
                            )
                            radius = small * (1f + after * 0.6f)
                        }
                        else -> {
                            ball = Offset(target.x + side * s.h * 0.22f * after, target.y - s.h * 0.14f * after)
                            radius = small * (1f - after * 0.5f)
                            visible = after < 0.55f
                        }
                    }
                }
                if (visible) {
                    val rest = s.spot.y + big * 0.7f
                    val floor = if (current == null) rest else mix(rest, s.groundY + 3f * s.u, eased)
                    val shadow = Size(radius * 2.1f, radius * 0.7f)
                    drawOval(
                        Color.Black.copy(alpha = if (current == null) 0.45f else 0.28f),
                        Offset(ball.x - shadow.width / 2f, floor),
                        shadow
                    )
                    drawBall(ballPath, ball, radius, spin)
                }

                particles.draw(this, 1f, 0f, 0f, 3.2f * density)
                if (scored && after < 0.22f) drawRect(ChipFocused.copy(alpha = 0.24f * (1f - after / 0.22f)))
                if (current?.result == ShotResult.SAVED && after in 0f..0.3f) {
                    drawCircle(
                        ChipFocused.copy(alpha = 1f - after / 0.3f),
                        (10f + 170f * after) * s.u,
                        current.target,
                        style = Stroke(2.5f * s.u)
                    )
                }
                if (finished) drawRect(Color.Black.copy(alpha = 0.38f))
            }
        }

        GameScore(
            score = stringResource(R.string.game_nova_progress, results.count { it }, PenaltyRules.SHOTS),
            best = best.value
        )

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(PenaltyRules.SHOTS) { i ->
                val mark = when (results.getOrNull(i)) {
                    true -> AccentGreen
                    false -> AccentRed
                    null -> Color.White.copy(alpha = 0.22f)
                }
                Box(Modifier.size(14.dp).background(mark, CircleShape))
            }
        }

        caption?.let { result ->
            Text(
                text = stringResource(
                    when (result) {
                        ShotResult.GOAL -> R.string.game_goal
                        ShotResult.SAVED -> R.string.game_saved
                        ShotResult.WIDE -> R.string.game_wide
                    }
                ),
                style = IpxTypography.HeadlineMedium.copy(
                    fontSize = 44.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black, Offset(0f, 4f), 14f)
                ),
                color = when (result) {
                    ShotResult.GOAL -> AccentAmber
                    ShotResult.SAVED -> ChipFocused
                    ShotResult.WIDE -> AccentRed
                },
                modifier = Modifier
                    .align(BiasAlignment(0f, -0.2f))
                    .graphicsLayer {
                        val grow = 0.6f + 0.4f * ((shotTime - FLIGHT) / 0.18f).coerceIn(0f, 1f)
                        scaleX = grow
                        scaleY = grow
                    }
            )
        }

        if (shot == null && !finished && results.isEmpty()) {
            Text(
                text = stringResource(R.string.game_hint),
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        if (finished) {
            val score = results.count { it }
            GameResultPanel(
                title = stringResource(R.string.game_title),
                score = stringResource(R.string.game_nova_progress, score, PenaltyRules.SHOTS),
                best = best.value,
                newBest = newBest,
                hint = stringResource(R.string.game_result, score, PenaltyRules.SHOTS),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** The stadium never changes, so its thousands of crowd dots are painted once into a bitmap. */
private fun stadiumBackdrop(reduced: Boolean): Modifier = Modifier.drawWithCache {
    if (size.width < 1f || size.height < 1f) return@drawWithCache onDrawBehind {}
    val image = ImageBitmap(size.width.toInt(), size.height.toInt())
    CanvasDrawScope().draw(this, layoutDirection, ImageCanvas(image), size) {
        drawStadium(PenaltyStage(size.width, size.height), reduced)
    }
    onDrawBehind { drawImage(image) }
}

private fun DrawScope.drawStadium(s: PenaltyStage, reduced: Boolean) {
    val w = s.w
    val h = s.h
    val u = s.u
    val rng = Random(7)

    drawRect(Brush.verticalGradient(listOf(SkyTop, SkyLow), endY = s.boardsTop))
    repeat(50) {
        drawCircle(
            Color.White.copy(alpha = 0.2f + rng.nextFloat() * 0.5f),
            (0.6f + rng.nextFloat()) * u,
            Offset(rng.nextFloat() * w, rng.nextFloat() * h * 0.14f)
        )
    }

    val bowl = Path().apply {
        moveTo(0f, s.boardsTop)
        for (i in 0..32) {
            val x = w * i / 32f
            lineTo(x, s.bowlEdge(x))
        }
        lineTo(w, s.boardsTop)
        close()
    }
    drawPath(bowl, Brush.verticalGradient(listOf(StandDark, StandLit), endY = s.boardsTop))

    val shirts = listOf(ChipFocused, AccentAmber, Accent, AccentOrchid, Color(0xFF58C7E8), Color(0xFF39445F))
    val gap = (if (reduced) 10f else 6.5f) * u
    var rowY = gap
    while (rowY < s.boardsTop) {
        val near = rowY / s.boardsTop
        var x = rng.nextFloat() * gap
        while (x < w) {
            if (rowY > s.bowlEdge(x) + 5f * u) {
                drawCircle(
                    shirts[rng.nextInt(shirts.size)].copy(alpha = 0.3f + 0.5f * near * rng.nextFloat() + 0.2f * near),
                    (1.7f + 1.5f * near) * u,
                    Offset(x + rng.nextFloat() * 2f * u, rowY + rng.nextFloat() * 2f * u)
                )
            }
            x += gap
        }
        rowY += gap
    }
    for (band in 1..3) {
        drawRect(Color.Black.copy(alpha = 0.32f), Offset(0f, s.boardsTop * band / 3.6f), Size(w, 4f * u))
    }

    for (lampX in floatArrayOf(w * 0.04f, w * 0.96f)) {
        val lamp = Offset(lampX, h * 0.17f)  // below the top pills and the shot dots
        val cone = Path().apply {
            moveTo(lamp.x - 22f * u, lamp.y)
            lineTo(lamp.x + 22f * u, lamp.y)
            lineTo(w / 2f + (lamp.x - w / 2f) * 0.1f, s.groundY)
            lineTo(w / 2f + (lamp.x - w / 2f) * 0.9f, s.groundY)
            close()
        }
        drawPath(cone, Brush.verticalGradient(listOf(FloodWarm.copy(alpha = 0.2f), Color.Transparent), startY = lamp.y, endY = s.groundY))
        drawCircle(Brush.radialGradient(listOf(FloodWarm.copy(alpha = 0.8f), Color.Transparent), lamp, 70f * u), 70f * u, lamp)
        drawRoundRect(
            Color(0xFF232B45),
            Offset(lamp.x - 26f * u, lamp.y - 12f * u),
            Size(52f * u, 24f * u),
            cornerRadius = CornerRadius(3f * u)
        )
        for (row in 0..1) for (column in 0..4) {
            drawCircle(FloodWarm, 3.2f * u, Offset(lamp.x + (column - 2) * 10f * u, lamp.y + (row - 0.5f) * 11f * u))
        }
    }

    val boards = listOf(Accent, BoardNavy, AccentOrchidDeep, BoardNavy, AccentAmber, BoardNavy)
    val boardW = w / 12f
    val boardH = s.pitchTop - s.boardsTop
    for (i in 0 until 12) {
        val left = boardW * i
        drawRect(boards[i % boards.size], Offset(left, s.boardsTop), Size(boardW, boardH))
        drawRect(Color.White.copy(alpha = 0.16f), Offset(left + boardW * 0.2f, s.boardsTop + boardH * 0.4f), Size(boardW * 0.6f, boardH * 0.2f))
        drawRect(Color.Black.copy(alpha = 0.3f), Offset(left, s.boardsTop), Size(1.5f * u, boardH))
    }
    drawRect(Color.White.copy(alpha = 0.2f), Offset(0f, s.boardsTop), Size(w, 1.5f * u))

    drawRect(
        Brush.verticalGradient(listOf(PitchFar, PitchNear), startY = s.pitchTop, endY = h),
        Offset(0f, s.pitchTop),
        Size(w, h - s.pitchTop)
    )
    for (i in 0 until 9) {
        val from = s.pitchTop + (h - s.pitchTop) * (i / 9f).pow(1.6f)
        val until = s.pitchTop + (h - s.pitchTop) * ((i + 1) / 9f).pow(1.6f)
        drawRect(
            if (i % 2 == 0) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.07f),
            Offset(0f, from),
            Size(w, until - from)
        )
    }
    val pool = Offset(w / 2f, s.groundY + h * 0.08f)
    drawOval(
        Brush.radialGradient(listOf(FloodWarm.copy(alpha = 0.16f), Color.Transparent), pool, w * 0.42f),
        Offset(pool.x - w * 0.42f, pool.y - h * 0.22f),
        Size(w * 0.84f, h * 0.44f)
    )

    val chalk = Color.White.copy(alpha = 0.72f)
    val line = 2.5f * u
    drawLine(chalk, Offset(0f, s.groundY), Offset(w, s.groundY), line)
    for ((halfWidth, depth) in listOf(s.goalW * 0.68f to s.groundY + h * 0.085f, s.goalW * 1.3f to h * 0.97f)) {
        val far = halfWidth * (1f + (depth - s.groundY) / (h - s.groundY) * 0.9f)
        drawLine(chalk, Offset(w / 2f - halfWidth, s.groundY), Offset(w / 2f - far, depth), line)
        drawLine(chalk, Offset(w / 2f + halfWidth, s.groundY), Offset(w / 2f + far, depth), line)
        drawLine(chalk, Offset(w / 2f - far, depth), Offset(w / 2f + far, depth), line)
    }
    drawOval(chalk, Offset(s.spot.x - 7f * u, s.spot.y + 7f * u), Size(14f * u, 6f * u))

    drawRect(Color.Black.copy(alpha = 0.3f), Offset(s.backL, s.backTop), Size(s.backR - s.backL, s.groundY - s.backTop))
    drawRect(Color.Black.copy(alpha = 0.25f), Offset(s.goalL, s.groundY), Size(s.goalW, 4f * u))

    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            Offset(w / 2f, h * 0.48f),
            max(w, h) * 0.72f
        )
    )
}

private fun DrawScope.drawCameraFlashes(s: PenaltyStage, now: Float) {
    for (i in s.flashX.indices) {
        val phase = (now * 0.31f + i * 0.137f) % 1f
        if (phase < 0.05f) {
            drawCircle(Color.White.copy(alpha = 1f - phase / 0.05f), 3f * s.u, Offset(s.flashX[i], s.flashY[i]))
        }
    }
}

private fun DrawScope.drawNet(s: PenaltyStage, hit: Offset, bulge: Float) {
    val net = Color.White.copy(alpha = 0.32f)
    val thin = s.u
    val pieces = if (bulge != 0f) 10 else 1
    for (i in 0..NET_COLS) {
        val x = mix(s.backL, s.backR, i.toFloat() / NET_COLS)
        var from = s.bulged(x, s.backTop, hit, bulge)
        for (k in 1..pieces) {
            val next = s.bulged(x, mix(s.backTop, s.backBottom, k.toFloat() / pieces), hit, bulge)
            drawLine(net, from, next, thin)
            from = next
        }
    }
    for (i in 0..NET_ROWS) {
        val y = mix(s.backTop, s.backBottom, i.toFloat() / NET_ROWS)
        var from = s.bulged(s.backL, y, hit, bulge)
        for (k in 1..pieces) {
            val next = s.bulged(mix(s.backL, s.backR, k.toFloat() / pieces), y, hit, bulge)
            drawLine(net, from, next, thin)
            from = next
        }
    }
    for (i in 0..NET_COLS step 2) {
        val f = i.toFloat() / NET_COLS
        drawLine(net, Offset(mix(s.goalL, s.goalR, f), s.goalTop), Offset(mix(s.backL, s.backR, f), s.backTop), thin)
    }
    for (i in 0..4) {
        val f = i / 4f
        val front = mix(s.goalTop, s.groundY, f)
        val back = mix(s.backTop, s.backBottom, f)
        drawLine(net, Offset(s.goalL, front), Offset(s.backL, back), thin)
        drawLine(net, Offset(s.goalR, front), Offset(s.backR, back), thin)
    }
}

private fun DrawScope.drawGoalFrame(s: PenaltyStage) {
    val width = 7f * s.u
    val a = Offset(s.goalL, s.groundY)
    val b = Offset(s.goalL, s.goalTop)
    val c = Offset(s.goalR, s.goalTop)
    val d = Offset(s.goalR, s.groundY)
    val drop = Offset(2.5f * s.u, 2.5f * s.u)
    val dark = Color.Black.copy(alpha = 0.35f)
    drawLine(dark, a + drop, b + drop, width, StrokeCap.Round)
    drawLine(dark, b + drop, c + drop, width, StrokeCap.Round)
    drawLine(dark, c + drop, d + drop, width, StrokeCap.Round)
    drawLine(ChipFocused, a, b, width, StrokeCap.Round)
    drawLine(ChipFocused, b, c, width, StrokeCap.Round)
    drawLine(ChipFocused, c, d, width, StrokeCap.Round)
    val shine = Offset(-1.5f * s.u, -1.5f * s.u)
    drawLine(Color.White, a + shine, b + shine, width * 0.28f)
    drawLine(Color.White, b + shine, c + shine, width * 0.28f)
    drawLine(Color.White, c + shine, d + shine, width * 0.28f)
}

/** Gloves sit at exactly the save reach, so what you see is what stops the ball. */
private fun DrawScope.drawKeeper(s: PenaltyStage, x: Float, shoulder: Float, lean: Float) {
    val u = s.u
    val k = s.goalH
    val lift = ((s.shoulderY - shoulder) / k).coerceIn(0f, 0.2f)
    val shadow = 72f * u * (1f - lift * 2f)
    drawOval(Color.Black.copy(alpha = 0.4f), Offset(x - shadow / 2f, s.groundY - 3f * u), Size(shadow, 9f * u))

    rotate(lean, Offset(x, shoulder)) {
        val hip = Offset(x, shoulder + k * 0.27f)
        val spread = k * 0.085f
        for (side in intArrayOf(-1, 1)) {
            val knee = Offset(hip.x + side * spread * 0.75f, hip.y + k * 0.15f)
            val ankle = Offset(hip.x + side * spread, hip.y + k * 0.3f)
            drawLine(KeeperKitDark, hip, knee, 12f * u, StrokeCap.Round)
            drawLine(ChipFocused, knee, ankle, 8.5f * u, StrokeCap.Round)
            drawLine(BootDark, ankle, ankle + Offset(side * 7f * u, 2f * u), 7f * u, StrokeCap.Round)

            val elbow = Offset(x + side * s.reach * 0.5f, shoulder + 5f * u)
            val glove = Offset(x + side * s.reach, shoulder)
            drawLine(KeeperKit, Offset(x + side * 8f * u, shoulder + 2f * u), elbow, 9f * u, StrokeCap.Round)
            drawLine(KeeperKitDark, elbow, glove, 7.5f * u, StrokeCap.Round)
            drawCircle(ChipFocused, 9f * u, glove)
            drawCircle(AccentAmber, 9f * u, glove, style = Stroke(2f * u))
        }
        val body = k * 0.17f
        drawRoundRect(
            KeeperKit,
            Offset(x - body / 2f, shoulder - 5f * u),
            Size(body, k * 0.27f + 9f * u),
            cornerRadius = CornerRadius(9f * u)
        )
        drawRect(KeeperKitDark, Offset(x + body * 0.12f, shoulder), Size(body * 0.28f, k * 0.27f))
        drawRect(AccentAmber, Offset(x - body * 0.42f, shoulder + k * 0.09f), Size(body * 0.84f, 3.5f * u))

        val head = 13f * u
        val face = Offset(x, shoulder - 5f * u - head)
        drawCircle(KeeperSkin, head, face)
        drawArc(KeeperHair, 180f, 180f, true, Offset(face.x - head, face.y - head), Size(head * 2f, head * 1.7f))
        drawCircle(BootDark, 1.6f * u, face + Offset(-4.5f * u, 3f * u))
        drawCircle(BootDark, 1.6f * u, face + Offset(4.5f * u, 3f * u))
    }
}

private fun DrawScope.drawReticle(s: PenaltyStage, at: Offset, wide: Boolean, now: Float) {
    val u = s.u
    val tint = if (wide) AccentRed else AccentAmber
    for (i in 1..6) drawCircle(tint.copy(alpha = 0.16f + i * 0.09f), 2.2f * u, s.flightPoint(at, i / 7f))
    val ring = (15f + sin(now * 9f) * 1.5f) * u
    drawCircle(Color.Black.copy(alpha = 0.45f), ring + 2f * u, at, style = Stroke(2f * u))
    drawCircle(tint, ring, at, style = Stroke(2.5f * u))
    drawCircle(tint, 2.5f * u, at)
    val inner = ring - 6f * u
    val outer = ring + 7f * u
    drawLine(tint, at + Offset(-outer, 0f), at + Offset(-inner, 0f), 2.5f * u)
    drawLine(tint, at + Offset(inner, 0f), at + Offset(outer, 0f), 2.5f * u)
    drawLine(tint, at + Offset(0f, -outer), at + Offset(0f, -inner), 2.5f * u)
    drawLine(tint, at + Offset(0f, inner), at + Offset(0f, outer), 2.5f * u)
}

private fun Path.addPentagon(x: Float, y: Float, radius: Float, turn: Float) {
    for (corner in 0..4) {
        val angle = turn + corner * 1.2566f
        val px = x + cos(angle) * radius
        val py = y + sin(angle) * radius
        if (corner == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

private fun DrawScope.drawBall(path: Path, at: Offset, radius: Float, spin: Float) {
    drawCircle(Color(0xFFF4F6FA), radius, at)
    path.reset()
    path.addPentagon(at.x, at.y, radius * 0.3f, spin)
    for (i in 0..4) {
        val angle = spin + i * 1.2566f
        path.addPentagon(at.x + cos(angle) * radius * 0.68f, at.y + sin(angle) * radius * 0.68f, radius * 0.2f, angle)
    }
    drawPath(path, BallPatch)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
            at + Offset(-radius * 0.3f, -radius * 0.3f),
            radius * 1.5f
        ),
        radius,
        at
    )
    drawCircle(Color.White.copy(alpha = 0.75f), radius * 0.16f, at + Offset(-radius * 0.38f, -radius * 0.42f))
}
