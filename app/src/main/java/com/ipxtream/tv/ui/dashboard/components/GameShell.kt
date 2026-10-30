package com.ipxtream.tv.ui.dashboard.components

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/** Keeps a game's best score in its own local prefs. */
internal class BestScore(private val prefs: android.content.SharedPreferences) {
    private var stored by mutableIntStateOf(prefs.getInt("best", 0))
    val value: Int get() = stored

    fun submit(score: Int) {
        if (score <= stored) return
        stored = score
        prefs.edit().putInt("best", score).apply()
    }
}

@Composable
internal fun rememberBestScore(name: String): BestScore {
    val context = LocalContext.current
    return remember(name) { BestScore(context.getSharedPreferences(name, Context.MODE_PRIVATE)) }
}

/** The focus target every game needs: the header and side menu jump to it on the way in. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.gameFocus(
    game: FocusRequester,
    sideNav: FocusRequester,
    chip: FocusRequester
): Modifier = focusRequester(game)
    .focusProperties {
        start = sideNav
        up = chip
        end = FocusRequester.Cancel
        down = FocusRequester.Cancel
    }
    .focusable()

/** Calls [step] once a frame while [running], with seconds since the last frame (a stall counts as 0.05 s). */
@Composable
internal fun GameLoop(running: Boolean, step: (Float) -> Unit) {
    val current by rememberUpdatedState(step)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) withFrameMillis { now ->
            current(((now - last) / 1000f).coerceAtMost(0.05f))
            last = now
        }
    }
}

/** Reports where a finger is, from touching down to lifting off. */
internal fun Modifier.fingerPosition(
    onDown: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onUp: () -> Unit = {}
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            onDown(awaitFirstDown().position)
            do {
                val event = awaitPointerEvent()
                val finger = event.changes.first()
                if (finger.pressed) {
                    onMove(finger.position)
                    finger.consume()
                }
            } while (event.changes.any { it.pressed })
            onUp()
        }
    }

/** The big score and the best score over a game. */
@Composable
internal fun GameScore(score: String, best: Int, modifier: Modifier = Modifier, extra: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            score,
            style = IpxTypography.HeadlineMedium.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(stringResource(R.string.game_best, best), style = IpxTypography.BodyMedium, color = TextSecondary)
        if (extra != null) Text(extra, style = IpxTypography.BodyMedium, color = TextSecondary)
    }
}
