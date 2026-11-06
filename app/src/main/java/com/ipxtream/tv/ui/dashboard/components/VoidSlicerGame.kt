package com.ipxtream.tv.ui.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.AccentOrchidDeep
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class SliceUpgrade(@StringRes val title: Int, @StringRes val detail: Int, val repeats: Boolean) {
    FAST_LINE(R.string.game_slicer_up_fast, R.string.game_slicer_up_fast_detail, true),
    SHIELD(R.string.game_slicer_up_shield, R.string.game_slicer_up_shield_detail, false),
    SLOW_FIELD(R.string.game_slicer_up_slow, R.string.game_slicer_up_slow_detail, false),
    TWO_WAY(R.string.game_slicer_up_two_way, R.string.game_slicer_up_two_way_detail, false),
    EXTRA_LIFE(R.string.game_slicer_up_life, R.string.game_slicer_up_life_detail, true)
}

enum class SlicePhase { PLAYING, CHOOSING, OVER }

/** The arena as cells, so a cut can leave any shape behind. */
class VoidArena(val cols: Int, val rows: Int) {
    private val open = BooleanArray(cols * rows) { true }
    var removed = 0
        private set

    fun isOpen(col: Int, row: Int): Boolean =
        col in 0 until cols && row in 0 until rows && open[row * cols + col]

    /** The unbroken run of open cells through ([col], [row]), end to end, in order. */
    fun span(col: Int, row: Int, horizontal: Boolean): List<Int> {
        val stepCol = if (horizontal) 1 else 0
        val stepRow = 1 - stepCol
        var c = col
        var r = row
        while (isOpen(c - stepCol, r - stepRow)) { c -= stepCol; r -= stepRow }
        val cells = ArrayList<Int>()
        while (isOpen(c, r)) { cells += r * cols + c; c += stepCol; r += stepRow }
        return cells
    }

    /** Closes [line] and every open cell no ball can reach, and returns how many cells went. */
    fun seal(line: List<Int>, ballCells: List<Int>): Int {
        for (cell in line) open[cell] = false
        val reached = BooleanArray(open.size)
        val stack = ArrayDeque<Int>()
        for (cell in ballCells) if (open[cell] && !reached[cell]) { reached[cell] = true; stack.addLast(cell) }
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            val col = cell % cols
            val row = cell / cols
            for ((c, r) in listOf(col - 1 to row, col + 1 to row, col to row - 1, col to row + 1)) {
                val next = r * cols + c
                if (isOpen(c, r) && !reached[next]) { reached[next] = true; stack.addLast(next) }
            }
        }
        var gone = line.size
        for (cell in open.indices) if (open[cell] && !reached[cell]) { open[cell] = false; gone++ }
        removed += gone
        return gone
    }
}

/** Void Slicer: cut off ball-free parts of the arena; 75% gone clears the round and earns an upgrade. */
class SlicerGame(private val random: Random) {
    var arena = VoidArena(COLS, ROWS)
        private set
    var round = 1
        private set
    var lives = 3
        private set
    var score = 0
        private set
    var phase = SlicePhase.PLAYING
        private set
    var offers: List<SliceUpgrade> = emptyList()
        private set
    private val owned = ArrayList<SliceUpgrade>()
    var shield = false
        private set
    /** The ball that just spent the shield; it can't hit the line again until it has left it. */
    private var shieldedBall = -1

    var ballCount = 0
        private set
    val ballX = FloatArray(MAX_BALLS)
    val ballY = FloatArray(MAX_BALLS)
    private val ballVx = FloatArray(MAX_BALLS)
    private val ballVy = FloatArray(MAX_BALLS)

    /** The growing line: one arm from a wall, or two from the finger with [SliceUpgrade.TWO_WAY]. */
    var arms: List<List<Int>> = emptyList()
        private set
    var grown = 0f
        private set

    init { placeBalls() }

    val cleared: Float get() = arena.removed.toFloat() / (COLS * ROWS)
    val lineActive: Boolean get() = arms.isNotEmpty()

    private fun placeBalls() {
        ballCount = min(1 + round, MAX_BALLS)
        val speed = 9f + round * 1.2f
        for (i in 0 until ballCount) {
            ballX[i] = 4f + random.nextFloat() * (COLS - 8)
            ballY[i] = 3f + random.nextFloat() * (ROWS - 6)
            ballVx[i] = if (random.nextBoolean()) speed else -speed
            ballVy[i] = if (random.nextBoolean()) speed * 0.7f else -speed * 0.7f
        }
    }

    private fun lineSpeed(): Float = 14f * (1f + 0.35f * owned.count { it == SliceUpgrade.FAST_LINE })

    /** Starts a line through a cell; ignored on a closed cell or while one is growing. */
    fun swipe(col: Int, row: Int, horizontal: Boolean) {
        if (phase != SlicePhase.PLAYING || lineActive || !arena.isOpen(col, row)) return
        val span = arena.span(col, row, horizontal)
        val at = span.indexOf(row * COLS + col)
        arms = when {
            SliceUpgrade.TWO_WAY in owned -> listOf(span.subList(0, at + 1).reversed(), span.subList(at, span.size))
            at < span.size / 2 -> listOf(span)
            else -> listOf(span.reversed())
        }
        grown = 0f
    }

    fun step(dt: Float) {
        if (phase != SlicePhase.PLAYING) return
        moveBalls(dt)
        if (!lineActive || phase != SlicePhase.PLAYING) return
        grown += lineSpeed() * dt
        if (arms.all { grown >= it.size }) finishLine()
    }

    private fun lineCell(cell: Int): Boolean = arms.any { arm -> arm.indexOf(cell).let { it in 0..grown.toInt() } }

    private fun moveBalls(dt: Float) {
        val slow = SliceUpgrade.SLOW_FIELD in owned && lineActive
        for (i in 0 until ballCount) {
            val rate = if (slow && nearLine(i)) 0.45f else 1f
            val travel = (abs(ballVx[i]) + abs(ballVy[i])) * dt * rate
            val parts = ceil(travel / 0.4f).toInt().coerceAtLeast(1)
            repeat(parts) { moveBall(i, dt * rate / parts) }
        }
    }

    private fun nearLine(i: Int): Boolean = arms.any { arm ->
        arm.any { cell ->
            val dx = (cell % COLS) + 0.5f - ballX[i]
            val dy = (cell / COLS) + 0.5f - ballY[i]
            sqrt(dx * dx + dy * dy) < 4f
        }
    }

    private fun moveBall(i: Int, dt: Float) {
        val nextX = ballX[i] + ballVx[i] * dt
        if (arena.isOpen(nextX.toInt(), ballY[i].toInt()) && nextX >= 0f) ballX[i] = nextX else ballVx[i] = -ballVx[i]
        val nextY = ballY[i] + ballVy[i] * dt
        if (arena.isOpen(ballX[i].toInt(), nextY.toInt()) && nextY >= 0f) ballY[i] = nextY else ballVy[i] = -ballVy[i]
        if (!lineActive) return
        val cell = ballY[i].toInt() * COLS + ballX[i].toInt()
        if (!lineCell(cell)) {
            if (shieldedBall == i) shieldedBall = -1
            return
        }
        if (shieldedBall == i) return
        if (shield) {
            shield = false
            shieldedBall = i
            ballVx[i] = -ballVx[i]
            ballVy[i] = -ballVy[i]
        } else {
            breakLine()
        }
    }

    private fun breakLine() {
        arms = emptyList()
        shieldedBall = -1
        lives--
        if (lives <= 0) phase = SlicePhase.OVER
    }

    private fun finishLine() {
        val line = arms.flatten().distinct()
        val balls = (0 until ballCount).map { ballY[it].toInt() * COLS + ballX[it].toInt() }
        // The line's last stretch can land on a ball; sealing there would wall the ball in.
        if (balls.any { it in line }) {
            if (shield || shieldedBall >= 0) {
                shield = false
                shieldedBall = -1
                arms = emptyList()
            } else {
                breakLine()
            }
            return
        }
        arms = emptyList()
        shieldedBall = -1
        score += arena.seal(line, balls)
        if (cleared >= TARGET) {
            offers = SliceUpgrade.entries.filter { it.repeats || it !in owned }.shuffled(random).take(3)
            phase = SlicePhase.CHOOSING
        }
    }

    fun choose(upgrade: SliceUpgrade) {
        if (phase != SlicePhase.CHOOSING) return
        owned += upgrade
        if (upgrade == SliceUpgrade.EXTRA_LIFE) lives++
        round++
        arena = VoidArena(COLS, ROWS)
        shield = SliceUpgrade.SHIELD in owned
        placeBalls()
        phase = SlicePhase.PLAYING
    }

    companion object {
        const val COLS = 40
        const val ROWS = 20
        const val TARGET = 0.75f
        const val MAX_BALLS = 8
    }
}

private val HullTop = Color(0xFF171B46)
private val HullBottom = Color(0xFF0D1030)
private val VoidCore = Color(0xFF1B0B3E)
private val VoidEdge = Color(0xFF05030F)
private val FieldLight = Color(0xFF9CF3FF)
private val FieldColors = listOf(Color(0xFF17B6C4), Color(0xFF1E7FD6), Color(0xFF4A4FE0))
private val CliffColor = Color(0xFF0A2A6B)
private val ChipColor = Color(0xFF0D3A73)
private val ShieldCyan = Color(0xFF5CF2E3)
private val RimLight = Color(0xFF7D8FC9)
private val RimDark = Color(0xFF2A3263)
private val CardTop = Color(0xFF243A86)
private val CardBottom = Color(0xFF12143F)

// dp reserved around the arena: the score capsule, the progress bar, the side rims.
private const val TOP_BAND = 82f
private const val BOTTOM_BAND = 22f
private const val SIDE_BAND = 16f
private const val TRAIL = 7
private const val POP_SECONDS = 0.9f
private const val EFFECT_SECONDS = 1.4f
private const val MAX_LIFE_ICONS = 8

private val VOID_TWINKLES = IntArray(18) { (it * 137 + 41) % (SlicerGame.COLS * SlicerGame.ROWS) }

private inline fun forEachRun(row: Int, open: (Int, Int) -> Boolean, draw: (Int, Int) -> Unit) {
    var col = 0
    while (col < SlicerGame.COLS) {
        if (!open(col, row)) { col++; continue }
        val from = col
        while (col < SlicerGame.COLS && open(col, row)) col++
        draw(from, col)
    }
}

private fun centerX(cell: Int): Float = cell % SlicerGame.COLS + 0.5f
private fun centerY(cell: Int): Float = cell / SlicerGame.COLS + 0.5f

/** Everything that only decorates a run: flashes, pops and ball trails. Plain fields, read while drawing. */
private class SlicerFx {
    var clock = 0f
    var left = 0f
    var sealFlash = 0f
    var breakFlash = 0f
    var guardFlash = 0f
    var popAge = POP_SECONDS
    var popText = ""
    var popX = 0f
    var popY = 0f
    var shownClear = 0f
    var trailHead = 0
    val trailX = FloatArray(SlicerGame.MAX_BALLS * TRAIL)
    val trailY = FloatArray(SlicerGame.MAX_BALLS * TRAIL)
    val sealed = BooleanArray(SlicerGame.COLS * SlicerGame.ROWS)
    val iconPath = Path()
    private val wasOpen = BooleanArray(SlicerGame.COLS * SlicerGame.ROWS) { true }

    fun startArena(game: SlicerGame) {
        wasOpen.fill(true)
        sealed.fill(false)
        sealFlash = 0f
        shownClear = 0f
        popAge = POP_SECONDS
        for (i in 0 until game.ballCount) for (t in 0 until TRAIL) {
            trailX[i * TRAIL + t] = game.ballX[i]
            trailY[i * TRAIL + t] = game.ballY[i]
        }
    }

    /** Finds the cells that just went, sets their flash and throws embers from them. */
    fun markSealed(arena: VoidArena, gain: Int, particles: GameParticles) {
        val stride = max(1, gain / 24)
        var count = 0
        var sumX = 0f
        var sumY = 0f
        for (cell in sealed.indices) {
            val open = arena.isOpen(cell % SlicerGame.COLS, cell / SlicerGame.COLS)
            sealed[cell] = wasOpen[cell] && !open
            wasOpen[cell] = open
            if (!sealed[cell]) continue
            sumX += centerX(cell)
            sumY += centerY(cell)
            if (count++ % stride == 0) {
                particles.burst(centerX(cell), centerY(cell), if (count % 2 == 0) AccentAmber else Accent, 2, 7f, 0.9f)
            }
        }
        if (count == 0) return
        popX = sumX / count
        popY = sumY / count
        popText = "+$gain"
        popAge = 0f
        sealFlash = 1f
        left = EFFECT_SECONDS
    }

    fun step(dt: Float, game: SlicerGame, reduced: Boolean) {
        clock += dt
        left = max(0f, left - dt)
        sealFlash = max(0f, sealFlash - dt * 1.8f)
        breakFlash = max(0f, breakFlash - dt * 3f)
        guardFlash = max(0f, guardFlash - dt * 3f)
        popAge += dt
        shownClear += (game.cleared - shownClear) * min(1f, dt * 8f)
        if (reduced || game.phase != SlicePhase.PLAYING) return
        trailHead = (trailHead + 1) % TRAIL
        for (i in 0 until game.ballCount) {
            trailX[i * TRAIL + trailHead] = game.ballX[i]
            trailY[i * TRAIL + trailHead] = game.ballY[i]
        }
    }
}

private fun burstAlong(arm: List<Int>, grown: Float, color: Color, particles: GameParticles) {
    var i = 0
    val last = min(grown.toInt(), arm.lastIndex)
    while (i <= last) {
        particles.burst(centerX(arm[i]), centerY(arm[i]), color, 3, 9f, 0.5f)
        i += 2
    }
}

/** The hull around the arena and the starry void under it. Drawn once per size, in its own layer. */
private fun DrawScope.drawSlicerBackdrop(start: Offset, cell: Float, reduced: Boolean) {
    val unit = density
    val w = cell * SlicerGame.COLS
    val h = cell * SlicerGame.ROWS
    drawGameBackdrop(HullTop, HullBottom)
    val bloom = size.maxDimension * 0.42f
    drawCircle(
        Brush.radialGradient(listOf(AccentOrchidDeep.copy(alpha = 0.5f), Color.Transparent), Offset(start.x, start.y + h), bloom),
        bloom,
        Offset(start.x, start.y + h)
    )
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF1E7FD6).copy(alpha = 0.4f), Color.Transparent), Offset(start.x + w, start.y), bloom),
        bloom,
        Offset(start.x + w, start.y)
    )

    val plate = 72f * unit
    val seams = Path()
    val rivets = ArrayList<Offset>()
    for (i in 0..(size.width / plate).toInt()) {
        seams.moveTo(i * plate, 0f)
        seams.lineTo(i * plate, size.height)
        for (j in 0..(size.height / plate).toInt()) rivets += Offset(i * plate + 6f * unit, j * plate + 6f * unit)
    }
    for (j in 0..(size.height / plate).toInt()) {
        seams.moveTo(0f, j * plate)
        seams.lineTo(size.width, j * plate)
    }
    drawPath(seams, Color.White.copy(alpha = 0.05f), style = Stroke(unit))
    drawPoints(rivets, PointMode.Points, Color.White.copy(alpha = 0.14f), 2.5f * unit, StrokeCap.Round)

    // Power conduits feeding the arena from every side.
    for (i in 1..3) {
        val y = start.y + h * i / 4f
        for (x in listOf(0f to start.x, start.x + w to size.width)) {
            drawLine(FieldLight.copy(alpha = 0.14f), Offset(x.first, y), Offset(x.second, y), 7f * unit)
            drawLine(FieldLight.copy(alpha = 0.55f), Offset(x.first, y), Offset(x.second, y), 1.5f * unit)
        }
        drawCircle(FieldLight, 3.5f * unit, Offset(start.x - 12f * unit, y))
        drawCircle(FieldLight, 3.5f * unit, Offset(start.x + w + 12f * unit, y))
    }
    for (i in 1..4) {
        val x = start.x + w * i / 5f
        drawLine(FieldLight.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, start.y), 6f * unit)
        drawLine(FieldLight.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, start.y), 1.2f * unit)
    }

    val corner = CornerRadius(6f * unit)
    val middle = Offset(start.x + w / 2f, start.y + h / 2f)
    drawRoundRect(
        brush = Brush.radialGradient(listOf(VoidCore, VoidEdge), middle, w * 0.6f),
        topLeft = start,
        size = Size(w, h),
        cornerRadius = corner
    )
    clipRect(start.x, start.y, start.x + w, start.y + h) {
        val cloud = h * 0.7f
        val first = Offset(start.x + w * 0.25f, start.y + h * 0.7f)
        val second = Offset(start.x + w * 0.72f, start.y + h * 0.3f)
        drawCircle(Brush.radialGradient(listOf(AccentOrchidDeep.copy(alpha = 0.45f), Color.Transparent), first, cloud), cloud, first)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF0E5FA8).copy(alpha = 0.35f), Color.Transparent), second, cloud), cloud, second)
        val random = Random(11)
        val small = List(if (reduced) 70 else 160) { Offset(start.x + random.nextFloat() * w, start.y + random.nextFloat() * h) }
        val large = List(30) { Offset(start.x + random.nextFloat() * w, start.y + random.nextFloat() * h) }
        drawPoints(small, PointMode.Points, Color.White.copy(alpha = 0.7f), 1.5f * unit, StrokeCap.Round)
        drawPoints(large, PointMode.Points, AccentOrchid, 2.8f * unit, StrokeCap.Round)
    }

    val rim = 4f * unit
    val grown = Offset(rim / 2f, rim / 2f)
    drawRoundRect(
        color = FieldLight.copy(alpha = 0.2f),
        topLeft = start - grown * 3f,
        size = Size(w + rim * 3f, h + rim * 3f),
        cornerRadius = corner,
        style = Stroke(rim * 2f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(RimLight, RimDark), start.y, start.y + h),
        topLeft = start - grown,
        size = Size(w + rim, h + rim),
        cornerRadius = corner,
        style = Stroke(rim)
    )
    val arm = 22f * unit
    val left = start.x - rim
    val right = start.x + w + rim
    val top = start.y - rim
    val bottom = start.y + h + rim
    for (x in listOf(left to arm, right to -arm)) for (y in listOf(top to arm, bottom to -arm)) {
        drawLine(AccentAmber, Offset(x.first, y.first), Offset(x.first + x.second, y.first), 3f * unit, StrokeCap.Round)
        drawLine(AccentAmber, Offset(x.first, y.first), Offset(x.first, y.first + y.second), 3f * unit, StrokeCap.Round)
    }
}

/** The circuit field still in play. Built from paths, and only again after a cut: it has its own layer. */
private fun DrawScope.drawOpenField(arena: VoidArena, start: Offset, cell: Float) {
    val field = Path()
    val cliff = Path()
    val edges = Path()
    val traces = Path()
    val tiles = Path()
    val pads = ArrayList<Offset>()
    val cols = arena.cols
    val rows = arena.rows
    for (row in 0 until rows) {
        var runFrom = 0
        for (col in 0 until cols) {
            if (!arena.isOpen(col, row)) continue
            if (!arena.isOpen(col - 1, row)) runFrom = col
            val left = start.x + col * cell
            val top = start.y + row * cell
            val right = left + cell
            val bottom = top + cell
            val midX = left + cell / 2f
            val midY = top + cell / 2f
            if (!arena.isOpen(col + 1, row)) field.addRect(Rect(start.x + runFrom * cell, top, right, bottom))
            if (!arena.isOpen(col, row - 1)) { edges.moveTo(left, top); edges.lineTo(right, top) }
            if (!arena.isOpen(col - 1, row)) { edges.moveTo(left, top); edges.lineTo(left, bottom) }
            if (!arena.isOpen(col + 1, row)) { edges.moveTo(right, top); edges.lineTo(right, bottom) }
            if (!arena.isOpen(col, row + 1)) {
                edges.moveTo(left, bottom)
                edges.lineTo(right, bottom)
                if (row < rows - 1) cliff.addRect(Rect(left, bottom, right, bottom + cell * 0.4f))
            }
            if (col % 4 == 0) { tiles.moveTo(left, top); tiles.lineTo(left, bottom) }
            if (row % 4 == 0) { tiles.moveTo(left, top); tiles.lineTo(right, top) }
            when (((col * 31) xor (row * 17)) % 6) {
                0 -> { traces.moveTo(left, midY); traces.lineTo(right, midY) }
                1 -> { traces.moveTo(midX, top); traces.lineTo(midX, bottom) }
                2 -> { traces.moveTo(left, midY); traces.lineTo(midX, midY); traces.lineTo(midX, bottom) }
                3 -> { traces.moveTo(midX, top); traces.lineTo(midX, midY); traces.lineTo(right, midY) }
                4 -> pads += Offset(midX, midY)
            }
        }
    }
    drawPath(cliff, CliffColor)
    drawPath(
        field,
        Brush.linearGradient(FieldColors, start, start + Offset(cell * cols, cell * rows))
    )
    drawPath(tiles, Color.Black.copy(alpha = 0.12f), style = Stroke(cell * 0.05f))
    for (row in 2 until rows - 1 step 5) for (col in 3 until cols - 1 step 8) {
        if (!arena.isOpen(col, row) || !arena.isOpen(col + 1, row) ||
            !arena.isOpen(col, row + 1) || !arena.isOpen(col + 1, row + 1)) continue
        val at = start + Offset(col * cell + cell * 0.2f, row * cell + cell * 0.2f)
        val chip = Size(cell * 1.6f, cell * 1.6f)
        drawRoundRect(color = ChipColor, topLeft = at, size = chip, cornerRadius = CornerRadius(cell * 0.18f))
        drawRoundRect(
            color = FieldLight.copy(alpha = 0.5f),
            topLeft = at,
            size = chip,
            cornerRadius = CornerRadius(cell * 0.18f),
            style = Stroke(cell * 0.06f)
        )
    }
    drawPath(traces, FieldLight.copy(alpha = 0.4f), style = Stroke(cell * 0.07f))
    drawPoints(pads, PointMode.Points, FieldLight.copy(alpha = 0.85f), cell * 0.2f, StrokeCap.Round)
    drawPath(edges, FieldLight.copy(alpha = 0.2f), style = Stroke(cell * 0.7f, cap = StrokeCap.Round))
    drawPath(edges, ShieldCyan.copy(alpha = 0.55f), style = Stroke(cell * 0.28f, cap = StrokeCap.Round))
    drawPath(edges, Color.White, style = Stroke(cell * 0.08f, cap = StrokeCap.Round))
}

private fun DrawScope.drawShieldIcon(at: Offset, radius: Float, color: Color, path: Path) {
    path.reset()
    path.moveTo(at.x, at.y - radius)
    path.lineTo(at.x + radius * 0.85f, at.y - radius * 0.62f)
    path.lineTo(at.x + radius * 0.85f, at.y + radius * 0.15f)
    path.lineTo(at.x + radius * 0.45f, at.y + radius * 0.68f)
    path.lineTo(at.x, at.y + radius)
    path.lineTo(at.x - radius * 0.45f, at.y + radius * 0.68f)
    path.lineTo(at.x - radius * 0.85f, at.y + radius * 0.15f)
    path.lineTo(at.x - radius * 0.85f, at.y - radius * 0.62f)
    path.close()
    drawPath(path, color)
    drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(radius * 0.12f))
}

private fun DrawScope.drawLifeIcon(at: Offset, radius: Float) {
    drawCircle(Accent.copy(alpha = 0.22f), radius * 2f, at)
    rotate(45f, at) {
        drawRect(Accent, at - Offset(radius, radius), Size(radius * 2f, radius * 2f))
        drawRect(ChipFocused, at - Offset(radius * 0.4f, radius * 0.4f), Size(radius * 0.8f, radius * 0.8f))
    }
}

/** What moves: everything here is redrawn each frame, so it stays a few plain shapes. */
private fun DrawScope.drawSlicerActors(
    game: SlicerGame,
    fx: SlicerFx,
    particles: GameParticles,
    start: Offset,
    cell: Float,
    reduced: Boolean,
    popPaint: android.graphics.Paint
) {
    val arena = game.arena
    val unit = density
    val w = cell * SlicerGame.COLS
    val h = cell * SlicerGame.ROWS
    val open = { col: Int, row: Int -> arena.isOpen(col, row) }

    if (!reduced) {
        for (i in VOID_TWINKLES.indices) {
            val at = VOID_TWINKLES[i]
            if (arena.isOpen(at % SlicerGame.COLS, at / SlicerGame.COLS)) continue
            val shine = 0.45f + 0.45f * sin(fx.clock * 2.6f + i)
            drawCircle(FieldLight.copy(alpha = shine), cell * 0.1f, start + Offset(centerX(at) * cell, centerY(at) * cell))
        }
        val sweep = ((fx.clock * 6f) % (SlicerGame.ROWS + 10)).toInt()
        if (sweep < SlicerGame.ROWS) forEachRun(sweep, open) { from, to ->
            drawRect(Color.White.copy(alpha = 0.14f), start + Offset(from * cell, sweep * cell), Size((to - from) * cell, cell))
        }
    }

    if (fx.sealFlash > 0f) {
        val tint = AccentAmber.copy(alpha = fx.sealFlash * 0.8f)
        for (row in 0 until arena.rows) forEachRun(row, { col, r -> fx.sealed[r * SlicerGame.COLS + col] }) { from, to ->
            drawRect(tint, start + Offset(from * cell, row * cell), Size((to - from) * cell, cell))
        }
    }

    for (arm in game.arms) {
        val lastAt = min(game.grown.toInt(), arm.lastIndex)
        val base = start + Offset(centerX(arm[0]) * cell, centerY(arm[0]) * cell)
        val half = if (arm.size > 1) {
            Offset((centerX(arm[1]) - centerX(arm[0])) * cell / 2f, (centerY(arm[1]) - centerY(arm[0])) * cell / 2f)
        } else {
            Offset.Zero
        }
        val from = base - half
        val tip = start + Offset(centerX(arm[lastAt]) * cell, centerY(arm[lastAt]) * cell) + half
        val goal = start + Offset(centerX(arm.last()) * cell, centerY(arm.last()) * cell) + half
        val glow = if (game.shield) ShieldCyan else Accent
        drawLine(ChipFocused.copy(alpha = 0.3f), tip, goal, cell * 0.08f)
        drawLine(glow.copy(alpha = 0.3f), from, tip, cell * 0.95f, StrokeCap.Round)
        drawLine(glow, from, tip, cell * 0.5f, StrokeCap.Round)
        drawLine(ChipFocused, from, tip, cell * 0.2f, StrokeCap.Round)
        drawCircle(glow.copy(alpha = 0.35f), cell * (1.05f + 0.15f * sin(fx.clock * 30f)), tip)
        drawCircle(Color.White, cell * 0.32f, tip)
    }

    val radius = cell * 0.55f
    for (i in 0 until game.ballCount) {
        val color = if (i % 2 == 0) Accent else AccentRed
        val at = start + Offset(game.ballX[i] * cell, game.ballY[i] * cell)
        if (!reduced) for (t in 1 until TRAIL) {
            val index = i * TRAIL + (fx.trailHead - t + TRAIL) % TRAIL
            val fade = 1f - t.toFloat() / TRAIL
            drawCircle(
                color.copy(alpha = fade * 0.3f),
                radius * (0.4f + 0.6f * fade),
                start + Offset(fx.trailX[index] * cell, fx.trailY[index] * cell)
            )
        }
        drawCircle(color.copy(alpha = 0.25f), radius * (1.9f + 0.2f * sin(fx.clock * 9f + i)), at)
        drawCircle(color, radius, at)
        drawCircle(AccentAmber, radius * 0.68f, at)
        drawCircle(Color.White.copy(alpha = 0.92f), radius * 0.3f, at - Offset(radius * 0.2f, radius * 0.2f))
    }

    particles.draw(this, cell, start.x, start.y, cell * 0.22f)

    if (fx.popAge < POP_SECONDS) {
        val age = fx.popAge / POP_SECONDS
        popPaint.textSize = cell * 2f
        popPaint.color = ChipFocused.copy(alpha = 1f - age * age).toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            fx.popText,
            start.x + fx.popX * cell,
            start.y + (fx.popY - age * 2.5f) * cell,
            popPaint
        )
    }

    if (fx.guardFlash > 0f) drawRect(ShieldCyan.copy(alpha = fx.guardFlash * 0.28f), start, Size(w, h))
    if (fx.breakFlash > 0f) drawRect(AccentRed.copy(alpha = fx.breakFlash * 0.38f))

    // Lives sit opposite the pills, which follow the layout direction.
    val rtl = layoutDirection == LayoutDirection.Rtl
    val edge = if (rtl) 28f * unit else size.width - 28f * unit
    val pace = if (rtl) 22f * unit else -22f * unit
    val icons = min(game.lives, MAX_LIFE_ICONS)
    for (i in 0 until icons) drawLifeIcon(Offset(edge + pace * i, 34f * unit), 5f * unit)
    if (game.shield) drawShieldIcon(Offset(edge + pace * icons, 34f * unit), 10f * unit, ShieldCyan, fx.iconPath)

    val barTop = start.y + h + 10f * unit
    val barHeight = 7f * unit
    val barRound = CornerRadius(barHeight / 2f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.5f),
        topLeft = Offset(start.x, barTop),
        size = Size(w, barHeight),
        cornerRadius = barRound
    )
    val fill = w * (fx.shownClear / SlicerGame.TARGET).coerceIn(0f, 1f)
    if (fill > 0f) {
        val fillLeft = if (rtl) start.x + w - fill else start.x
        drawRoundRect(color = Accent, topLeft = Offset(fillLeft, barTop), size = Size(fill, barHeight), cornerRadius = barRound)
        drawRoundRect(
            color = AccentAmber,
            topLeft = Offset(fillLeft, barTop + unit),
            size = Size(fill, barHeight * 0.35f),
            cornerRadius = barRound
        )
    }
}

private fun DrawScope.drawUpgradeIcon(upgrade: SliceUpgrade) {
    val s = size.minDimension
    val c = center
    val line = s * 0.08f
    when (upgrade) {
        SliceUpgrade.FAST_LINE -> for (shift in listOf(-0.16f, 0.12f)) {
            val x = c.x + s * shift
            drawLine(AccentAmber, Offset(x - s * 0.1f, c.y - s * 0.24f), Offset(x + s * 0.12f, c.y), line, StrokeCap.Round)
            drawLine(AccentAmber, Offset(x + s * 0.12f, c.y), Offset(x - s * 0.1f, c.y + s * 0.24f), line, StrokeCap.Round)
        }
        SliceUpgrade.SHIELD -> drawShieldIcon(c, s * 0.38f, ShieldCyan, Path())
        SliceUpgrade.SLOW_FIELD -> {
            drawCircle(FieldLight.copy(alpha = 0.35f), s * 0.42f, c, style = Stroke(line * 0.6f))
            drawCircle(FieldLight.copy(alpha = 0.7f), s * 0.28f, c, style = Stroke(line * 0.6f))
            drawCircle(Accent, s * 0.13f, c)
        }
        SliceUpgrade.TWO_WAY -> {
            val left = c.x - s * 0.36f
            val right = c.x + s * 0.36f
            val head = s * 0.14f
            drawLine(Accent, Offset(left, c.y), Offset(right, c.y), line, StrokeCap.Round)
            drawLine(ChipFocused, Offset(left, c.y), Offset(left + head, c.y - head), line, StrokeCap.Round)
            drawLine(ChipFocused, Offset(left, c.y), Offset(left + head, c.y + head), line, StrokeCap.Round)
            drawLine(ChipFocused, Offset(right, c.y), Offset(right - head, c.y - head), line, StrokeCap.Round)
            drawLine(ChipFocused, Offset(right, c.y), Offset(right - head, c.y + head), line, StrokeCap.Round)
        }
        SliceUpgrade.EXTRA_LIFE -> drawLifeIcon(c, s * 0.2f)
    }
}

@Composable
private fun UpgradeCard(upgrade: SliceUpgrade, onPick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(184.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)), shape)
            .border(1.5.dp, FieldLight.copy(alpha = 0.6f), shape)
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(Modifier.size(44.dp)) { drawUpgradeIcon(upgrade) }
        Text(
            stringResource(upgrade.title),
            style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ChipFocused,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(upgrade.detail),
            style = IpxTypography.BodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** A swipe picks the line's direction; it grows from where the finger went down. */
@Composable
internal fun VoidSlicerScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.SLICER.prefs)
    val density = LocalDensity.current.density
    val reduced = LocalReducedEffects.current
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    var game by remember { mutableStateOf(SlicerGame(Random.Default)) }
    val fx = remember { SlicerFx().apply { startArena(game) } }
    val popPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }
    var phase by remember { mutableStateOf(SlicePhase.PLAYING) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var cleared by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var arenaVersion by remember { mutableIntStateOf(0) }
    var effects by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var choosingAt by remember { mutableStateOf(0L) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var downAt by remember { mutableStateOf(Offset.Zero) }
    var armed by remember { mutableStateOf(false) }
    var swiped by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    fun cellSize() = min(
        (canvas.width - 2f * SIDE_BAND * density) / SlicerGame.COLS,
        (canvas.height - (TOP_BAND + BOTTOM_BAND) * density) / SlicerGame.ROWS
    )
    fun origin(): Offset {
        val cell = cellSize()
        val room = canvas.height - (TOP_BAND + BOTTOM_BAND) * density
        return Offset(
            (canvas.width - cell * SlicerGame.COLS) / 2f,
            TOP_BAND * density + (room - cell * SlicerGame.ROWS) / 2f
        )
    }

    fun sync() {
        score = game.score
        lives = game.lives
        cleared = (game.cleared * 100f).toInt()
        if (phase != SlicePhase.OVER && game.phase == SlicePhase.OVER) {
            overAt = System.currentTimeMillis()
            newBest = game.score > best.value
            best.played(game.score)
        }
        if (phase != SlicePhase.CHOOSING && game.phase == SlicePhase.CHOOSING) choosingAt = System.currentTimeMillis()
        phase = game.phase
    }

    fun newArena() {
        fx.startArena(game)
        arenaVersion++
        sync()
    }

    // A swipe that starts in the void or the margin slides along its direction to the first open cell.
    fun cut(delta: Offset) {
        val cell = cellSize()
        if (cell <= 0f) return
        val horizontal = abs(delta.x) > abs(delta.y)
        val at = (downAt - origin()) / cell
        var col = at.x.toInt().coerceIn(0, SlicerGame.COLS - 1)
        var row = at.y.toInt().coerceIn(0, SlicerGame.ROWS - 1)
        val stepCol = if (!horizontal) 0 else if (delta.x > 0f) 1 else -1
        val stepRow = if (horizontal) 0 else if (delta.y > 0f) 1 else -1
        while (col in 0 until SlicerGame.COLS && row in 0 until SlicerGame.ROWS && !game.arena.isOpen(col, row)) {
            col += stepCol
            row += stepRow
        }
        game.swipe(col, row, horizontal)
        if (game.lineActive) started = true
    }

    fun pick(upgrade: SliceUpgrade) {
        // A tap meant for the last cut mustn't land on a card that has only just appeared.
        if (System.currentTimeMillis() - choosingAt < 400) return
        game.choose(upgrade)
        newArena()
        runCatching { gameFocusRequester.requestFocus() }
    }

    // Runs on after a run ends until the embers and flashes have died, then stops.
    GameLoop(running = phase == SlicePhase.PLAYING || effects) { dt ->
        val armsBefore = game.arms
        val grownBefore = game.grown
        val shieldBefore = game.shield
        val livesBefore = game.lives
        val removedBefore = game.arena.removed
        game.step(dt)
        val gain = game.arena.removed - removedBefore
        if (gain > 0) {
            fx.markSealed(game.arena, gain, particles)
            shake.kick(3f)
            arenaVersion++
        } else if (armsBefore.isNotEmpty() && !game.lineActive) {
            val broke = game.lives < livesBefore
            for (arm in armsBefore) burstAlong(arm, grownBefore, if (broke) AccentRed else ShieldCyan, particles)
            if (broke) fx.breakFlash = 1f else fx.guardFlash = 1f
            fx.left = EFFECT_SECONDS
            shake.kick(if (broke) 7f else 3f)
        } else if (shieldBefore && !game.shield) {
            fx.guardFlash = 1f
            shake.kick(3f)
        }
        if (frame % 3 == 0) for (arm in game.arms) {
            val tip = arm[min(game.grown.toInt(), arm.lastIndex)]
            particles.burst(centerX(tip), centerY(tip), AccentAmber, 1, 6f, 0.35f)
        }
        particles.step(dt, -5f)
        shake.step(dt)
        fx.step(dt, game, reduced)
        effects = fx.left > 0f
        frame++
        sync()
    }

    Box(
        modifier
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .onSizeChanged { canvas = it }
            .fingerPosition(
                onDown = { position ->
                    armed = phase == SlicePhase.PLAYING
                    if (phase == SlicePhase.OVER && System.currentTimeMillis() - overAt > 500) {
                        game = SlicerGame(Random.Default)
                        newArena()
                    }
                    downAt = position
                    swiped = false
                },
                onMove = { position ->
                    val delta = position - downAt
                    if (armed && !swiped && (abs(delta.x) > 12f * density || abs(delta.y) > 12f * density)) {
                        swiped = true
                        cut(delta)
                    }
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val cell = cellSize()
            if (cell <= 0f) return@Canvas
            drawSlicerBackdrop(origin(), cell, reduced)
        }

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                frame  // moves the layer, no recomposition
                translationX = shake.x * density
                translationY = shake.y * density
            }
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                arenaVersion  // the field only changes on a cut
                val cell = cellSize()
                if (cell <= 0f) return@Canvas
                drawOpenField(game.arena, origin(), cell)
            }
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                frame  // redraw only, no recomposition
                val cell = cellSize()
                if (cell <= 0f) return@Canvas
                drawSlicerActors(game, fx, particles, origin(), cell, reduced, popPaint)
            }
        }

        GameScore(
            score.toString(),
            best.value,
            extra = stringResource(R.string.game_slicer_status, game.round, lives, cleared)
        )

        if (phase == SlicePhase.PLAYING && !started) {
            Text(
                stringResource(R.string.game_slicer_hint),
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 34.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        if (phase == SlicePhase.CHOOSING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.game_slicer_choose),
                        style = IpxTypography.HeadlineMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        game.offers.forEach { upgrade -> UpgradeCard(upgrade) { pick(upgrade) } }
                    }
                }
            }
        }

        if (phase == SlicePhase.OVER) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                GameResultPanel(
                    title = stringResource(R.string.game_slicer_title),
                    score = score.toString(),
                    best = best.value,
                    newBest = newBest,
                    hint = stringResource(R.string.game_slicer_over)
                )
            }
        }
    }
}
