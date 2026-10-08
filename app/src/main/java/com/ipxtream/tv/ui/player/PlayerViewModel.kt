package com.ipxtream.tv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import com.ipxtream.tv.data.local.LibraryStore
import com.ipxtream.tv.data.local.LibraryItem

/**
 * ViewModel owning the [ExoPlayer] instance.
 *
 * ## Responsibilities (this phase adds):
 * - **HUD auto-hide timer** — shows the overlay on any interaction and starts a
 *   3-second coroutine Job that hides it. Cancelled and restarted on each
 *   interaction. Suspended while a track-selection menu is open.
 * - **Track parsing** — converts [Tracks.Group] entries into [TrackInfo] lists
 *   that the Composable can render without touching Media3 types directly.
 * - **Track selection** — wraps [TrackSelectionOverride] and [setTrackTypeDisabled]
 *   to select audio/subtitle tracks or explicitly disable subtitles.
 *
 * ## Lifecycle contract (unchanged from Phase 4)
 * Pause/resume — `DisposableEffect` in [PlayerPanel].
 * Release       — `onCleared()` here (the ONLY place).
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // =========================================================================
    //  ExoPlayer
    // =========================================================================

    private val trackSelector = DefaultTrackSelector(application).apply {
        setParameters(
            buildUponParameters()
                .setTunnelingEnabled(true)
        )
    }

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 2_500,
            /* bufferForPlaybackAfterRebufferMs = */ 5_000
        )
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .build()
        .also { player ->
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setFlags(C.FLAG_AUDIBILITY_ENFORCED)
                    .build(),
                /* handleAudioFocus = */ true
            )
            player.addListener(PlayerEventListener())
        }

    // =========================================================================
    //  UI State
    // =========================================================================

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // ─── Raw track group references ────────────────────────────────────────────
    // NOT exposed to Composables. Used only for building TrackSelectionOverride.
    private var rawAudioGroups: List<Tracks.Group> = emptyList()
    private var rawTextGroups:  List<Tracks.Group> = emptyList()

    // ─── Coroutine jobs ────────────────────────────────────────────────────────
    private var positionPollingJob: Job? = null
    private var hudHideJob:         Job? = null

    // ─── Scoped Storage ────────────────────────────────────────────────────────
    private var credentials: AuthCredentials? = null
    private var libraryStore: LibraryStore? = null

    // =========================================================================
    //  Public API — playback control
    // =========================================================================

    fun loadStream(stream: StreamItem, credentials: AuthCredentials) {
        this.credentials = credentials
        val store = LibraryStore(getApplication(), credentials)
        this.libraryStore = store
        val streamType = stream.streamType ?: if (stream.isLive) "live" else "movie"
        val isFav = store.isFavorite(stream.streamId.toString(), streamType)

        val resumePositionMs = if (!stream.isLive) {
            val historyItem = store.getHistory().firstOrNull { it.id == stream.streamId.toString() && it.type == streamType }
            if (historyItem != null && historyItem.durationMs > 0L) {
                val fraction = historyItem.lastWatchedPositionMs.toFloat() / historyItem.durationMs
                if (fraction < 0.90f && historyItem.lastWatchedPositionMs > 3_000L) {
                    historyItem.lastWatchedPositionMs
                } else 0L
            } else 0L
        } else 0L

        _uiState.update { it.copy(
            playbackState    = PlaybackState.LOADING,
            activeStreamName = stream.name,
            activeMimeHint   = if (stream.isLive) "LIVE" else stream.containerExtension?.uppercase(),
            error            = null,
            activeStream     = stream,
            activeEpisode    = null,
            activeSeries     = null,
            isCurrentFavorite = isFav
        ) }
        prepareAndPlay(StreamUrlBuilder.buildForStream(credentials, stream), resumePositionMs)
    }

    fun loadEpisode(episode: EpisodeItem, series: SeriesItem, credentials: AuthCredentials) {
        this.credentials = credentials
        val store = LibraryStore(getApplication(), credentials)
        this.libraryStore = store
        val isFav = store.isFavorite(episode.id, "episode")

        val historyItem = store.getHistory().firstOrNull { it.id == episode.id && it.type == "episode" }
        val resumePositionMs = if (historyItem != null && historyItem.durationMs > 0L) {
            val fraction = historyItem.lastWatchedPositionMs.toFloat() / historyItem.durationMs
            if (fraction < 0.90f && historyItem.lastWatchedPositionMs > 3_000L) {
                historyItem.lastWatchedPositionMs
            } else 0L
        } else 0L

        _uiState.update { it.copy(
            playbackState    = PlaybackState.LOADING,
            activeStreamName = episode.title,
            activeMimeHint   = episode.containerExtension.uppercase(),
            error            = null,
            activeStream     = null,
            activeEpisode    = episode,
            activeSeries     = series,
            isCurrentFavorite = isFav
        ) }
        prepareAndPlay(StreamUrlBuilder.buildForEpisode(credentials, episode), resumePositionMs)
    }

    fun toggleCurrentFavorite() {
        val store = libraryStore ?: return
        val currentState = _uiState.value
        val isFav = !currentState.isCurrentFavorite
        
        if (currentState.activeStream != null) {
            val stream = currentState.activeStream
            val type = stream.streamType ?: "live"
            val id = stream.streamId.toString()
            if (isFav) {
                store.addFavorite(
                    LibraryItem(
                        id = id,
                        name = stream.name,
                        type = type,
                        iconUrl = stream.streamIcon,
                        categoryId = stream.categoryId,
                        rating = stream.rating,
                        containerExtension = stream.containerExtension
                    )
                )
            } else {
                store.removeFavorite(id, type)
            }
        } else if (currentState.activeEpisode != null) {
            val episode = currentState.activeEpisode
            val id = episode.id
            if (isFav) {
                store.addFavorite(
                    LibraryItem(
                        id = id,
                        parentId = currentState.activeSeries?.seriesId?.toString(),
                        name = if (currentState.activeSeries != null) "${currentState.activeSeries.name} - S${String.format("%02d", episode.season)}E${String.format("%02d", episode.episodeNum)}" else episode.title,
                        type = "episode",
                        iconUrl = episode.info?.movieImage ?: currentState.activeSeries?.cover,
                        categoryId = currentState.activeSeries?.categoryId,
                        rating = episode.info?.rating,
                        containerExtension = episode.containerExtension
                    )
                )
            } else {
                store.removeFavorite(id, "episode")
            }
        }
        _uiState.update { it.copy(isCurrentFavorite = isFav) }
        onHudInteraction()
    }

    fun pause() {
        saveCurrentProgress()
        exoPlayer.pause()
    }

    fun resume() { if (_uiState.value.hasActiveMedia) exoPlayer.play() }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            saveCurrentProgress()
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        onHudInteraction()
    }

    /**
     * Seeks forward or backward by [offsetMs] milliseconds.
     * Clamped to [0, duration]. No-op on live streams.
     */
    fun seekRelative(offsetMs: Long) {
        if (_uiState.value.isLive) return
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: return
        val newPos   = (exoPlayer.currentPosition + offsetMs).coerceIn(0L, duration)
        exoPlayer.seekTo(newPos)
        onHudInteraction()
    }

    /**
     * Restarts playback from 0:00.
     */
    fun replay() {
        if (_uiState.value.isLive) return
        exoPlayer.seekTo(0L)
        exoPlayer.play()
        onHudInteraction()
    }

    /**
     * Seeks to an absolute timestamp in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        if (_uiState.value.isLive) return
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: return
        val newPos   = positionMs.coerceIn(0L, duration)
        exoPlayer.seekTo(newPos)
        onHudInteraction()
    }

    fun stop() {
        saveCurrentProgress()
        stopPositionPolling()
        stopHudTimer()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _uiState.value = PlayerUiState()
    }

    // =========================================================================
    //  Public API — HUD visibility
    // =========================================================================

    /**
     * Shows the HUD and resets the auto-hide timer.
     *
     * Call this on EVERY D-Pad key event from [PlayerPanel]'s `onPreviewKeyEvent`.
     *
     * The timer is **suspended** when a track-selection menu is open so the
     * HUD can never disappear while the user is picking a track.
     */
    fun onHudInteraction() {
        _uiState.update { it.copy(isHudVisible = true) }
        scheduleHudHide()
    }

    /** Immediately hides the HUD and cancels any pending hide timer. */
    fun hideHud() {
        stopHudTimer()
        _uiState.update { it.copy(isHudVisible = false) }
    }

    // =========================================================================
    //  Public API — Track selection menus
    // =========================================================================

    /**
     * Opens the audio or subtitle track selection panel.
     * Cancels the HUD auto-hide timer while the menu is open.
     */
    fun showTrackMenu(type: TrackMenuType) {
        stopHudTimer()   // keep HUD alive until user dismisses
        _uiState.update { it.copy(
            isHudVisible    = true,
            activeTrackMenu = type
        ) }
    }

    /** Closes the track menu and restarts the HUD auto-hide timer. */
    fun dismissTrackMenu() {
        _uiState.update { it.copy(activeTrackMenu = TrackMenuType.NONE) }
        scheduleHudHide()
    }

    // =========================================================================
    //  Public API — Track selection
    // =========================================================================

    /**
     * Forces playback to use the audio rendition identified by
     * [groupIndex] (into [rawAudioGroups]) and [trackIndex].
     *
     * Clears any previous audio override before applying the new one so
     * old constraints never interfere.
     */
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val trackGroup = rawAudioGroups.getOrNull(groupIndex)?.mediaTrackGroup ?: return
        applyTrackOverride(C.TRACK_TYPE_AUDIO, trackGroup, trackIndex, enable = true)
        dismissTrackMenu()
    }

    /**
     * Forces playback to use the subtitle rendition identified by
     * [groupIndex] (into [rawTextGroups]) and [trackIndex].
     *
     * Any previously disabled-subtitle flag is cleared first.
     */
    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val trackGroup = rawTextGroups.getOrNull(groupIndex)?.mediaTrackGroup ?: return
        applyTrackOverride(C.TRACK_TYPE_TEXT, trackGroup, trackIndex, enable = true)
        dismissTrackMenu()
    }

    /**
     * Explicitly disables all subtitle/text tracks.
     *
     * This is the "(None)" option. ExoPlayer will not render any text overlay
     * until [selectSubtitleTrack] is called again.
     *
     * Implementation uses [setTrackTypeDisabled] which is the correct Media3
     * API — NOT simply clearing overrides, which would let the player
     * re-select a default subtitle automatically.
     */
    fun disableSubtitles() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, /* disabled = */ true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        _uiState.update { it.copy(areSubtitlesEnabled = false) }
        dismissTrackMenu()
    }

    // =========================================================================
    //  Private: track override helper
    // =========================================================================

    /**
     * Applies a [TrackSelectionOverride] for the given [trackType].
     *
     * @param enable  When false, disables the track type entirely (used for
     *                subtitles-off). When true, enables it and applies the override.
     */
    private fun applyTrackOverride(
        trackType:  Int,
        trackGroup: TrackGroup,
        trackIndex: Int,
        enable:     Boolean
    ) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, !enable)
            .clearOverridesOfType(trackType)
            .setOverrideForType(TrackSelectionOverride(trackGroup, listOf(trackIndex)))
            .build()

        // Reflect enabled state in UiState immediately (don't wait for onTracksChanged).
        if (trackType == C.TRACK_TYPE_TEXT) {
            _uiState.update { it.copy(areSubtitlesEnabled = enable) }
        }
    }

    // =========================================================================
    //  Private: track parsing
    // =========================================================================

    /**
     * Converts the raw [Tracks] from [onTracksChanged] into [TrackInfo] lists.
     *
     * Stores the raw [Tracks.Group] references in [rawAudioGroups] / [rawTextGroups]
     * so [selectAudioTrack] / [selectSubtitleTrack] can reconstruct overrides.
     *
     * Filters out tracks that are not in a supported format for this device;
     * they are still shown in the UI but marked [TrackInfo.isSupported] = false.
     */
    private fun updateAvailableTracks(tracks: Tracks) {
        rawAudioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        rawTextGroups  = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT  }

        val audioList = rawAudioGroups.flatMapIndexed { gi, group ->
            (0 until group.length).map { ti ->
                val fmt = group.getTrackFormat(ti)
                TrackInfo(
                    groupIndex  = gi,
                    trackIndex  = ti,
                    label       = buildTrackLabel(fmt, C.TRACK_TYPE_AUDIO),
                    language    = fmt.language,
                    isSelected  = group.isTrackSelected(ti),
                    isSupported = group.isTrackSupported(ti)
                )
            }
        }

        val subList = rawTextGroups.flatMapIndexed { gi, group ->
            (0 until group.length).map { ti ->
                val fmt = group.getTrackFormat(ti)
                TrackInfo(
                    groupIndex  = gi,
                    trackIndex  = ti,
                    label       = buildTrackLabel(fmt, C.TRACK_TYPE_TEXT),
                    language    = fmt.language,
                    isSelected  = group.isTrackSelected(ti),
                    isSupported = group.isTrackSupported(ti)
                )
            }
        }

        _uiState.update { it.copy(
            audioTrackOptions    = audioList,
            subtitleTrackOptions = subList,
            areSubtitlesEnabled  = subList.any { it.isSelected }
        ) }
    }

    /**
     * Builds a human-readable label for a track [Format].
     *
     * Priority: Language display name → stream label → codec hint → fallback.
     *
     * Audio example:  "English · AC3 · 5.1"
     * Sub example:    "Arabic"
     * Fallback:       "Track 3"
     */
    private fun buildTrackLabel(format: Format, trackType: Int): String {
        val parts = mutableListOf<String>()

        // Language (convert BCP-47 tag to readable name)
        format.language?.takeIf { it.isNotBlank() }?.let { tag ->
            val locale      = Locale.forLanguageTag(tag)
            val displayName = locale.getDisplayLanguage(Locale.ENGLISH)
            parts += if (displayName.isNotBlank() && displayName != tag) displayName
                     else tag.uppercase()
        }

        // Free-text label from the stream (e.g. "Commentary", "Dub", "SDH")
        format.label?.takeIf { it.isNotBlank() }?.let { parts += it }

        // Audio-specific enrichment
        if (trackType == C.TRACK_TYPE_AUDIO) {
            format.sampleMimeType?.let { mime ->
                val codec = when {
                    "ac-3"  in mime || "ac3"  in mime -> "AC3"
                    "eac-3" in mime || "eac3" in mime -> "E-AC3"
                    "aac"   in mime                   -> "AAC"
                    "mp4a"  in mime                   -> "AAC"
                    "vorbis" in mime                  -> "Vorbis"
                    "opus"   in mime                  -> "Opus"
                    "mp3"    in mime                  -> "MP3"
                    else                              -> null
                }
                codec?.let { parts += it }
            }

            if (format.channelCount > 0) {
                parts += when (format.channelCount) {
                    1    -> "Mono"
                    2    -> "Stereo"
                    6    -> "5.1"
                    8    -> "7.1"
                    else -> "${format.channelCount}ch"
                }
            }
        }

        return parts.joinToString(" · ").ifBlank { "Track ${format.id ?: "?"}" }
    }

    // =========================================================================
    //  Private: HUD timer
    // =========================================================================

    private fun scheduleHudHide() {
        // Do NOT schedule a hide while the track menu is open.
        if (_uiState.value.isTrackMenuOpen) return

        hudHideJob?.cancel()
        hudHideJob = viewModelScope.launch {
            delay(HUD_TIMEOUT_MS)
            if (isActive) {
                _uiState.update { it.copy(isHudVisible = false) }
            }
        }
    }

    private fun stopHudTimer() {
        hudHideJob?.cancel()
        hudHideJob = null
    }

    // =========================================================================
    //  Private: position polling
    // =========================================================================

    private fun startPositionPolling() {
        stopPositionPolling()
        positionPollingJob = viewModelScope.launch {
            while (isActive) {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                if (_uiState.value.videoWidth <= 0 || _uiState.value.videoHeight <= 0) {
                    checkAndExtractVideoResolution()
                }
                _uiState.update { it.copy(
                    currentPositionMs = currentPos,
                    durationMs        = duration,
                    bufferedPercent   = exoPlayer.bufferedPercentage
                ) }

                saveCurrentProgress()
                delay(1000)
            }
        }
    }

    private fun updateVideoResolution(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _uiState.update { current ->
                if (current.videoWidth != width || current.videoHeight != height) {
                    current.copy(videoWidth = width, videoHeight = height)
                } else {
                    current
                }
            }
        }
    }

    private fun checkAndExtractVideoResolution() {
        val vs = exoPlayer.videoSize
        if (vs.width > 0 && vs.height > 0) {
            updateVideoResolution(vs.width, vs.height)
            return
        }
        val vf = exoPlayer.videoFormat
        if (vf != null && vf.width > 0 && vf.height > 0) {
            updateVideoResolution(vf.width, vf.height)
        }
    }

    private fun saveCurrentProgress() {
        val currentState = _uiState.value
        val store = libraryStore ?: return
        if (currentState.isLive) return
        val currentPos = exoPlayer.currentPosition
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        if (duration <= 0L || currentPos <= 0L) return

        val progressFraction = currentPos.toFloat() / duration
        val id = currentState.activeStream?.streamId?.toString() ?: currentState.activeEpisode?.id ?: return
        val type = if (currentState.activeStream != null) (currentState.activeStream.streamType ?: "movie") else "episode"

        if (progressFraction >= 0.90f) {
            store.removeFromHistory(id, type)
        } else {
            val item = if (currentState.activeStream != null) {
                val stream = currentState.activeStream
                LibraryItem(
                    id = id,
                    name = stream.name,
                    type = type,
                    iconUrl = stream.streamIcon,
                    categoryId = stream.categoryId,
                    rating = stream.rating,
                    containerExtension = stream.containerExtension,
                    lastWatchedPositionMs = currentPos,
                    durationMs = duration
                )
            } else {
                val episode = currentState.activeEpisode ?: return
                LibraryItem(
                    id = id,
                    parentId = currentState.activeSeries?.seriesId?.toString(),
                    name = if (currentState.activeSeries != null) "${currentState.activeSeries.name} - S${String.format("%02d", episode.season)}E${String.format("%02d", episode.episodeNum)}" else episode.title,
                    type = type,
                    iconUrl = episode.info?.movieImage ?: currentState.activeSeries?.cover,
                    categoryId = currentState.activeSeries?.categoryId,
                    rating = episode.info?.rating,
                    containerExtension = episode.containerExtension,
                    lastWatchedPositionMs = currentPos,
                    durationMs = duration
                )
            }
            store.saveHistoryItem(item)
        }
    }

    private fun clearCurrentProgressFromHistory() {
        val currentState = _uiState.value
        val store = libraryStore ?: return
        val id = currentState.activeStream?.streamId?.toString() ?: currentState.activeEpisode?.id ?: return
        val type = if (currentState.activeStream != null) (currentState.activeStream.streamType ?: "movie") else "episode"
        store.removeFromHistory(id, type)
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    // =========================================================================
    //  Private: prepare ExoPlayer
    // =========================================================================

    private fun prepareAndPlay(url: String, startPositionMs: Long = 0L) {
        _uiState.update { it.copy(videoWidth = 0, videoHeight = 0) }
        exoPlayer.stop()
        if (startPositionMs > 0L) {
            exoPlayer.setMediaItem(MediaItem.fromUri(url), startPositionMs)
        } else {
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // =========================================================================
    //  Lifecycle
    // =========================================================================

    override fun onCleared() {
        super.onCleared()
        stopPositionPolling()
        stopHudTimer()
        exoPlayer.release()
    }

    // =========================================================================
    //  Player.Listener (inner class)
    // =========================================================================

    private inner class PlayerEventListener : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionPolling() else stopPositionPolling()
        }

        override fun onPlaybackStateChanged(state: Int) {
            val newState = when (state) {
                Player.STATE_BUFFERING -> PlaybackState.LOADING
                Player.STATE_READY     -> {
                    checkAndExtractVideoResolution()
                    PlaybackState.READY
                }
                Player.STATE_ENDED     -> {
                    stopPositionPolling()
                    clearCurrentProgressFromHistory()
                    PlaybackState.ENDED
                }
                else                   -> PlaybackState.IDLE
            }
            _uiState.update { it.copy(playbackState = newState) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateVideoResolution(videoSize.width, videoSize.height)
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableTracks(tracks)
            checkAndExtractVideoResolution()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionPolling()
            _uiState.update { it.copy(
                playbackState = PlaybackState.ERROR,
                isPlaying     = false,
                error         = buildErrorMessage(error)
            ) }
        }
    }

    // =========================================================================
    //  Error message formatting
    // =========================================================================

    private fun buildErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network error. Check your connection and server URL."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Stream unavailable. The server returned an error."
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This stream format is not supported on your device."
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
            "Fell behind the live stream. Reconnecting..."
        else -> error.localizedMessage ?: "Playback error (${error.errorCode})."
    }

    // =========================================================================
    //  Constants
    // =========================================================================

    private companion object {
        const val HUD_TIMEOUT_MS = 3_000L
    }
}
