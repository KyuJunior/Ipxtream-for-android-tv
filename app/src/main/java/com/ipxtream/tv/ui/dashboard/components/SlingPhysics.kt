package com.ipxtream.tv.ui.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import com.ipxtream.tv.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** What a body is made of. [strength] is the hit speed (above [SlingWorld.DAMAGE_FLOOR]) that breaks it in one go. */
enum class SlingMaterial(val density: Float, val friction: Float, val strength: Float, val points: Int) {
    CRYSTAL(0.9f, 0.2f, 120f, 300),
    WOOD(1f, 0.6f, 230f, 500),
    STONE(2.6f, 0.8f, 500f, 800),
    CELL(0.8f, 0.5f, 110f, 600),     // blows up when it breaks
    GLOOM(0.6f, 0.7f, 90f, 5000),
    COMET(1f, 0.5f, Float.MAX_VALUE, 0),
    ROCK(1f, 0.9f, Float.MAX_VALUE, 0)   // the fixed ground and plinths
}

/** The throwables: each has its own tap trick and hits one material harder than the rest. */
enum class SlingShot(
    val radius: Float,
    val density: Float,
    @StringRes val label: Int,
    @StringRes val trick: Int,
    private val crystal: Float,
    private val wood: Float,
    private val stone: Float
) {
    EMBER(9f, 3f, R.string.game_sling_ember, R.string.game_sling_ember_trick, 1f, 1f, 1f),
    STREAK(7.5f, 3f, R.string.game_sling_streak, R.string.game_sling_streak_trick, 1f, 2.2f, 0.7f),
    TRIO(6.5f, 3f, R.string.game_sling_trio, R.string.game_sling_trio_trick, 2.5f, 0.8f, 0.6f),
    METEOR(12f, 4f, R.string.game_sling_meteor, R.string.game_sling_meteor_trick, 1f, 1.2f, 2f),
    PULSAR(9.5f, 3f, R.string.game_sling_pulsar, R.string.game_sling_pulsar_trick, 1f, 1f, 1.6f);

    fun versus(material: SlingMaterial) = when (material) {
        SlingMaterial.CRYSTAL -> crystal
        SlingMaterial.WOOD -> wood
        SlingMaterial.STONE -> stone
        else -> 1f
    }
}

/** A box, or a ball when it is a throwable or a Gloomlet ([w] is then its diameter). [x], [y] are its centre. */
class SlingBody(
    val material: SlingMaterial,
    var x: Float,
    var y: Float,
    val w: Float,
    val h: Float,
    val shot: SlingShot? = null,
    val fixed: Boolean = false
) {
    var angle = 0f
    var vx = 0f
    var vy = 0f
    var spin = 0f
    var health = 1f
    var alive = true
    var touched = false
    internal var id = 0
    internal val links = ArrayList<SlingArbiter>(4)
    val round = shot != null || material == SlingMaterial.GLOOM
    val bound = if (round) w / 2f else hypot(w, h) / 2f
    val inverseMass: Float
    val inverseInertia: Float

    init {
        val density = shot?.density ?: material.density
        val mass = if (round) 3.1416f * w * w / 4f * density else w * h * density
        val inertia = if (round) mass * w * w / 8f else mass * (w * w + h * h) / 12f
        inverseMass = if (fixed) 0f else 1f / mass
        inverseInertia = if (fixed) 0f else 1f / inertia
    }
}

internal class SlingContact {
    var x = 0f
    var y = 0f
    var nx = 0f
    var ny = 0f
    var separation = 0f
    var feature = 0
    var fresh = false
    var pn = 0f
    var pt = 0f
    var r1x = 0f
    var r1y = 0f
    var r2x = 0f
    var r2y = 0f
    var massNormal = 0f
    var massTangent = 0f
    var bias = 0f

    fun set(x: Float, y: Float, nx: Float, ny: Float, separation: Float, feature: Int) {
        this.x = x
        this.y = y
        this.nx = nx
        this.ny = ny
        this.separation = separation
        this.feature = feature
    }
}

/**
 * The contacts between two touching bodies, solved with sequential impulses. Impulses carry over to the
 * next step for contacts that persist (same [SlingContact.feature]), which is what keeps stacks standing.
 * The method follows Erin Catto's Box2D-Lite (zlib licence).
 */
internal class SlingArbiter(val a: SlingBody, val b: SlingBody) {
    val contacts = arrayOf(SlingContact(), SlingContact())
    var count = 0
    var stamp = 0

    /**
     * The impulse of this step's hits: only contact points that just touched count, so a weight resting on a body,
     * or the corner a toppling pillar pivots on, is not a hit.
     */
    var strike = 0f
    private val friction = sqrt(a.material.friction * b.material.friction)

    fun update(fresh: Array<SlingContact>, n: Int) {
        val f0 = contacts[0].feature
        val f1 = contacts[1].feature
        val n0 = contacts[0].pn
        val t0 = contacts[0].pt
        val n1 = contacts[1].pn
        val t1 = contacts[1].pt
        val old = count
        for (i in 0 until n) {
            val c = contacts[i]
            val f = fresh[i]
            c.set(f.x, f.y, f.nx, f.ny, f.separation, f.feature)
            when {
                old > 0 && f.feature == f0 -> { c.pn = n0; c.pt = t0; c.fresh = false }
                old > 1 && f.feature == f1 -> { c.pn = n1; c.pt = t1; c.fresh = false }
                else -> { c.pn = 0f; c.pt = 0f; c.fresh = true }
            }
        }
        count = n
    }

    fun preStep(inverseStep: Float) {
        strike = 0f
        for (i in 0 until count) {
            val c = contacts[i]
            c.r1x = c.x - a.x
            c.r1y = c.y - a.y
            c.r2x = c.x - b.x
            c.r2y = c.y - b.y
            val rn1 = c.r1x * c.nx + c.r1y * c.ny
            val rn2 = c.r2x * c.nx + c.r2y * c.ny
            val r11 = c.r1x * c.r1x + c.r1y * c.r1y
            val r22 = c.r2x * c.r2x + c.r2y * c.r2y
            c.massNormal = 1f / (a.inverseMass + b.inverseMass + a.inverseInertia * (r11 - rn1 * rn1) + b.inverseInertia * (r22 - rn2 * rn2))
            if (c.fresh) {
                val dvx = b.vx - b.spin * c.r2y - a.vx + a.spin * c.r1y
                val dvy = b.vy + b.spin * c.r2x - a.vy - a.spin * c.r1x
                strike = max(strike, -c.massNormal * (dvx * c.nx + dvy * c.ny))
            }
            val tx = c.ny
            val ty = -c.nx
            val rt1 = c.r1x * tx + c.r1y * ty
            val rt2 = c.r2x * tx + c.r2y * ty
            c.massTangent = 1f / (a.inverseMass + b.inverseMass + a.inverseInertia * (r11 - rt1 * rt1) + b.inverseInertia * (r22 - rt2 * rt2))
            c.bias = -SlingWorld.BIAS * inverseStep * min(0f, c.separation + SlingWorld.SLOP)
            push(c, c.pn * c.nx + c.pt * tx, c.pn * c.ny + c.pt * ty)
        }
    }

    fun applyImpulses() {
        for (i in 0 until count) {
            val c = contacts[i]
            var dvx = b.vx - b.spin * c.r2y - a.vx + a.spin * c.r1y
            var dvy = b.vy + b.spin * c.r2x - a.vy - a.spin * c.r1x
            val vn = dvx * c.nx + dvy * c.ny
            val pn0 = c.pn
            c.pn = max(pn0 + c.massNormal * (-vn + c.bias), 0f)
            val dPn = c.pn - pn0
            push(c, dPn * c.nx, dPn * c.ny)

            dvx = b.vx - b.spin * c.r2y - a.vx + a.spin * c.r1y
            dvy = b.vy + b.spin * c.r2x - a.vy - a.spin * c.r1x
            val tx = c.ny
            val ty = -c.nx
            val vt = dvx * tx + dvy * ty
            val limit = friction * c.pn
            val pt0 = c.pt
            c.pt = (pt0 - c.massTangent * vt).coerceIn(-limit, limit)
            val dPt = c.pt - pt0
            push(c, dPt * tx, dPt * ty)
        }
    }

    private fun push(c: SlingContact, px: Float, py: Float) {
        a.vx -= a.inverseMass * px
        a.vy -= a.inverseMass * py
        a.spin -= a.inverseInertia * (c.r1x * py - c.r1y * px)
        b.vx += b.inverseMass * px
        b.vy += b.inverseMass * py
        b.spin += b.inverseInertia * (c.r2x * py - c.r2y * px)
    }

}

enum class SlingPhase { READY, FLYING, WON, LOST }

/**
 * One level of Comet Sling in world units (y down). Towers come from [level], so a retry is the same layout;
 * tests can pass their own [layout] and [queue].
 */
class SlingWorld(val level: Int, layout: List<SlingBody>? = null, queue: List<SlingShot>? = null) {
    val bodies = ArrayList<SlingBody>()

    /** What just happened, for the screen's effects: it reads and clears these every frame. */
    val popped = ArrayList<SlingBody>()
    val blasts = ArrayList<Offset>()
    val tricks = ArrayList<Offset>()
    var impact = 0f
    var impactX = 0f
    var impactY = 0f

    var phase = SlingPhase.READY
        private set
    var score = 0
        private set
    val queue: List<SlingShot>
    var shotsLeft = 0
        private set

    /** The pieces of the current throw: one, or three after a [SlingShot.TRIO] split. */
    val shots = ArrayList<SlingBody>()
    val loaded: SlingShot? get() = queue.getOrNull(queue.size - shotsLeft)

    private val arbiters = ArrayList<SlingArbiter>()
    private val fresh = arrayOf(SlingContact(), SlingContact())
    private val incident = arrayOf(Clip(), Clip())
    private val clip1 = arrayOf(Clip(), Clip())
    private val clip2 = arrayOf(Clip(), Clip())
    private val pendingBlasts = ArrayList<Blast>()
    private var lead: SlingBody? = null
    private var trickUsed = false
    private var fuse = 0f
    private var nextId = 0
    private var stamp = 0
    private var armed = false
    private var flight = 0f
    private var calm = 0f
    private var pending = 0f

    val glooms: Int get() = bodies.count { it.material == SlingMaterial.GLOOM }

    init {
        add(SlingBody(SlingMaterial.ROCK, WIDTH / 2f, GROUND + 50f, 2000f, 100f, fixed = true))
        if (layout != null) layout.forEach(::add) else build()
        this.queue = queue ?: makeQueue()
        shotsLeft = this.queue.size
        // Let the towers settle with damage off, so a level never opens mid-wobble.
        repeat(SETTLE_STEPS) { fixedStep(STEP) }
        armed = true
        score = 0
        popped.clear()
        blasts.clear()
        impact = 0f
    }

    private fun add(body: SlingBody) {
        body.id = nextId++
        bodies += body
    }

    private fun build() {
        val random = Random(level)
        var x = 470f
        repeat(min(1 + level / 2, 3)) { tower ->
            var base = GROUND
            if (level >= 3 && random.nextInt(3) == 0) {
                val tall = 24f + random.nextInt(3) * 12f
                add(SlingBody(SlingMaterial.ROCK, x, GROUND - tall / 2f, 74f, tall, fixed = true))
                base -= tall
            }
            if (level >= 2 && random.nextInt(4) == 0) {
                crates(x, base, random)
            } else {
                hut(x, base, min(1 + (level + tower) / 3, 3), random)
            }
            x += 110f + random.nextFloat() * 25f
        }
    }

    private fun material(random: Random) = when (level) {
        1 -> SlingMaterial.WOOD
        2 -> if (random.nextBoolean()) SlingMaterial.CRYSTAL else SlingMaterial.WOOD
        else -> MIX[random.nextInt(MIX.size)]
    }

    /** Floors of two pillars and a roof, a Gloomlet sheltering on the ground floor and one on top. */
    private fun hut(x: Float, base: Float, floors: Int, random: Random) {
        var floor = base
        repeat(floors) { storey ->
            val wall = material(random)
            val roof = if (random.nextInt(3) == 0) material(random) else wall
            if (storey == 0 || (level >= 5 && random.nextBoolean())) gloom(x, floor)
            if (storey == 0 && base == GROUND && level >= 4 && random.nextInt(2) == 0) {
                add(SlingBody(SlingMaterial.CELL, x - 40f, floor - 8f, 16f, 16f))
            }
            add(SlingBody(wall, x - 22f, floor - 22f, 10f, 44f))
            add(SlingBody(wall, x + 22f, floor - 22f, 10f, 44f))
            add(SlingBody(roof, x, floor - 49f, 60f, 10f))
            floor -= 54f
        }
        gloom(x, floor)
    }

    /** Two columns of crates under a plank, a Gloomlet on top. */
    private fun crates(x: Float, base: Float, random: Random) {
        val crate = material(random)
        val rows = 2 + min(level / 3, 2)
        for (row in 0 until rows) {
            add(SlingBody(crate, x - 12f, base - 11f - row * 22f, 22f, 22f))
            add(SlingBody(crate, x + 12f, base - 11f - row * 22f, 22f, 22f))
        }
        val top = base - rows * 22f
        add(SlingBody(SlingMaterial.WOOD, x, top - 4f, 60f, 8f))
        gloom(x, top - 8f)
    }

    private fun gloom(x: Float, floor: Float) = add(SlingBody(SlingMaterial.GLOOM, x, floor - 11f, 22f, 22f))

    private fun makeQueue(): List<SlingShot> {
        val random = Random(level * 7919 + 1)
        val unlocked = SlingShot.entries.take(min(1 + level, SlingShot.entries.size))
        return List(min(glooms + 1, 5)) { i ->
            if (i == 0 && level == 1) SlingShot.EMBER else unlocked[random.nextInt(unlocked.size)]
        }
    }

    /** Launches the loaded throwable from [pull] away from the sling; a tiny pull is a change of mind. */
    fun launch(pull: Offset): Boolean {
        val held = clampPull(pull)
        val kind = loaded
        if (phase != SlingPhase.READY || kind == null || held.getDistance() < MIN_PULL) return false
        val size = kind.radius * 2f
        val comet = SlingBody(SlingMaterial.COMET, SLING_X + held.x, SLING_Y + held.y, size, size, shot = kind)
        comet.vx = -held.x * POWER
        comet.vy = -held.y * POWER
        add(comet)
        shots += comet
        lead = comet
        trickUsed = false
        fuse = 0f
        shotsLeft--
        phase = SlingPhase.FLYING
        flight = 0f
        calm = 0f
        return true
    }

    /** A tap in flight: the throwable's own trick, once, and (except a Pulsar's blast) only before it hits. */
    fun activate(): Boolean {
        val comet = lead ?: return false
        val kind = comet.shot ?: return false
        if (phase != SlingPhase.FLYING || !comet.alive || trickUsed || kind == SlingShot.EMBER) return false
        if (comet.touched && kind != SlingShot.PULSAR) return false
        trickUsed = true
        tricks += Offset(comet.x, comet.y)
        when (kind) {
            SlingShot.STREAK -> {
                val speed = hypot(comet.vx, comet.vy).coerceAtLeast(1f)
                val boost = min(speed * 2.2f, DASH_SPEED) / speed
                comet.vx *= boost
                comet.vy *= boost
            }
            SlingShot.TRIO -> for (turn in floatArrayOf(-SPLIT, SPLIT)) {
                val c = cos(turn)
                val s = sin(turn)
                val piece = SlingBody(SlingMaterial.COMET, comet.x, comet.y, comet.w, comet.h, shot = kind)
                piece.vx = comet.vx * c - comet.vy * s
                piece.vy = comet.vx * s + comet.vy * c
                val speed = hypot(comet.vx, comet.vy).coerceAtLeast(1f)
                piece.x -= comet.vy / speed * 8f * kotlin.math.sign(turn)  // fanned out, not stacked in one spot
                piece.y += comet.vx / speed * 8f * kotlin.math.sign(turn)
                add(piece)
                shots += piece
            }
            SlingShot.METEOR -> {
                comet.vx *= 0.3f
                comet.vy = max(comet.vy, 0f) + SLAM_SPEED
            }
            SlingShot.PULSAR -> detonate(comet)
            SlingShot.EMBER -> Unit
        }
        return true
    }

    private fun detonate(comet: SlingBody) {
        comet.health = 0f
        comet.alive = false
        pendingBlasts += Blast(comet.x, comet.y, 1f, SlingShot.PULSAR)
    }

    /** Advances in fixed small steps, so a slow frame can't push a fast comet through a block. */
    fun step(dt: Float) {
        pending += dt  // leftover time carries over; rounding up ran the world 50% fast at 60 Hz
        while (pending >= STEP) {
            pending -= STEP
            fixedStep(STEP)
        }
    }

    private fun fixedStep(h: Float) {
        findContacts()
        for (body in bodies) {
            if (body.fixed) continue
            body.vy += GRAVITY * h
            if (body.round) {
                // Rolling resistance: a ball on a near-flat roof stays put instead of creeping off it.
                body.spin *= 1f - ROLL_DRAG * h
                body.spin -= body.spin.coerceIn(-ROLL_GRIP * h, ROLL_GRIP * h)
            }
        }
        val inverse = 1f / h
        for (arbiter in arbiters) arbiter.preStep(inverse)
        repeat(ITERATIONS) { for (arbiter in arbiters) arbiter.applyImpulses() }
        for (body in bodies) {
            if (body.fixed) continue
            val speed = hypot(body.vx, body.vy)
            if (speed > MAX_SPEED) {
                body.vx *= MAX_SPEED / speed
                body.vy *= MAX_SPEED / speed
            }
            body.x += body.vx * h
            body.y += body.vy * h
            body.angle += body.spin * h
        }
        if (armed) {
            for (arbiter in arbiters) {
                hit(arbiter.a, arbiter.b, arbiter.strike * arbiter.a.inverseMass, arbiter)
                hit(arbiter.b, arbiter.a, arbiter.strike * arbiter.b.inverseMass, arbiter)
            }
        }
        lead?.let { comet ->
            if (comet.alive && comet.shot == SlingShot.PULSAR && comet.touched && !trickUsed) {
                fuse += h
                if (fuse >= PULSAR_FUSE) {
                    trickUsed = true
                    detonate(comet)
                }
            }
        }
        clearDead()
        if (pendingBlasts.isNotEmpty()) {
            val now = ArrayList(pendingBlasts)
            pendingBlasts.clear()
            for (blast in now) blast(blast)
        }
        if (phase == SlingPhase.FLYING) settle(h)
        // A Gloomlet still falling when the flight limit ended the turn can die while READY.
        if (armed && phase == SlingPhase.READY && glooms == 0 && shots.isEmpty()) {
            score += shotsLeft * SHOT_BONUS
            phase = SlingPhase.WON
        }
    }

    private fun findContacts() {
        stamp++
        for (i in bodies.indices) {
            val a = bodies[i]
            for (j in i + 1 until bodies.size) {
                val b = bodies[j]
                if (a.fixed && b.fixed) continue
                if (a.shot != null && b.shot != null) continue  // a Trio's pieces pass through each other
                val dx = b.x - a.x
                val dy = b.y - a.y
                val reach = a.bound + b.bound
                if (dx * dx + dy * dy > reach * reach) continue
                val n = collide(a, b)
                if (n == 0) continue
                var arbiter: SlingArbiter? = null
                for (link in a.links) if (link.b === b) arbiter = link
                if (arbiter == null) {
                    arbiter = SlingArbiter(a, b)
                    a.links += arbiter
                    arbiters += arbiter
                }
                arbiter.update(fresh, n)
                arbiter.stamp = stamp
            }
        }
        prune { it.stamp != stamp }
    }

    private inline fun prune(gone: (SlingArbiter) -> Boolean) {
        var keep = 0
        for (i in arbiters.indices) {
            val arbiter = arbiters[i]
            if (gone(arbiter)) arbiter.a.links.remove(arbiter) else arbiters[keep++] = arbiter
        }
        while (arbiters.size > keep) arbiters.removeAt(arbiters.size - 1)
    }

    /** The body's share of a hit's speed costs health; a throwable hitting its favourite material costs more. */
    private fun hit(body: SlingBody, other: SlingBody, change: Float, arbiter: SlingArbiter) {
        if (body.shot != null) {
            body.touched = true
            return
        }
        if (body.fixed || change <= 0f) return
        if (change > impact) {
            impact = change
            impactX = arbiter.contacts[0].x
            impactY = arbiter.contacts[0].y
        }
        hurt(body, change, other.shot?.versus(body.material) ?: 1f)
    }

    private fun hurt(body: SlingBody, speedChange: Float, factor: Float) {
        if (speedChange > DAMAGE_FLOOR) body.health -= (speedChange - DAMAGE_FLOOR) / body.material.strength * factor
    }

    private fun blast(blast: Blast) {
        blasts += Offset(blast.x, blast.y)
        for (body in bodies) {
            if (body.fixed || !body.alive) continue
            val dx = body.x - blast.x
            val dy = body.y - blast.y
            val distance = hypot(dx, dy)
            val reach = BLAST_RADIUS + body.bound
            if (distance >= reach) continue
            val falloff = 1f - distance / reach
            val nx = if (distance > 0.01f) dx / distance else 0f
            val ny = if (distance > 0.01f) dy / distance else -1f
            val kick = min(BLAST_IMPULSE * blast.power * falloff * body.inverseMass, BLAST_MAX_SPEED)
            body.vx += nx * kick
            body.vy += ny * kick - kick * 0.25f  // a little lift sends the debris flying
            if (!body.round) body.spin += nx * falloff * 6f
            if (body.shot == null) hurt(body, BLAST_SPEED * blast.power * falloff, blast.kind?.versus(body.material) ?: 1f)
        }
    }

    private fun clearDead() {
        var removed = false
        val iterator = bodies.iterator()
        while (iterator.hasNext()) {
            val body = iterator.next()
            if (body.fixed) continue
            val outside = body.x < -EDGE || body.x > WIDTH + EDGE || body.y > HEIGHT + EDGE
            if (body.health > 0f && body.alive && !outside) continue
            iterator.remove()
            removed = true
            body.alive = false
            popped += body
            if (body.shot == null) score += body.material.points
            if (body.material == SlingMaterial.CELL && !outside) pendingBlasts += Blast(body.x, body.y, CELL_POWER, null)
            shots.remove(body)
        }
        if (removed) prune { !it.a.alive || !it.b.alive }
    }

    /** The turn ends once everything is still, or after a limit in case something keeps creeping. */
    private fun settle(h: Float) {
        flight += h
        val moving = bodies.any { !it.fixed && (abs(it.vx) > STILL || abs(it.vy) > STILL || abs(it.spin) > STILL_SPIN) }
        calm = if (moving || pendingBlasts.isNotEmpty()) 0f else calm + h
        if (calm < CALM_TIME && flight < MAX_FLIGHT) return
        for (piece in shots) {
            bodies.remove(piece)
            piece.alive = false
            popped += piece
        }
        shots.clear()
        lead = null
        prune { !it.a.alive || !it.b.alive }
        phase = when {
            glooms == 0 -> {
                score += shotsLeft * SHOT_BONUS
                SlingPhase.WON
            }
            shotsLeft == 0 -> SlingPhase.LOST
            else -> SlingPhase.READY
        }
    }

    private fun collide(a: SlingBody, b: SlingBody): Int = when {
        a.round && b.round -> circles(a, b)
        a.round -> circleBox(a, b, circleFirst = true)
        b.round -> circleBox(b, a, circleFirst = false)
        else -> boxes(a, b)
    }

    private fun circles(a: SlingBody, b: SlingBody): Int {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val distance = hypot(dx, dy)
        val ra = a.w / 2f
        val rb = b.w / 2f
        if (distance > ra + rb) return 0
        val nx = if (distance > 1e-4f) dx / distance else 0f
        val ny = if (distance > 1e-4f) dy / distance else 1f
        val separation = distance - ra - rb
        fresh[0].set(a.x + nx * (ra + separation / 2f), a.y + ny * (ra + separation / 2f), nx, ny, separation, 0)
        return 1
    }

    /** One contact on the box's surface; the normal points from the first body to the second. */
    private fun circleBox(circle: SlingBody, box: SlingBody, circleFirst: Boolean): Int {
        val c = cos(box.angle)
        val s = sin(box.angle)
        val dx = circle.x - box.x
        val dy = circle.y - box.y
        val lx = c * dx + s * dy
        val ly = -s * dx + c * dy
        val hx = box.w / 2f
        val hy = box.h / 2f
        val r = circle.w / 2f
        var px = lx.coerceIn(-hx, hx)
        var py = ly.coerceIn(-hy, hy)
        val nlx: Float
        val nly: Float
        val separation: Float
        if (px == lx && py == ly) {
            // The centre is inside: leave by the nearest face.
            val ex = hx - abs(lx)
            val ey = hy - abs(ly)
            if (ex < ey) {
                nlx = if (lx >= 0f) 1f else -1f
                nly = 0f
                px = nlx * hx
            } else {
                nlx = 0f
                nly = if (ly >= 0f) 1f else -1f
                py = nly * hy
            }
            separation = -min(ex, ey) - r
        } else {
            val ox = lx - px
            val oy = ly - py
            val distance = hypot(ox, oy)
            if (distance > r) return 0
            nlx = ox / distance
            nly = oy / distance
            separation = distance - r
        }
        val sign = if (circleFirst) -1f else 1f
        fresh[0].set(
            box.x + c * px - s * py,
            box.y + s * px + c * py,
            sign * (c * nlx - s * nly),
            sign * (s * nlx + c * nly),
            separation,
            0
        )
        return 1
    }

    /** Box against box by separating axes, then clipping the incident edge to the reference face (Box2D-Lite). */
    private fun boxes(a: SlingBody, b: SlingBody): Int {
        val hAx = a.w / 2f
        val hAy = a.h / 2f
        val hBx = b.w / 2f
        val hBy = b.h / 2f
        val ca = cos(a.angle)
        val sa = sin(a.angle)
        val cb = cos(b.angle)
        val sb = sin(b.angle)
        val dpx = b.x - a.x
        val dpy = b.y - a.y
        val dAx = ca * dpx + sa * dpy
        val dAy = -sa * dpx + ca * dpy
        val dBx = cb * dpx + sb * dpy
        val dBy = -sb * dpx + cb * dpy
        val cc = abs(cos(b.angle - a.angle))
        val ss = abs(sin(b.angle - a.angle))

        val faceAx = abs(dAx) - hAx - (cc * hBx + ss * hBy)
        val faceAy = abs(dAy) - hAy - (ss * hBx + cc * hBy)
        if (faceAx > 0f || faceAy > 0f) return 0
        val faceBx = abs(dBx) - (cc * hAx + ss * hAy) - hBx
        val faceBy = abs(dBy) - (ss * hAx + cc * hAy) - hBy
        if (faceBx > 0f || faceBy > 0f) return 0

        var axis = 0
        var separation = faceAx
        var nx = if (dAx > 0f) ca else -ca
        var ny = if (dAx > 0f) sa else -sa
        if (faceAy > RELATIVE_TOL * separation + ABSOLUTE_TOL * hAy) {
            axis = 1
            separation = faceAy
            nx = if (dAy > 0f) -sa else sa
            ny = if (dAy > 0f) ca else -ca
        }
        if (faceBx > RELATIVE_TOL * separation + ABSOLUTE_TOL * hBx) {
            axis = 2
            separation = faceBx
            nx = if (dBx > 0f) cb else -cb
            ny = if (dBx > 0f) sb else -sb
        }
        if (faceBy > RELATIVE_TOL * separation + ABSOLUTE_TOL * hBy) {
            axis = 3
            nx = if (dBy > 0f) -sb else sb
            ny = if (dBy > 0f) cb else -cb
        }

        val fnx: Float
        val fny: Float
        val front: Float
        val snx: Float
        val sny: Float
        val side: Float
        val half: Float
        val negEdge: Int
        val posEdge: Int
        when (axis) {
            0 -> {
                fnx = nx; fny = ny
                front = a.x * fnx + a.y * fny + hAx
                snx = -sa; sny = ca
                side = a.x * snx + a.y * sny
                half = hAy
                negEdge = 3; posEdge = 1
                incidentEdge(hBx, hBy, b.x, b.y, cb, sb, fnx, fny)
            }
            1 -> {
                fnx = nx; fny = ny
                front = a.x * fnx + a.y * fny + hAy
                snx = ca; sny = sa
                side = a.x * snx + a.y * sny
                half = hAx
                negEdge = 2; posEdge = 4
                incidentEdge(hBx, hBy, b.x, b.y, cb, sb, fnx, fny)
            }
            2 -> {
                fnx = -nx; fny = -ny
                front = b.x * fnx + b.y * fny + hBx
                snx = -sb; sny = cb
                side = b.x * snx + b.y * sny
                half = hBy
                negEdge = 3; posEdge = 1
                incidentEdge(hAx, hAy, a.x, a.y, ca, sa, fnx, fny)
            }
            else -> {
                fnx = -nx; fny = -ny
                front = b.x * fnx + b.y * fny + hBy
                snx = cb; sny = sb
                side = b.x * snx + b.y * sny
                half = hBx
                negEdge = 2; posEdge = 4
                incidentEdge(hAx, hAy, a.x, a.y, ca, sa, fnx, fny)
            }
        }
        if (clipSegment(clip1, incident, -snx, -sny, -side + half, negEdge) < 2) return 0
        if (clipSegment(clip2, clip1, snx, sny, side + half, posEdge) < 2) return 0

        var count = 0
        for (point in clip2) {
            val gap = fnx * point.x + fny * point.y - front
            if (gap > 0f) continue
            val feature = if (axis >= 2) flip(point.feature) else point.feature
            fresh[count++].set(point.x - gap * fnx, point.y - gap * fny, nx, ny, gap, feature)
        }
        return count
    }

    private fun incidentEdge(hx: Float, hy: Float, px: Float, py: Float, c: Float, s: Float, fnx: Float, fny: Float) {
        val nx = -(c * fnx + s * fny)
        val ny = -(-s * fnx + c * fny)
        if (abs(nx) > abs(ny)) {
            if (nx >= 0f) {
                corner(0, hx, -hy, 3, 4, px, py, c, s)
                corner(1, hx, hy, 4, 1, px, py, c, s)
            } else {
                corner(0, -hx, hy, 1, 2, px, py, c, s)
                corner(1, -hx, -hy, 2, 3, px, py, c, s)
            }
        } else {
            if (ny >= 0f) {
                corner(0, hx, hy, 4, 1, px, py, c, s)
                corner(1, -hx, hy, 1, 2, px, py, c, s)
            } else {
                corner(0, -hx, -hy, 2, 3, px, py, c, s)
                corner(1, hx, -hy, 3, 4, px, py, c, s)
            }
        }
    }

    private fun corner(i: Int, lx: Float, ly: Float, inEdge: Int, outEdge: Int, px: Float, py: Float, c: Float, s: Float) {
        val point = incident[i]
        point.x = px + c * lx - s * ly
        point.y = py + s * lx + c * ly
        point.feature = (inEdge shl IN2) or (outEdge shl OUT2)
    }

    private fun clipSegment(out: Array<Clip>, input: Array<Clip>, nx: Float, ny: Float, offset: Float, clipEdge: Int): Int {
        var count = 0
        val d0 = nx * input[0].x + ny * input[0].y - offset
        val d1 = nx * input[1].x + ny * input[1].y - offset
        if (d0 <= 0f) out[count++].copy(input[0])
        if (d1 <= 0f) out[count++].copy(input[1])
        if (d0 * d1 < 0f) {
            val t = d0 / (d0 - d1)
            val point = out[count++]
            point.x = input[0].x + t * (input[1].x - input[0].x)
            point.y = input[0].y + t * (input[1].y - input[0].y)
            point.feature = if (d0 > 0f) {
                withEdge(withEdge(input[0].feature, IN1, clipEdge), IN2, 0)
            } else {
                withEdge(withEdge(input[1].feature, OUT1, clipEdge), OUT2, 0)
            }
        }
        return count
    }

    private class Clip {
        var x = 0f
        var y = 0f
        var feature = 0

        fun copy(other: Clip) {
            x = other.x
            y = other.y
            feature = other.feature
        }
    }

    private class Blast(val x: Float, val y: Float, val power: Float, val kind: SlingShot?)

    companion object {
        const val WIDTH = 800f
        const val HEIGHT = 360f
        const val GROUND = 340f
        const val SLING_X = 110f
        const val SLING_Y = 260f
        const val MAX_PULL = 70f
        const val MIN_PULL = 12f
        const val POWER = 12f
        const val GRAVITY = 900f
        const val STEP = 1f / 120f
        const val ITERATIONS = 10
        const val SETTLE_STEPS = 240
        const val BIAS = 0.2f
        const val SLOP = 0.4f
        const val ROLL_DRAG = 1.5f
        const val ROLL_GRIP = 4f
        const val MAX_SPEED = 2000f
        const val DAMAGE_FLOOR = 70f
        const val EDGE = 60f
        const val STILL = 12f
        const val STILL_SPIN = 0.6f
        const val CALM_TIME = 0.4f
        const val MAX_FLIGHT = 7f
        const val SHOT_BONUS = 10000
        const val DASH_SPEED = 1500f
        const val SLAM_SPEED = 900f
        const val SPLIT = 0.2f
        const val PULSAR_FUSE = 1.2f
        const val BLAST_RADIUS = 85f
        const val BLAST_IMPULSE = 600_000f
        const val BLAST_MAX_SPEED = 900f
        const val BLAST_SPEED = 700f
        const val CELL_POWER = 0.9f
        private const val RELATIVE_TOL = 0.95f
        private const val ABSOLUTE_TOL = 0.01f
        private const val IN1 = 0
        private const val OUT1 = 8
        private const val IN2 = 16
        private const val OUT2 = 24
        private val MIX = arrayOf(SlingMaterial.CRYSTAL, SlingMaterial.WOOD, SlingMaterial.WOOD, SlingMaterial.STONE, SlingMaterial.STONE)

        private fun withEdge(feature: Int, slot: Int, edge: Int) = (feature and (0xFF shl slot).inv()) or (edge shl slot)

        private fun flip(feature: Int): Int {
            val in1 = feature and 0xFF
            val out1 = (feature shr OUT1) and 0xFF
            val in2 = (feature shr IN2) and 0xFF
            val out2 = (feature shr OUT2) and 0xFF
            return in2 or (out2 shl OUT1) or (in1 shl IN2) or (out1 shl OUT2)
        }

        fun clampPull(pull: Offset): Offset {
            val length = pull.getDistance()
            return if (length <= MAX_PULL) pull else pull * (MAX_PULL / length)
        }

        /** Where a throwable pulled by [pull] would be [time] seconds after release. */
        fun flightPoint(pull: Offset, time: Float): Offset {
            val held = clampPull(pull)
            return Offset(
                SLING_X + held.x - held.x * POWER * time,
                SLING_Y + held.y - held.y * POWER * time + GRAVITY * time * time / 2f
            )
        }
    }
}
