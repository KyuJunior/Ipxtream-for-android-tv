package com.ipxtream.tv.ui.player

import com.ipxtream.tv.R
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import kotlinx.coroutines.Dispatchers
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
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // =========================================================================
    //  ExoPlayer
    // =========================================================================

    private val appSettingsStore = com.ipxtream.tv.data.local.AppSettingsStore.getInstance(application)

    private val trackSelector = DefaultTrackSelector(application).apply {
        setParameters(
            buildUponParameters()
                .setTunnelingEnabled(true)
        )
    }

    // Settings → Streaming Buffer Profile. ExoPlayer can't change buffers on a live player,
    // so the profile is read when the player is created (next app start).
    private val bufferProfile = appSettingsStore.settings.value.bufferProfile

    private val loadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            /* minBufferMs = */ bufferProfile.minBufferMs,
            /* maxBufferMs = */ bufferProfile.maxBufferMs,
            /* bufferForPlaybackMs = */ bufferProfile.playbackBufferMs,
            /* bufferForPlaybackAfterRebufferMs = */ (bufferProfile.playbackBufferMs * 2).coerceAtMost(bufferProfile.minBufferMs)
        )
        .setBackBuffer(/* backBufferDurationMs = */ 20_000, /* retainBackBufferFromKeyframe = */ true)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        // A codec that fails to start falls back to the next one (often a software decoder). The
        // FFmpeg audio renderer comes after the device's own, so AC3/E-AC3/DTS play on phones without them.
        .setRenderersFactory(
            DefaultRenderersFactory(application)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        )
        // Panels often redirect HTTPS streams to an HTTP edge server; Media3 refuses that by default.
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    application,
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                )
            )
        )
        .setHandleAudioBecomingNoisy(true)
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

    init {
        viewModelScope.launch {
            appSettingsStore.settings.collect { settings ->
                applySettings(settings)
            }
        }
    }

    private fun applySettings(settings: com.ipxtream.tv.data.local.AppSettings) {
        val trackParamBuilder = trackSelector.buildUponParameters()
            .setTunnelingEnabled(settings.decoderMode == com.ipxtream.tv.data.local.DecoderMode.HARDWARE_FIRST)
            .setPreferredAudioLanguage(if (settings.preferredAudioLanguage == "default") null else settings.preferredAudioLanguage)
            .setPreferredTextLanguage(if (settings.defaultSubtitlesEnabled) settings.preferredSubtitleLanguage else null)
            .setSelectUndeterminedTextLanguage(false)

        trackSelector.setParameters(trackParamBuilder)
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
    private var seekSaveJob: Job? = null
    private var lastProgressSaveMs = 0L
    private var hudHideJob:         Job? = null
    private var reconnectJob:       Job? = null

    // ─── Live reconnect ────────────────────────────────────────────────────────
    private var currentUrl: String? = null
    private var reconnectAttempts = 0
    /** False until the current title has played once; until then an HLS/TS swap is worth trying. */
    private var everReady = false

    // ─── Scoped Storage ────────────────────────────────────────────────────────
    private var credentials: AuthCredentials? = null
    private var libraryStore: LibraryStore? = null

    // =========================================================================
    //  Public API — playback control
    // =========================================================================

    fun loadStream(stream: StreamItem, credentials: AuthCredentials) {
        leaveCurrentTitle()
        this.credentials = credentials
        val store = com.ipxtream.tv.di.AppContainer.from(getApplication()).libraryStore(credentials)
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
        prepareAndPlay(StreamUrlBuilder.buildForStream(credentials, stream), mediaKey(streamType, stream.streamId.toString()), resumePositionMs)
    }

    fun loadEpisode(episode: EpisodeItem, series: SeriesItem, credentials: AuthCredentials) {
        leaveCurrentTitle()
        this.credentials = credentials
        val store = com.ipxtream.tv.di.AppContainer.from(getApplication()).libraryStore(credentials)
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
            activeMimeHint   = episode.containerExtension?.uppercase() ?: "MP4",
            error            = null,
            activeStream     = null,
            activeEpisode    = episode,
            activeSeries     = series,
            isCurrentFavorite = isFav
        ) }
        prepareAndPlay(StreamUrlBuilder.buildForEpisode(credentials, episode), mediaKey("episode", episode.id), resumePositionMs)
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
        saveProgressAfterSeek()
        onHudInteraction()
    }

    /** Try Again on the error card. A live stream rejoins at the live edge, not where it stalled. */
    fun retry() {
        reconnectJob?.cancel()
        reconnectAttempts = 0
        if (_uiState.value.isLive) exoPlayer.seekToDefaultPosition()
        exoPlayer.prepare()
        exoPlayer.play()
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
        saveProgressAfterSeek()
        onHudInteraction()
    }

    fun stop() {
        // Written before returning so the dashboard's Continue Watching row sees it right away.
        saveCurrentProgress(inBackground = false)
        seekSaveJob?.cancel()
        reconnectJob?.cancel()
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
            val displayName = locale.getDisplayLanguage(Locale.getDefault())
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
                    1    -> text(R.string.track_mono)
                    2    -> text(R.string.track_stereo)
                    6    -> "5.1"
                    8    -> "7.1"
                    else -> "${format.channelCount}ch"
                }
            }
        }

        return parts.joinToString(" · ").ifBlank { text(R.string.track_n, format.id ?: "?") }
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

                // The position ticks every second for the HUD; history is written every 10 s.
                if (System.currentTimeMillis() - lastProgressSaveMs >= PROGRESS_SAVE_INTERVAL_MS) {
                    saveCurrentProgress()
                }
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

    /** Saves once the user stops scrubbing, so holding Right doesn't write on every step. */
    private fun saveProgressAfterSeek() {
        seekSaveJob?.cancel()
        seekSaveJob = viewModelScope.launch {
            delay(1500)
            saveCurrentProgress()
        }
    }

    /**
     * Records the current position in watch history. Player state is read here on the main
     * thread; the JSON write runs on [Dispatchers.IO] unless [inBackground] is false.
     */
    private fun saveCurrentProgress(inBackground: Boolean = true) {
        lastProgressSaveMs = System.currentTimeMillis()
        val currentState = _uiState.value
        val store = libraryStore ?: return
        if (currentState.isLive) return
        val currentPos = exoPlayer.currentPosition
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        if (duration <= 0L || currentPos <= 0L) return

        val progressFraction = currentPos.toFloat() / duration
        val id = currentState.activeStream?.streamId?.toString() ?: currentState.activeEpisode?.id ?: return
        val type = if (currentState.activeStream != null) (currentState.activeStream.streamType ?: "movie") else "episode"
        // The player may still hold the previous title while the state already names the next one.
        if (exoPlayer.currentMediaItem?.mediaId != mediaKey(type, id)) return

        if (progressFraction >= 0.90f) {
            runStoreWrite(inBackground) { store.removeFromHistory(id, type) }
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
                    season = episode.season,
                    episodeNum = episode.episodeNum,
                    lastWatchedPositionMs = currentPos,
                    durationMs = duration
                )
            }
            runStoreWrite(inBackground) { store.saveHistoryItem(item) }
        }
    }

    private fun runStoreWrite(inBackground: Boolean, write: () -> Unit) {
        if (inBackground) viewModelScope.launch(Dispatchers.IO) { write() } else write()
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

    /** Saves where the outgoing title stopped while the state still names it, then stops it. */
    private fun leaveCurrentTitle() {
        reconnectJob?.cancel()
        if (_uiState.value.hasActiveMedia) saveCurrentProgress()
        exoPlayer.stop()
    }

    private fun mediaKey(type: String, id: String) = "$type:$id"

    private fun mediaItem(url: String, key: String) = MediaItem.Builder().setUri(url).setMediaId(key).build()

    private fun prepareAndPlay(url: String, key: String, startPositionMs: Long = 0L) {
        _uiState.update { it.copy(videoWidth = 0, videoHeight = 0) }
        currentUrl = url
        reconnectAttempts = 0
        everReady = false
        if (startPositionMs > 0L) {
            exoPlayer.setMediaItem(mediaItem(url, key), startPositionMs)
        } else {
            exoPlayer.setMediaItem(mediaItem(url, key))
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /**
     * Live only: reconnects quietly after a drop, waiting longer each time. A channel that never
     * started also alternates between HLS and MPEG-TS, for providers or accounts that serve one only.
     * False when it has given up, so the caller shows the error card.
     */
    private fun scheduleReconnect(): Boolean {
        val limit = if (everReady) MAX_LIVE_RECONNECTS else MAX_START_ATTEMPTS
        if (reconnectAttempts >= limit) return false
        val url = currentUrl ?: return false
        val key = exoPlayer.currentMediaItem?.mediaId ?: return false
        val wait = LiveReconnect.delayMs(reconnectAttempts)
        reconnectAttempts++
        val nextUrl = if (everReady) url else LiveReconnect.alternate(url)
        _uiState.update { it.copy(playbackState = PlaybackState.LOADING, isPlaying = false) }
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(wait)
            if (nextUrl != url) {
                currentUrl = nextUrl
                exoPlayer.setMediaItem(mediaItem(nextUrl, key))
            } else {
                exoPlayer.seekToDefaultPosition()
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        return true
    }

    // =========================================================================
    //  Lifecycle
    // =========================================================================

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.hasActiveMedia) saveCurrentProgress(inBackground = false)
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
            if (isPlaying) {
                startPositionPolling()
            } else {
                stopPositionPolling()
                // Paused, buffering or ended: keep the last position the user saw.
                if (_uiState.value.hasActiveMedia) saveCurrentProgress()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            val newState = when (state) {
                Player.STATE_BUFFERING -> PlaybackState.LOADING
                Player.STATE_READY     -> {
                    everReady = true
                    reconnectAttempts = 0
                    checkAndExtractVideoResolution()
                    PlaybackState.READY
                }
                // A live stream has no end: the provider dropped it.
                Player.STATE_ENDED     -> if (_uiState.value.isLive) {
                    stopPositionPolling()
                    if (scheduleReconnect()) return
                    _uiState.update { it.copy(error = text(R.string.player_err_network)) }
                    PlaybackState.ERROR
                } else {
                    stopPositionPolling()
                    clearCurrentProgressFromHistory()
                    PlaybackState.ENDED
                }
                // Media3 goes idle after an error: keep the error card, or the spinner while reconnecting.
                else                   -> when {
                    reconnectJob?.isActive == true -> PlaybackState.LOADING
                    _uiState.value.playbackState == PlaybackState.ERROR -> PlaybackState.ERROR
                    else -> PlaybackState.IDLE
                }
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
            if (_uiState.value.isLive && scheduleReconnect()) return
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

    private fun text(id: Int, vararg args: Any): String =
        com.ipxtream.tv.util.LocaleHelper.localized(getApplication()).getString(id, *args)

    private fun buildErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            text(R.string.player_err_network)
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            when ((error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode) {
                401, 403 -> text(R.string.player_err_rejected)
                else -> text(R.string.player_err_http)
            }
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            text(R.string.player_err_format)
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
            text(R.string.player_err_behind_live)
        // The exception text can carry the stream URL, which holds the login.
        else -> error.localizedMessage?.let { com.ipxtream.tv.data.api.ApiClient.redactCredentials(it) }
            ?: text(R.string.player_err_code, error.errorCode)
    }

    // =========================================================================
    //  Constants
    // =========================================================================

    private companion object {
        const val HUD_TIMEOUT_MS = 5_000L
        const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
        /** About two and a half minutes of retries once a channel has played. */
        const val MAX_LIVE_RECONNECTS = 9
        /** A channel that never started: TS, HLS, TS within about 7 s. */
        const val MAX_START_ATTEMPTS = 3
    }
}

/** Pure parts of the live reconnect, kept apart so they can be tested on the JVM. */
internal object LiveReconnect {
    /** 1 s, 2 s, 4 s … up to 30 s. */
    fun delayMs(attempt: Int): Long = (1_000L shl attempt.coerceIn(0, 5)).coerceAtMost(30_000L)

    /** HLS and MPEG-TS for the same live channel, swapped; other addresses stay as they are. */
    fun alternate(url: String): String =
        StreamUrlBuilder.toTransportStream(url) ?: if (url.endsWith(".ts")) url.removeSuffix(".ts") + ".m3u8" else url
}
