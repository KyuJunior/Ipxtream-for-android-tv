package com.ipxtream.tv.ui.player

import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.model.SeriesItem

// ─── Playback state enum ──────────────────────────────────────────────────────

enum class PlaybackState {
    IDLE, LOADING, READY, ENDED, ERROR
}

// ─── UI State ─────────────────────────────────────────────────────────────────

/**
 * Complete immutable snapshot of everything the [PlayerPanel] and [PlayerHud]
 * need to render themselves.
 *
 * All mutations happen in [PlayerViewModel] via `_uiState.update { }`.
 *
 * @param playbackState       Current [PlaybackState].
 * @param activeStreamName    Title shown in the HUD top bar.
 * @param activeMimeHint      Badge string, e.g. "LIVE", "MKV", "4K".
 * @param isPlaying           ExoPlayer is actively advancing the position.
 * @param bufferedPercent     0–100 buffered fraction.
 * @param currentPositionMs   Playback position in milliseconds.
 * @param durationMs          Total duration in ms; 0 or [Long.MAX_VALUE] → live.
 * @param error               Error message when [playbackState] == [PlaybackState.ERROR].
 * @param activeStream        Loaded [StreamItem]; null unless Live/VOD.
 * @param activeEpisode       Loaded [EpisodeItem]; null unless Series.
 *
 * @param isHudVisible        Whether the overlay HUD (top/bottom bars) is shown.
 * @param audioTrackOptions   Available audio renditions parsed from ExoPlayer.
 * @param subtitleTrackOptions Available subtitle/text renditions.
 * @param areSubtitlesEnabled  Whether a subtitle track is currently active.
 * @param activeTrackMenu     Which track selection panel is open ([TrackMenuType]).
 */
data class PlayerUiState(
    // ── Playback ──────────────────────────────────────────────────────────────
    val playbackState:     PlaybackState = PlaybackState.IDLE,
    val activeStreamName:  String?       = null,
    val activeMimeHint:    String?       = null,
    val isPlaying:         Boolean       = false,
    val bufferedPercent:   Int           = 0,
    val currentPositionMs: Long          = 0L,
    val durationMs:        Long          = 0L,
    val error:             String?       = null,
    val activeStream:      StreamItem?   = null,
    val activeEpisode:     EpisodeItem?  = null,
    val activeSeries:      SeriesItem?   = null,
    val isCurrentFavorite: Boolean       = false,

    // ── HUD ───────────────────────────────────────────────────────────────────
    val isHudVisible: Boolean = false,

    // ── Track selection ───────────────────────────────────────────────────────
    val audioTrackOptions:    List<TrackInfo> = emptyList(),
    val subtitleTrackOptions: List<TrackInfo> = emptyList(),
    val areSubtitlesEnabled:  Boolean          = false,
    val activeTrackMenu:      TrackMenuType    = TrackMenuType.NONE
) {
    /** True when the split player panel should be visible in the Dashboard. */
    val hasActiveMedia: Boolean get() =
        playbackState != PlaybackState.IDLE

    /** True for live channels (duration is 0 or TIME_UNSET). */
    val isLive: Boolean get() =
        durationMs <= 0L || durationMs == Long.MAX_VALUE

    /** 0.0–1.0 progress fraction for the VOD progress bar. */
    val progressFraction: Float get() =
        if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else 0f

    /** True if there is at least one audio track to select. */
    val hasMultipleAudioTracks: Boolean get() = audioTrackOptions.isNotEmpty()

    /** Always show subtitle button to allow user to disable or see '(None)'. */
    val hasSubtitleTracks: Boolean get() = true

    /** True when the track selection menu is open (HUD must not auto-hide). */
    val isTrackMenuOpen: Boolean get() = activeTrackMenu != TrackMenuType.NONE
}
