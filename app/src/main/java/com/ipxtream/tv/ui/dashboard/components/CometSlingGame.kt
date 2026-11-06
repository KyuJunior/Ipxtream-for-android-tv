package com.ipxtream.tv.ui.dashboard.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val SlingSkyTop = Color(0xFF090723)
private val SlingSkyMid = Color(0xFF22104E)
private val SlingSkyLow = Color(0xFF5B1F7C)
private val SlingSkyGlow = Color(0xFFD4597F)
private val SlingNebula = Color(0xFF7C4DFF)
private val SlingPlanetLight = Color(0xFFFFD2AE)
private val SlingPlanetMid = Color(0xFFD9638C)
private val SlingPlanetDark = Color(0xFF341657)
private val SlingRing = Color(0xFFFFDCC0)
private val SlingMoon = Color(0xFF8FE3DA)
private val SlingDuneFar = Color(0xFF43217A)
private val SlingDuneMid = Color(0xFF2B1560)
private val SlingRidge = Color(0xFF1F1049)
private val SlingGroundTop = Color(0xFF2A1762)
private val SlingGroundLow = Color(0xFF0E0728)
private val SlingTeal = Color(0xFF4FE8D4)
private val SlingCrystal = Color(0xFF3FD0C4)
private val SlingCrystalLight = Color(0xFFB4FBEF)
private val SlingCrystalDeep = Color(0xFF176F7E)
private val SlingWood = Color(0xFFC98A52)
private val SlingWoodDark = Color(0xFF55301C)
private val SlingStone = Color(0xFF9A96AC)
private val SlingStoneCracked = Color(0xFF6F6A7E)
private val SlingStoneDark = Color(0xFF37334A)
private val SlingGloomDeep = Color(0xFF3A1C78)
private val SlingCometRock = Color(0xFF7A2A12)
private val SlingFlame = listOf(ChipFocused, AccentAmber, Accent, AccentRed, Color(0xFFB02A8A))
private val SlingBackTip = Offset(SlingWorld.SLING_X + 13f, SlingWorld.SLING_Y - 10f)
private val SlingFrontTip = Offset(SlingWorld.SLING_X - 13f, SlingWorld.SLING_Y - 10f)
private const val SlingTau = 6.2832f
private const val SlingHardHit = 220f

/** Rises and fades over a block or Gloomlet that just went; a Gloomlet's also throws a shock ring. */
private class SlingPop(val text: String, val x: Float, var y: Float, val ring: Boolean) {
    var life = 1f
}

/** The moon world, built once per window size. It fills the window; the 800x360 world sits on its ground. */
private class SlingScene(area: IntSize, val scale: Float, private val reduced: Boolean) {
    val width = area.width.toFloat()
    private val height = area.height.toFloat()
    val left = (width - SlingWorld.WIDTH * scale) / 2f
    val top = height - SlingWorld.HEIGHT * scale
    val worldTop = -top / scale
    private val groundY = top + SlingWorld.GROUND * scale
    private val worldLeft = -left / scale - 40f  // the bleed keeps the edges covered during a camera kick
    private val worldSpan = width / scale + 80f

    private val sky = Brush.verticalGradient(
        0f to SlingSkyTop, 0.5f to SlingSkyMid, 0.8f to SlingSkyLow, 1f to SlingSkyGlow,
        endY = groundY
    )
    private val nebulaAt = Offset(width * 0.3f, height * 0.3f)
    private val nebulaRadius = height * 0.7f
    private val nebula = Brush.radialGradient(
        listOf(SlingNebula.copy(alpha = 0.2f), Color.Transparent),
        center = nebulaAt,
        radius = nebulaRadius
    )
    private val stars = Random(11).let { random ->
        List(if (reduced) 30 else 80) { Offset(random.nextFloat() * width, random.nextFloat() * groundY * 0.85f) }
    }
    private val planetAt = Offset(width * 0.73f, height * 0.2f)
    private val planetRadius = height * 0.1f
    private val planet = Brush.radialGradient(
        listOf(SlingPlanetLight, SlingPlanetMid, SlingPlanetDark),
        center = planetAt - Offset(planetRadius, planetRadius) * 0.4f,
        radius = planetRadius * 1.9f
    )
    private val ringSize = Size(planetRadius * 3.5f, planetRadius * 0.95f)
    private val ringStroke = Stroke(planetRadius * 0.09f)
    private val moonAt = Offset(width * 0.6f, height * 0.36f)
    private val moonRadius = height * 0.022f
    private val duneFar = spires(ridge(0f, width, groundY - 54f * scale, 15f * scale, height + 60f, 2.5f, 0.6f))
    private val duneMid = ridge(0f, width, groundY - 22f * scale, 11f * scale, height + 60f, 3.5f, 2.1f)
    private val rimStroke = Stroke(1.4f * scale)

    private val nearRidge = ridge(worldLeft, worldSpan, SlingWorld.GROUND - 5f, 4f, SlingWorld.GROUND + 2f, 5f, 4.2f)
    private val groundFill = Brush.verticalGradient(
        listOf(SlingGroundTop, SlingGroundLow),
        startY = SlingWorld.GROUND,
        endY = SlingWorld.HEIGHT
    )
    private val groundGlow = Brush.verticalGradient(
        listOf(Color.Transparent, SlingTeal.copy(alpha = 0.3f)),
        startY = SlingWorld.GROUND - 26f,
        endY = SlingWorld.GROUND
    )
    private val craters = Path()
    private val cracks = Path()
    private val crystals = Path()
    private val crystalFacets = Path()
    private val crystalAt: FloatArray
    private val crackStroke = Stroke(0.9f)
    private val tealGlow = Brush.radialGradient(
        listOf(SlingTeal.copy(alpha = 0.34f), Color.Transparent),
        center = Offset.Zero,
        radius = 26f
    )
    val emberGlow = Brush.radialGradient(
        listOf(AccentAmber.copy(alpha = 0.55f), Accent.copy(alpha = 0.2f), Color.Transparent),
        center = Offset.Zero,
        radius = 30f
    )

    val slingBack = Path().apply {
        val x = SlingWorld.SLING_X
        val y = SlingWorld.SLING_Y
        moveTo(x - 3f, y + 40f)
        lineTo(x + 7f, y + 40f)
        lineTo(x + 18f, y - 7f)
        lineTo(x + 13f, y - 14f)
        lineTo(x + 8f, y - 5f)
        close()
    }
    val slingFront = Path().apply {
        val x = SlingWorld.SLING_X
        val y = SlingWorld.SLING_Y
        val ground = SlingWorld.GROUND
        moveTo(x - 12f, ground)
        lineTo(x + 12f, ground)
        lineTo(x + 7f, y + 38f)
        lineTo(x + 3f, y + 34f)
        lineTo(x - 8f, y - 5f)
        lineTo(x - 13f, y - 14f)
        lineTo(x - 18f, y - 7f)
        lineTo(x - 7f, y + 38f)
        close()
    }
    val slingFacet = Path().apply {
        val x = SlingWorld.SLING_X
        val y = SlingWorld.SLING_Y
        moveTo(x - 4f, SlingWorld.GROUND)
        lineTo(x + 2f, SlingWorld.GROUND)
        lineTo(x + 1f, y + 36f)
        lineTo(x - 12f, y - 10f)
        lineTo(x - 13f, y - 4f)
        close()
    }

    init {
        val random = Random(23)
        repeat(9) {
            val x = worldLeft + random.nextFloat() * worldSpan
            val y = SlingWorld.GROUND + 4f + random.nextFloat() * 22f
            val wide = 12f + random.nextFloat() * 24f
            craters.addOval(Rect(x - wide, y - 2f, x + wide, y + 2.5f))
        }
        repeat(6) {
            var x = worldLeft + random.nextFloat() * worldSpan
            var y = SlingWorld.GROUND + 1f
            cracks.moveTo(x, y)
            repeat(3) {
                x += 7f + random.nextFloat() * 12f
                y += 3f + random.nextFloat() * 4f
                cracks.lineTo(x, y)
            }
        }
        val spots = ArrayList<Float>()
        repeat(14) {
            val x = worldLeft + random.nextFloat() * worldSpan
            if (x !in 0f..170f) spots += x  // the launcher and the waiting comets need a clean backdrop
        }
        for (x in spots) {
            spike(x, 6f, 16f + random.nextFloat() * 10f, -0.3f)
            spike(x - 7f, 4f, 9f + random.nextFloat() * 6f, -1f)
            spike(x + 7f, 4.5f, 11f + random.nextFloat() * 6f, 0.9f)
        }
        crystalAt = FloatArray(spots.size) { spots[it] }
    }

    private fun spike(x: Float, half: Float, tall: Float, lean: Float) {
        val tipX = x + lean * tall * 0.3f
        val tipY = SlingWorld.GROUND - tall
        crystals.moveTo(x - half, SlingWorld.GROUND)
        crystals.lineTo(tipX, tipY)
        crystals.lineTo(x + half, SlingWorld.GROUND)
        crystals.close()
        crystalFacets.moveTo(x - half * 0.5f, SlingWorld.GROUND)
        crystalFacets.lineTo(tipX, tipY)
        crystalFacets.lineTo(x + half * 0.2f, SlingWorld.GROUND)
        crystalFacets.close()
    }

    private fun ridge(startX: Float, span: Float, base: Float, tall: Float, bottom: Float, waves: Float, phase: Float) =
        Path().apply {
            moveTo(startX, bottom)
            for (i in 0..48) {
                val t = i / 48f
                val wave = 0.6f * sin(t * waves * SlingTau + phase) + 0.4f * sin(t * waves * 2.3f * SlingTau + phase * 1.7f)
                lineTo(startX + t * span, base - tall * wave)
            }
            lineTo(startX + span, bottom)
            close()
        }

    private fun spires(path: Path) = path.apply {
        val random = Random(5)
        repeat(12) {
            val x = random.nextFloat() * width
            val half = (7f + random.nextFloat() * 9f) * scale
            val tall = (24f + random.nextFloat() * 42f) * scale
            val base = groundY - 40f * scale
            moveTo(x - half, base)
            lineTo(x + half * 0.3f, base - tall)
            lineTo(x + half, base)
            close()
        }
    }

    /** Far from the action, so it stays still when the camera kicks. */
    fun drawBackdrop(scope: DrawScope, clock: Float) {
        with(scope) {
            drawRect(sky)
            if (!reduced) drawCircle(nebula, nebulaRadius, nebulaAt)
            drawPoints(stars, PointMode.Points, Color.White.copy(alpha = 0.6f), 1.5f * scale, StrokeCap.Round)
            if (!reduced) {
                for (i in 0 until 10) {
                    drawCircle(
                        Color.White.copy(alpha = 0.25f + 0.65f * abs(sin(clock * 1.5f + i * 2.1f))),
                        1.5f * scale,
                        stars[i]
                    )
                }
            }
            val ringTop = planetAt - Offset(ringSize.width, ringSize.height) * 0.5f
            rotate(-16f, planetAt) {
                drawArc(SlingRing.copy(alpha = 0.55f), 180f, 180f, false, ringTop, ringSize, style = ringStroke)
                drawCircle(planet, planetRadius, planetAt)
                drawArc(SlingRing.copy(alpha = 0.8f), 0f, 180f, false, ringTop, ringSize, style = ringStroke)
            }
            if (!reduced) {
                drawCircle(SlingMoon, moonRadius, moonAt)
                drawCircle(SlingSkyMid, moonRadius * 0.86f, moonAt + Offset(moonRadius * 0.42f, -moonRadius * 0.2f))
            }
            drawPath(duneFar, SlingDuneFar)
            if (!reduced) drawPath(duneFar, SlingSkyGlow.copy(alpha = 0.3f), style = rimStroke)
            drawPath(duneMid, SlingDuneMid)
        }
    }

    /** In world units, under the towers. */
    fun drawGround(scope: DrawScope, clock: Float) {
        with(scope) {
            drawPath(nearRidge, SlingRidge)
            drawRect(groundGlow, Offset(worldLeft, SlingWorld.GROUND - 26f), Size(worldSpan, 26f))
            val depth = SlingWorld.HEIGHT + 40f - SlingWorld.GROUND
            drawRect(groundFill, Offset(worldLeft, SlingWorld.GROUND), Size(worldSpan, depth))
            drawPath(craters, Color.Black.copy(alpha = 0.3f))
            drawPath(cracks, SlingTeal.copy(alpha = 0.35f), style = crackStroke)
            drawRect(SlingTeal.copy(alpha = 0.85f), Offset(worldLeft, SlingWorld.GROUND), Size(worldSpan, 1.4f))
            if (!reduced) {
                for (i in crystalAt.indices) {
                    translate(crystalAt[i], SlingWorld.GROUND - 8f) {
                        drawCircle(tealGlow, 26f, Offset.Zero, alpha = 0.65f + 0.35f * sin(clock * 1.4f + i))
                    }
                }
            }
            drawPath(crystals, SlingCrystalDeep)
            drawPath(crystalFacets, SlingCrystal)
            if (!reduced) {
                for (i in 0 until 12) {
                    val rise = (clock * (7f + i % 4 * 3f) + i * 53f) % 170f
                    val x = worldLeft + (i * 137f) % worldSpan + sin(clock * 0.5f + i) * 10f
                    drawCircle(SlingCrystalLight.copy(alpha = 0.55f * (1f - rise / 170f)), 1.1f, Offset(x, SlingWorld.GROUND - rise))
                }
            }
        }
    }
}

/** Drag anywhere to pull the comet back, let go to fire, and clear every Gloomlet with the shots you have. */
@Composable
internal fun CometSlingScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.SLING.prefs)
    val context = androidx.compose.ui.platform.LocalContext.current
    val slingPrefs = remember { context.getSharedPreferences(GameId.SLING.prefs, android.content.Context.MODE_PRIVATE) }
    // The furthest level reached: the game reopens there, and any level up to it can be picked.
    var reached by remember { mutableIntStateOf(slingPrefs.getInt("reached", 1).coerceAtLeast(1)) }
    val reduced = LocalReducedEffects.current
    val paused by rememberUpdatedState(LocalGamePaused.current)
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val pops = remember { ArrayList<SlingPop>() }
    val tick = remember { intArrayOf(0) }
    val popPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = 15f  // world units: the world's transform scales it
            setShadowLayer(2f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }
    val ringStroke = remember { Stroke(1.8f) }
    var level by remember { mutableIntStateOf(reached) }
    var banked by remember { mutableIntStateOf(0) }
    var world by remember { mutableStateOf(SlingWorld(reached)) }
    var phase by remember { mutableStateOf(SlingPhase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(world.shotsLeft) }
    var clock by remember { mutableFloatStateOf(0f) }
    var effectsLeft by remember { mutableFloatStateOf(0f) }
    var newBest by remember { mutableStateOf(false) }
    var pull by remember { mutableStateOf<Offset?>(null) }
    var pullStart by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var finishedAt by remember { mutableStateOf(0L) }
    val pulling by remember { derivedStateOf { pull != null } }
    val gloomsTotal = remember(world) { world.glooms }

    fun worldScale() = min(area.width / SlingWorld.WIDTH, area.height / SlingWorld.HEIGHT).coerceAtLeast(0.01f)

    val scene = remember(area, reduced) { SlingScene(area, worldScale(), reduced) }

    fun play(nextLevel: Int) {
        level = nextLevel
        world = SlingWorld(nextLevel)
        phase = SlingPhase.READY
        score = 0
        shots = world.shotsLeft
        effectsLeft = 0f
        newBest = false
    }

    // The finger may stay down while How to play or the pills take over: that pull must not fire on release.
    LaunchedEffect(paused) { if (paused) pull = null }

    val finished = phase == SlingPhase.WON || phase == SlingPhase.LOST
    GameLoop(running = !finished || effectsLeft > 0f) { dt ->
        clock += dt
        particles.step(dt, 140f)
        shake.step(dt)
        for (pop in pops) {
            pop.y -= 30f * dt
            pop.life -= dt / 0.9f
        }
        pops.removeAll { it.life <= 0f }

        val w = world
        if (w.phase == SlingPhase.WON || w.phase == SlingPhase.LOST) {
            effectsLeft -= dt
            return@GameLoop
        }
        w.step(dt)
        if (!reduced && tick[0]++ % 3 == 0) {
            for (piece in w.shots) particles.burst(piece.x, piece.y, slingLook(piece.shot).flame[1], 1, 30f, 0.35f)
        }
        for (body in w.popped) {
            when {
                body.x !in 0f..SlingWorld.WIDTH -> Unit
                body.shot != null -> particles.burst(body.x, body.y, slingLook(body.shot).body, 12, 90f)
                body.material == SlingMaterial.GLOOM -> {
                    particles.burst(body.x, body.y, AccentOrchid, 18, 170f)
                    particles.burst(body.x, body.y, ChipFocused, 6, 110f)
                    shake.kick(5f)
                    pops += SlingPop("+${body.material.points}", body.x, body.y - 14f, true)
                }
                else -> {
                    particles.burst(body.x, body.y, slingMaterialColor(body.material), 12, 130f)
                    if (body.material == SlingMaterial.CRYSTAL) particles.burst(body.x, body.y, SlingCrystalLight, 8, 160f, 0.4f)
                    pops += SlingPop("+${body.material.points}", body.x, body.y, false)
                }
            }
        }
        w.popped.clear()
        for (blast in w.blasts) {
            particles.burst(blast.x, blast.y, AccentAmber, if (reduced) 14 else 28, 280f, 0.7f)
            particles.burst(blast.x, blast.y, Accent, if (reduced) 8 else 16, 200f, 0.6f)
            shake.kick(8f)
            pops += SlingPop("", blast.x, blast.y - 14f, true)
        }
        w.blasts.clear()
        for (trick in w.tricks) particles.burst(trick.x, trick.y, ChipFocused, 10, 120f, 0.4f)
        w.tricks.clear()
        if (w.impact > SlingHardHit) {
            particles.burst(w.impactX, w.impactY, AccentAmber, 7, 130f, 0.4f)
            shake.kick(min(w.impact / 90f, 7f))
        }
        w.impact = 0f

        score = w.score
        shots = w.shotsLeft
        if (w.phase != phase) {
            phase = w.phase
            if (phase == SlingPhase.WON || phase == SlingPhase.LOST) {
                finishedAt = System.currentTimeMillis()
                effectsLeft = 1.3f
                if (phase == SlingPhase.WON) {
                    banked += w.score
                    particles.burst(400f, 170f, AccentAmber, 30, 260f, 1.1f)
                    particles.burst(400f, 170f, AccentOrchid, 30, 240f, 1.1f)
                    particles.burst(400f, 170f, SlingTeal, 30, 220f, 1.1f)
                }
                val total = if (phase == SlingPhase.WON) banked else banked + w.score
                newBest = total > best.value
                if (phase == SlingPhase.WON) {
                    best.submit(total)
                    if (level + 1 > reached) {
                        reached = level + 1
                        slingPrefs.edit().putInt("reached", reached).apply()
                    }
                } else {
                    best.played(total)  // a lost level ends the run
                }
            }
        }
    }

    Box(
        modifier
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .onSizeChanged { area = it }
            .fingerPosition(
                onDown = { position ->
                    when {
                        paused -> Unit  // How to play sits over the game and lets touches through
                        phase == SlingPhase.READY -> {
                            pullStart = position
                            pull = Offset.Zero
                        }
                        phase == SlingPhase.FLYING -> world.activate()
                        System.currentTimeMillis() - finishedAt <= 500 -> Unit  // a stray tap must not skip the result
                        phase == SlingPhase.WON -> play(level + 1)
                        else -> { banked = 0; play(level) }
                    }
                },
                onMove = { position -> if (pull != null) pull = (position - pullStart) / worldScale() },
                onUp = {
                    pull?.let { if (world.launch(it)) phase = SlingPhase.FLYING }
                    pull = null
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            if (scene.width <= 0f) return@Canvas
            val now = clock  // the read that redraws every frame
            val w = world
            val held = pull?.let { SlingWorld.clampPull(it) }
            scene.drawBackdrop(this, now)
            withTransform({
                translate(scene.left + shake.x * scene.scale, scene.top + shake.y * scene.scale)
                scale(scene.scale, scene.scale, Offset.Zero)
            }) {
                scene.drawGround(this, now)
                drawSlingWorld(scene, w, held, now, reduced)
                particles.draw(this, 1f, 0f, 0f, 2.4f)
                for (pop in pops) {
                    if (pop.ring) {
                        drawCircle(AccentAmber.copy(alpha = pop.life * 0.8f), 12f + (1f - pop.life) * 34f, Offset(pop.x, pop.y + 14f), style = ringStroke)
                    }
                    popPaint.color = if (pop.ring) AccentAmber.toArgb() else android.graphics.Color.WHITE
                    popPaint.alpha = (pop.life.coerceIn(0f, 1f) * 255f).toInt()
                    drawContext.canvas.nativeCanvas.drawText(pop.text, pop.x, pop.y, popPaint)
                }
            }
            drawGloomTally(scene.scale, gloomsTotal, w.glooms, now)
        }

        GameScore(
            score = (banked + if (phase == SlingPhase.WON) 0 else score).toString(),
            best = best.value,
            extra = stringResource(R.string.game_sling_status, level, shots)
        )
        if (finished) {
            GameResultPanel(
                title = stringResource(R.string.game_sling_title),
                score = (banked + if (phase == SlingPhase.WON) 0 else score).toString(),
                best = best.value,
                newBest = newBest,
                hint = stringResource(if (phase == SlingPhase.WON) R.string.game_sling_clear else R.string.game_sling_retry),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        // Not hidden while pulling: a tap on an arrow also starts a pull, and the arrow must still be there on release.
        if (phase == SlingPhase.READY && reached > 1) {
            // Previous / next level; arrows stay left-to-right like a timeline.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    // Below the remaining-blob row, which also sits top-end.
                    Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillChip(label = "‹", selected = false, quiet = level <= 1, onClick = {
                        if (level > 1) { banked = 0; play(level - 1) }
                    })
                    Text(
                        stringResource(R.string.game_sling_level_pick, level, reached),
                        style = IpxTypography.BodyMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    PillChip(label = "›", selected = false, quiet = level >= reached, onClick = {
                        if (level < reached) { banked = 0; play(level + 1) }
                    })
                }
            }
        }
        if (!finished && phase == SlingPhase.READY && !pulling) {
            val loaded = world.loaded
            Text(
                if (loaded == null) stringResource(R.string.game_sling_hint)
                else stringResource(loaded.label) + " · " + stringResource(loaded.trick) + "\n" + stringResource(R.string.game_sling_hint),
                style = IpxTypography.BodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 1.dp)
            )
        }
    }
}

/** Colours of one kind of throwable: [rock] outline, [body], [glow] highlight, [spark] hot spot, and its [flame] trail. */
private class SlingLook(val rock: Color, val body: Color, val glow: Color, val spark: Color, val flame: List<Color>)

private val SlingLooks = mapOf(
    SlingShot.EMBER to SlingLook(SlingCometRock, Accent, AccentAmber, ChipFocused, SlingFlame),
    SlingShot.STREAK to SlingLook(Color(0xFF7A4A00), AccentAmber, Color(0xFFFFE27A), Color.White, listOf(Color.White, Color(0xFFFFE27A), AccentAmber, Accent)),
    SlingShot.TRIO to SlingLook(SlingCrystalDeep, SlingTeal, SlingCrystalLight, Color.White, listOf(Color.White, SlingCrystalLight, SlingTeal, SlingCrystalDeep)),
    SlingShot.METEOR to SlingLook(Color(0xFF2C2638), Color(0xFF6E6680), Color(0xFFB9B2C8), AccentAmber, listOf(AccentAmber, Accent, AccentRed, Color(0xFF5A2030))),
    SlingShot.PULSAR to SlingLook(Color(0xFF151A52), Color(0xFF4F63FF), Color(0xFFB6C2FF), Color.White, listOf(Color.White, Color(0xFFB6C2FF), Color(0xFF4F63FF), Color(0xFF2A1F7A)))
)

private fun slingLook(kind: SlingShot?) = SlingLooks.getValue(kind ?: SlingShot.EMBER)

private fun slingMaterialColor(material: SlingMaterial) = when (material) {
    SlingMaterial.CRYSTAL -> SlingCrystal
    SlingMaterial.STONE -> SlingStone
    SlingMaterial.CELL -> AccentAmber
    else -> SlingWood
}

/** The launcher, towers, Gloomlets and throwables, all in world units. */
private fun DrawScope.drawSlingWorld(scene: SlingScene, w: SlingWorld, held: Offset?, clock: Float, reduced: Boolean) {
    val waiting = held != null || w.phase == SlingPhase.READY
    val loaded = w.loaded
    val cometAt = Offset(SlingWorld.SLING_X, SlingWorld.SLING_Y) + (held ?: Offset.Zero)
    val flying = w.shots.firstOrNull()
    val look = if (flying != null) Offset(flying.x, flying.y) else cometAt
    val tension = (held?.getDistance() ?: 0f) / SlingWorld.MAX_PULL
    val band = lerp(SlingTeal, Accent, tension)

    drawOval(SlingCrystalDeep, Offset(SlingWorld.SLING_X - 24f, SlingWorld.GROUND - 3f), Size(48f, 7f))
    drawPath(scene.slingBack, SlingCrystalDeep)
    if (held != null) {
        drawCircle(
            Color.White.copy(alpha = 0.12f),
            SlingWorld.MAX_PULL,
            Offset(SlingWorld.SLING_X, SlingWorld.SLING_Y),
            style = Stroke(1f)
        )
    }
    if (waiting && loaded != null) drawLine(band, SlingBackTip, cometAt, 3.2f, StrokeCap.Round)

    for (body in w.bodies) {
        if (body.fixed) continue
        val above = SlingWorld.GROUND - (body.y + body.bound)
        val near = 1f - above / 140f
        if (near > 0f) {
            drawOval(
                Color.Black.copy(alpha = 0.32f * near.coerceAtMost(1f)),
                Offset(body.x - body.bound * 0.9f, SlingWorld.GROUND + 1f),
                Size(body.bound * 1.8f, 5f)
            )
        }
    }
    for (body in w.bodies) {
        val kind = body.shot
        when {
            body.fixed -> if (body.w < SlingWorld.WIDTH) drawSlingPlinth(body)
            kind != null -> drawSlingTail(Offset(body.x, body.y), body.vx, body.vy, clock, reduced, kind, body.touched)
            body.material == SlingMaterial.GLOOM -> drawGloomlet(Offset(body.x, body.y), body.w / 2f, look, clock, body.x * 0.37f, body.health)
            else -> rotate(body.angle * 57.29578f, Offset(body.x, body.y)) { drawSlingBlock(body, clock) }
        }
    }

    if (waiting && loaded != null) {
        drawSlingTail(cometAt, 0f, 0f, clock, reduced, loaded, false)
        drawLine(band, SlingFrontTip, cometAt, 3.2f, StrokeCap.Round)
    } else {
        drawLine(SlingTeal, SlingBackTip, SlingFrontTip, 2.2f, StrokeCap.Round)
    }
    drawPath(scene.slingFront, SlingCrystal)
    drawPath(scene.slingFacet, SlingCrystalLight.copy(alpha = 0.7f))

    // The throwables still to come wait in line behind the launcher, next one first.
    val first = w.queue.size - w.shotsLeft + if (w.phase == SlingPhase.READY) 1 else 0
    var x = SlingWorld.SLING_X - 32f
    for (i in first until w.queue.size) {
        val kind = w.queue[i]
        val r = kind.radius * 0.75f
        drawSlingCometCore(Offset(x, SlingWorld.GROUND - r), r, kind)
        x -= r * 2f + 6f
    }

    if (held != null && held.getDistance() >= SlingWorld.MIN_PULL) {
        // Only the start of the arc: the rest is the player's skill.
        for (dot in 1..7) {
            val point = SlingWorld.flightPoint(held, dot * 0.06f)
            drawCircle(Color.White.copy(alpha = 0.75f - dot * 0.07f), 3f - dot * 0.17f, point)
        }
    }
    if (flying != null && flying.y < scene.worldTop - 9f) {
        // Hard shots leave the top of a phone's window: this keeps them trackable.
        val tip = Offset(flying.x, scene.worldTop + 6f)
        drawLine(Accent, tip, tip + Offset(-6f, 7f), 2.4f, StrokeCap.Round)
        drawLine(Accent, tip, tip + Offset(6f, 7f), 2.4f, StrokeCap.Round)
    }
}

/** A fixed moon-rock ledge that lifts a tower. */
private fun DrawScope.drawSlingPlinth(body: SlingBody) {
    val corner = Offset(body.x - body.w / 2f, body.y - body.h / 2f)
    drawRoundRect(SlingDuneMid, corner, Size(body.w, body.h + 4f), CornerRadius(4f))
    drawRect(SlingRidge, corner + Offset(3f, body.h * 0.45f), Size(body.w - 6f, body.h * 0.55f))
    drawRect(SlingTeal.copy(alpha = 0.8f), corner, Size(body.w, 1.4f))
}

/** Drawn level around the body's centre: the caller rotates the canvas. Cracks deepen as health drops. */
private fun DrawScope.drawSlingBlock(body: SlingBody, clock: Float) {
    val corner = Offset(body.x - body.w / 2f, body.y - body.h / 2f)
    val size = Size(body.w, body.h)
    val inner = Size(body.w - 1.8f, body.h - 1.8f)
    val middle = Offset(body.x, body.y)
    val upright = body.h > body.w
    val along = if (upright) Offset(0f, body.h / 2f) else Offset(body.w / 2f, 0f)
    val across = if (upright) Offset(body.w / 2f, 0f) else Offset(0f, body.h / 2f)
    val dark: Color
    when (body.material) {
        SlingMaterial.CRYSTAL -> {
            dark = SlingCrystalDeep
            drawRoundRect(SlingCrystalLight.copy(alpha = 0.85f), corner, size, CornerRadius(1.5f))
            drawRect(SlingCrystal.copy(alpha = 0.55f), corner + Offset(0.9f, 0.9f), inner)
            drawLine(Color.White.copy(alpha = 0.6f), middle - along * 0.7f - across * 0.4f, middle - along * 0.2f + across * 0.4f, 1f)
        }
        SlingMaterial.STONE -> {
            dark = SlingStoneDark
            drawRoundRect(dark, corner, size, CornerRadius(1.8f))
            drawRect(if (body.health < 0.67f) SlingStoneCracked else SlingStone, corner + Offset(0.9f, 0.9f), inner)
            val seam = dark.copy(alpha = 0.55f)
            drawLine(seam, middle + along * 0.34f - across * 0.8f, middle + along * 0.34f + across * 0.8f, 0.8f)
            drawLine(seam, middle - along * 0.34f - across * 0.8f, middle - along * 0.34f + across * 0.8f, 0.8f)
        }
        SlingMaterial.CELL -> {
            dark = SlingStoneDark
            drawRoundRect(dark, corner, size, CornerRadius(2.5f))
            drawRoundRect(
                Accent.copy(alpha = 0.55f + 0.35f * abs(sin(clock * 5f))),
                corner + Offset(2f, 2f),
                Size(body.w - 4f, body.h - 4f),
                CornerRadius(1.5f)
            )
            drawLine(ChipFocused, middle + Offset(1.5f, -5f), middle + Offset(-2f, 0.5f), 1.4f, StrokeCap.Round)
            drawLine(ChipFocused, middle + Offset(-2f, 0.5f), middle + Offset(1.5f, 0.5f), 1.4f, StrokeCap.Round)
            drawLine(ChipFocused, middle + Offset(1.5f, 0.5f), middle + Offset(-1.5f, 5f), 1.4f, StrokeCap.Round)
        }
        else -> {
            dark = SlingWoodDark
            drawRoundRect(dark, corner, size, CornerRadius(1.8f))
            drawRect(SlingWood, corner + Offset(0.9f, 0.9f), inner)
            val grain = dark.copy(alpha = 0.5f)
            drawLine(grain, middle + across * 0.32f - along * 0.84f, middle + across * 0.32f + along * 0.84f, 0.7f)
            drawLine(grain, middle - across * 0.34f - along * 0.6f, middle - across * 0.34f + along * 0.72f, 0.7f)
            drawCircle(dark, 0.9f, middle + along * 0.8f)
            drawCircle(dark, 0.9f, middle - along * 0.8f)
        }
    }
    if (body.material != SlingMaterial.CRYSTAL) {
        drawRect(Color.White.copy(alpha = 0.22f), corner + Offset(0.9f, 0.9f), Size(body.w - 1.8f, 1.4f))
        drawRect(Color.Black.copy(alpha = 0.18f), corner + Offset(0.9f, body.h - 2.3f), Size(body.w - 1.8f, 1.4f))
    }
    val crack = if (body.material == SlingMaterial.CRYSTAL) Color.White.copy(alpha = 0.8f) else dark
    val bend = middle - along * 0.08f - across * 0.25f
    if (body.health < 0.67f) {
        val knee = middle + along * 0.22f + across * 0.15f
        drawLine(crack, middle + along * 0.5f - across * 0.9f, knee, 1f)
        drawLine(crack, knee, bend, 1f)
        drawLine(crack, knee, middle + along * 0.62f + across * 0.85f, 0.8f)
    }
    if (body.health < 0.34f) {
        drawLine(crack, bend, middle - along * 0.42f + across * 0.9f, 1f)
        drawLine(crack, bend, middle - along * 0.7f - across * 0.6f, 0.8f)
    }
}

/** A round one-eyed creature that watches [look]; it blinks on its own beat from [seed], and squints once hurt. */
private fun DrawScope.drawGloomlet(center: Offset, r: Float, look: Offset, clock: Float, seed: Float, health: Float = 1f) {
    val sway = sin(clock * 2.6f + seed) * r * 0.14f
    drawGloomAntenna(center, r, -1f, sway)
    drawGloomAntenna(center, r, 1f, sway)
    drawCircle(SlingGloomDeep, r, center)
    drawCircle(AccentOrchid, r * 0.87f, center + Offset(0f, -r * 0.07f))
    drawCircle(Color.White.copy(alpha = 0.24f), r * 0.26f, center + Offset(-r * 0.42f, -r * 0.46f))

    val eye = center + Offset(0f, -r * 0.14f)
    if ((clock + seed) % 3.7f < 0.13f) {
        drawLine(SlingGloomDeep, eye - Offset(r * 0.38f, 0f), eye + Offset(r * 0.38f, 0f), r * 0.13f, StrokeCap.Round)
    } else {
        val toward = look - eye
        val pupil = eye + toward * (r * 0.17f / toward.getDistance().coerceAtLeast(1f))
        drawCircle(Color.White, r * 0.44f, eye)
        drawCircle(SlingGloomDeep, r * 0.23f, pupil)
        drawCircle(Color.White, r * 0.07f, pupil - Offset(r * 0.07f, r * 0.08f))
        if (health < 0.99f) {
            // A heavy lid over the top of the eye, and a bruise.
            drawArc(SlingGloomDeep, 180f, 180f, true, eye - Offset(r * 0.46f, r * 0.46f), Size(r * 0.92f, r * 0.92f))
            drawCircle(SlingGloomDeep.copy(alpha = 0.5f), r * 0.2f, center + Offset(r * 0.48f, r * 0.3f))
        }
    }
    val mouth = center + Offset(0f, r * 0.56f)
    drawLine(SlingGloomDeep, mouth - Offset(r * 0.2f, 0f), mouth + Offset(r * 0.2f, 0f), r * 0.1f, StrokeCap.Round)
}

private fun DrawScope.drawGloomAntenna(center: Offset, r: Float, side: Float, sway: Float) {
    val tip = center + Offset(side * r * 0.55f + sway, -r * 1.45f)
    drawLine(SlingGloomDeep, center + Offset(side * r * 0.32f, -r * 0.7f), tip, r * 0.13f, StrokeCap.Round)
    drawCircle(AccentGreen, r * 0.17f, tip)
}

/** Left to clear, top right, so a level's progress reads at a glance. Pixels, not world units. */
private fun DrawScope.drawGloomTally(s: Float, total: Int, alive: Int, clock: Float) {
    val cell = 22f * s
    val boxWidth = cell * total + 8f * s
    val boxHeight = 30f * s
    val corner = Offset(size.width - boxWidth - 10f * s, 10f * s)
    drawRoundRect(Color.Black.copy(alpha = 0.38f), corner, Size(boxWidth, boxHeight), CornerRadius(12f * s))
    for (i in 0 until total) {
        val at = corner + Offset(4f * s + cell * (i + 0.5f), boxHeight * 0.6f)
        if (i < alive) {
            drawGloomlet(at, 7.5f * s, at + Offset(0f, 10f * s), clock, i * 1.3f)
        } else {
            drawCircle(Color.White.copy(alpha = 0.14f), 5f * s, at)
        }
    }
}

/** The trail streams away from the throwable's travel and licks upward while it waits. A lit Pulsar pulses. */
private fun DrawScope.drawSlingTail(center: Offset, vx: Float, vy: Float, clock: Float, reduced: Boolean, kind: SlingShot, lit: Boolean) {
    val look = slingLook(kind)
    val radius = kind.radius
    val steps = if (reduced) 3 else 9
    val back = Offset(-vx, -vy) * 0.06f + Offset(sin(clock * 11f) * 2.5f, -radius * 1.4f)
    if (!reduced) drawCircle(look.flame[1].copy(alpha = 0.18f), radius * 2.6f, center)
    for (i in steps downTo 1) {
        val t = i / steps.toFloat()
        val flame = look.flame[(t * (look.flame.size - 1)).toInt()]
        drawCircle(flame.copy(alpha = 0.78f * (1f - t * 0.8f)), radius * (1.05f - 0.8f * t), center + back * t)
    }
    drawSlingCometCore(center, radius, kind)
    if (kind == SlingShot.PULSAR && lit) {
        drawCircle(Color.White.copy(alpha = 0.5f + 0.5f * abs(sin(clock * 18f))), radius * 1.5f, center, style = Stroke(1.6f))
    }
}

private fun DrawScope.drawSlingCometCore(center: Offset, radius: Float, kind: SlingShot) {
    val look = slingLook(kind)
    drawCircle(look.rock, radius, center)
    drawCircle(look.body, radius * 0.84f, center + Offset(-radius * 0.06f, -radius * 0.08f))
    drawCircle(look.glow, radius * 0.56f, center + Offset(-radius * 0.12f, -radius * 0.14f))
    drawCircle(look.spark, radius * 0.3f, center + Offset(-radius * 0.2f, -radius * 0.24f))
    when (kind) {
        SlingShot.METEOR -> {
            drawCircle(look.rock.copy(alpha = 0.6f), radius * 0.2f, center + Offset(radius * 0.35f, radius * 0.3f))
            drawCircle(look.rock.copy(alpha = 0.6f), radius * 0.14f, center + Offset(-radius * 0.4f, radius * 0.4f))
        }
        SlingShot.STREAK -> drawLine(
            Color.White.copy(alpha = 0.8f),
            center + Offset(-radius * 0.5f, radius * 0.3f),
            center + Offset(radius * 0.5f, -radius * 0.3f),
            1.2f,
            StrokeCap.Round
        )
        SlingShot.PULSAR -> drawCircle(look.glow, radius * 1.2f, center, style = Stroke(0.9f))
        else -> Unit
    }
}
