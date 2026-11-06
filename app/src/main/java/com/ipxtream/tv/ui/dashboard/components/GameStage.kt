package com.ipxtream.tv.ui.dashboard.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.touchShield

/** Remembers that a game's How to play has been seen, so it opens by itself only once. */
private fun Context.seenHowTo(game: GameId): Boolean =
    getSharedPreferences("game_how_to", Context.MODE_PRIVATE).getBoolean(game.name, false)

private fun Context.markHowToSeen(game: GameId) {
    getSharedPreferences("game_how_to", Context.MODE_PRIVATE).edit().putBoolean(game.name, true).apply()
}

/** A game on the whole window, with All games and How to play pills over its top corner. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun GameStage(game: GameId, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var howToOpen by remember(game) { mutableStateOf(!context.seenHowTo(game)) }
    var scoresOpen by remember(game) { mutableStateOf(false) }
    var barFocused by remember { mutableStateOf(false) }
    val gameFocus = remember { FocusRequester() }
    val allGames = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }

    BackHandler {
        when {
            howToOpen -> howToOpen = false
            scoresOpen -> scoresOpen = false
            else -> onClose()
        }
    }

    LaunchedEffect(howToOpen, scoresOpen) {
        if (howToOpen || scoresOpen) {
            runCatching { playButton.requestFocus() }
        } else {
            context.markHowToSeen(game)
            runCatching { gameFocus.requestFocus() }
        }
    }

    Box(modifier.touchShield().background(SlateDeep)) {
        CompositionLocalProvider(LocalGamePaused provides (howToOpen || scoresOpen || barFocused)) {
            val area = Modifier.fillMaxSize()
            when (game) {
                GameId.STACK -> StackTowerScreen(gameFocus, allGames, area)
                GameId.PENALTY -> PenaltyGameScreen(gameFocus, allGames, area)
                GameId.DRIFT -> JellyDriftScreen(gameFocus, allGames, area)
                GameId.SLING -> CometSlingScreen(gameFocus, allGames, area)
                GameId.NOVA -> NovaGameScreen(gameFocus, allGames, area)
                GameId.SLICER -> VoidSlicerScreen(gameFocus, allGames, area)
                GameId.WRECK -> TetheredWreckScreen(gameFocus, allGames, area)
                GameId.BOUNCER -> LogicBouncerScreen(gameFocus, allGames, area)
                GameId.SYNERGY -> SynergyGridScreen(gameFocus, allGames, area)
            }
        }

        if (!howToOpen && !scoresOpen) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }  // a pill tap stays out of the game
                    .onFocusChanged { barFocused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillChip(
                    label = stringResource(R.string.game_all),
                    selected = false,
                    onClick = onClose,
                    modifier = Modifier.focusRequester(allGames).focusProperties {
                        start = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = gameFocus
                    }
                )
                PillChip(
                    label = stringResource(R.string.game_how_to),
                    selected = false,
                    onClick = { howToOpen = true },
                    modifier = Modifier.focusProperties {
                        up = FocusRequester.Cancel
                        down = gameFocus
                    }
                )
                PillChip(
                    label = stringResource(R.string.game_scores),
                    selected = false,
                    onClick = { scoresOpen = true },
                    modifier = Modifier.focusProperties {
                        end = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = gameFocus
                    }
                )
            }
        } else if (scoresOpen) {
            GameScores(
                game = game,
                closeButton = playButton,
                onClose = { scoresOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            HowToPlay(
                game = game,
                playButton = playButton,
                onPlay = { howToOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun HowToPlay(game: GameId, playButton: FocusRequester, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val steps = stringResource(game.howTo).split('\n')
    // The empty pointer block keeps touches on this sheet from reaching the game below.
    Box(
        modifier.background(Color.Black.copy(alpha = 0.72f)).pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
        },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .widthIn(max = 760.dp)
                .background(SlateDeep, RoundedCornerShape(22.dp))
                .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(0.42f).aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp))) {
                Canvas(Modifier.fillMaxSize()) { drawThumbnail(game) }
            }
            Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(game.title), style = IpxTypography.HeadlineMedium, color = TextPrimary)
                Text(stringResource(R.string.game_how_to), style = IpxTypography.BodyMedium, color = TextSecondary)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 2.dp).background(Accent, CircleShape).padding(horizontal = 7.dp)) {
                                Text(
                                    (index + 1).toString(),
                                    style = IpxTypography.BodyMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                    color = TextOnAccent
                                )
                            }
                            Text(step, style = IpxTypography.BodyMedium, color = TextPrimary, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                PillChip(
                    label = stringResource(R.string.game_how_play),
                    selected = true,
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playButton).padding(top = 4.dp)
                )
            }
        }
    }
}

/** This game's top 10 and its recent plays, all kept on this device. */
@Composable
private fun GameScores(game: GameId, closeButton: FocusRequester, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val records = rememberBestScore(game.prefs)
    val top = remember(game) { records.top() }
    val recent = remember(game) { records.history() }
    val locale = androidx.core.os.ConfigurationCompat.getLocales(LocalContext.current.resources.configuration)[0]
        ?: java.util.Locale.getDefault()
    val dates = remember(locale) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, locale) }
    fun date(play: GamePlay) = if (play.at > 0) dates.format(java.util.Date(play.at)) else "—"

    // The empty pointer block keeps touches on this sheet from reaching the game below.
    Box(
        modifier.background(Color.Black.copy(alpha = 0.72f)).pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 760.dp)
                .background(SlateDeep, RoundedCornerShape(22.dp))
                .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(game.title), style = IpxTypography.HeadlineMedium, color = TextPrimary)
            if (top.isEmpty()) {
                Text(stringResource(R.string.game_scores_empty), style = IpxTypography.BodyMedium, color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.game_scores_top), style = IpxTypography.TitleMedium, color = Accent)
                        top.forEachIndexed { index, play -> ScoreRow("${index + 1}", play.score, date(play), highlight = index == 0) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.game_scores_recent), style = IpxTypography.TitleMedium, color = Accent)
                        recent.forEach { play -> ScoreRow(null, play.score, date(play), highlight = false) }
                    }
                }
            }
            PillChip(
                label = stringResource(R.string.game_how_play),
                selected = true,
                onClick = onClose,
                modifier = Modifier.focusRequester(closeButton)
            )
        }
    }
}

@Composable
private fun ScoreRow(rank: String?, score: Int, date: String, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rank != null) {
            Text(rank, style = IpxTypography.BodyMedium, color = TextSecondary, modifier = Modifier.widthIn(min = 22.dp))
        }
        Text(
            score.toString(),
            style = IpxTypography.BodyMedium.copy(fontFeatureSettings = "tnum"),
            color = if (highlight) Accent else TextPrimary,
            modifier = Modifier.widthIn(min = 56.dp)
        )
        Text(date, style = IpxTypography.BodyMedium.copy(fontSize = 12.sp), color = TextSecondary)
    }
}
