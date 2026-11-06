package com.ipxtream.tv.ui.dashboard.components

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentDim
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class SynergySymbol(
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val value: Long,
    val rare: Boolean,
    val color: Color
) {
    METAL(R.string.game_synergy_metal, R.string.game_synergy_metal_detail, 1, false, Color(0xFF8E97A8)),
    SEED(R.string.game_synergy_seed, R.string.game_synergy_seed_detail, 1, false, Color(0xFF58B67F)),
    SPARK(R.string.game_synergy_spark, R.string.game_synergy_spark_detail, 2, false, Color(0xFFE9B949)),
    MAGNET(R.string.game_synergy_magnet, R.string.game_synergy_magnet_detail, 0, false, Color(0xFF5B9DFF)),
    FURNACE(R.string.game_synergy_furnace, R.string.game_synergy_furnace_detail, 0, true, Color(0xFFE66A3C)),
    CRYSTAL(R.string.game_synergy_crystal, R.string.game_synergy_crystal_detail, 6, true, Color(0xFFB58CFF))
}

class SynergyCell(val symbol: SynergySymbol, val growth: Int = 0)

enum class SynergyBeatKind { BURN, VALUE, MAGNET }

/** One scoring moment of a run: [cell] earns [gain]; [links] are the furnace behind a burn or the squares a magnet doubled. */
class SynergyBeat(val kind: SynergyBeatKind, val cell: Int, val gain: Long, val links: List<Int> = emptyList())

class SynergyPlay(val total: Long, val after: List<SynergyCell?>, val beats: List<SynergyBeat>)

/** Scoring for Synergy Grid; pure, and always in this order: furnaces, then values, then magnets. */
object SynergyRules {
    const val SIZE = 4
    const val TURNS_PER_STAGE = 5
    const val SCORE_CAP = 1_000_000_000L  // chained magnets double fast, and scores are stored as Int

    private fun neighbours(index: Int): List<Int> {
        val col = index % SIZE
        val row = index / SIZE
        return buildList {
            if (col > 0) add(index - 1)
            if (col < SIZE - 1) add(index + 1)
            if (row > 0) add(index - SIZE)
            if (row < SIZE - 1) add(index + SIZE)
        }
    }

    /** Runs the grid: returns the points and the grid as it stands afterwards. */
    fun run(grid: List<SynergyCell?>): Pair<Long, List<SynergyCell?>> = play(grid).let { it.total to it.after }

    /** The same run, with every scoring moment in order so the screen can play it back. */
    fun play(grid: List<SynergyCell?>): SynergyPlay {
        val after = grid.toMutableList()
        val beats = ArrayList<SynergyBeat>()
        var bonus = 0L
        for (i in after.indices) {
            if (after[i]?.symbol != SynergySymbol.FURNACE) continue
            for (n in neighbours(i)) if (after[n]?.symbol == SynergySymbol.METAL) {
                after[n] = null
                bonus += 10
                beats += SynergyBeat(SynergyBeatKind.BURN, n, 10, listOf(i))
            }
        }
        val sparks = after.count { it?.symbol == SynergySymbol.SPARK }
        val values = LongArray(after.size) { i ->
            val cell = after[i] ?: return@LongArray 0L
            when (cell.symbol) {
                SynergySymbol.SEED -> cell.symbol.value + cell.growth
                SynergySymbol.SPARK -> cell.symbol.value + 2L * (sparks - 1)
                else -> cell.symbol.value
            }
        }
        for (i in values.indices) if (values[i] > 0) beats += SynergyBeat(SynergyBeatKind.VALUE, i, values[i])
        for (i in after.indices) {
            if (after[i]?.symbol != SynergySymbol.MAGNET) continue
            val lit = ArrayList<Int>()
            var gain = 0L
            for (n in neighbours(i)) {
                val doubled = min(values[n] * 2, SCORE_CAP)
                if (doubled > values[n]) {
                    gain += doubled - values[n]
                    lit += n
                }
                values[n] = doubled
            }
            if (lit.isNotEmpty()) beats += SynergyBeat(SynergyBeatKind.MAGNET, i, gain, lit)
        }
        var total = bonus
        for (value in values) total = min(total + value, SCORE_CAP)
        for (i in after.indices) {
            val cell = after[i] ?: continue
            if (cell.symbol == SynergySymbol.SEED) after[i] = SynergyCell(cell.symbol, cell.growth + 1)
        }
        return SynergyPlay(total, after, beats)
    }

    /** Points needed across the five turns of [stage], counted from 1. */
    fun quota(stage: Int): Long = 25L * stage * stage + 15L * stage
}

/** The running game: place the three offered symbols, press Run, reach the quota every five turns. */
class SynergyGame(private val random: Random) {
    var grid: List<SynergyCell?> = List(SynergyRules.SIZE * SynergyRules.SIZE) { null }
        private set
    var hand: List<SynergySymbol> = deal()
        private set
    var turn = 1
        private set
    var stage = 1
        private set
    var stagePoints = 0L
        private set
    var total = 0L
        private set
    var lastRun = 0L
        private set
    var over = false
        private set

    private fun deal(): List<SynergySymbol> = List(3) {
        val rareChance = min(10 + turn * 2, 40)
        val rare = random.nextInt(100) < rareChance
        val pool = SynergySymbol.entries.filter { it.rare == rare }
        pool[random.nextInt(pool.size)]
    }

    /** Puts the next symbol from the hand on a cell, replacing whatever was there. */
    fun place(index: Int) {
        if (over || hand.isEmpty()) return
        grid = grid.toMutableList().also { it[index] = SynergyCell(hand.first()) }
        hand = hand.drop(1)
    }

    fun run() {
        if (over || hand.isNotEmpty()) return
        val (points, after) = SynergyRules.run(grid)
        grid = after
        lastRun = points
        stagePoints += points
        total = min(total + points, SynergyRules.SCORE_CAP)
        if (turn % SynergyRules.TURNS_PER_STAGE == 0) {
            if (stagePoints < SynergyRules.quota(stage)) {
                over = true
                return
            }
            stage++
            stagePoints = 0L
        }
        turn++
        hand = deal()
    }
}

private val WoodWarm = Color(0xFF6B3F25)
private val WoodMid = Color(0xFF45271A)
private val WoodDark = Color(0xFF21130D)
private val BrassLight = Color(0xFFF0CF7E)
private val Brass = Color(0xFFBC8D3C)
private val BrassDark = Color(0xFF6A4A1B)
private val SocketDark = Color(0xFF15100E)
private val Leather = Color(0xFF2B1A15)
private val LeatherDark = Color(0xFF1A0F0C)
private val Stitch = Color(0xFF9A7141)
private val ButtonIdle = Color(0xFF3B2A1A)

private const val POP_SECONDS = 0.3f
private val TileSize = Size(100f, 100f)
private val TileCorner = CornerRadius(13f)
private val TileEdge = Stroke(1.6f)
private val TileRing = Stroke(4f)
private val SheenAt = Offset(4f, 4f)
private val SheenSize = Size(92f, 30f)
private val PlateBrushes = SynergySymbol.entries.map {
    Brush.verticalGradient(listOf(lerp(it.color, Color.Black, 0.42f), lerp(it.color, Color.Black, 0.78f)), 0f, 100f)
}

private fun forgePoly(vararg p: Float) = Path().apply {
    moveTo(p[0], p[1])
    for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
    close()
}

// Icons are drawn in a box from -1 to 1, so the tile and the tray share them at any size.
private val IngotTop = forgePoly(-0.5f, -0.55f, 0.5f, -0.55f, 0.68f, -0.2f, -0.68f, -0.2f)
private val IngotFront = forgePoly(-0.68f, -0.2f, 0.68f, -0.2f, 0.95f, 0.55f, -0.95f, 0.55f)
private val IngotLow = forgePoly(-0.82f, 0.18f, 0.82f, 0.18f, 0.95f, 0.55f, -0.95f, 0.55f)
private val SproutLeaf = Path().apply {
    moveTo(0f, -0.1f)
    cubicTo(-0.35f, -0.15f, -0.8f, -0.4f, -0.82f, -0.85f)
    cubicTo(-0.4f, -0.85f, -0.05f, -0.6f, 0f, -0.1f)
    close()
}
private val SparkStar = forgePoly(0f, -1f, 0.2f, -0.2f, 1f, 0f, 0.2f, 0.2f, 0f, 1f, -0.2f, 0.2f, -1f, 0f, -0.2f, -0.2f)
private val MagnetArc = Stroke(0.38f)
private val MagnetShine = Stroke(0.07f)
private val FurnaceMouth = Path().apply {
    moveTo(-0.42f, 0.9f)
    lineTo(-0.42f, 0.28f)
    cubicTo(-0.42f, -0.2f, 0.42f, -0.2f, 0.42f, 0.28f)
    lineTo(0.42f, 0.9f)
    close()
}
private val FurnaceFlame = Path().apply {
    moveTo(0f, -0.05f)
    cubicTo(0.42f, 0.32f, 0.3f, 0.86f, 0f, 0.86f)
    cubicTo(-0.3f, 0.86f, -0.42f, 0.32f, 0f, -0.05f)
    close()
}
private val GemCore = forgePoly(-0.7f, -0.42f, 0f, -0.22f, 0.7f, -0.42f, 0.7f, 0.35f, 0f, 0.2f, -0.7f, 0.35f)
private val GemTopLeft = forgePoly(-0.7f, -0.42f, 0f, -0.95f, 0f, -0.22f)
private val GemTopRight = forgePoly(0f, -0.95f, 0.7f, -0.42f, 0f, -0.22f)
private val GemLowLeft = forgePoly(-0.7f, 0.35f, 0f, 0.2f, 0f, 0.95f)
private val GemLowRight = forgePoly(0f, 0.2f, 0.7f, 0.35f, 0f, 0.95f)

private fun DrawScope.drawSymbolIcon(symbol: SynergySymbol, growth: Int) {
    when (symbol) {
        SynergySymbol.METAL -> {
            drawPath(IngotFront, Color(0xFFA2ABBE))
            drawPath(IngotLow, Color(0xFF6E768A))
            drawPath(IngotTop, Color(0xFFE3E7EF))
            drawLine(Color.White, Offset(-0.56f, -0.1f), Offset(0.3f, -0.1f), 0.07f, StrokeCap.Round)
        }
        SynergySymbol.SEED -> {
            drawOval(Color(0xFF5E3C22), Offset(-0.75f, 0.42f), Size(1.5f, 0.5f))
            drawOval(Color(0xFFC79A5B), Offset(-0.26f, 0.3f), Size(0.52f, 0.36f))
            drawLine(Color(0xFF7BDB9B), Offset(0f, 0.45f), Offset(0f, -0.2f), 0.13f, StrokeCap.Round)
            drawPath(SproutLeaf, Color(0xFF58B67F))
            scale(-1f, 1f, Offset.Zero) { drawPath(SproutLeaf, Color(0xFF7BDB9B)) }
            if (growth > 0) {
                scale(0.62f, 0.62f, Offset(0f, 0.2f)) {
                    translate(0f, -0.55f) {
                        drawPath(SproutLeaf, Color(0xFF9BEBB5))
                        scale(-1f, 1f, Offset.Zero) { drawPath(SproutLeaf, Color(0xFFB9F3CB)) }
                    }
                }
            }
        }
        SynergySymbol.SPARK -> {
            drawCircle(symbol.color.copy(alpha = 0.3f), 0.82f, Offset.Zero)
            drawPath(SparkStar, symbol.color)
            scale(0.55f, 0.55f, Offset.Zero) { drawPath(SparkStar, Color(0xFFFFF0B8)) }
            drawCircle(Color.White, 0.16f, Offset.Zero)
            translate(0.62f, -0.62f) { scale(0.24f, 0.24f, Offset.Zero) { drawPath(SparkStar, Color(0xFFFFF0B8)) } }
            translate(-0.6f, 0.6f) { scale(0.18f, 0.18f, Offset.Zero) { drawPath(SparkStar, symbol.color) } }
        }
        SynergySymbol.MAGNET -> {
            drawArc(symbol.color, 180f, 180f, false, Offset(-0.6f, -0.72f), Size(1.2f, 1.2f), style = MagnetArc)
            drawArc(Color(0xFFBFD9FF), 195f, 150f, false, Offset(-0.68f, -0.8f), Size(1.36f, 1.36f), style = MagnetShine)
            drawRect(symbol.color, Offset(-0.79f, -0.13f), Size(0.38f, 0.5f))
            drawRect(symbol.color, Offset(0.41f, -0.13f), Size(0.38f, 0.5f))
            drawRect(Color(0xFFE3E7EF), Offset(-0.79f, 0.37f), Size(0.38f, 0.42f))
            drawRect(Color(0xFFE3E7EF), Offset(0.41f, 0.37f), Size(0.38f, 0.42f))
        }
        SynergySymbol.FURNACE -> {
            drawRect(Color(0xFF5C2B18), Offset(0.22f, -0.92f), Size(0.34f, 0.6f))
            drawRect(Color(0xFF8D4A2C), Offset(0.16f, -0.98f), Size(0.46f, 0.14f))
            drawRoundRect(Color(0xFF8A3F22), Offset(-0.8f, -0.42f), Size(1.6f, 1.34f), CornerRadius(0.24f))
            for (row in 1..3) {
                val line = -0.42f + row * 0.34f
                drawLine(Color(0xFF5C2B18), Offset(-0.8f, line), Offset(0.8f, line), 0.05f)
            }
            drawPath(FurnaceMouth, Color(0xFF260C05))
            drawPath(FurnaceFlame, symbol.color)
            scale(0.55f, 0.55f, Offset(0f, 0.86f)) { drawPath(FurnaceFlame, AccentAmber) }
        }
        SynergySymbol.CRYSTAL -> {
            drawPath(GemCore, symbol.color)
            drawPath(GemTopLeft, Color(0xFFE2D1FF))
            drawPath(GemTopRight, Color(0xFFCDB2FF))
            drawPath(GemLowLeft, Color(0xFF6E45C4))
            drawPath(GemLowRight, Color(0xFF8C62E0))
            translate(0.32f, -0.5f) { scale(0.26f, 0.26f, Offset.Zero) { drawPath(SparkStar, Color.White) } }
        }
    }
}

private fun DrawScope.drawForgeLabel(text: String, x: Float, y: Float, textSize: Float, maxWidth: Float, paint: Paint) {
    paint.textSize = textSize
    val width = paint.measureText(text)
    if (width > maxWidth) paint.textSize = textSize * maxWidth / width
    paint.color = 0xFFFFFFFF.toInt()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun DrawScope.drawForgeNumber(text: String, x: Float, y: Float, textSize: Float, color: Color, paint: Paint) {
    paint.textSize = textSize
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = textSize * 0.2f
    paint.color = 0xE0000000.toInt()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
    paint.style = Paint.Style.FILL
    paint.color = color.toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** A filled tile, drawn in a 100x100 box. */
private fun DrawScope.drawForgeTile(symbol: SynergySymbol, growth: Int, label: String, paint: Paint, reduced: Boolean) {
    if (reduced) {
        drawRoundRect(lerp(symbol.color, Color.Black, 0.6f), size = TileSize, cornerRadius = TileCorner)
    } else {
        drawRoundRect(PlateBrushes[symbol.ordinal], size = TileSize, cornerRadius = TileCorner)
    }
    drawRoundRect(Color.White.copy(alpha = 0.1f), SheenAt, SheenSize, TileCorner)
    drawRoundRect(symbol.color, size = TileSize, cornerRadius = TileCorner, style = TileEdge)
    translate(50f, 41f) { scale(27f, 27f, Offset.Zero) { drawSymbolIcon(symbol, growth) } }
    drawForgeLabel(label, 50f, 90f, 13f, 92f, paint)
}

/** Where everything sits, in dp. The grid hugs the side away from the top pills, which swap sides in Arabic. */
private class SynergyBoard(val w: Float, val h: Float, val rtl: Boolean) {
    val outer = min(h - 16f, w * 0.52f)
    val gridX = if (rtl) 8f else w - outer - 8f
    val gridY = (h - outer) / 2f
    val rim = outer * 0.035f
    private val gap = outer * 0.012f
    val tile = (outer - rim * 2 - gap * 3) / SynergyRules.SIZE

    val panelW = w - outer - 28f
    val panelX = if (rtl) w - 12f - panelW else 12f
    val scoreX = if (rtl) panelX else 262f  // beside the pills
    val scoreW = panelW + 12f - 262f

    val matY = 78f
    val matH = h - matY - 10f
    private val padded = matH - 20f
    private val slotGap = 10f
    val slot = min((panelW - 48f - slotGap * 2) / 3f, padded * 0.36f)
    val runH = max(52f, h * 0.12f)
    private val spare = max(0f, padded - 36f - 22f - slot - 44f - runH) / 4f
    val quotaY = matY + 10f
    val gaugeY = quotaY + 22f
    val gaugeH = 14f
    val hintY = quotaY + 36f + spare
    val trayY = hintY + 22f
    val detailY = trayY + slot + spare
    val runY = detailY + 44f + spare
    val runW = min(panelW - 48f, 320f)
    val runX = panelX + (panelW - runW) / 2f
    private val trayX = panelX + (panelW - slot * 3 - slotGap * 2) / 2f

    fun slotX(i: Int) = trayX + (if (rtl) 2 - i else i) * (slot + slotGap)
    fun tileX(i: Int) = gridX + rim + i % SynergyRules.SIZE * (tile + gap)
    fun tileY(i: Int) = gridY + rim + i / SynergyRules.SIZE * (tile + gap)
    fun midX(i: Int) = tileX(i) + tile / 2f
    fun midY(i: Int) = tileY(i) + tile / 2f

    /** The square under a finger; gaps count, so a slightly off tap still lands. */
    fun tileAt(x: Float, y: Float): Int {
        if (x < gridX || x > gridX + outer || y < gridY || y > gridY + outer) return -1
        val col = ((x - gridX - rim) / (tile + gap)).toInt().coerceIn(0, SynergyRules.SIZE - 1)
        val row = ((y - gridY - rim) / (tile + gap)).toInt().coerceIn(0, SynergyRules.SIZE - 1)
        return row * SynergyRules.SIZE + col
    }

    fun onRun(x: Float, y: Float) = x in runX - 8f..runX + runW + 8f && y in runY - 8f..runY + runH + 8f
}

private fun DrawScope.drawForgeSocket(x: Float, y: Float, side: Float) {
    val corner = CornerRadius(side * 0.13f)
    drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(x, y + 1.5f), Size(side, side), corner)
    drawRoundRect(SocketDark, Offset(x, y), Size(side, side), corner)
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(x, y), Size(side, side * 0.14f), corner)
}

private fun DrawScope.drawForgeRivet(x: Float, y: Float, radius: Float) {
    drawCircle(BrassDark, radius, Offset(x, y + 0.8f))
    drawCircle(BrassLight, radius * 0.85f, Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.7f), radius * 0.3f, Offset(x - radius * 0.25f, y - radius * 0.25f))
}

private fun DrawScope.drawBrassPlate(x: Float, y: Float, w: Float, h: Float, corner: Float) {
    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(x + 1f, y + 4f), Size(w, h), CornerRadius(corner))
    drawRoundRect(
        Brush.linearGradient(listOf(BrassLight, Brass, BrassDark, Brass), Offset(x, y), Offset(x + w, y + h)),
        Offset(x, y),
        Size(w, h),
        CornerRadius(corner)
    )
    drawRoundRect(BrassLight.copy(alpha = 0.7f), Offset(x + 1f, y + 1f), Size(w - 2f, h - 2f), CornerRadius(corner), Stroke(1f))
}

/** The bench, frame and empty sockets. Static: it has its own layer, so run frames never repaint it. */
private fun DrawScope.drawForgeBench(b: SynergyBoard) {
    val full = Size(b.w, b.h)
    drawRect(Brush.verticalGradient(listOf(WoodWarm, WoodMid, WoodDark), 0f, b.h), size = full)
    val random = Random(11)
    val plank = b.h / 5f
    val grain = Path()
    for (row in 0 until 5) {
        val top = row * plank
        if (row % 2 == 1) drawRect(Color.Black.copy(alpha = 0.14f), Offset(0f, top), Size(b.w, plank))
        repeat(7) {
            val y = top + plank * (0.1f + random.nextFloat() * 0.8f)
            val bend = random.nextFloat() * 10f - 5f
            grain.reset()
            grain.moveTo(0f, y)
            grain.cubicTo(b.w * 0.3f, y + bend * 2, b.w * 0.65f, y - bend * 2, b.w, y + bend)
            drawPath(grain, Color.Black, alpha = 0.14f + random.nextFloat() * 0.14f, style = Stroke(0.7f + random.nextFloat()))
        }
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(0f, top), Offset(b.w, top), 1.8f)
        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, top + 1.8f), Offset(b.w, top + 1.8f), 1f)
    }
    repeat(5) {
        val knotX = random.nextFloat() * b.w
        val knotY = random.nextFloat() * b.h
        val radius = 10f + random.nextFloat() * 12f
        for (ring in 3 downTo 1) {
            val r = radius * ring / 3f
            drawOval(Color.Black, Offset(knotX - r * 1.5f, knotY - r * 0.7f), Size(r * 3f, r * 1.4f), alpha = 0.16f)
        }
        // Old burns from dropped embers.
        drawCircle(Color.Black, 5f + radius * 0.4f, Offset(random.nextFloat() * b.w, random.nextFloat() * b.h), alpha = 0.25f)
    }
    drawRect(
        Brush.radialGradient(
            listOf(Accent.copy(alpha = 0.34f), Color.Transparent),
            Offset(b.gridX + b.outer / 2f, b.gridY + b.outer / 2f),
            b.outer * 0.95f
        ),
        size = full
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
            Offset(b.w / 2f, b.h / 2f),
            max(b.w, b.h) * 0.72f
        ),
        size = full
    )

    // Leather mat under the panel.
    val matCorner = CornerRadius(16f)
    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(b.panelX + 1f, b.matY + 4f), Size(b.panelW, b.matH), matCorner)
    drawRoundRect(
        Brush.verticalGradient(listOf(Leather, LeatherDark), b.matY, b.matY + b.matH),
        Offset(b.panelX, b.matY),
        Size(b.panelW, b.matH),
        matCorner
    )
    drawRoundRect(
        Stitch,
        Offset(b.panelX + 5f, b.matY + 5f),
        Size(b.panelW - 10f, b.matH - 10f),
        CornerRadius(12f),
        Stroke(1.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
    )
    drawRoundRect(SocketDark, Offset(b.panelX + 16f, b.gaugeY), Size(b.panelW - 32f, b.gaugeH), CornerRadius(7f))
    drawRoundRect(BrassDark, Offset(b.panelX + 15f, b.gaugeY - 1f), Size(b.panelW - 30f, b.gaugeH + 2f), CornerRadius(8f), Stroke(1.5f))
    for (i in 0 until 3) {
        drawBrassPlate(b.slotX(i) - 3f, b.trayY - 3f, b.slot + 6f, b.slot + 6f, b.slot * 0.15f)
        drawForgeSocket(b.slotX(i), b.trayY, b.slot)
    }
    drawBrassPlate(b.runX - 4f, b.runY - 4f, b.runW + 8f, b.runH + 8f, (b.runH + 8f) / 2f)

    // Brass frame around the grid.
    drawBrassPlate(b.gridX, b.gridY, b.outer, b.outer, b.rim * 1.1f)
    drawRoundRect(
        Color.Black.copy(alpha = 0.72f),
        Offset(b.gridX + b.rim * 0.75f, b.gridY + b.rim * 0.75f),
        Size(b.outer - b.rim * 1.5f, b.outer - b.rim * 1.5f),
        CornerRadius(b.rim * 0.6f)
    )
    for (i in 0 until SynergyRules.SIZE * SynergyRules.SIZE) drawForgeSocket(b.tileX(i), b.tileY(i), b.tile)
    val rivet = b.rim * 0.2f
    val near = b.rim * 0.4f
    val far = b.outer - near
    for (step in 0..4) {
        val along = near + (far - near) * step / 4f
        drawForgeRivet(b.gridX + along, b.gridY + near, rivet)
        drawForgeRivet(b.gridX + along, b.gridY + far, rivet)
        drawForgeRivet(b.gridX + near, b.gridY + along, rivet)
        drawForgeRivet(b.gridX + far, b.gridY + along, rivet)
    }
}

/** A run played back beat by beat; the game itself only moves on when this ends. */
private class RunShow(play: SynergyPlay) {
    private val beats = play.beats
    private val pace = min(1f, 2.6f / max(0.01f, beats.sumOf { length(it).toDouble() }.toFloat()))
    private var next = 0
    private var nextAt = 0.2f
    private var clock = 0f
    private val count = SynergyRules.SIZE * SynergyRules.SIZE
    private val number = LongArray(count)

    val text = arrayOfNulls<String>(count)
    val age = FloatArray(count) { -1f }
    val burned = BooleanArray(count)
    val totalText = "+${play.total}"
    var active: SynergyBeat? = null
        private set
    var activeAge = 0f
        private set

    /** Seconds since the last beat, negative before it. */
    val tail: Float get() = if (next < beats.size) -1f else clock - nextAt
    val done: Boolean get() = tail >= 0.75f
    val skippable: Boolean get() = clock > 0.3f  // so a double press on Run can't skip the show

    private fun length(beat: SynergyBeat) = when (beat.kind) {
        SynergyBeatKind.BURN -> 0.32f
        SynergyBeatKind.VALUE -> 0.13f
        SynergyBeatKind.MAGNET -> 0.34f
    }

    fun advance(dt: Float, fire: (SynergyBeat) -> Unit) {
        clock += dt
        activeAge += dt
        for (i in age.indices) if (age[i] >= 0f) age[i] += dt
        while (next < beats.size && clock >= nextAt) {
            val beat = beats[next++]
            nextAt += length(beat) * pace
            active = beat
            activeAge = 0f
            when (beat.kind) {
                SynergyBeatKind.BURN -> {
                    burned[beat.cell] = true
                    mark(beat.cell, beat.gain)
                }
                SynergyBeatKind.VALUE -> mark(beat.cell, beat.gain)
                SynergyBeatKind.MAGNET -> for (target in beat.links) {
                    mark(target, min(number[target] * 2, SynergyRules.SCORE_CAP))
                }
            }
            fire(beat)
        }
    }

    private fun mark(cell: Int, value: Long) {
        number[cell] = value
        text[cell] = "+$value"
        age[cell] = 0f
    }
}

/** Everything that changes: gauge, tray, Run button, tiles and the run playback. */
private fun DrawScope.drawForgeLive(
    b: SynergyBoard,
    game: SynergyGame,
    cellLabels: List<String>,
    names: List<String>,
    show: RunShow?,
    earned: Long,
    pops: FloatArray,
    shake: GameShake,
    paint: Paint,
    reduced: Boolean
) {
    val fill = ((game.stagePoints + earned).toFloat() / SynergyRules.quota(game.stage)).coerceIn(0f, 1f)
    val gaugeW = (b.panelW - 32f) * fill
    if (gaugeW > 0f) {
        val gaugeX = if (b.rtl) b.panelX + b.panelW - 16f - gaugeW else b.panelX + 16f
        val molten = if (fill >= 1f) listOf(AccentGreen, AccentGreen) else listOf(AccentDim, Accent, AccentAmber)
        drawRoundRect(
            Brush.horizontalGradient(if (b.rtl) molten.reversed() else molten, gaugeX, gaugeX + gaugeW),
            Offset(gaugeX, b.gaugeY + 1f),
            Size(gaugeW, b.gaugeH - 2f),
            CornerRadius(6f)
        )
        drawLine(
            Color.White.copy(alpha = 0.3f),
            Offset(gaugeX + 4f, b.gaugeY + 3.5f),
            Offset(max(gaugeX + 4f, gaugeX + gaugeW - 4f), b.gaugeY + 3.5f),
            1.5f
        )
    }

    // The next symbol wears the glow.
    val trayUnit = b.slot / 100f
    game.hand.forEachIndexed { i, symbol ->
        if (i == 0) {
            drawRoundRect(
                AccentAmber.copy(alpha = 0.3f),
                Offset(b.slotX(0) - 7f, b.trayY - 7f),
                Size(b.slot + 14f, b.slot + 14f),
                CornerRadius(b.slot * 0.2f)
            )
        }
        translate(b.slotX(i), b.trayY) {
            scale(trayUnit, trayUnit, Offset.Zero) {
                drawForgeTile(symbol, 0, names[symbol.ordinal], paint, reduced)
                if (i == 0) drawRoundRect(AccentAmber, size = TileSize, cornerRadius = TileCorner, style = TileRing)
            }
        }
    }

    val ready = show == null && !game.over && game.hand.isEmpty()
    val pill = CornerRadius(b.runH / 2f)
    val runSize = Size(b.runW, b.runH)
    if (ready) {
        drawRoundRect(
            AccentAmber.copy(alpha = 0.25f),
            Offset(b.runX - 11f, b.runY - 11f),
            Size(b.runW + 22f, b.runH + 22f),
            CornerRadius(b.runH / 2f + 11f)
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(AccentAmber, Accent), b.runY, b.runY + b.runH),
            Offset(b.runX, b.runY),
            runSize,
            pill
        )
        drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(b.runX + 8f, b.runY + 3f), Size(b.runW - 16f, b.runH * 0.34f), pill)
    } else {
        drawRoundRect(ButtonIdle, Offset(b.runX, b.runY), runSize, pill)
    }

    // The tiles rattle inside the fixed frame.
    val unit = b.tile / 100f
    val half = b.tile / 2f
    translate(shake.x, shake.y) {
        for (i in game.grid.indices) {
            val cell = game.grid[i]
            val burned = show != null && show.burned[i]
            val pop = 1f + 0.24f * (pops[i] / POP_SECONDS).coerceIn(0f, 1f)
            translate(b.tileX(i) + half, b.tileY(i) + half) {
                scale(unit * pop, unit * pop, Offset.Zero) {
                    translate(-50f, -50f) {
                        if (cell != null && !burned) {
                            drawForgeTile(cell.symbol, cell.growth, cellLabels[i], paint, reduced)
                        } else if (cell == null && game.hand.isNotEmpty()) {
                            drawLine(Color.White, Offset(40f, 50f), Offset(60f, 50f), 2.5f, StrokeCap.Round, alpha = 0.16f)
                            drawLine(Color.White, Offset(50f, 40f), Offset(50f, 60f), 2.5f, StrokeCap.Round, alpha = 0.16f)
                        }
                        val label = show?.text?.get(i)
                        if (show != null && label != null) {
                            val age = show.age[i]
                            if (age < 0.45f) {
                                drawRoundRect(
                                    AccentAmber,
                                    size = TileSize,
                                    cornerRadius = TileCorner,
                                    alpha = 1f - age / 0.45f,
                                    style = TileRing
                                )
                            }
                            val swell = 1f + 0.6f * (1f - age / 0.18f).coerceAtLeast(0f)
                            drawForgeNumber(label, 50f, 62f, 24f * swell, AccentAmber, paint)
                        }
                    }
                }
            }
        }
        if (show != null) {
            val beat = show.active
            if (beat != null && show.activeAge < 0.4f) {
                val color = if (beat.kind == SynergyBeatKind.BURN) Accent else SynergySymbol.MAGNET.color
                for (link in beat.links) {
                    drawLine(
                        color,
                        Offset(b.midX(beat.cell), b.midY(beat.cell)),
                        Offset(b.midX(link), b.midY(link)),
                        b.tile * 0.05f,
                        StrokeCap.Round,
                        alpha = 1f - show.activeAge / 0.4f
                    )
                }
            }
            if (show.tail >= 0f) {
                val grow = (show.tail / 0.2f).coerceAtMost(1f)
                drawForgeNumber(
                    show.totalText,
                    b.gridX + b.outer / 2f,
                    b.gridY + b.outer / 2f + b.tile * 0.25f,
                    b.tile * (0.45f + 0.35f * grow),
                    AccentAmber,
                    paint
                )
            }
        }
    }
}

/** Nothing animates at rest: the frame loop runs only for a run's playback and for sparks still in the air. */
@Composable
internal fun SynergyGridScreen(
    gameFocusRequester: FocusRequester,
    chipFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.SYNERGY.prefs)
    val particles = rememberGameParticles()
    val shake = rememberGameShake()
    val reduced = LocalReducedEffects.current
    val paused = LocalGamePaused.current
    val screenDensity = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val pops = remember { FloatArray(SynergyRules.SIZE * SynergyRules.SIZE) }
    var game by remember { mutableStateOf(SynergyGame(Random.Default)) }
    var version by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf<RunShow?>(null) }
    var earned by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableIntStateOf(0) }
    var effects by remember { mutableFloatStateOf(0f) }  // seconds of sparks left in the air
    var overAt by remember { mutableLongStateOf(0L) }
    var newBest by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .gameFocus(gameFocusRequester, chipFocusRequester)
            .clipToBounds()
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val b = remember(width, height, rtl) { SynergyBoard(width, height, rtl) }
        version  // the game object changes in place; this recomposes after each move

        fun finishRun() {
            val stage = game.stage
            game.run()
            show = null
            earned = 0L
            effects = 0.8f
            version++
            if (game.over) {
                val score = game.total.toInt()
                newBest = score > best.value
                best.played(score)
                overAt = SystemClock.uptimeMillis()
                shake.kick(5f)
            } else if (game.stage > stage) {
                val gaugeMid = b.panelX + b.panelW / 2f
                particles.burst(gaugeMid, b.gaugeY, AccentGreen, 40, 190f, 0.7f)
                particles.burst(gaugeMid, b.gaugeY, AccentAmber, 24, 130f, 0.7f)
                shake.kick(4f)
            }
        }

        fun fire(beat: SynergyBeat) {
            earned += beat.gain
            val x = b.midX(beat.cell)
            val y = b.midY(beat.cell)
            when (beat.kind) {
                SynergyBeatKind.BURN -> {
                    particles.burst(x, y, Accent, 18, 170f)
                    shake.kick(2.5f)
                }
                SynergyBeatKind.VALUE -> particles.burst(x, y, AccentAmber, 5, 80f, 0.4f)
                SynergyBeatKind.MAGNET -> {
                    for (target in beat.links) {
                        particles.burst(b.midX(target), b.midY(target), SynergySymbol.MAGNET.color, 7, 110f, 0.45f)
                    }
                    shake.kick(1.5f)
                }
            }
        }

        // How to play doesn't take touches, so a tap can fall through to the board behind it.
        val press = rememberUpdatedState<(Offset) -> Unit> { finger ->
            val x = finger.x / screenDensity
            val y = finger.y / screenDensity
            val running = show
            val tile = b.tileAt(x, y)
            when {
                paused -> Unit
                running != null -> {
                    if (running.skippable) finishRun()
                }
                game.over -> {
                    if (SystemClock.uptimeMillis() - overAt >= 500L) {
                        game = SynergyGame(Random.Default)
                        newBest = false
                        version++
                    }
                }
                tile >= 0 && game.hand.isNotEmpty() -> {
                    val old = game.grid[tile]
                    val symbol = game.hand.first()
                    game.place(tile)
                    pops[tile] = POP_SECONDS
                    particles.burst(b.midX(tile), b.midY(tile), symbol.color, 10, 90f, 0.4f)
                    if (old != null) particles.burst(b.midX(tile), b.midY(tile), old.symbol.color, 14, 150f, 0.45f)
                    effects = max(effects, 0.5f)
                    version++
                }
                game.hand.isEmpty() && b.onRun(x, y) -> {
                    earned = 0L
                    show = RunShow(SynergyRules.play(game.grid))
                }
                else -> Unit
            }
        }

        GameLoop(running = show != null || effects > 0f) { dt ->
            effects = max(0f, effects - dt)
            particles.step(dt, 150f)
            shake.step(dt)
            for (i in pops.indices) pops[i] = max(0f, pops[i] - dt)
            show?.let { running ->
                running.advance(dt) { fire(it) }
                if (running.done) finishRun()
            }
            frame++
        }

        Canvas(Modifier.fillMaxSize().graphicsLayer {}) {
            scale(density, density, Offset.Zero) { drawForgeBench(b) }
        }

        val names = SynergySymbol.entries.map { stringResource(it.title) }
        val cellLabels = game.grid.map { cell ->
            when {
                cell == null -> ""
                cell.growth > 0 -> stringResource(R.string.game_synergy_grown, names[cell.symbol.ordinal], cell.growth)
                else -> names[cell.symbol.ordinal]
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {}
                .fingerPosition(onDown = { press.value(it) }, onMove = {})
        ) {
            frame  // reading it here redraws only this layer
            scale(density, density, Offset.Zero) {
                drawForgeLive(b, game, cellLabels, names, show, earned, pops, shake, paint, reduced)
                if (game.over) drawRect(Color.Black.copy(alpha = 0.5f), size = Size(b.w, b.h))
                particles.draw(this, 1f, 0f, 0f, 2.6f)
            }
        }

        fun at(x: Float, y: Float, w: Float) = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x.dp, y.dp).width(w.dp)

        GameScore(
            stringResource(R.string.game_synergy_total, min(game.total + earned, SynergyRules.SCORE_CAP)),
            best.value,
            at(b.scoreX, 0f, b.scoreW)
        )
        Row(at(b.panelX + 18f, b.quotaY, b.panelW - 36f), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.game_synergy_stage, game.stage),
                style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.Bold),
                color = AccentAmber
            )
            Text(
                stringResource(
                    R.string.game_synergy_quota,
                    game.stagePoints + earned,
                    SynergyRules.quota(game.stage),
                    SynergyRules.TURNS_PER_STAGE - (game.turn - 1) % SynergyRules.TURNS_PER_STAGE
                ),
                style = IpxTypography.BodyMedium,
                color = TextPrimary,
                maxLines = 1
            )
        }
        val next = game.hand.firstOrNull()
        if (show == null && !game.over) {
            Text(
                when {
                    next != null -> stringResource(R.string.game_synergy_place)
                    game.turn == 1 -> stringResource(R.string.game_synergy_ready)
                    else -> stringResource(R.string.game_synergy_last, game.lastRun)
                },
                at(b.panelX + 18f, b.hintY, b.panelW - 36f),
                style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (next == null) AccentAmber else TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (next != null) {
                Text(
                    stringResource(next.detail),
                    at(b.panelX + 18f, b.detailY, b.panelW - 36f),
                    style = IpxTypography.BodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        Box(at(b.runX, b.runY, b.runW).height(b.runH.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.game_synergy_run),
                style = IpxTypography.HeadlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (show == null && !game.over && next == null) TextOnAccent else TextSecondary
            )
        }
        if (game.over) {
            GameResultPanel(
                title = stringResource(R.string.game_synergy_over),
                score = game.total.toString(),
                best = best.value,
                newBest = newBest,
                hint = stringResource(R.string.game_synergy_again),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
