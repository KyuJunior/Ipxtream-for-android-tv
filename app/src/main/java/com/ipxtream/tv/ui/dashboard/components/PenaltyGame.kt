package com.ipxtream.tv.ui.dashboard.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.BorderSubtle
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.random.Random

enum class ShotResult { GOAL, SAVED, WIDE }

/**
 * The rules of the penalty shootout. Positions run 0..1 across the goal mouth: the keeper guesses
 * at random, so the corners are the safest shots, but the outer [POST] of the sweep goes wide.
 */
object PenaltyRules {
    const val SHOTS = 5
    const val POST = 0.05f
    const val KEEPER_REACH = 0.2f

    /** Where the sweeping aim is after [elapsedMs]; each shot of the round sweeps faster. */
    fun aimAt(elapsedMs: Long, shotIndex: Int): Float {
        val period = (1800L - 220L * shotIndex).coerceAtLeast(700L)
        val t = (elapsedMs % period).toFloat() / period
        return if (t < 0.5f) t * 2f else 2f - t * 2f
    }

    fun resultOf(aim: Float, keeper: Float): ShotResult = when {
        aim < POST || aim > 1f - POST -> ShotResult.WIDE
        abs(aim - keeper) <= KEEPER_REACH -> ShotResult.SAVED
        else -> ShotResult.GOAL
    }
}

private class Shot(val aim: Float, val keeper: Float, val result: ShotResult)

/** Penalty shootout for the Play build's testers: OK (or a tap) shoots, then starts a new round. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PenaltyGameScreen(
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    headerFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val prefs = LocalContext.current.getSharedPreferences("penalty_game", Context.MODE_PRIVATE)
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var results by remember { mutableStateOf(emptyList<Boolean>()) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var aim by remember { mutableFloatStateOf(0.5f) }
    var focused by remember { mutableStateOf(false) }
    val flight = remember { Animatable(0f) }
    val finished = results.size == PenaltyRules.SHOTS

    LaunchedEffect(shot, finished) {
        if (shot != null || finished) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { aim = PenaltyRules.aimAt(it - start, results.size) }
    }
    LaunchedEffect(shot) {
        val current = shot ?: return@LaunchedEffect
        flight.snapTo(0f)
        flight.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(900)
        results = results + (current.result == ShotResult.GOAL)
        shot = null
        val goals = results.count { it }
        if (results.size == PenaltyRules.SHOTS && goals > best) {
            best = goals
            prefs.edit().putInt("best", goals).apply()
        }
    }

    val act by rememberUpdatedState {
        when {
            finished -> results = emptyList()
            shot == null -> {
                val keeper = Random.nextFloat()
                shot = Shot(aim, keeper, PenaltyRules.resultOf(aim, keeper))
            }
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = IpxSpacing.Gutter, vertical = 16.dp)
    ) {
        Text(stringResource(R.string.game_title), style = IpxTypography.HeadlineMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.game_best, best), style = IpxTypography.BodyMedium, color = TextSecondary)
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(firstItemFocusRequester)
                .onFocusChanged { focused = it.isFocused }
                .focusProperties {
                    start = sideNavFocusRequester
                    up = headerFocusRequester
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
                val goalWidth = size.width * 0.7f
                val goalLeft = (size.width - goalWidth) / 2f
                val goalTop = size.height * 0.14f
                val goalHeight = size.height * 0.6f
                val groundY = goalTop + goalHeight
                // The outer POST of the sweep lies outside the posts, so a wide shot looks wide.
                fun xOf(position: Float) =
                    goalLeft + (position - PenaltyRules.POST) / (1f - 2f * PenaltyRules.POST) * goalWidth

                val net = BorderSubtle
                for (i in 1..9) {
                    val x = goalLeft + goalWidth * i / 10f
                    drawLine(net, Offset(x, goalTop), Offset(x, groundY), 1.5f)
                }
                for (i in 1..4) {
                    val y = goalTop + goalHeight * i / 5f
                    drawLine(net, Offset(goalLeft, y), Offset(goalLeft + goalWidth, y), 1.5f)
                }
                drawLine(net, Offset(0f, groundY), Offset(size.width, groundY), 3f)
                val frame = if (focused) ChipFocused else TextMuted
                val post = Path().apply {
                    moveTo(goalLeft, groundY)
                    lineTo(goalLeft, goalTop)
                    lineTo(goalLeft + goalWidth, goalTop)
                    lineTo(goalLeft + goalWidth, groundY)
                }
                drawPath(post, frame, style = Stroke(width = 8f))

                val current = shot
                val progress = if (current != null) flight.value else 0f

                // Keeper: arms span the real save reach, so what you see is what stops the ball.
                val keeperX = xOf(0.5f) + ((current?.keeper?.let(::xOf) ?: xOf(0.5f)) - xOf(0.5f)) * progress
                val reachPx = PenaltyRules.KEEPER_REACH * goalWidth / (1f - 2f * PenaltyRules.POST)
                val bodyTop = groundY - goalHeight * 0.55f
                drawRoundRect(
                    AccentOrchid, Offset(keeperX - 16f, bodyTop), Size(32f, groundY - bodyTop),
                    cornerRadius = CornerRadius(14f)
                )
                drawCircle(AccentOrchid, 18f, Offset(keeperX, bodyTop - 16f))
                drawRoundRect(
                    AccentOrchid, Offset(keeperX - reachPx, bodyTop + 16f), Size(reachPx * 2f, 14f),
                    cornerRadius = CornerRadius(7f)
                )

                // Ball: from the spot to the aimed point, shrinking with distance.
                val spot = Offset(size.width / 2f, size.height * 0.9f)
                val target = Offset(xOf(current?.aim ?: aim), goalTop + goalHeight * 0.45f)
                val ball = if (current != null) spot + (target - spot) * progress else spot
                drawCircle(ChipFocused, 20f - 8f * progress, ball)

                if (current == null && !finished) {
                    val x = xOf(aim)
                    val marker = Path().apply {
                        moveTo(x - 18f, goalTop - 40f)
                        lineTo(x + 18f, goalTop - 40f)
                        lineTo(x, goalTop - 10f)
                        close()
                    }
                    drawPath(marker, Accent)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(PenaltyRules.SHOTS) { i ->
                val scored = results.getOrNull(i)
                Box(
                    Modifier.size(18.dp).background(
                        when (scored) {
                            true -> Accent
                            false -> TextMuted
                            null -> BorderSubtle
                        },
                        CircleShape
                    )
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = when {
                    finished -> stringResource(R.string.game_result, results.count { it }, PenaltyRules.SHOTS)
                    shot != null && flight.value >= 1f -> stringResource(
                        when (shot?.result) {
                            ShotResult.GOAL -> R.string.game_goal
                            ShotResult.SAVED -> R.string.game_saved
                            else -> R.string.game_wide
                        }
                    )
                    else -> stringResource(R.string.game_hint)
                },
                style = IpxTypography.BodyMedium,
                color = TextPrimary
            )
        }
    }
}
