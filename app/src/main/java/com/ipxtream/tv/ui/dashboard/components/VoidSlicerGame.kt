package com.ipxtream.tv.ui.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
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
    private var shield = false

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
        if (!lineCell(cell)) return
        if (shield) {
            shield = false
            ballVx[i] = -ballVx[i]
            ballVy[i] = -ballVy[i]
        } else {
            arms = emptyList()
            lives--
            if (lives <= 0) phase = SlicePhase.OVER
        }
    }

    private fun finishLine() {
        val line = arms.flatten().distinct()
        arms = emptyList()
        val balls = (0 until ballCount).map { ballY[it].toInt() * COLS + ballX[it].toInt() }.filter { it !in line }
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

/** A swipe picks the line's direction; it grows from where the finger went down. */
@Composable
internal fun VoidSlicerScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore("slicer_game")
    val density = LocalDensity.current.density
    var game by remember { mutableStateOf(SlicerGame(Random.Default)) }
    var phase by remember { mutableStateOf(SlicePhase.PLAYING) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var cleared by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var overAt by remember { mutableStateOf(0L) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var downAt by remember { mutableStateOf(Offset.Zero) }
    var swiped by remember { mutableStateOf(false) }

    fun cellSize() = min(canvas.width.toFloat() / SlicerGame.COLS, canvas.height.toFloat() / SlicerGame.ROWS)
    fun origin() = Offset(
        (canvas.width - cellSize() * SlicerGame.COLS) / 2f,
        (canvas.height - cellSize() * SlicerGame.ROWS) / 2f
    )

    fun sync() {
        score = game.score
        lives = game.lives
        cleared = (game.cleared * 100f).toInt()
        if (phase != SlicePhase.OVER && game.phase == SlicePhase.OVER) {
            overAt = System.currentTimeMillis()
            best.submit(game.score)
        }
        phase = game.phase
    }

    GameLoop(running = phase == SlicePhase.PLAYING) { dt ->
        game.step(dt)
        frame++
        sync()
    }

    Box(
        modifier
            .gameFocus(gameFocusRequester, sideNavFocusRequester, chipFocusRequester)
            .onSizeChanged { canvas = it }
            .fingerPosition(
                onDown = { position ->
                    if (phase == SlicePhase.OVER && System.currentTimeMillis() - overAt > 500) {
                        game = SlicerGame(Random.Default)
                        sync()
                    }
                    downAt = position
                    swiped = false
                },
                onMove = { position ->
                    val delta = position - downAt
                    if (!swiped && (abs(delta.x) > 12f * density || abs(delta.y) > 12f * density)) {
                        swiped = true
                        val cell = (downAt - origin()) / cellSize()
                        game.swipe(cell.x.toInt(), cell.y.toInt(), abs(delta.x) > abs(delta.y))
                    }
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame  // redraw only, no recomposition
            val cell = cellSize()
            if (cell <= 0f) return@Canvas
            val start = origin()
            val arena = game.arena
            for (row in 0 until arena.rows) {
                var col = 0
                while (col < arena.cols) {
                    if (!arena.isOpen(col, row)) { col++; continue }
                    val from = col
                    while (arena.isOpen(col, row)) col++
                    drawRect(
                        Color.White.copy(alpha = 0.07f),
                        start + Offset(from * cell, row * cell),
                        Size((col - from) * cell, cell)
                    )
                }
            }
            for (arm in game.arms) {
                for (i in 0..min(game.grown.toInt(), arm.lastIndex)) {
                    val index = arm[i]
                    drawRect(
                        ChipFocused,
                        start + Offset((index % arena.cols) * cell, (index / arena.cols) * cell),
                        Size(cell, cell)
                    )
                }
            }
            for (i in 0 until game.ballCount) {
                drawCircle(Accent, cell * 0.5f, start + Offset(game.ballX[i] * cell, game.ballY[i] * cell))
            }
        }

        GameScore(
            score.toString(),
            best.value,
            extra = stringResource(R.string.game_slicer_status, game.round, lives, cleared)
        )
        Text(
            stringResource(if (phase == SlicePhase.OVER) R.string.game_slicer_over else R.string.game_slicer_hint),
            style = IpxTypography.BodyMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp)
        )

        if (phase == SlicePhase.CHOOSING) {
            Column(
                Modifier.align(Alignment.Center).background(SlateDeep.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.game_slicer_choose), style = IpxTypography.BodyMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    game.offers.forEach { upgrade ->
                        Column(
                            Modifier
                                .width(170.dp)
                                .border(1.dp, GlassStroke, RoundedCornerShape(10.dp))
                                .clickable {
                                    game.choose(upgrade)
                                    sync()
                                }
                                .padding(10.dp)
                        ) {
                            Text(
                                stringResource(upgrade.title),
                                style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Accent
                            )
                            Text(stringResource(upgrade.detail), style = IpxTypography.BodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
