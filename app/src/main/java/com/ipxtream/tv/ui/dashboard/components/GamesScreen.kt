package com.ipxtream.tv.ui.dashboard.components

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
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
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A tower block; [left] and [width] are fractions of the tower's width. */
data class StackBlock(val left: Float, val width: Float)

data class StackDrop(val block: StackBlock, val combo: Int)

/** Rules of Stack Tower: only the overlap survives a drop; perfect drops in a row win width back. */
object StackRules {
    const val START_WIDTH = 0.5f
    const val PERFECT = 0.02f
    const val COMBO_FOR_GROWTH = 3
    const val GROWTH = 0.04f

    /** Tower widths per second; [score] speeds it up to a cap a remote can still follow. */
    fun speed(score: Int): Float = min(0.45f + 0.012f * score, 1.1f)

    /** Where the moving block is: sweeps edge to edge, each new block starting from the other side. */
    fun slideLeft(elapsedMs: Long, score: Int, width: Float): Float {
        val range = 1f - width
        if (range <= 0f) return 0f
        val periodMs = (2000f * range / speed(score)).toLong().coerceAtLeast(1L)
        val phase = (elapsedMs + if (score % 2 == 1) periodMs / 2 else 0L) % periodMs
        val t = phase.toFloat() / periodMs
        return range * (if (t < 0.5f) t * 2f else 2f - t * 2f)
    }

    /** The result of dropping [moving] on [top]; null when it missed and the game is over. */
    fun drop(moving: StackBlock, top: StackBlock, combo: Int): StackDrop? {
        val offset = moving.left - top.left
        if (abs(offset) <= PERFECT) {
            val newCombo = combo + 1
            val width = if (newCombo >= COMBO_FOR_GROWTH) min(top.width + GROWTH, START_WIDTH) else top.width
            val left = (top.left - (width - top.width) / 2f).coerceIn(0f, 1f - width)
            return StackDrop(StackBlock(left, width), newCombo)
        }
        val overlap = top.width - abs(offset)
        if (overlap <= 0f) return null
        return StackDrop(StackBlock(max(moving.left, top.left), overlap), 0)
    }
}

/** The games on offer. Touch-only ones need a finger, so a TV remote never sees them. */
enum class GameId(
    @StringRes val title: Int,
    @StringRes val howTo: Int,
    val touchOnly: Boolean,
    /** The game's prefs file (best score, history, top 10). */
    val prefs: String
) {
    STACK(R.string.game_stack_title, R.string.game_how_stack, false, "stack_game"),
    PENALTY(R.string.game_title, R.string.game_how_penalty, false, "penalty_game"),
    DRIFT(R.string.game_drift_title, R.string.game_how_drift, false, "drift_game"),
    SLING(R.string.game_sling_title, R.string.game_how_sling, true, "sling_game"),
    NOVA(R.string.game_nova_title, R.string.game_how_nova, true, "nova_game"),
    SLICER(R.string.game_slicer_title, R.string.game_how_slicer, true, "slicer_game"),
    WRECK(R.string.game_wreck_title, R.string.game_how_wreck, true, "wreck_game"),
    BOUNCER(R.string.game_bouncer_title, R.string.game_how_bouncer, true, "bouncer_game"),
    SYNERGY(R.string.game_synergy_title, R.string.game_how_synergy, true, "synergy_game")
}

/** The Games section: a menu of thumbnails. An opened game is drawn over everything by [GameStage]. */
@Composable
fun GamesScreen(
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    headerFocusRequester: FocusRequester,
    onOpen: (GameId) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasTouch = remember {
        // Some TVs report a touchscreen they don't have, so the TV check comes first.
        val isTv = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        !isTv && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
    val games = remember(hasTouch) { GameId.entries.filter { hasTouch || !it.touchOnly } }
    var lastPlayed by rememberSaveable { mutableStateOf<GameId?>(null) }
    val playerPrefs = remember { context.getSharedPreferences(GamePlayerName.PREFS, android.content.Context.MODE_PRIVATE) }
    var player by remember { mutableStateOf(playerPrefs.getString(GamePlayerName.KEY, null).orEmpty()) }
    // The first visit asks for a name; the chip above the games changes it later.
    var naming by rememberSaveable { mutableStateOf(player.isEmpty()) }
    val playerChip = remember { FocusRequester() }

    Column(modifier) {
        PillChip(
            label = stringResource(R.string.game_player_chip, player),
            selected = false,
            onClick = { naming = true },
            modifier = Modifier
                .padding(start = IpxSpacing.Gutter, top = 4.dp, bottom = 4.dp)
                .focusRequester(playerChip)
                .focusProperties {
                    up = headerFocusRequester
                    start = sideNavFocusRequester
                    down = firstItemFocusRequester
                }
        )
        GameMenu(
            games = games,
            lastPlayed = lastPlayed,
            firstItemFocusRequester = firstItemFocusRequester,
            sideNavFocusRequester = sideNavFocusRequester,
            headerFocusRequester = playerChip,
            onOpen = {
                lastPlayed = it
                onOpen(it)
            },
            modifier = Modifier.weight(1f)
        )
    }
    var named by remember { mutableStateOf(false) }
    if (naming) {
        PlayerNameDialog(current = player) { name ->
            playerPrefs.edit().putString(GamePlayerName.KEY, name).apply()
            player = name
            naming = false
            named = true
        }
    }
    // Back on the games, on the chip that shows the new name, not wherever focus fell when the dialog closed.
    LaunchedEffect(named) {
        if (!named) return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        runCatching { playerChip.requestFocus() }
        named = false
    }
}

internal fun blockColor(index: Int) = Color.hsv((20f + index * 11f) % 360f, 0.62f, 0.98f)

private val StackStart = StackBlock((1f - StackRules.START_WIDTH) / 2f, StackRules.START_WIDTH)

private val SkyDuskTop = Color(0xFF241452)
private val SkyDuskMid = Color(0xFFB23A86)
private val SkyDuskLow = Color(0xFFFFA053)
private val SkyNightTop = Color(0xFF05051A)
private val SkyNightMid = Color(0xFF15114A)
private val SkyNightLow = Color(0xFF3A1E72)
private val CloudDusk = Color(0x4DFFB3C8)
private val CloudNight = Color(0x267C74E0)
private val MoonDisc = Color(0xFFEDE6FF)
private val MoonShade = Color(0x33806FB8)
private val WindowWarm = Color(0xFFFFD27A)
private val WindowCool = Color(0xFF7FE3FF)
private val GroundColor = Color(0xFF1A1036)
private val GroundLine = Color(0x33FFFFFF)
private val PlinthColor = Color(0xFF2E1E58)
private val SlabEdge = Color(0x59FFFFFF)
private val SlabShade = Color(0x47000000)
private val SlabWindowLit = Color(0xFFFFF0C2)
private val SlabWindowDark = Color(0x55000000)
private val GuideLine = Color(0x4DFFFFFF)
private val GuideLanding = Color(0x40FFFFFF)

/** A falling offcut, in pixels. */
private class CutPiece(var x: Float, var y: Float, val width: Float, val side: Float, val index: Int) {
    var speed = 0f
    var angle = 0f
}

/** One row of the skyline. Towers run down to the window's bottom, so a sinking layer never shows a gap. */
private class CityLayer(
    val dusk: Color,
    val night: Color,
    val parallax: Float,
    val neon: Boolean,
    val towers: List<Rect>,
    val warm: List<Offset>,
    val cool: List<Offset>,
    val dot: Float
)

private fun cityLayer(
    seed: Int,
    dusk: Color,
    night: Color,
    parallax: Float,
    neon: Boolean,
    width: Float,
    height: Float,
    groundY: Float,
    tall: ClosedFloatingPointRange<Float>,
    wide: ClosedFloatingPointRange<Float>,
    pitch: Float
): CityLayer {
    val random = Random(seed)
    val towers = ArrayList<Rect>()
    val warm = ArrayList<Offset>()
    val cool = ArrayList<Offset>()
    var x = -width * 0.02f
    while (x < width) {
        val w = width * (wide.start + random.nextFloat() * (wide.endInclusive - wide.start))
        val top = groundY - height * (tall.start + random.nextFloat() * (tall.endInclusive - tall.start))
        towers += Rect(x, top, x + w, height)
        if (pitch > 0f) {
            var wy = top + pitch
            while (wy < height) {
                var wx = x + pitch
                while (wx < x + w - pitch * 0.5f) {
                    val roll = random.nextFloat()
                    if (roll < 0.34f) warm += Offset(wx, wy) else if (roll < 0.42f) cool += Offset(wx, wy)
                    wx += pitch
                }
                wy += pitch
            }
        }
        x += w + width * 0.006f * random.nextFloat()
    }
    return CityLayer(dusk, night, parallax, neon, towers, warm, cool, pitch * 0.4f)
}

/** What only changes with the window's size: tower metrics, and the skyline and stars, built once. */
private class StackScene(val width: Float, val height: Float, val reduced: Boolean) {
    val towerWidth = width * 0.55f
    val towerLeft = (width - towerWidth) / 2f
    val rowHeight = height / 12f
    val baseY = height * 0.88f
    val depth = rowHeight * 0.32f
    val hover = rowHeight * 0.4f
    val slabPath = Path()
    val outline = Stroke(max(2f, height * 0.005f))
    val city: List<CityLayer> = buildList {
        add(cityLayer(11, Color(0xFF8A3A8E), Color(0xFF241A55), 0.25f, false, width, height, baseY, 0.3f..0.62f, 0.05f..0.1f, 0f))
        if (!reduced) {
            add(cityLayer(23, Color(0xFF4F2470), Color(0xFF17103A), 0.4f, false, width, height, baseY, 0.2f..0.46f, 0.06f..0.11f, rowHeight * 0.3f))
        }
        add(
            cityLayer(
                37, Color(0xFF25123F), Color(0xFF0C0824), 0.7f, true, width, height, baseY,
                0.08f..0.3f, 0.08f..0.14f, rowHeight * (if (reduced) 0.55f else 0.4f)
            )
        )
    }
    private val stars = Random(5)
    private val starCount = if (reduced) 24 else 70
    val starX = FloatArray(starCount) { stars.nextFloat() }
    val starY = FloatArray(starCount) { stars.nextFloat() }
    val starSize = FloatArray(starCount) { (0.6f + stars.nextFloat()) * height / 400f }

    private var skyStep = -1
    private lateinit var sky: Brush

    /** The sky only changes every few rows, so its gradient is rebuilt only then. */
    fun skyBrush(night: Float): Brush {
        val step = (night * 48f).toInt()
        if (step != skyStep) {
            skyStep = step
            val t = step / 48f
            sky = Brush.verticalGradient(
                0f to lerp(SkyDuskTop, SkyNightTop, t),
                0.6f to lerp(SkyDuskMid, SkyNightMid, t),
                1f to lerp(SkyDuskLow, SkyNightLow, t)
            )
        }
        return sky
    }

    fun rowY(row: Float, camera: Float) = baseY - (row + 1f - camera) * rowHeight

    fun x(fraction: Float) = towerLeft + fraction * towerWidth
}

/** Everything that moves but isn't a game rule. Only [clock] is state: it redraws the Canvas without recomposing. */
private class StackFx {
    var clock by mutableFloatStateOf(0f)
    var scene: StackScene? = null
    var slideSeconds = 0f
    var slide = 0f
    var camera = 0f
    var settle = 0f
    var flash = 0f
    var flashColor = ChipFocused
    var overClock = 0f
    var resultAt = 0L
    val cuts = ArrayList<CutPiece>()

    fun sceneFor(width: Float, height: Float, reduced: Boolean): StackScene {
        val current = scene
        if (current != null && current.width == width && current.height == height && current.reduced == reduced) return current
        return StackScene(width, height, reduced).also { scene = it }
    }

    fun reset() {
        slideSeconds = 0f
        slide = 0f
        settle = 0f
        overClock = 0f
        resultAt = 0L
        cuts.clear()
    }

    fun placed(scene: StackScene, moving: StackBlock, top: StackBlock, placed: StackBlock, row: Int, perfect: Boolean, sparks: GameParticles) {
        val y = scene.rowY(row.toFloat(), camera) - scene.hover
        val mid = y + scene.rowHeight / 2f
        val leftEdge = scene.x(placed.left)
        val rightEdge = scene.x(placed.left + placed.width)
        settle = 1f
        val lost = moving.width - placed.width
        if (lost > 0.001f) {
            val fromLeft = moving.left < top.left
            val cutLeft = if (fromLeft) moving.left else placed.left + placed.width
            cuts += CutPiece(scene.x(cutLeft), y, lost * scene.towerWidth, if (fromLeft) -1f else 1f, row)
            sparks.burst(if (fromLeft) leftEdge else rightEdge, mid, blockColor(row), 12, scene.rowHeight * 3f, 0.45f)
        }
        if (perfect) {
            flash = 1f
            flashColor = if (placed.width > top.width) AccentGreen else ChipFocused
            sparks.burst(leftEdge, mid, flashColor, 16, scene.rowHeight * 6f)
            sparks.burst(rightEdge, mid, flashColor, 16, scene.rowHeight * 6f)
        }
    }

    fun missed(scene: StackScene, moving: StackBlock, top: StackBlock, row: Int, sparks: GameParticles) {
        val y = scene.rowY(row.toFloat(), camera) - scene.hover
        cuts += CutPiece(scene.x(moving.left), y, moving.width * scene.towerWidth, if (moving.left < top.left) -1f else 1f, row)
        flash = 1f
        flashColor = AccentRed
        sparks.burst(scene.x(moving.left + moving.width / 2f), y + scene.rowHeight / 2f, AccentRed, 24, scene.rowHeight * 7f, 0.8f)
    }

    fun step(dt: Float) {
        settle = max(0f, settle - dt * 9f)
        flash = max(0f, flash - dt * 2.8f)
        val current = scene ?: return
        val gravity = current.rowHeight * 26f
        for (i in cuts.indices.reversed()) {
            val piece = cuts[i]
            piece.speed += gravity * dt
            piece.y += piece.speed * dt
            piece.x += piece.side * current.rowHeight * 2.5f * dt
            piece.angle += piece.side * 150f * dt
            if (piece.y > current.height + current.rowHeight * 2f) cuts.removeAt(i)
        }
    }
}

private fun DrawScope.drawHeavens(scene: StackScene, camera: Float, night: Float, clock: Float) {
    val w = size.width
    val h = size.height
    val climb = camera * scene.rowHeight

    val starFade = ((night - 0.12f) * 2f).coerceIn(0f, 1f)
    if (starFade > 0f) {
        for (i in scene.starX.indices) {
            val twinkle = if (scene.reduced) 0.8f else 0.55f + 0.45f * sin(clock * 2.2f + i)
            val y = (scene.starY[i] * h + climb * 0.05f) % h
            drawCircle(Color.White, scene.starSize[i], Offset(scene.starX[i] * w, y), alpha = starFade * twinkle)
        }
    }

    val sunFade = (1f - night * 2f).coerceIn(0f, 1f)
    val sunRadius = h * 0.15f
    val sunY = scene.baseY - h * 0.22f + climb * 0.3f
    if (sunFade > 0f && sunY - sunRadius < h) {
        val sun = Offset(w * 0.76f, sunY)
        if (scene.reduced) {
            drawCircle(AccentAmber, sunRadius, sun, alpha = sunFade)
        } else {
            drawCircle(AccentAmber, sunRadius * 1.9f, sun, alpha = sunFade * 0.14f)
            drawCircle(AccentAmber, sunRadius * 1.4f, sun, alpha = sunFade * 0.2f)
            drawCircle(
                Brush.verticalGradient(listOf(AccentAmber, Accent), sunY - sunRadius, sunY + sunRadius),
                sunRadius, sun, alpha = sunFade
            )
        }
    }

    val rise = ((camera - 10f) / 30f).coerceIn(0f, 1f)
    if (rise > 0f) {
        val moon = Offset(w * 0.83f, h * (-0.18f + 0.42f * rise))
        val radius = h * 0.085f
        if (!scene.reduced) drawCircle(MoonDisc, radius * 1.5f, moon, alpha = 0.1f)
        drawCircle(MoonDisc, radius, moon)
        drawCircle(MoonShade, radius * 0.24f, moon + Offset(-radius * 0.3f, -radius * 0.25f))
        drawCircle(MoonShade, radius * 0.16f, moon + Offset(radius * 0.35f, radius * 0.1f))
        drawCircle(MoonShade, radius * 0.2f, moon + Offset(-radius * 0.1f, radius * 0.45f))
    }

    val cloud = lerp(CloudDusk, CloudNight, night)
    val band = h * 1.5f
    for (i in 0 until if (scene.reduced) 2 else 4) {
        val cloudWidth = w * (0.22f + 0.07f * i)
        val y = ((i * 0.37f + 0.05f) * band + climb * 0.55f) % band - h * 0.25f
        val x = (i * 0.29f * (w + cloudWidth) + clock * w * (0.006f + 0.003f * i)) % (w + cloudWidth) - cloudWidth
        drawOval(cloud, Offset(x, y), Size(cloudWidth, h * 0.05f))
        drawOval(cloud, Offset(x + cloudWidth * 0.18f, y - h * 0.03f), Size(cloudWidth * 0.55f, h * 0.07f))
    }
}

private fun DrawScope.drawCity(scene: StackScene, camera: Float, night: Float, clock: Float) {
    for (layer in scene.city) {
        val sink = camera * scene.rowHeight * layer.parallax
        if (sink > size.height) continue
        val color = lerp(layer.dusk, layer.night, night)
        for (i in layer.towers.indices) {
            val tower = layer.towers[i]
            val top = tower.top + sink
            if (top >= size.height) continue
            drawRect(color, Offset(tower.left, top), Size(tower.width, size.height - top))
            if (layer.neon) {
                val glow = if (scene.reduced) 0.8f else 0.65f + 0.35f * sin(clock * 1.5f + i)
                drawRect(
                    if (i % 2 == 0) Accent else AccentOrchid,
                    Offset(tower.left, top), Size(tower.width, scene.rowHeight * 0.05f), alpha = glow
                )
            }
        }
        if (layer.dot > 0f) {
            translate(top = sink) {
                drawPoints(layer.warm, PointMode.Points, WindowWarm, strokeWidth = layer.dot)
                drawPoints(layer.cool, PointMode.Points, WindowCool, strokeWidth = layer.dot)
            }
        }
    }
}

/** The plaza the tower stands on. It runs past the window's edges so a camera kick can't show a gap. */
private fun DrawScope.drawPlaza(scene: StackScene, camera: Float) {
    val y = scene.baseY + camera * scene.rowHeight
    val spill = scene.rowHeight
    if (y > size.height + spill) return
    val rowHeight = scene.rowHeight
    val centre = size.width / 2f
    drawRect(GroundColor, Offset(-spill, y), Size(size.width + spill * 2f, size.height - y + spill * 2f))
    for (i in -6..6) {
        drawLine(
            GroundLine,
            Offset(centre + i * scene.towerWidth * 0.12f, y),
            Offset(centre + i * scene.towerWidth * 0.42f, y + (size.height - scene.baseY) * 1.2f),
            strokeWidth = 1.5f
        )
    }
    drawRect(Accent, Offset(-spill, y), Size(size.width + spill * 2f, rowHeight * 0.05f), alpha = 0.8f)
    if (!scene.reduced) {
        drawOval(Accent, Offset(centre - scene.towerWidth * 0.5f, y - rowHeight * 0.1f), Size(scene.towerWidth, rowHeight * 0.8f), alpha = 0.2f)
    }
    drawRect(PlinthColor, Offset(scene.x(0.16f), y), Size(scene.towerWidth * 0.68f, rowHeight * 0.24f))
    drawRect(AccentOrchid, Offset(scene.x(0.16f), y), Size(scene.towerWidth * 0.68f, rowHeight * 0.04f))
}

/** A lit slab: a lighter top face in perspective, then a front face with highlight, shade and windows. */
private fun DrawScope.drawSlab(scene: StackScene, x: Float, y: Float, width: Float, color: Color, index: Int, windows: Boolean) {
    val h = scene.rowHeight
    val inset = min(scene.depth * 0.7f, width * 0.3f)
    scene.slabPath.apply {
        reset()
        moveTo(x, y)
        lineTo(x + width, y)
        lineTo(x + width - inset, y - scene.depth)
        lineTo(x + inset, y - scene.depth)
        close()
    }
    drawPath(scene.slabPath, lerp(color, Color.White, 0.38f))
    drawRect(color, Offset(x, y), Size(width, h - 1f))
    drawRect(SlabEdge, Offset(x, y), Size(width, h * 0.09f))
    drawRect(SlabShade, Offset(x, y + h * 0.72f), Size(width, h * 0.28f - 1f))
    if (windows) {
        val pitch = h * 0.62f
        val count = ((width - h * 0.3f) / pitch).toInt()
        val firstX = x + (width - count * pitch) / 2f + h * 0.13f
        for (j in 0 until count) {
            val lit = (index * 7 + j * 13) % 5 != 0
            drawRect(
                if (lit) SlabWindowLit else SlabWindowDark,
                Offset(firstX + j * pitch, y + h * 0.26f), Size(h * 0.36f, h * 0.34f)
            )
        }
    }
}

/** Endless one-button stacking: OK (or a tap) drops the block; a miss ends the run. */
@Composable
internal fun StackTowerScreen(
    gameFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val best = rememberBestScore(GameId.STACK.prefs)
    val reduced = LocalReducedEffects.current
    val paused = LocalGamePaused.current
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sparks = rememberGameParticles()
    val shake = rememberGameShake()
    val fx = remember { StackFx() }
    var blocks by remember { mutableStateOf(listOf(StackStart)) }
    var combo by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var perfectPop by remember { mutableFloatStateOf(0f) }
    val score = blocks.size - 1

    GameLoop(running = !settled) { dt ->
        if (over) {
            fx.overClock += dt
            // The tumbling block gets a moment on screen before the result covers it.
            if (fx.overClock >= 0.6f && !showResult) {
                showResult = true
                fx.resultAt = System.currentTimeMillis()
            }
            if (fx.overClock >= 1.8f) settled = true
        } else {
            fx.slideSeconds += dt
            fx.slide = StackRules.slideLeft((fx.slideSeconds * 1000f).toLong(), blocks.size - 1, blocks.last().width)
        }
        fx.camera += (max(0, blocks.size - 6) - fx.camera) * min(1f, dt * 10f)
        fx.step(dt)
        sparks.step(dt, gravity = (fx.scene?.rowHeight ?: 0f) * 14f)
        shake.step(dt)
        perfectPop = max(0f, perfectPop - dt * 1.3f)
        fx.clock += dt
    }

    val act by rememberUpdatedState<() -> Unit> {
        when {
            paused -> Unit
            over -> {
                // A held or double press right after losing must not throw the result screen away.
                if (showResult && System.currentTimeMillis() - fx.resultAt > 500) {
                    blocks = listOf(StackStart)
                    combo = 0
                    over = false
                    newBest = false
                    showResult = false
                    settled = false
                    fx.reset()
                }
            }
            else -> {
                val top = blocks.last()
                val row = blocks.size
                val moving = StackBlock(fx.slide, top.width)
                val drop = StackRules.drop(moving, top, combo)
                if (drop == null) {
                    over = true
                    newBest = row - 1 > best.value
                    best.played(row - 1)
                    fx.scene?.let { fx.missed(it, moving, top, row, sparks) }
                    shake.kick(11f * density)
                } else {
                    val placed = drop.block
                    val perfect = drop.combo > 0
                    combo = drop.combo
                    blocks = blocks + placed
                    // The next block starts now, not on the next frame, so a fast press can't use the old position.
                    fx.slideSeconds = 0f
                    fx.slide = StackRules.slideLeft(0L, row, placed.width)
                    fx.scene?.let { fx.placed(it, moving, top, placed, row, perfect, sparks) }
                    shake.kick((if (perfect) 5f else 2f) * density)
                    if (perfect) perfectPop = 1f
                }
            }
        }
    }

    Box(
        modifier = modifier
            .gameFocus(gameFocusRequester, upFocusRequester)
            .onKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0) act()
                    true
                } else false
            }
            .fingerPosition(
                onDown = { finger ->
                    // Tv surfaces don't consume pointers, so a tap on the top pills lands here too.
                    val fromStart = fx.scene?.let { if (rtl) it.width - finger.x else finger.x } ?: finger.x
                    if (fromStart > 280f * density || finger.y > 60f * density) act()
                },
                onMove = {}
            )
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val clock = fx.clock
            val camera = fx.camera
            val scene = fx.sceneFor(size.width, size.height, reduced)
            val night = (camera / 45f).coerceIn(0f, 1f)
            val rowHeight = scene.rowHeight

            drawRect(scene.skyBrush(night))
            drawHeavens(scene, camera, night, clock)
            drawCity(scene, camera, night, clock)

            translate(shake.x, shake.y) {
                drawPlaza(scene, camera)
                for (i in max(0, camera.toInt() - 3) until blocks.size) {
                    val y = scene.rowY(i.toFloat(), camera) - if (i == blocks.lastIndex) fx.settle * scene.hover else 0f
                    drawSlab(scene, scene.x(blocks[i].left), y, blocks[i].width * scene.towerWidth, blockColor(i), i, !reduced)
                }

                val top = blocks.last()
                val topLeft = scene.x(top.left)
                val topRight = scene.x(top.left + top.width)
                val topY = scene.rowY(blocks.lastIndex.toFloat(), camera) - fx.settle * scene.hover
                if (!over) {
                    val x = scene.x(fx.slide)
                    val width = top.width * scene.towerWidth
                    val y = scene.rowY(blocks.size.toFloat(), camera) - scene.hover
                    drawLine(GuideLine, Offset(topLeft, topY), Offset(topLeft, y - rowHeight), strokeWidth = 2f)
                    drawLine(GuideLine, Offset(topRight, topY), Offset(topRight, y - rowHeight), strokeWidth = 2f)
                    val landFrom = max(x, topLeft)
                    val landTo = min(x + width, topRight)
                    if (landTo > landFrom) {
                        drawRect(GuideLanding, Offset(landFrom, topY - scene.depth), Size(landTo - landFrom, scene.depth))
                    }
                    drawSlab(scene, x, y, width, blockColor(blocks.size), blocks.size, !reduced)
                    drawRect(
                        ChipFocused, Offset(x, y), Size(width, rowHeight - 1f),
                        alpha = 0.55f + 0.3f * sin(clock * 6f), style = scene.outline
                    )
                }

                if (fx.flash > 0f) {
                    val grow = (1f - fx.flash) * rowHeight * 0.5f
                    drawRect(
                        fx.flashColor, Offset(topLeft - grow, topY - grow),
                        Size(topRight - topLeft + grow * 2f, rowHeight + grow * 2f),
                        alpha = fx.flash, style = scene.outline
                    )
                }

                for (i in fx.cuts.indices) {
                    val piece = fx.cuts[i]
                    rotate(piece.angle, Offset(piece.x + piece.width / 2f, piece.y + rowHeight / 2f)) {
                        drawSlab(scene, piece.x, piece.y, piece.width, blockColor(piece.index), piece.index, false)
                    }
                }

                sparks.draw(this, 1f, 0f, 0f, rowHeight * 0.09f)
            }

            if (fx.flash > 0f && !reduced) drawRect(fx.flashColor, alpha = fx.flash * 0.16f)
        }

        GameScore(score.toString(), best.value)

        if (combo >= 1 && !over) {
            Text(
                stringResource(R.string.game_stack_perfect, combo),
                style = IpxTypography.HeadlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f)
                ),
                color = ChipFocused,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    val t = perfectPop
                    alpha = min(1f, t * 2.5f)
                    scaleX = 0.85f + 0.3f * t
                    scaleY = scaleX
                    translationY = (1f - t) * -36f * density
                }
            )
        }

        if (score == 0 && !over) {
            Text(
                stringResource(R.string.game_stack_hint),
                style = IpxTypography.BodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    .widthIn(max = 640.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }

        if (showResult) {
            GameResultPanel(
                title = stringResource(R.string.game_stack_title),
                score = score.toString(),
                best = best.value,
                newBest = newBest,
                hint = stringResource(if (newBest) R.string.game_stack_new_best else R.string.game_stack_over, score),
                modifier = Modifier.align(Alignment.Center).padding(16.dp).widthIn(max = 480.dp)
            )
        }
    }
}
