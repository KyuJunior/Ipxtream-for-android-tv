package com.ipxtream.tv.ui.dashboard.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
enum class GameId(@StringRes val title: Int, val touchOnly: Boolean) {
    STACK(R.string.game_stack_title, false),
    PENALTY(R.string.game_title, false),
    NOVA(R.string.game_nova_title, true),
    SLICER(R.string.game_slicer_title, true),
    WRECK(R.string.game_wreck_title, true),
    BOUNCER(R.string.game_bouncer_title, true),
    SYNERGY(R.string.game_synergy_title, true)
}

/** The Play build's Games section: a chip per game above the one being played. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun GamesScreen(
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    headerFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasTouch = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) }
    val games = remember(hasTouch) { GameId.entries.filter { hasTouch || !it.touchOnly } }
    var selected by rememberSaveable { mutableStateOf(GameId.STACK) }
    val chips = remember { GameId.entries.associateWith { FocusRequester() } }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = IpxSpacing.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            games.forEachIndexed { index, game ->
                PillChip(
                    label = stringResource(game.title),
                    selected = game == selected,
                    onClick = { selected = game },
                    modifier = Modifier.focusRequester(chips.getValue(game)).focusProperties {
                        if (index == 0) start = sideNavFocusRequester
                        up = headerFocusRequester
                        down = firstItemFocusRequester
                    }
                )
            }
        }
        val chip = chips.getValue(selected)
        val gameModifier = Modifier.weight(1f).fillMaxWidth()
        when (selected) {
            GameId.STACK -> StackTowerScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
            GameId.PENALTY -> PenaltyGameScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
            GameId.NOVA -> NovaGameScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
            GameId.SLICER -> VoidSlicerScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
            GameId.WRECK -> TetheredWreckScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
            GameId.BOUNCER -> LogicBouncerScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
            GameId.SYNERGY -> SynergyGridScreen(firstItemFocusRequester, sideNavFocusRequester, chip, gameModifier)
        }
    }
}

private class CutPiece(val left: Float, val width: Float, val row: Int, val color: Color)

private fun blockColor(index: Int) = Color.hsv((20f + index * 11f) % 360f, 0.62f, 0.98f)

/** Endless one-button stacking: OK (or a tap) drops the block; a miss ends the run. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun StackTowerScreen(
    gameFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val prefs = LocalContext.current.getSharedPreferences("stack_game", Context.MODE_PRIVATE)
    val start = StackBlock((1f - StackRules.START_WIDTH) / 2f, StackRules.START_WIDTH)
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var blocks by remember { mutableStateOf(listOf(start)) }
    var combo by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var overAt by remember { mutableStateOf(0L) }
    var slide by remember { mutableFloatStateOf(0f) }
    var cut by remember { mutableStateOf<CutPiece?>(null) }
    var focused by remember { mutableStateOf(false) }
    val fall = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val score = blocks.size - 1
    val camera by animateFloatAsState(max(0, blocks.size - 8).toFloat(), tween(220), label = "camera")

    LaunchedEffect(score, over) {
        if (over) return@LaunchedEffect
        val began = withFrameMillis { it }
        val width = blocks.last().width
        while (true) withFrameMillis { slide = StackRules.slideLeft(it - began, score, width) }
    }

    val act by rememberUpdatedState {
        if (over) {
            // A held or double press right after losing must not throw the result screen away.
            if (System.currentTimeMillis() - overAt > 500) {
                blocks = listOf(start)
                combo = 0
                over = false
                newBest = false
                cut = null
            }
        } else {
            val top = blocks.last()
            val moving = StackBlock(slide, top.width)
            val drop = StackRules.drop(moving, top, combo)
            if (drop == null) {
                cut = CutPiece(moving.left, moving.width, blocks.size, blockColor(blocks.size))
                over = true
                overAt = System.currentTimeMillis()
                if (score > best) {
                    best = score
                    newBest = true
                    prefs.edit().putInt("best", score).apply()
                }
            } else {
                val placed = drop.block
                val lostWidth = moving.width - placed.width
                cut = if (lostWidth > 0.001f) {
                    val left = if (moving.left < top.left) moving.left else placed.left + placed.width
                    CutPiece(left, lostWidth, blocks.size, blockColor(blocks.size))
                } else null
                combo = drop.combo
                blocks = blocks + placed
            }
            scope.launch {
                fall.snapTo(0f)
                fall.animateTo(1f, tween(500))
            }
        }
    }

    Box(
        modifier = modifier
            .focusRequester(gameFocusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                this.start = sideNavFocusRequester
                up = upFocusRequester
                end = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .focusable()
            .onKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0) act()
                    true
                } else false
            }
            .pointerInput(Unit) { detectTapGestures { act() } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val towerWidth = size.width * 0.55f
            val towerLeft = (size.width - towerWidth) / 2f
            val rowHeight = size.height / 11f
            // Row 0 sits on the bottom edge; the camera rises so the top of the tower stays in view.
            fun yOf(row: Float) = size.height - (row + 1f - camera) * rowHeight
            fun drawBlock(left: Float, width: Float, row: Float, color: Color) = drawRect(
                color, Offset(towerLeft + left * towerWidth, yOf(row)), Size(width * towerWidth, rowHeight - 3f)
            )

            drawRect(
                if (focused) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                Offset(towerLeft, 0f), Size(towerWidth, size.height)
            )
            for (i in max(0, camera.toInt() - 1) until blocks.size) {
                drawBlock(blocks[i].left, blocks[i].width, i.toFloat(), blockColor(i))
            }
            if (!over) {
                drawBlock(slide, blocks.last().width, blocks.size.toFloat(), blockColor(blocks.size))
            }
            cut?.let { piece ->
                val t = fall.value
                if (t < 1f) {
                    drawRect(
                        piece.color.copy(alpha = 1f - t),
                        Offset(towerLeft + piece.left * towerWidth, yOf(piece.row - t * 3f)),
                        Size(piece.width * towerWidth, rowHeight - 3f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                score.toString(),
                style = IpxTypography.HeadlineMedium.copy(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(stringResource(R.string.game_best, best), style = IpxTypography.BodyMedium, color = TextSecondary)
            if (combo >= 2 && !over) {
                Text(stringResource(R.string.game_stack_perfect, combo), style = IpxTypography.BodyMedium, color = Accent)
            }
        }

        Text(
            text = when {
                newBest -> stringResource(R.string.game_stack_new_best, score)
                over -> stringResource(R.string.game_stack_over, score)
                else -> stringResource(R.string.game_stack_hint)
            },
            style = IpxTypography.BodyMedium,
            color = TextPrimary,
            textAlign = TextAlign.Start,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = IpxSpacing.Gutter, bottom = 16.dp).fillMaxWidth(0.2f)
        )
    }
}
