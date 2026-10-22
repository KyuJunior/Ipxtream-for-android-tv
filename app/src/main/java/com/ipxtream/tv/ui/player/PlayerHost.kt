package com.ipxtream.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem

/**
 * What the dashboard needs to know about playback. It changes only when the title changes,
 * unlike [PlayerUiState], which ticks every second while something plays.
 */
data class PlaybackSummary(
    val isLive:        Boolean     = false,
    val activeStream:  StreamItem? = null,
    val activeEpisode: EpisodeItem? = null,
    val activeSeries:  SeriesItem? = null
)

fun PlayerUiState.summary() = PlaybackSummary(isLive, activeStream, activeEpisode, activeSeries)

/**
 * [PlayerPanel] that collects the player's state itself, so the per-second position updates
 * recompose only the player and not the whole dashboard behind it.
 */
@Composable
fun PlayerHost(
    exoPlayer:        ExoPlayer,
    viewModel:        PlayerViewModel,
    onStop:           () -> Unit,
    onNextEpisode:    (() -> Unit)? = null,
    onPrevEpisode:    (() -> Unit)? = null,
    onEpisodesClick:  (() -> Unit)? = null,
    onNextChannel:    (() -> Unit)? = null,
    onPrevChannel:    (() -> Unit)? = null,
    nextEpisodeLabel: String? = null,
    modifier:         Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    PlayerPanel(
        exoPlayer        = exoPlayer,
        uiState          = uiState,
        viewModel        = viewModel,
        onStop           = onStop,
        onNextEpisode    = onNextEpisode,
        onPrevEpisode    = onPrevEpisode,
        onEpisodesClick  = onEpisodesClick,
        onNextChannel    = onNextChannel,
        onPrevChannel    = onPrevChannel,
        nextEpisodeLabel = nextEpisodeLabel,
        modifier         = modifier
    )
}
