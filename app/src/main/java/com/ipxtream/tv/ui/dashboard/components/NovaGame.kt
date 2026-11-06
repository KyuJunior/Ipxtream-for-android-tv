package com.ipxtream.tv.ui.dashboard.components

import android.graphics.Paint
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.AccentOrchidDeep
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class NovaKind { NORMAL, MAGNET, LASER, HOLE }

/** One level of One-Tap Nova, in dp: one tap makes a blast, and every particle it touches blasts too. */
class NovaWorld(val level: Int, width: Float, height: Float, random: Random) {
    private var width = width
    private var height = height
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
    val blastKind = Array(count + 1) { NovaKind.NORMAL }
    var blasts = 0
        private set
    var poppedCount = 0
        private set
    private var tapped = false
    val reach get() = min(width, height) * 0.26f

    /** How many blasts a hole has swallowed, and the last hole to do it, so the screen can react. */
    var swallows = 0
        private set
    var lastHole = -1
        private set

    /** Follows the game area when it changes size; the next step brings any stray particle back in. */
    fun resize(newWidth: Float, newHeight: Float) {
        width = newWidth
        height = newHeight
    }

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
                swallows++
                lastHole = i
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

private const val STARS_FULL = 70
private const val STARS_REDUCED = 30
private const val LABELS = 12
private const val LABEL_LIFE = 0.7f
private const val BEAM_LIFE = 0.45f
private const val SETTLE_SECONDS = 1.2f

private val NovaKind.tint: Color
    get() = when (this) {
        NovaKind.NORMAL -> Accent
        NovaKind.MAGNET -> AccentOrchid
        NovaKind.LASER -> AccentGreen
        NovaKind.HOLE -> AccentOrchidDeep
    }

private class NovaPalette(val top: Long, val bottom: Long, vararg val clouds: Long)

private val NOVA_PALETTES = listOf(
    NovaPalette(0xFF120A2E, 0xFF2A0B3F, 0xFFFF3D8B, 0xFF6E3BD0, 0xFF2E7BFF),
    NovaPalette(0xFF06152E, 0xFF0A2A3C, 0xFF16C2C2, 0xFF2E6BFF, 0xFFB488FF),
    NovaPalette(0xFF1E0A1F, 0xFF3A0F1E, 0xFFFF6A2B, 0xFFFF3D6E, 0xFF7A2BD0),
    NovaPalette(0xFF161033, 0xFF2B1240, 0xFFFFC23D, 0xFFFF6A9A, 0xFF4B5BFF)
)

/** Baked once per size and palette at half resolution: soft clouds scale well, and each frame is one image. */
private fun nebulaImage(width: Int, height: Int, palette: NovaPalette): ImageBitmap {
    val image = ImageBitmap(width, height)
    val random = Random(palette.top)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawRect(Brush.verticalGradient(listOf(Color(palette.top), Color(palette.bottom))))
        repeat(10) { i ->
            val tint = Color(palette.clouds[i % palette.clouds.size])
            val radius = height * (0.35f + random.nextFloat() * 0.6f)
            val at = Offset(random.nextFloat() * width, random.nextFloat() * height)
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.1f), Color.Transparent), at, radius),
                radius,
                at
            )
        }
        repeat(160) {
            val at = Offset(random.nextFloat() * width, random.nextFloat() * height)
            drawCircle(Color.White.copy(alpha = 0.05f + random.nextFloat() * 0.2f), 0.5f + random.nextFloat() * 0.5f, at)
        }
        drawRect(
            Brush.radialGradient(
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f),
                center = center,
                radius = max(width, height) * 0.72f
            )
        )
    }
    return image
}

private fun orbSprite(pixels: Int, kind: NovaKind): ImageBitmap {
    val image = ImageBitmap(pixels, pixels)
    val radius = pixels / 2f
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(pixels.toFloat(), pixels.toFloat())) {
        if (kind == NovaKind.HOLE) {
            drawCircle(
                Brush.radialGradient(
                    0.42f to Color.Black,
                    0.58f to AccentOrchidDeep,
                    0.68f to AccentOrchid.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius
                ),
                radius
            )
        } else {
            val tint = kind.tint
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0.16f), Color.Transparent), center, radius),
                radius
            )
            val core = if (kind == NovaKind.NORMAL) ChipFocused else tint
            drawCircle(
                Brush.radialGradient(0f to Color.White, 0.5f to core, 1f to tint, center = center, radius = radius * 0.36f),
                radius * 0.36f
            )
            drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.08f, center + Offset(-radius * 0.12f, -radius * 0.13f))
        }
    }
    return image
}

/** Everything that depends only on the density, made once. */
private class NovaKit(density: Float) {
    val ring = Stroke(2.5f * density)
    val thin = Stroke(1.2f * density)
    val hud = Stroke(5f * density, cap = StrokeCap.Round)
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
        isFakeBoldText = true
    }
    val labelColor = ChipFocused.toArgb()
    val orbs = NovaKind.entries.map { orbSprite(((if (it == NovaKind.HOLE) 40f else 36f) * density).toInt(), it) }
}

/** What only the screen needs: the stars, and the events it has already reacted to. */
private class NovaEffects {
    private val random = Random(7)
    val starX = FloatArray(STARS_FULL) { random.nextFloat() }
    val starY = FloatArray(STARS_FULL) { random.nextFloat() }
    val starPhase = FloatArray(STARS_FULL) { random.nextFloat() * 6.283f }
    val starSize = FloatArray(STARS_FULL) { 0.6f + random.nextFloat() * 1.1f }
    val labelX = FloatArray(LABELS)
    val labelY = FloatArray(LABELS)
    val labelAge = FloatArray(LABELS) { LABEL_LIFE }
    val labelText = arrayOfNulls<String>(LABELS)
    var clock = 0f
    var flash = 0f
    var flashTint = Color.White
    var settle = 0f
    private var nextLabel = 0
    private var seenBlasts = 0
    private var seenSwallows = 0
    private var cleared = false

    fun reset() {
        seenBlasts = 0
        seenSwallows = 0
        cleared = false
        settle = 0f
        flash = 0f
        labelAge.fill(LABEL_LIFE)
    }

    fun step(dt: Float) {
        clock += dt
        flash = max(0f, flash - dt * 1.2f)
        for (i in labelAge.indices) labelAge[i] += dt
    }

    fun collect(world: NovaWorld, particles: GameParticles, shake: GameShake) {
        for (b in seenBlasts until world.blasts) {
            val kind = world.blastKind[b]
            particles.burst(world.blastX[b], world.blastY[b], if (b == 0) ChipFocused else kind.tint, 9, 130f)
            if (b == 0) continue
            labelX[nextLabel] = world.blastX[b]
            labelY[nextLabel] = world.blastY[b]
            labelAge[nextLabel] = 0f
            labelText[nextLabel] = b.toString()  // blast b is the b-th pop of the chain
            nextLabel = (nextLabel + 1) % LABELS
            if (b % 5 == 0) shake.kick(2f + min(b, 30) / 6f)
            if (kind == NovaKind.LASER) shake.kick(4f)
        }
        seenBlasts = world.blasts
        if (world.swallows > seenSwallows) {
            seenSwallows = world.swallows
            val hole = world.lastHole
            particles.burst(world.x[hole], world.y[hole], AccentOrchid, 24, 170f)
            shake.kick(5f)
            flash = 0.28f
            flashTint = AccentOrchidDeep
        }
        if (!cleared && world.won) {
            cleared = true
            val last = world.blasts - 1
            particles.burst(world.blastX[last], world.blastY[last], AccentGreen, 30, 220f, 0.9f)
            shake.kick(7f)
            flash = 0.3f
            flashTint = AccentGreen
        }
    }
}

private fun DrawScope.drawStars(fx: NovaEffects, count: Int) {
    for (i in 0 until count) {
        val twinkle = 0.3f + 0.7f * abs(sin(fx.clock * (0.5f + fx.starSize[i]) + fx.starPhase[i]))
        val driftX = (fx.starX[i] * size.width + fx.clock * fx.starSize[i] * 3f * density) % size.width
        drawCircle(Color.White.copy(alpha = twinkle), fx.starSize[i] * density, Offset(driftX, fx.starY[i] * size.height))
    }
}

private fun DrawScope.drawBlasts(w: NovaWorld, kit: NovaKit, reduced: Boolean) {
    val scale = density
    for (b in 0 until w.blasts) {
        val age = w.blastAge[b]
        if (age >= NovaWorld.BLAST_LIFE) continue
        val fade = 1f - age / NovaWorld.BLAST_LIFE
        val at = Offset(w.blastX[b] * scale, w.blastY[b] * scale)
        val radius = w.blastRadius(b) * scale
        val kind = w.blastKind[b]
        val tint = if (b == 0) ChipFocused else kind.tint
        drawCircle(tint.copy(alpha = fade), radius, at, style = kit.ring)
        if (!reduced) {
            drawCircle(tint.copy(alpha = 0.16f * fade), radius, at)
            drawCircle(Color.White.copy(alpha = 0.4f * fade * fade), radius * 0.72f, at, style = kit.thin)
        }
        val core = 1f - age / NovaWorld.GROW_TIME
        if (core > 0f) drawCircle(Color.White.copy(alpha = core), 7f * scale + radius * 0.3f, at)
        if (kind == NovaKind.MAGNET) {
            val pull = age * 1.4f % 1f
            drawCircle(AccentOrchid.copy(alpha = 0.4f * fade * pull), w.reach * 3f * scale * (1f - pull), at, style = kit.ring)
        }
        if (kind == NovaKind.LASER && age < BEAM_LIFE) drawBeams(at, 1f - age / BEAM_LIFE)
    }
}

/** The beams are as wide as the laser's real reach. */
private fun DrawScope.drawBeams(at: Offset, power: Float) {
    val wide = NovaWorld.LASER_WIDTH * 2f * density
    val far = size.width + size.height
    val glow = AccentGreen.copy(alpha = 0.3f * power)
    val bright = Color.White.copy(alpha = 0.9f * power)
    drawLine(glow, Offset(-far, at.y), Offset(far, at.y), wide)
    drawLine(glow, Offset(at.x, -far), Offset(at.x, far), wide)
    drawLine(bright, Offset(-far, at.y), Offset(far, at.y), 2f * density)
    drawLine(bright, Offset(at.x, -far), Offset(at.x, far), 2f * density)
}

private fun DrawScope.drawOrbs(w: NovaWorld, kit: NovaKit, clock: Float, reduced: Boolean) {
    val scale = density
    for (i in 0 until w.count) {
        if (w.popped[i]) continue
        val at = Offset(w.x[i] * scale, w.y[i] * scale)
        val orb = kit.orbs[w.kind[i].ordinal]
        val half = orb.width / 2f
        drawImage(orb, Offset(at.x - half, at.y - half), alpha = 0.84f + 0.16f * sin(clock * 3f + i))
        if (reduced) continue
        when (w.kind[i]) {
            NovaKind.NORMAL -> Unit
            NovaKind.MAGNET -> {
                val pull = (clock * 0.8f + i * 0.37f) % 1f
                drawCircle(AccentOrchid.copy(alpha = 0.7f * pull), (9f + 15f * (1f - pull)) * scale, at, style = kit.thin)
            }
            NovaKind.LASER -> {
                val angle = clock * 1.6f + i
                val arm = Offset(cos(angle), sin(angle)) * (12f * scale)
                val cross = Offset(-arm.y, arm.x)
                drawLine(AccentGreen, at - arm, at + arm, 1.5f * scale)
                drawLine(AccentGreen, at - cross, at + cross, 1.5f * scale)
            }
            NovaKind.HOLE -> {
                val rim = 13f * scale
                repeat(3) { arc ->
                    drawArc(
                        color = AccentOrchid,
                        startAngle = clock * 150f + i * 40f + arc * 120f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(at.x - rim, at.y - rim),
                        size = Size(rim * 2f, rim * 2f),
                        style = kit.ring
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLabels(fx: NovaEffects, kit: NovaKit) {
    val canvas = drawContext.canvas.nativeCanvas
    kit.label.color = kit.labelColor
    for (i in 0 until LABELS) {
        val text = fx.labelText[i] ?: continue
        val age = fx.labelAge[i]
        if (age >= LABEL_LIFE) continue
        kit.label.alpha = ((1f - age / LABEL_LIFE) * 255f).toInt()
        canvas.drawText(text, fx.labelX[i] * density, (fx.labelY[i] - 14f - age * 40f) * density, kit.label)
    }
}

/** A ring filling toward the target, with the lives under it; it sits opposite the pills. */
private fun DrawScope.drawProgress(w: NovaWorld, kit: NovaKit, lives: Int, pillsOnRight: Boolean) {
    val inset = 46f * density
    val radius = 24f * density
    val hub = Offset(if (pillsOnRight) inset else size.width - inset, inset)
    val progress = (w.poppedCount.toFloat() / w.target).coerceIn(0f, 1f)
    drawCircle(Color.Black.copy(alpha = 0.42f), radius + 10f * density, hub)
    drawCircle(Color.White.copy(alpha = 0.16f), radius, hub, style = kit.hud)
    drawArc(
        color = if (w.won) AccentGreen else Accent,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(hub.x - radius, hub.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = kit.hud
    )
    val orb = kit.orbs[NovaKind.NORMAL.ordinal]
    drawImage(orb, Offset(hub.x - orb.width / 2f, hub.y - orb.height / 2f), alpha = 0.35f + 0.65f * progress)
    for (life in 0 until 3) {
        val at = Offset(hub.x + (life - 1) * 12f * density, hub.y + radius + 18f * density)
        drawCircle(if (life < lives) ChipFocused else Color.White.copy(alpha = 0.2f), 4f * density, at)
    }
}

/** Tap once per level and hope the chain reaches the target. Three failed levels end the run. */
@Composable
internal fun NovaGameScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.NOVA.prefs)
    val reduced = LocalReducedEffects.current
    val density = LocalDensity.current.density
    val paused by rememberUpdatedState(LocalGamePaused.current)
    val pillsOnRight = LocalLayoutDirection.current == LayoutDirection.Rtl
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val fx = remember { NovaEffects() }
    val kit = remember(density) { NovaKit(density) }
    var world by remember { mutableStateOf<NovaWorld?>(null) }
    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var finished by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var popped by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var finishedAt by remember { mutableStateOf(0L) }

    fun newWorld() {
        if (area.width == 0) return
        world = NovaWorld(level, area.width / density, area.height / density, Random.Default)
        finished = false
        tapped = false
        settled = false
        popped = 0
        fx.reset()
    }

    GameLoop(running = world != null && !settled) { dt ->
        val active = world ?: return@GameLoop
        active.step(dt)
        fx.step(dt)
        particles.step(dt)
        shake.step(dt)
        fx.collect(active, particles, shake)
        popped = active.poppedCount
        if (!finished && active.finished) {
            finished = true
            finishedAt = System.currentTimeMillis()
            if (active.won) {
                if (level > best.value) newBest = true
                best.submit(level)
            } else {
                lives--
                if (lives <= 0) best.played(level - 1)  // levels cleared in this run
            }
        }
        if (finished && lives <= 0) {  // let the sparks finish, then stop drawing behind the result
            fx.settle += dt
            settled = fx.settle > SETTLE_SECONDS
        }
        frame++
    }

    val palette = NOVA_PALETTES[(level - 1) % NOVA_PALETTES.size]
    val nebula = remember(area, palette) {
        if (area.width < 2 || area.height < 2) null else nebulaImage(area.width / 2, area.height / 2, palette)
    }
    val current = world
    val runOver = finished && lives <= 0
    Box(
        modifier
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .onSizeChanged {
                area = it
                val active = world
                if (active == null) newWorld() else active.resize(it.width / density, it.height / density)
            }
            .fingerPosition(
                onDown = { position ->
                    val active = world
                    when {
                        paused -> Unit  // a touch through How to play must not spend the level's one tap
                        active == null -> newWorld()
                        !finished -> {
                            tapped = true
                            active.tap(position.x / density, position.y / density)
                        }
                        System.currentTimeMillis() - finishedAt <= 500 -> Unit  // a stray tap must not skip the result
                        active.won -> { level++; newWorld() }
                        lives <= 0 -> { level = 1; lives = 3; newBest = false; newWorld() }
                        else -> newWorld()
                    }
                },
                onMove = {}
            )
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            frame  // redraw only, no recomposition
            if (nebula != null) drawImage(nebula, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            drawStars(fx, if (reduced) STARS_REDUCED else STARS_FULL)
            val w = current ?: return@Canvas
            translate(shake.x * density, shake.y * density) {
                drawBlasts(w, kit, reduced)
                drawOrbs(w, kit, fx.clock, reduced)
                particles.draw(this, density, 0f, 0f, 2.5f * density)
                if (!reduced) drawLabels(fx, kit)
            }
            if (fx.flash > 0f) drawRect(fx.flashTint.copy(alpha = fx.flash))
            drawProgress(w, kit, lives, pillsOnRight)
        }

        GameScore(
            score = stringResource(R.string.game_nova_progress, popped, current?.target ?: 0),
            best = best.value,
            extra = stringResource(R.string.game_nova_level, level, lives)
        )
        val message = when {
            current == null || runOver -> null
            !finished -> if (tapped) null else stringResource(if (level >= 5) R.string.game_nova_hole_hint else R.string.game_nova_hint)
            current.won -> stringResource(R.string.game_nova_clear)
            else -> stringResource(R.string.game_nova_retry)
        }
        if (message != null) {
            Text(
                message,
                style = IpxTypography.BodyMedium.copy(fontSize = if (finished) 19.sp else 15.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(if (finished) Alignment.Center else Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
        if (runOver) {
            GameResultPanel(
                title = stringResource(R.string.game_nova_title),
                score = (level - 1).toString(),  // the level it ended on was not cleared
                best = best.value,
                newBest = newBest,
                hint = stringResource(R.string.game_nova_over, level),
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
    }
}
