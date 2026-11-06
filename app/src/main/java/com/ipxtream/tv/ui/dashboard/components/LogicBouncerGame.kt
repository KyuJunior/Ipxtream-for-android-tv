package com.ipxtream.tv.ui.dashboard.components

import android.graphics.Paint
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class BouncerShape { CIRCLE, SQUARE, TRIANGLE }
enum class BouncerColor(val color: Color) {
    RED(Color(0xFFFF5D5D)), BLUE(Color(0xFF5B9DFF)), YELLOW(Color(0xFFFFD25A))
}

class BouncerCard(val shape: BouncerShape, var color: BouncerColor, val number: Int, val gold: Boolean, val changesTo: BouncerColor?)

/** Rules are whole sentences, so each reads properly in Arabic; never join them from parts. */
enum class BouncerRule(@StringRes val text: Int, val hard: Boolean, val accepts: (BouncerCard) -> Boolean) {
    ONLY_RED(R.string.game_rule_red, false, { it.color == BouncerColor.RED }),
    ONLY_BLUE(R.string.game_rule_blue, false, { it.color == BouncerColor.BLUE }),
    ONLY_CIRCLES(R.string.game_rule_circles, false, { it.shape == BouncerShape.CIRCLE }),
    ONLY_SQUARES(R.string.game_rule_squares, false, { it.shape == BouncerShape.SQUARE }),
    ONLY_EVEN(R.string.game_rule_even, false, { it.number % 2 == 0 }),
    ONLY_ODD(R.string.game_rule_odd, false, { it.number % 2 == 1 }),
    EVEN_NOT_BLUE_TRIANGLES(R.string.game_rule_even_not_blue_triangles, true, {
        it.number % 2 == 0 && !(it.color == BouncerColor.BLUE && it.shape == BouncerShape.TRIANGLE)
    }),
    RED_OR_CIRCLES(R.string.game_rule_red_or_circles, true, { it.color == BouncerColor.RED || it.shape == BouncerShape.CIRCLE }),
    BIG_NOT_YELLOW(R.string.game_rule_big_not_yellow, true, { it.number > 5 && it.color != BouncerColor.YELLOW }),
    ODD_SQUARES(R.string.game_rule_odd_squares, true, { it.number % 2 == 1 && it.shape == BouncerShape.SQUARE }),
    BLUE_NOT_CIRCLES(R.string.game_rule_blue_not_circles, true, { it.color == BouncerColor.BLUE && it.shape != BouncerShape.CIRCLE }),
    SMALL_OR_TRIANGLES(R.string.game_rule_small_or_triangles, true, { it.number < 5 || it.shape == BouncerShape.TRIANGLE })
}

/** Logic Bouncer: swipe each card right to accept or left to reject before it reaches the gate. */
class BouncerGame(private val random: Random) {
    var rule = BouncerRule.ONLY_RED
        private set
    var card = newCard()
        private set
    var cardAge = 0f
        private set
    var ruleAge = 0f
        private set
    var score = 0
        private set
    var lives = LIVES
        private set
    val over: Boolean get() = lives <= 0
    private var changed = false

    fun travelTime(): Float = max(3.6f - score * 0.05f, 1.5f)

    fun step(dt: Float) {
        if (over) return
        ruleAge += dt
        cardAge += dt
        val change = card.changesTo
        if (!changed && change != null && cardAge > travelTime() * 0.6f) {
            card.color = change
            changed = true
        }
        if (cardAge >= travelTime()) settle(right = false)
    }

    /** The player's swipe. Returns whether it was right. */
    fun judge(accept: Boolean): Boolean {
        val right = rule.accepts(card) == accept
        settle(right)
        return right
    }

    private fun settle(right: Boolean) {
        val weight = if (card.gold) 2 else 1
        if (right) score += weight else lives = (lives - weight).coerceAtLeast(0)
        // Only between cards: a rule swapped under a card already read judges the swipe by a sign nobody saw.
        if (ruleAge >= RULE_TIME) {
            ruleAge = 0f
            rule = nextRule()
        }
        card = newCard()
        cardAge = 0f
        changed = false
    }

    private fun nextRule(): BouncerRule {
        val pool = BouncerRule.entries.filter { it != rule && (score >= HARD_AT || !it.hard) }
        return pool[random.nextInt(pool.size)]
    }

    private fun newCard(): BouncerCard {
        val colors = BouncerColor.entries
        val color = colors[random.nextInt(colors.size)]
        return BouncerCard(
            shape = BouncerShape.entries[random.nextInt(BouncerShape.entries.size)],
            color = color,
            number = 1 + random.nextInt(9),
            gold = score >= 5 && random.nextInt(8) == 0,
            changesTo = if (score >= 8 && random.nextInt(4) == 0) colors[(color.ordinal + 1) % colors.size] else null
        )
    }

    companion object {
        const val LIVES = 3
        const val RULE_TIME = 15f
        const val HARD_AT = 10
    }
}

private val WallTop = Color(0xFF0D0720)
private val WallBottom = Color(0xFF32104A)
private val FloorTop = Color(0xFF241033)
private val FloorBottom = Color(0xFF0A0512)
private val CarpetFar = Color(0xFF8E1D5C)
private val CarpetNear = Color(0xFF3F0C34)
private val DoorTop = Color(0xFFFFE3B0)
private val DoorBottom = Color(0xFFFF7FA8)
private val Crowd = Color(0xFF0A0516)
private val Brass = Color(0xFFE9B85A)
private val BrassDark = Color(0xFF7A5420)
private val RopeRed = Color(0xFFB01446)
private val CardTop = Color(0xFF2B2A44)
private val CardBottom = Color(0xFF15131F)
private val PhotoBox = Color(0xFF0B0A12)
private val SignTop = Color(0xFF4A1466)
private val SignBottom = Color(0xFF1C0A30)

/** Where a card starts and ends on the walkway, as parts of the window height. */
private const val CARD_START = 0.37f
private const val CARD_END = 0.80f

/** The club's fixed scenery, built once per window size so a frame only paints it. */
private class BouncerScene(val w: Float, val h: Float, val d: Float) {
    val horizon = h * 0.48f
    val doorTopLeft = Offset(w * 0.43f, h * 0.2f)
    val doorSize = Size(w * 0.14f, horizon - h * 0.2f)
    val cardH = h * 0.24f
    val cardW = cardH * 1.6f
    val leftLamp = Offset(w * 0.1f, h * 0.27f)
    val rightLamp = Offset(w * 0.9f, h * 0.27f)
    val lampRadius = h * 0.055f

    val wall = Brush.verticalGradient(listOf(WallTop, WallBottom), 0f, horizon)
    val wallGlow = Brush.radialGradient(listOf(Color(0x77C040FF), Color.Transparent), Offset(w / 2f, h * 0.34f), w * 0.42f)
    val floor = Brush.verticalGradient(listOf(FloorTop, FloorBottom), horizon, h)
    val door = Brush.verticalGradient(listOf(DoorTop, DoorBottom), doorTopLeft.y, horizon)
    val carpetFill = Brush.verticalGradient(listOf(CarpetFar, CarpetNear), horizon, h)
    val spillFill = Brush.verticalGradient(listOf(Color(0x88FFC890), Color.Transparent), horizon, h * 0.8f)
    val beamLeft = Brush.verticalGradient(listOf(AccentOrchid.copy(alpha = 0.26f), Color.Transparent), 0f, h * 0.85f)
    val beamRight = Brush.verticalGradient(listOf(Accent.copy(alpha = 0.22f), Color.Transparent), 0f, h * 0.85f)
    val vignette = Brush.radialGradient(
        0.5f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.62f),
        center = Offset(w / 2f, h / 2f),
        radius = w * 0.62f
    )
    val cardFace = Brush.verticalGradient(listOf(CardTop, CardBottom), -cardH / 2f, cardH / 2f)
    val post = Brush.horizontalGradient(listOf(BrassDark, Brass, BrassDark), -d * 5f, d * 5f)

    val hair = Stroke(d)
    val thin = Stroke(d * 1.5f)
    val neon = Stroke(d * 3f)
    val neonGlow = Stroke(d * 10f)
    val rimGold = Stroke(d * 4f)
    val ropeStroke = Stroke(d * 8f, cap = StrokeCap.Round)
    val ropeShine = Stroke(d * 2f, cap = StrokeCap.Round)

    val carpet = Path().apply {
        moveTo(w * 0.445f, horizon)
        lineTo(w * 0.555f, horizon)
        lineTo(w * 0.77f, h)
        lineTo(w * 0.23f, h)
        close()
    }
    val spill = Path().apply {
        moveTo(w * 0.43f, horizon)
        lineTo(w * 0.57f, horizon)
        lineTo(w * 0.66f, h * 0.8f)
        lineTo(w * 0.34f, h * 0.8f)
        close()
    }
    val tiles = Path().apply {
        for (i in -9..9) {
            moveTo(w / 2f + i * w * 0.06f, horizon)
            lineTo(w / 2f + i * w * 0.42f, h)
        }
        for (i in 1..5) {
            val y = horizon + (h - horizon) * i * i / 25f
            moveTo(0f, y)
            lineTo(w, y)
        }
    }
    val bricks = Path().apply {
        val rowHeight = h * 0.05f
        val brick = w / 14f
        var row = 0
        var y = 0f
        while (y < horizon) {
            moveTo(0f, y)
            lineTo(w, y)
            var x = if (row % 2 == 0) 0f else brick / 2f
            while (x < w) {
                moveTo(x, y)
                lineTo(x, min(y + rowHeight, horizon))
                x += brick
            }
            y += rowHeight
            row++
        }
    }

    /** The queue waiting along the wall, either side of the door. */
    val crowd = Path().apply {
        for (side in intArrayOf(-1, 1)) for (i in 0..6) {
            val x = w / 2f + side * (w * 0.12f + i * w * 0.055f)
            val r = h * (0.024f + (i % 3) * 0.005f)
            val top = horizon - r * (4.2f + (i % 2) * 0.8f)
            addOval(Rect(x - r, top, x + r, top + r * 2f))
            addOval(Rect(x - r * 1.9f, top + r * 2.1f, x + r * 1.9f, horizon + r * 3f))
        }
    }
    val beamOne = beam(w * 0.17f, w * 0.26f)
    val beamTwo = beam(w * 0.83f, w * 0.74f)
    val leftPost = w * 0.21f
    val rightPost = w * 0.79f
    val postTop = h * 0.8f
    val rope = Path().apply {
        val hang = h * 0.84f
        val sag = h * 0.97f
        moveTo(-w * 0.02f, hang)
        cubicTo(w * 0.05f, sag, w * 0.14f, sag, leftPost, hang)
        cubicTo(w * 0.36f, sag, w * 0.64f, sag, rightPost, hang)
        cubicTo(w * 0.86f, sag, w * 0.95f, sag, w * 1.02f, hang)
    }
    val heart = Path().apply {
        val r = d * 9f
        moveTo(0f, r)
        cubicTo(-r * 1.7f, -r * 0.1f, -r * 0.9f, -r * 1.2f, 0f, -r * 0.35f)
        cubicTo(r * 0.9f, -r * 1.2f, r * 1.7f, -r * 0.1f, 0f, r)
        close()
    }

    /** Reused for each triangle drawn, so a frame allocates nothing. */
    val triangle = Path()

    private fun beam(fromX: Float, toX: Float) = Path().apply {
        moveTo(fromX, -d * 12f)
        lineTo(toX + w * 0.1f, h * 0.85f)
        lineTo(toX - w * 0.1f, h * 0.85f)
        close()
    }
}

/** Everything drawn that isn't game state. Plain fields: the frame counter is what triggers redraws. */
private class BouncerFx {
    var width = 0f
    var height = 0f
    var clock = 0f
    var overFor = 0f

    var dragCard: BouncerCard? = null
    var downX = 0f
    var fingerX = 0f
    var snapX = 0f

    var x = 0f
    var y = 0f
    var zoom = 1f
    var tilt = 0f
    var offset = 0f

    var gone: BouncerCard? = null
    var goneX = 0f
    var goneY = 0f
    var goneZoom = 1f
    var goneTilt = 0f
    var goneVx = 0f
    var goneVy = 0f
    var goneSpin = 0f

    var flash = 0f
    var flashColor = AccentGreen
    var mark = 0f
    var markRight = true
    var markX = 0f
    var markY = 0f
    var pop = 0f
    var popText = ""
    var popColor = AccentGreen
    var ruleFlash = 0f
    var colorPulse = 0f
    var watched: BouncerCard? = null
    var watchedColor = BouncerColor.RED

    /** Works out where the live card sits this frame: on the walkway, plus the finger's pull. */
    fun pose(game: BouncerGame) {
        val travel = (game.cardAge / game.travelTime()).coerceIn(0f, 1f)
        val drag = if (dragCard === game.card) fingerX - downX else 0f
        offset = drag + snapX
        x = width / 2f + offset
        y = height * (CARD_START + (CARD_END - CARD_START) * travel)
        zoom = (0.72f + 0.28f * travel) * (game.cardAge / 0.12f).coerceAtMost(1f)
        tilt = (offset / width.coerceAtLeast(1f) * 50f).coerceIn(-24f, 24f)
    }

    /** [side] is -1 or 1 for a swipe, 0 for a card that reached the rope and drops. */
    fun leave(card: BouncerCard, right: Boolean, side: Int) {
        gone = card
        goneX = x
        goneY = y
        goneZoom = zoom
        goneTilt = tilt
        goneVx = side * width * 2.4f
        goneVy = if (side == 0) height * 0.7f else -height * 0.25f
        goneSpin = side * 240f
        snapX = 0f
        dragCard = null
        flash = 1f
        flashColor = if (right) AccentGreen else AccentRed
        mark = 1f
        markRight = right
        markX = width / 2f
        markY = y
        if (right) {
            pop = 1f
            popText = if (card.gold) "+2" else "+1"
            popColor = if (card.gold) AccentAmber else AccentGreen
        }
    }

    fun step(dt: Float, game: BouncerGame) {
        clock += dt
        if (game.over) overFor += dt
        snapX -= snapX * min(1f, 14f * dt)
        flash = (flash - dt * 3.2f).coerceAtLeast(0f)
        mark = (mark - dt * 2f).coerceAtLeast(0f)
        pop = (pop - dt * 1.4f).coerceAtLeast(0f)
        ruleFlash = (ruleFlash - dt * 1.2f).coerceAtLeast(0f)
        colorPulse = (colorPulse - dt * 2.4f).coerceAtLeast(0f)
        if (gone != null) {
            goneVy += height * 2.2f * dt
            goneX += goneVx * dt
            goneY += goneVy * dt
            goneTilt += goneSpin * dt
            if (goneX < -width * 0.4f || goneX > width * 1.4f || goneY > height * 1.4f) gone = null
        }
        if (watched === game.card && watchedColor != game.card.color) colorPulse = 1f
        watched = game.card
        watchedColor = game.card.color
    }

    fun reset() {
        overFor = 0f
        gone = null
        dragCard = null
        snapX = 0f
        flash = 0f
        mark = 0f
        pop = 0f
    }
}

private fun heartX(width: Float, density: Float, index: Int) = width / 2f + (96f + index * 26f) * density

private fun DrawScope.drawMark(center: Offset, r: Float, right: Boolean, color: Color, alpha: Float) {
    val width = r * 0.28f
    if (right) {
        val elbow = center + Offset(-r * 0.15f, r * 0.55f)
        drawLine(color, center + Offset(-r * 0.7f, 0f), elbow, width, StrokeCap.Round, alpha = alpha)
        drawLine(color, elbow, center + Offset(r * 0.75f, -r * 0.55f), width, StrokeCap.Round, alpha = alpha)
    } else {
        drawLine(color, center + Offset(-r * 0.6f, -r * 0.6f), center + Offset(r * 0.6f, r * 0.6f), width, StrokeCap.Round, alpha = alpha)
        drawLine(color, center + Offset(r * 0.6f, -r * 0.6f), center + Offset(-r * 0.6f, r * 0.6f), width, StrokeCap.Round, alpha = alpha)
    }
}

private fun DrawScope.drawLamp(s: BouncerScene, center: Offset, color: Color, lit: Float, tick: Boolean) {
    if (lit > 0f) drawCircle(color, s.lampRadius * 1.8f, center, alpha = 0.2f * lit)
    drawCircle(Color(0xFF14091F), s.lampRadius, center)
    drawCircle(color, s.lampRadius, center, alpha = 0.4f + 0.6f * lit, style = s.neon)
    drawMark(center, s.lampRadius * 0.62f, tick, color, 0.5f + 0.5f * lit)
}

private fun DrawScope.drawClub(s: BouncerScene, clock: Float, leftLit: Float, rightLit: Float, reduced: Boolean) {
    drawRect(s.wall, Offset.Zero, Size(s.w, s.horizon))
    if (!reduced) {
        drawRect(s.wallGlow, Offset.Zero, Size(s.w, s.horizon))
        drawPath(s.bricks, Color.White, alpha = 0.05f, style = s.hair)
    }
    val neonPulse = 0.78f + 0.22f * sin(clock * 2.3f)
    drawRect(s.door, s.doorTopLeft, s.doorSize)
    if (!reduced) drawRect(AccentOrchid, s.doorTopLeft, s.doorSize, alpha = 0.22f * neonPulse, style = s.neonGlow)
    drawRect(AccentOrchid, s.doorTopLeft, s.doorSize, alpha = neonPulse, style = s.neon)
    drawPath(s.crowd, Crowd)

    drawRect(s.floor, Offset(0f, s.horizon), Size(s.w, s.h - s.horizon))
    if (!reduced) drawPath(s.tiles, Color.White, alpha = 0.05f, style = s.hair)
    drawPath(s.carpet, s.carpetFill)
    drawPath(s.spill, s.spillFill)
    drawLine(Brass, Offset(s.w * 0.445f, s.horizon), Offset(s.w * 0.23f, s.h), s.d * 2f, alpha = 0.8f)
    drawLine(Brass, Offset(s.w * 0.555f, s.horizon), Offset(s.w * 0.77f, s.h), s.d * 2f, alpha = 0.8f)

    if (!reduced) {
        val sway = sin(clock * 0.7f) * 7f
        withTransform({ rotate(sway, Offset(s.w * 0.17f, 0f)) }) { drawPath(s.beamOne, s.beamLeft) }
        withTransform({ rotate(-sway, Offset(s.w * 0.83f, 0f)) }) { drawPath(s.beamTwo, s.beamRight) }
    }
    drawLamp(s, s.leftLamp, AccentRed, leftLit, tick = false)
    drawLamp(s, s.rightLamp, AccentGreen, rightLit, tick = true)
}

private fun DrawScope.drawPost(s: BouncerScene, x: Float, danger: Float) {
    drawOval(Color.Black, Offset(x - s.d * 20f, s.h - s.d * 8f), Size(s.d * 40f, s.d * 14f), alpha = 0.5f)
    withTransform({ translate(x, 0f) }) {
        drawRect(s.post, Offset(-s.d * 5f, s.postTop), Size(s.d * 10f, s.h - s.postTop))
    }
    val knob = Offset(x, s.postTop)
    drawCircle(Brass, s.d * 11f, knob)
    drawCircle(lerp(Color.White, AccentRed, danger), s.d * 4f, knob - Offset(s.d * 3f, s.d * 3f), alpha = 0.8f)
}

/** The gate: the rope warms to red as the card closes in on it. */
private fun DrawScope.drawRopeLine(s: BouncerScene, danger: Float) {
    drawPath(s.rope, lerp(RopeRed, AccentRed, danger), style = s.ropeStroke)
    drawPath(s.rope, Color.White, alpha = 0.22f, style = s.ropeShine)
    drawPost(s, s.leftPost, danger)
    drawPost(s, s.rightPost, danger)
}

private fun DrawScope.drawCardShape(card: BouncerCard, s: BouncerScene, center: Offset, r: Float) {
    val color = card.color.color
    when (card.shape) {
        BouncerShape.CIRCLE -> {
            drawCircle(color, r, center)
            drawCircle(Color.White, r, center, alpha = 0.4f, style = s.thin)
        }
        BouncerShape.SQUARE -> {
            val corner = center - Offset(r * 0.9f, r * 0.9f)
            val side = Size(r * 1.8f, r * 1.8f)
            drawRect(color, corner, side)
            drawRect(Color.White, corner, side, alpha = 0.4f, style = s.thin)
        }
        BouncerShape.TRIANGLE -> {
            s.triangle.reset()
            s.triangle.moveTo(center.x, center.y - r * 1.05f)
            s.triangle.lineTo(center.x + r * 1.1f, center.y + r * 0.85f)
            s.triangle.lineTo(center.x - r * 1.1f, center.y + r * 0.85f)
            s.triangle.close()
            drawPath(s.triangle, color)
            drawPath(s.triangle, Color.White, alpha = 0.4f, style = s.thin)
        }
    }
}

/** An ID card: its colour on the header strip and the shape, its number large on the right. */
private fun DrawScope.drawIdCard(
    card: BouncerCard,
    s: BouncerScene,
    paint: Paint,
    x: Float,
    y: Float,
    zoom: Float,
    tilt: Float,
    clock: Float,
    pulse: Float,
    pull: Float,
    reduced: Boolean
) {
    val w = s.cardW
    val h = s.cardH
    val origin = Offset(-w / 2f, -h / 2f)
    val whole = Size(w, h)
    val corner = CornerRadius(h * 0.09f)
    val tint = card.color.color
    withTransform({
        translate(x, y)
        rotate(tilt, Offset.Zero)
        scale(zoom, zoom, Offset.Zero)
    }) {
        drawRoundRect(Color.Black, origin + Offset(h * 0.03f, h * 0.08f), whole, corner, alpha = 0.45f)
        drawRoundRect(brush = s.cardFace, topLeft = origin, size = whole, cornerRadius = corner)

        drawRoundRect(tint, origin, Size(w, h * 0.26f), corner)
        drawRect(tint, origin + Offset(0f, h * 0.13f), Size(w, h * 0.13f))
        drawRoundRect(Brass, origin + Offset(w * 0.05f, h * 0.06f), Size(h * 0.2f, h * 0.14f), CornerRadius(h * 0.03f))
        drawLine(Color.Black, Offset(w * 0.02f, -h * 0.38f), Offset(w * 0.42f, -h * 0.38f), h * 0.04f, alpha = 0.45f)
        drawLine(Color.Black, Offset(w * 0.16f, -h * 0.3f), Offset(w * 0.42f, -h * 0.3f), h * 0.04f, alpha = 0.45f)

        drawRoundRect(PhotoBox, Offset(-w * 0.45f, -h * 0.16f), Size(w * 0.44f, h * 0.6f), CornerRadius(h * 0.05f))
        drawCardShape(card, s, Offset(-w * 0.23f, h * 0.14f), h * 0.21f)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = h * 0.54f
        drawContext.canvas.nativeCanvas.drawText(card.number.toString(), w * 0.24f, h * 0.33f, paint)

        drawRoundRect(Color.White, origin, whole, corner, style = s.thin, alpha = 0.25f)
        if (card.gold) {
            drawRoundRect(AccentAmber, origin, whole, corner, style = s.rimGold)
            if (!reduced) {
                val sweep = -w * 0.7f + ((clock * 0.6f) % 1f) * w * 1.4f
                clipRect(-w / 2f, -h / 2f, w / 2f, h / 2f) {
                    drawLine(Color.White, Offset(sweep - h * 0.3f, h * 0.6f), Offset(sweep + h * 0.3f, -h * 0.6f), h * 0.22f, alpha = 0.28f)
                }
            }
        }
        if (pulse > 0f) drawRoundRect(Color.White, origin, whole, corner, alpha = 0.55f * pulse)
        if (pull != 0f) drawRoundRect(if (pull > 0f) AccentGreen else AccentRed, origin, whole, corner, alpha = 0.3f * abs(pull))
    }
}

/** The lit sign's bulbs: every third one is bright, and the bright ones chase along. */
private fun DrawScope.drawBulbs(clock: Float, density: Float) {
    val gap = 14f * density
    val r = 2.2f * density
    val inset = 6f * density
    val count = (size.width / gap).toInt()
    val shift = (size.width - (count - 1) * gap) / 2f
    val tick = (clock * 7f).toInt()
    for (i in 0 until count) {
        val x = shift + i * gap
        val bright = (i + tick) % 3 == 0
        val color = if (bright) DoorTop else AccentAmber
        val alpha = if (bright) 1f else 0.35f
        drawCircle(color, r, Offset(x, inset), alpha = alpha)
        drawCircle(color, r, Offset(size.width - x, size.height - inset), alpha = alpha)
    }
}

/** Three lives; a gold card counts double either way, and some cards change colour on the way down. */
@Composable
internal fun LogicBouncerScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.BOUNCER.prefs)
    val density = LocalDensity.current.density
    val reduced = LocalReducedEffects.current
    val paused by rememberUpdatedState(LocalGamePaused.current)
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val fx = remember { BouncerFx() }
    var game by remember { mutableStateOf(BouncerGame(Random.Default)) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(BouncerGame.LIVES) }
    var rule by remember { mutableStateOf(game.rule) }
    var frame by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    val paint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    fun sync() {
        score = game.score
        lives = game.lives
        if (game.rule != rule) {
            rule = game.rule
            fx.ruleFlash = 1f
        }
        if (game.over && !over) {
            over = true
            overAt = System.currentTimeMillis()
            newBest = game.score > best.value
            best.played(game.score)
        }
    }

    fun cardLeft(card: BouncerCard, right: Boolean, side: Int) {
        fx.leave(card, right, side)
        particles.burst(fx.x, fx.y, if (right) AccentGreen else AccentRed, if (right) 16 else 24, 260f * density)
        if (right && card.gold) particles.burst(fx.x, fx.y, AccentAmber, 20, 340f * density)
        if (!right) {
            shake.kick(8f * density)
            particles.burst(heartX(fx.width, density, game.lives), 35f * density, AccentRed, 10, 120f * density)
        }
    }

    // Runs a moment past the end so the last card and its sparks finish behind the result.
    GameLoop(running = !settled) { dt ->
        fx.pose(game)
        val card = game.card
        game.step(dt)
        if (game.card !== card) cardLeft(card, right = false, side = 0)
        fx.step(dt, game)
        particles.step(dt, 320f * density)
        shake.step(dt)
        if (fx.overFor > 1f) settled = true
        frame++
        sync()
    }

    fun restart() {
        if (System.currentTimeMillis() - overAt <= 500) return
        game = BouncerGame(Random.Default)
        fx.reset()
        over = false
        settled = false
        newBest = false
        sync()
    }

    val stage = remember(reduced) {
        Modifier.fillMaxSize().clipToBounds().drawWithCache {
            val s = BouncerScene(size.width, size.height, density)
            onDrawBehind {
                frame  // redraw only, no recomposition
                fx.width = size.width
                fx.height = size.height
                val live = game
                fx.pose(live)
                val lean = (fx.offset / (size.width * 0.18f)).coerceIn(-1f, 1f)
                val leftLit = max(-lean, if (fx.goneVx < 0f) fx.flash else 0f)
                val rightLit = max(lean, if (fx.goneVx > 0f) fx.flash else 0f)
                val danger = if (over) 0f else ((live.cardAge / live.travelTime() - 0.72f) / 0.28f).coerceIn(0f, 1f)
                withTransform({
                    translate(shake.x, shake.y)
                    scale(1.02f, 1.02f, center) // so the kick never shows the window's edge
                }) {
                    drawClub(s, fx.clock, leftLit, rightLit, reduced)
                    fx.gone?.let {
                        drawIdCard(it, s, paint, fx.goneX, fx.goneY, fx.goneZoom, fx.goneTilt, fx.clock, 0f, 0f, reduced)
                    }
                    if (!over) {
                        drawIdCard(live.card, s, paint, fx.x, fx.y, fx.zoom, fx.tilt, fx.clock, fx.colorPulse, lean, reduced)
                    }
                    drawRopeLine(s, danger)
                    particles.draw(this, 1f, 0f, 0f, 3.5f * density)
                    if (fx.mark > 0f) {
                        val at = Offset(fx.markX, fx.markY)
                        val r = s.cardH * (0.5f + 0.25f * (1f - fx.mark))
                        val color = if (fx.markRight) AccentGreen else AccentRed
                        drawCircle(Color.Black, r, at, alpha = 0.55f * fx.mark)
                        drawCircle(color, r, at, alpha = fx.mark, style = s.neon)
                        drawMark(at, r * 0.75f, fx.markRight, color, fx.mark)
                    }
                    if (fx.pop > 0f) {
                        paint.color = fx.popColor.toArgb()
                        paint.alpha = (255 * min(1f, fx.pop * 2f)).toInt()
                        paint.textSize = s.cardH * 0.34f
                        val rise = (1f - fx.pop) * s.cardH * 0.7f
                        drawContext.canvas.nativeCanvas.drawText(fx.popText, fx.markX + s.cardH * 0.75f, fx.markY - rise, paint)
                    }
                    if (!reduced) drawRect(s.vignette)
                }
                if (fx.flash > 0f) drawRect(fx.flashColor, alpha = 0.26f * fx.flash)

                // Beside the score, because the pills sit top-left, or top-right in Arabic.
                drawRoundRect(
                    Color.Black,
                    Offset(heartX(size.width, density, 0) - 18f * density, 20f * density),
                    Size((BouncerGame.LIVES * 26f + 10f) * density, 30f * density),
                    CornerRadius(15f * density),
                    alpha = 0.42f
                )
                for (i in 0 until BouncerGame.LIVES) {
                    withTransform({ translate(heartX(size.width, density, i), 35f * density) }) {
                        if (i < live.lives) {
                            drawPath(s.heart, AccentRed)
                        } else {
                            drawPath(s.heart, Color.White, alpha = 0.35f, style = s.thin)
                        }
                    }
                }
            }
        }
    }

    val signShape = RoundedCornerShape(12.dp)
    val signFill = remember { Brush.verticalGradient(listOf(SignTop, SignBottom)) }
    val livesLabel = stringResource(R.string.game_bouncer_lives, lives)

    Box(
        modifier
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .fingerPosition(
                onDown = {
                    fx.dragCard = null
                    if (over) {
                        restart()
                    } else if (!paused) {
                        fx.dragCard = game.card
                        fx.downX = it.x
                        fx.fingerX = it.x
                    }
                },
                onMove = { fx.fingerX = it.x },
                onUp = {
                    val swipe = fx.fingerX - fx.downX
                    // The card may have reached the rope while the finger was down: never judge the next one.
                    if (fx.dragCard === game.card && !paused && !over) {
                        if (abs(swipe) > 24f * density) {
                            val card = game.card
                            fx.pose(game)
                            val right = game.judge(accept = swipe > 0f)
                            cardLeft(card, right, if (swipe > 0f) 1 else -1)
                            sync()
                        } else {
                            fx.snapX = swipe
                        }
                    }
                    fx.dragCard = null
                }
            )
    ) {
        Spacer(stage.semantics { contentDescription = livesLabel })
        GameScore(score.toString(), best.value)
        if (!over) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 68.dp)
                    .widthIn(min = 320.dp, max = 560.dp)
                    .background(signFill, signShape)
                    .border(2.dp, AccentAmber.copy(alpha = 0.8f), signShape)
                    .drawBehind {
                        frame  // redraw only, no recomposition
                        if (!reduced) drawBulbs(fx.clock, density)
                        if (fx.ruleFlash > 0f) drawRect(AccentAmber, alpha = 0.5f * fx.ruleFlash)
                    }
                    .padding(horizontal = 26.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(rule.text),
                    style = IpxTypography.BodyMedium.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                    color = ChipFocused,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            // The swipe sides stay physical in Arabic too: right always accepts.
            SideLabel(R.string.game_bouncer_reject, Modifier.align(AbsoluteAlignment.CenterLeft))
            SideLabel(R.string.game_bouncer_accept, Modifier.align(AbsoluteAlignment.CenterRight))
        } else {
            GameResultPanel(
                title = stringResource(R.string.game_bouncer_title),
                score = score.toString(),
                best = best.value,
                newBest = newBest,
                hint = stringResource(R.string.game_bouncer_over),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SideLabel(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        style = IpxTypography.BodyMedium,
        color = TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 170.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
