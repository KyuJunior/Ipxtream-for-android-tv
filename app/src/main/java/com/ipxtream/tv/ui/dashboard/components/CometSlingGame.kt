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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
import kotlin.random.Random

enum class SlingKind(val density: Float, val hits: Int, val toughness: Float, val points: Int) {
    SHOT(3f, 1, Float.MAX_VALUE, 0),
    WOOD(1f, 1, 170f, 50),
    STONE(2.5f, 2, 260f, 100),
    GLOOM(0.5f, 1, 110f, 500)
}

/** Everything is an axis-aligned box and [x], [y] are its centre. shortcut: boxes never rotate, add angles if towers should topple. */
class SlingBody(val kind: SlingKind, var x: Float, var y: Float, val w: Float, val h: Float) {
    var vx = 0f
    var vy = 0f
    var hits = kind.hits
    val inverseMass = 1f / (w * h * kind.density)

    /** A speed change above the toughness costs a hit; resting weight never does. */
    fun jolt(speedChange: Float) {
        if (speedChange > kind.toughness) hits--
    }
}

enum class SlingPhase { READY, FLYING, WON, LOST }

/** One level of Comet Sling in world units (y down): towers come from [level], so a retry is the same layout. */
class SlingWorld(val level: Int) {
    val bodies = ArrayList<SlingBody>()
    var phase = SlingPhase.READY
        private set
    var score = 0
        private set
    var shotsLeft = 0
        private set
    private var shot: SlingBody? = null
    private var flight = 0f
    private var calm = 0f

    val glooms: Int get() = bodies.count { it.kind == SlingKind.GLOOM }

    init {
        val random = Random(level)
        var x = 500f
        repeat(min(1 + level / 2, 3)) { tower ->
            val material = if (level >= 3 && random.nextBoolean()) SlingKind.STONE else SlingKind.WOOD
            build(x, min(1 + (level + tower) / 3, 3), material)
            x += 100f + random.nextFloat() * 30f
        }
        shotsLeft = min(glooms + 1, 5)
    }

    private fun build(x: Float, floors: Int, material: SlingKind) {
        bodies += SlingBody(SlingKind.GLOOM, x, GROUND - 11f, 22f, 22f)
        var floor = GROUND
        repeat(floors) {
            bodies += SlingBody(material, x - 22f, floor - 22f, 10f, 44f)
            bodies += SlingBody(material, x + 22f, floor - 22f, 10f, 44f)
            bodies += SlingBody(material, x, floor - 49f, 60f, 10f)
            floor -= 54f
        }
        bodies += SlingBody(SlingKind.GLOOM, x, floor - 11f, 22f, 22f)
    }

    /** Launches the comet from [pull] away from the sling; a tiny pull is a change of mind. */
    fun launch(pull: Offset): Boolean {
        val held = clampPull(pull)
        if (phase != SlingPhase.READY || held.getDistance() < MIN_PULL) return false
        val comet = SlingBody(SlingKind.SHOT, SLING_X + held.x, SLING_Y + held.y, SHOT_SIZE, SHOT_SIZE)
        comet.vx = -held.x * POWER
        comet.vy = -held.y * POWER
        bodies += comet
        shot = comet
        shotsLeft--
        phase = SlingPhase.FLYING
        flight = 0f
        calm = 0f
        return true
    }

    /** Advances in fixed small steps, so a slow frame can't push a fast comet through a block. */
    fun step(dt: Float) {
        repeat(ceil(dt / STEP).toInt().coerceAtLeast(1)) { fixedStep(STEP) }
    }

    private fun fixedStep(h: Float) {
        for (body in bodies) {
            body.vy += GRAVITY * h
            body.x += body.vx * h
            body.y += body.vy * h
        }
        repeat(PASSES) {
            for (a in bodies.indices) for (b in a + 1 until bodies.size) collide(bodies[a], bodies[b])
            for (body in bodies) land(body, h / PASSES)
        }
        clearDead()
        if (phase == SlingPhase.FLYING) settle(h)
    }

    private fun collide(a: SlingBody, b: SlingBody) {
        val overlapX = (a.w + b.w) / 2f - abs(a.x - b.x)
        if (overlapX <= 0f) return
        val overlapY = (a.h + b.h) / 2f - abs(a.y - b.y)
        if (overlapY <= 0f) return
        val alongX = overlapX < overlapY
        val normalX = if (!alongX) 0f else if (b.x >= a.x) 1f else -1f
        val normalY = if (alongX) 0f else if (b.y >= a.y) 1f else -1f
        val overlap = min(overlapX, overlapY)
        val total = a.inverseMass + b.inverseMass
        a.x -= normalX * overlap * a.inverseMass / total
        a.y -= normalY * overlap * a.inverseMass / total
        b.x += normalX * overlap * b.inverseMass / total
        b.y += normalY * overlap * b.inverseMass / total

        val closing = (b.vx - a.vx) * normalX + (b.vy - a.vy) * normalY
        if (closing >= 0f) return
        val bounce = if (-closing > BOUNCE_SPEED) 0.2f else 0f
        val impulse = -(1f + bounce) * closing / total
        a.vx -= impulse * a.inverseMass * normalX
        a.vy -= impulse * a.inverseMass * normalY
        b.vx += impulse * b.inverseMass * normalX
        b.vy += impulse * b.inverseMass * normalY
        a.jolt(impulse * a.inverseMass)
        b.jolt(impulse * b.inverseMass)

        val sliding = (b.vx - a.vx) * -normalY + (b.vy - a.vy) * normalX
        val grip = (-sliding / total).coerceIn(-FRICTION * impulse, FRICTION * impulse)
        a.vx += grip * a.inverseMass * normalY
        a.vy -= grip * a.inverseMass * normalX
        b.vx -= grip * b.inverseMass * normalY
        b.vy += grip * b.inverseMass * normalX
    }

    private fun land(body: SlingBody, h: Float) {
        val bottom = GROUND - body.h / 2f
        if (body.y < bottom) return
        body.y = bottom
        if (body.vy > 0f) {
            body.jolt(body.vy)
            body.vy = if (body.vy > BOUNCE_SPEED) -body.vy * 0.2f else 0f
        }
        body.vx *= 1f - GROUND_DRAG * h
    }

    private fun clearDead() {
        val iterator = bodies.iterator()
        while (iterator.hasNext()) {
            val body = iterator.next()
            val outside = body.x < -EDGE || body.x > WIDTH + EDGE
            if (body.hits > 0 && !outside) continue
            iterator.remove()
            score += body.kind.points
            if (body === shot) shot = null
        }
    }

    /** The turn ends once everything is still, or after a limit in case something keeps creeping. */
    private fun settle(h: Float) {
        flight += h
        calm = if (bodies.any { abs(it.vx) > STILL || abs(it.vy) > STILL }) 0f else calm + h
        if (calm < CALM_TIME && flight < MAX_FLIGHT) return
        shot?.let { bodies.remove(it) }
        shot = null
        when {
            glooms == 0 -> {
                score += shotsLeft * SHOT_BONUS
                phase = SlingPhase.WON
            }
            shotsLeft == 0 -> phase = SlingPhase.LOST
            else -> phase = SlingPhase.READY
        }
    }

    companion object {
        const val WIDTH = 800f
        const val HEIGHT = 360f
        const val GROUND = 340f
        const val SLING_X = 110f
        const val SLING_Y = 260f
        const val SHOT_SIZE = 18f
        const val MAX_PULL = 70f
        const val MIN_PULL = 12f
        const val POWER = 12f
        const val GRAVITY = 900f
        const val STEP = 1f / 120f
        const val PASSES = 3
        const val BOUNCE_SPEED = 60f
        const val FRICTION = 0.5f
        const val GROUND_DRAG = 6f
        const val EDGE = 60f
        const val STILL = 12f
        const val CALM_TIME = 0.4f
        const val MAX_FLIGHT = 7f
        const val SHOT_BONUS = 300

        fun clampPull(pull: Offset): Offset {
            val length = pull.getDistance()
            return if (length <= MAX_PULL) pull else pull * (MAX_PULL / length)
        }

        /** Where a comet pulled by [pull] would be [time] seconds after release. */
        fun flightPoint(pull: Offset, time: Float): Offset {
            val held = clampPull(pull)
            return Offset(
                SLING_X + held.x - held.x * POWER * time,
                SLING_Y + held.y - held.y * POWER * time + GRAVITY * time * time / 2f
            )
        }
    }
}

private val WoodColor = Color(0xFFB9774A)
private val StoneColor = Color(0xFF8A8694)
private val StoneCrackedColor = Color(0xFF65616E)
private val SlingColor = Color(0xFF6D4A36)

/** Drag anywhere to pull the comet back, let go to fire, and clear every Gloomlet with the shots you have. */
@Composable
internal fun CometSlingScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("sling_game")
    val reduced = LocalReducedEffects.current
    var level by remember { mutableIntStateOf(1) }
    var banked by remember { mutableIntStateOf(0) }
    var world by remember { mutableStateOf(SlingWorld(1)) }
    var phase by remember { mutableStateOf(SlingPhase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(world.shotsLeft) }
    var frame by remember { mutableIntStateOf(0) }
    var pull by remember { mutableStateOf<Offset?>(null) }
    var pullStart by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var finishedAt by remember { mutableStateOf(0L) }

    fun scale() = min(area.width / SlingWorld.WIDTH, area.height / SlingWorld.HEIGHT).coerceAtLeast(0.01f)

    fun play(nextLevel: Int) {
        level = nextLevel
        world = SlingWorld(nextLevel)
        phase = SlingPhase.READY
        score = 0
        shots = world.shotsLeft
    }

    val finished = phase == SlingPhase.WON || phase == SlingPhase.LOST
    GameLoop(running = !finished) { dt ->
        world.step(dt)
        frame++
        score = world.score
        shots = world.shotsLeft
        if (world.phase != phase) {
            phase = world.phase
            if (phase == SlingPhase.WON || phase == SlingPhase.LOST) finishedAt = System.currentTimeMillis()
            if (phase == SlingPhase.WON) {
                banked += world.score
                best.submit(banked)
            }
        }
    }

    Box(
        modifier
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
            .onSizeChanged { area = it }
            .fingerPosition(
                onDown = { position ->
                    when {
                        phase == SlingPhase.READY -> {
                            pullStart = position
                            pull = Offset.Zero
                        }
                        phase == SlingPhase.FLYING -> Unit
                        System.currentTimeMillis() - finishedAt <= 500 -> Unit  // a stray tap must not skip the result
                        phase == SlingPhase.WON -> play(level + 1)
                        else -> play(level)
                    }
                },
                onMove = { position -> if (pull != null) pull = (position - pullStart) / scale() },
                onUp = {
                    pull?.let { if (world.launch(it)) phase = SlingPhase.FLYING }
                    pull = null
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame  // redraw only, no recomposition
            val w = world
            val s = min(size.width / SlingWorld.WIDTH, size.height / SlingWorld.HEIGHT)
            val left = (size.width - SlingWorld.WIDTH * s) / 2f
            val top = size.height - SlingWorld.HEIGHT * s
            fun at(x: Float, y: Float) = Offset(left + x * s, top + y * s)

            drawRect(
                Color.White.copy(alpha = 0.10f),
                Offset(0f, top + SlingWorld.GROUND * s),
                Size(size.width, (SlingWorld.HEIGHT - SlingWorld.GROUND) * s)
            )
            val fork = at(SlingWorld.SLING_X, SlingWorld.SLING_Y)
            val base = at(SlingWorld.SLING_X, SlingWorld.GROUND)
            drawLine(SlingColor, base, fork, 6f * s)

            val held = pull?.let(SlingWorld::clampPull)
            val waiting = held != null || w.phase == SlingPhase.READY
            val cometAt = held ?: Offset.Zero
            if (waiting) {
                val comet = at(SlingWorld.SLING_X + cometAt.x, SlingWorld.SLING_Y + cometAt.y)
                drawLine(SlingColor, fork, comet, 3f * s)
                drawComet(comet, SlingWorld.SHOT_SIZE / 2f * s)
            }
            if (held != null && held.getDistance() >= SlingWorld.MIN_PULL) {
                // Only the start of the arc: the rest is the player's skill.
                for (dot in 1..7) {
                    val point = SlingWorld.flightPoint(held, dot * 0.06f)
                    drawCircle(Color.White.copy(alpha = 0.6f - dot * 0.06f), 2.5f * s, at(point.x, point.y))
                }
            }

            for (body in w.bodies) {
                val center = at(body.x, body.y)
                val halfW = body.w / 2f * s
                val halfH = body.h / 2f * s
                when (body.kind) {
                    SlingKind.SHOT -> {
                        if (!reduced) {
                            for (i in 1..4) {
                                val trail = center - Offset(body.vx, body.vy) * (i * 0.012f * s)
                                drawCircle(Accent.copy(alpha = 0.5f - i * 0.1f), halfW * (1f - i * 0.15f), trail)
                            }
                        }
                        drawComet(center, halfW)
                    }
                    SlingKind.GLOOM -> {
                        drawCircle(AccentOrchid, halfW, center)
                        drawCircle(Color.White, halfW * 0.42f, center + Offset(0f, -halfW * 0.1f))
                        drawCircle(Color.Black, halfW * 0.2f, center + Offset(-halfW * 0.1f, -halfW * 0.1f))
                    }
                    else -> drawRect(
                        when {
                            body.kind == SlingKind.WOOD -> WoodColor
                            body.hits < body.kind.hits -> StoneCrackedColor
                            else -> StoneColor
                        },
                        center - Offset(halfW, halfH),
                        Size(halfW * 2f, halfH * 2f)
                    )
                }
            }
        }

        GameScore(
            score = (banked + if (phase == SlingPhase.WON) 0 else score).toString(),
            best = best.value,
            extra = stringResource(R.string.game_sling_status, level, shots)
        )
        Text(
            stringResource(
                when (phase) {
                    SlingPhase.WON -> R.string.game_sling_clear
                    SlingPhase.LOST -> R.string.game_sling_retry
                    else -> R.string.game_sling_hint
                }
            ),
            style = IpxTypography.BodyMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 2.dp)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawComet(center: Offset, radius: Float) {
    drawCircle(Accent, radius, center)
    drawCircle(ChipFocused, radius * 0.55f, center)
}
