package com.ipxtream.tv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.TextSecondary
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.Hairline
import com.ipxtream.tv.ui.theme.IpxMotion
import com.ipxtream.tv.ui.theme.SurfaceElevated
import com.ipxtream.tv.ui.dashboard.components.PremiumButton

/**
 * Embedded player composable — the root of the Phase 4 player panel, now
 * fully upgraded with Phase 5 features.
 *
 * ## What changed in Phase 5
 * - `PlayerView.useController = false` — built-in controls are **disabled**.
 *   All transport controls, track selection, and progress are now handled by
 *   the custom [PlayerHud] + [TrackSelectionMenu] composables.
 * - `Modifier.onPreviewKeyEvent` intercepts EVERY D-Pad key **before** it
 *   reaches the [AndroidView] so the HUD can wake up and keys can be routed
 *   to the correct handler (play/pause, seek, or close menu).
 *
 * ## D-Pad key routing logic
 * ```
 * Any KeyDown
 *   ├── Always: call viewModel.onHudInteraction() (show HUD / reset timer)
 *   ├── If track menu is open:
 *   │     BACK → dismissTrackMenu   (consume)
 *   │     other → pass to menu list (don't consume)
 *   ├── Else if HUD is visible:
 *   │     CENTER / MEDIA_PLAY_PAUSE → togglePlayPause  (consume)
 *   │     LEFT / MEDIA_REWIND      → seekRelative(-30s)(consume)
 *   │     RIGHT / MEDIA_FF         → seekRelative(+30s)(consume)
 *   │     BACK                     → hideHud           (consume)
 *   │     other                    → don't consume
 *   └── Else (HUD was just woken up by this key):
 *         don't consume (key intent already fulfilled by showing HUD)
 * ```
 *
 * @param exoPlayer   The live [ExoPlayer] instance from [PlayerViewModel].
 * @param uiState     Reactive state from [PlayerViewModel.uiState].
 * @param viewModel   The [PlayerViewModel] - passed directly so key events can
 *                    call VM methods without lambda parameter explosion.
 * @param onStop      Called when the user closes the panel (BACK when HUD hidden,
 *                    or Close button press).
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerPanel(
    exoPlayer:       ExoPlayer,
    uiState:         PlayerUiState,
    viewModel:       PlayerViewModel,
    onStop:          () -> Unit,
    onNextEpisode:   (() -> Unit)? = null,
    onPrevEpisode:   (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onNextChannel:   (() -> Unit)? = null,
    onPrevChannel:   (() -> Unit)? = null,
    nextEpisodeLabel: String? = null,
    modifier:        Modifier = Modifier
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // -- PlayerView: created ONCE, never recreated ----------------------------
    val appSettings by remember(context) { com.ipxtream.tv.data.local.AppSettingsStore.getInstance(context).settings }.collectAsState()

    val playerView = remember {
        PlayerView(context).apply {
            player                  = exoPlayer
            useController           = false   // custom HUD replaces built-in controls
            layoutParams            = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable             = false
            isFocusableInTouchMode  = false
            descendantFocusability  = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }.apply {
        resizeMode = when (appSettings.defaultAspectRatio) {
            com.ipxtream.tv.data.local.AspectRatioMode.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            com.ipxtream.tv.data.local.AspectRatioMode.FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            com.ipxtream.tv.data.local.AspectRatioMode.ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, appSettings.subtitleTextSize.sizeSp)
        // Lift subtitles clear of the controls while the overlay is up.
        subtitleView?.setBottomPaddingFraction(
            if (uiState.isHudVisible) 0.30f else androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
        )
    }

    // ── Lifecycle: Pause on background, Resume on foreground ──────────────────
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    playerView.player = exoPlayer
                    if (uiState.hasActiveMedia) exoPlayer.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.pause()
                    playerView.player = null   // release video surface
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerView.player = exoPlayer  // re-attach on compose teardown
        }
    }

    // ── Prevent TV from going to sleep or screensaver while playing ───────────
    val activity = context as? android.app.Activity
    DisposableEffect(uiState.isPlaying, appSettings.keepScreenAwake) {
        val keepAwake = uiState.isPlaying && appSettings.keepScreenAwake
        if (keepAwake) {
            playerView.keepScreenOn = true
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            playerView.keepScreenOn = false
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            playerView.keepScreenOn = false
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Focus Requester to steal focus on launch ─────────────────────────────
    val focusRequester = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(uiState.hasActiveMedia, uiState.activeEpisode, uiState.activeStream) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    androidx.compose.runtime.LaunchedEffect(uiState.isHudVisible) {
        if (uiState.isHudVisible) {
            delay(50)
            runCatching { playButtonFocus.requestFocus() }
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // ── Live TV Channel Surfing Banner ─────────────────────────────────────────
    var showChannelBanner by remember { mutableStateOf(false) }
    var lastChannelId by remember { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(uiState.activeStream?.streamId) {
        val currentId = uiState.activeStream?.streamId
        if (currentId != null && currentId != lastChannelId) {
            lastChannelId = currentId
            if (uiState.isLive) {
                showChannelBanner = true
                delay(2800L)
                showChannelBanner = false
            }
        }
    }

    // ── Centre feedback (play/pause/seek glyph that blooms and fades) ─────────
    var feedback by remember { mutableStateOf<HudFeedback?>(null) }
    val togglePlayPause: () -> Unit = {
        val willPlay = !exoPlayer.isPlaying
        viewModel.togglePlayPause()
        feedback = HudFeedback(if (willPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, 0)
    }
    val seekBy: (Long) -> Unit = { delta ->
        if (!uiState.isLive) {
            viewModel.seekRelative(delta)
            feedback = HudFeedback(if (delta < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, if (delta < 0) -1 else 1)
        }
    }

    // ── End-of-title / error overlays take focus so their buttons work ────────
    val overlayFocus = remember { FocusRequester() }
    val isEndOrError = uiState.playbackState == PlaybackState.ENDED || uiState.playbackState == PlaybackState.ERROR
    androidx.compose.runtime.LaunchedEffect(uiState.playbackState) {
        if (isEndOrError) {
            viewModel.hideHud()
            delay(120)
            runCatching { overlayFocus.requestFocus() }
        }
    }

    // ── Root Box with D-Pad interception ─────────────────────────────────────
    var isRootFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDeep)
            .focusRequester(focusRequester)
            .onFocusChanged { isRootFocused = it.isFocused }
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // End/error cards own the D-Pad while they're up; BACK still leaves.
                if (isEndOrError && !uiState.isHudVisible) {
                    if (event.key == Key.Back) { onStop(); return@onPreviewKeyEvent true }
                    if (isRootFocused) runCatching { overlayFocus.requestFocus() }
                    return@onPreviewKeyEvent false
                }

                // Hardware remote Channel Up / Down keys (always handled)
                val isChanUp = event.key == Key.ChannelUp
                val isChanDown = event.key == Key.ChannelDown

                if (isChanUp && onNextChannel != null) {
                    onNextChannel()
                    showChannelBanner = true
                    return@onPreviewKeyEvent true
                }
                if (isChanDown && onPrevChannel != null) {
                    onPrevChannel()
                    showChannelBanner = true
                    return@onPreviewKeyEvent true
                }

                when {
                    // Track menu is open — only intercept BACK; menu list handles navigation.
                    uiState.isTrackMenuOpen -> {
                        viewModel.onHudInteraction()
                        if (event.key == Key.Back || event.key == Key.DirectionLeft) {
                            viewModel.dismissTrackMenu()
                            true  // consumed
                        } else false
                    }

                    // HUD is visible — handle playback controls.
                    uiState.isHudVisible -> {
                        viewModel.onHudInteraction()

                        // Allow D-Pad navigation/clicking inside HUD components if root isn't focused
                        val isDpadNav = event.key in listOf(
                            Key.DirectionCenter, Key.Enter, Key.DirectionLeft, 
                            Key.DirectionRight, Key.DirectionUp, Key.DirectionDown
                        )
                        if (isDpadNav && !isRootFocused) return@onPreviewKeyEvent false

                        // If root IS focused, map navigation keys to quick actions or push focus into the HUD
                        when (event.key) {
                            Key.DirectionUp,
                            Key.DirectionDown -> {
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.DirectionCenter,
                            Key.Enter,
                            Key.MediaPlay,
                            Key.MediaPause,
                            Key.MediaPlayPause  -> { togglePlayPause(); true }

                            Key.DirectionLeft,
                            Key.MediaRewind     -> { seekBy(-10_000L); true }

                            Key.DirectionRight,
                            Key.MediaFastForward -> { seekBy(10_000L); true }

                            Key.Back -> {
                                viewModel.hideHud()
                                runCatching { focusRequester.requestFocus() }
                                true  // consume — don't propagate to Activity
                            }

                            else -> false
                        }
                    }

                    // HUD was not visible
                    else -> {
                        when (event.key) {
                            Key.DirectionCenter,
                            Key.Enter,
                            Key.MediaPlayPause,
                            Key.MediaPause -> {
                                if (exoPlayer.isPlaying) {
                                    viewModel.pause()
                                    feedback = HudFeedback(Icons.Rounded.Pause, 0)
                                }
                                viewModel.onHudInteraction()
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.MediaPlay -> {
                                viewModel.resume()
                                feedback = HudFeedback(Icons.Rounded.PlayArrow, 0)
                                viewModel.onHudInteraction()
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.Back -> {
                                onStop()
                                true
                            }

                            Key.DirectionLeft,
                            Key.MediaRewind -> {
                                seekBy(-10_000L)
                                viewModel.onHudInteraction()
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.DirectionRight,
                            Key.MediaFastForward -> {
                                seekBy(10_000L)
                                viewModel.onHudInteraction()
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.DirectionUp -> {
                                if (uiState.isLive && onPrevChannel != null) {
                                    onPrevChannel()
                                    showChannelBanner = true
                                    true
                                } else {
                                    viewModel.onHudInteraction()
                                    runCatching { playButtonFocus.requestFocus() }
                                    true
                                }
                            }

                            Key.DirectionDown -> {
                                if (uiState.isLive && onNextChannel != null) {
                                    onNextChannel()
                                    showChannelBanner = true
                                    true
                                } else {
                                    viewModel.onHudInteraction()
                                    runCatching { playButtonFocus.requestFocus() }
                                    true
                                }
                            }

                            else -> false
                        }
                    }
                }
            }
    ) {
        // ── Video surface ─────────────────────────────────────────────────────
        AndroidView(
            factory  = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // ── Channel banner (live surfing) ─────────────────────────────────────
        val isBannerVisible = showChannelBanner && !uiState.isHudVisible && uiState.isLive && uiState.activeStream != null
        AnimatedVisibility(
            visible  = isBannerVisible,
            enter    = slideInVertically(tween(450, easing = IpxMotion.EaseApple)) { -it / 2 } + fadeIn(tween(IpxMotion.Standard)),
            exit     = slideOutVertically(tween(IpxMotion.Standard)) { -it / 2 } + fadeOut(tween(IpxMotion.Fast)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 36.dp, start = 48.dp)
        ) {
            val stream = uiState.activeStream
            if (stream != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xE61C1C1E))
                        .border(BorderStroke(1.dp, GlassStroke), RoundedCornerShape(18.dp))
                        .padding(start = 12.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 88.dp, height = 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2C2C2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!stream.streamIcon.isNullOrBlank()) {
                            AsyncImage(
                                model = stream.streamIcon,
                                contentDescription = stream.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LivePill()
                            if (stream.num != null && stream.num > 0) {
                                Text(
                                    text = "${stream.num}",
                                    style = IpxTypography.TitleMedium.copy(fontFeatureSettings = "tnum"),
                                    color = TextSecondary
                                )
                            }
                        }
                        Text(
                            text = stream.name,
                            style = IpxTypography.TitleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 520.dp)
                        )
                    }
                }
            }
        }

        // ── Buffering indicator ───────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.LOADING,
            enter    = fadeIn(tween(IpxMotion.Standard, delayMillis = 150)),
            exit     = fadeOut(tween(IpxMotion.Fast)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier    = Modifier.size(52.dp),
                    color       = Color.White,
                    trackColor  = Color.White.copy(alpha = 0.14f),
                    strokeWidth = 3.dp,
                    strokeCap   = StrokeCap.Round
                )
                if (uiState.bufferedPercent in 1..99) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text  = "${uiState.bufferedPercent}%",
                        style = IpxTypography.BodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = TextSecondary
                    )
                }
            }
        }

        // ── Play / pause / seek glyph ─────────────────────────────────────────
        FeedbackFlash(feedback = feedback, modifier = Modifier.fillMaxSize())

        // ── Overlay ───────────────────────────────────────────────────────────
        PlayerHud(
            uiState            = uiState,
            onTogglePlayPause  = togglePlayPause,
            onSeekBack10       = { seekBy(-10_000L) },
            onSeekForward10    = { seekBy(10_000L) },
            onSeekTo           = viewModel::seekTo,
            onPrev             = onPrevEpisode ?: { viewModel.seekTo(0L) },
            onNext             = onNextEpisode ?: { seekBy(10_000L) },
            onReplay           = viewModel::replay,
            onShowAudioMenu    = { viewModel.showTrackMenu(TrackMenuType.AUDIO) },
            onShowSubMenu      = { viewModel.showTrackMenu(TrackMenuType.SUBTITLE) },
            onToggleFavorite   = viewModel::toggleCurrentFavorite,
            onEpisodesClick    = onEpisodesClick,
            onClose            = onStop,
            playButtonFocus    = playButtonFocus,
            onHideAndFocusRoot = {
                viewModel.hideHud()
                runCatching { focusRequester.requestFocus() }
            },
            modifier           = Modifier.fillMaxSize()
        )

        // ── Track selection sheet ─────────────────────────────────────────────
        TrackSelectionMenu(
            menuType            = uiState.activeTrackMenu,
            audioTracks         = uiState.audioTrackOptions,
            subtitleTracks      = uiState.subtitleTrackOptions,
            areSubtitlesEnabled = uiState.areSubtitlesEnabled,
            onSelectAudio       = viewModel::selectAudioTrack,
            onSelectSubtitle    = viewModel::selectSubtitleTrack,
            onDisableSubtitles  = viewModel::disableSubtitles,
            onDismiss           = viewModel::dismissTrackMenu,
            modifier            = Modifier.fillMaxSize()
        )

        // ── Error card ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.ERROR,
            enter    = fadeIn(tween(IpxMotion.Standard)),
            exit     = fadeOut(tween(IpxMotion.Fast)),
            modifier = Modifier.fillMaxSize()
        ) {
            ErrorOverlay(
                message      = uiState.error ?: "Unknown playback error.",
                focusTarget  = overlayFocus,
                onRetry      = { exoPlayer.prepare(); exoPlayer.play() },
                onClose      = onStop
            )
        }

        // ── End of title: Up Next (series) or Watch again ─────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.ENDED,
            enter    = fadeIn(tween(IpxMotion.Slow, easing = IpxMotion.EaseOut)),
            exit     = fadeOut(tween(IpxMotion.Fast)),
            modifier = Modifier.fillMaxSize()
        ) {
            EndedOverlay(
                title         = uiState.activeSeries?.name ?: uiState.activeStreamName ?: "",
                nextLabel     = if (onNextEpisode != null) nextEpisodeLabel ?: "Next episode" else null,
                autoPlay      = appSettings.autoPlayNextEpisode,
                focusTarget   = overlayFocus,
                onPlayNext    = { onNextEpisode?.invoke() },
                onReplay      = { exoPlayer.seekTo(0); exoPlayer.play() },
                onClose       = onStop
            )
        }
    }
}

// =============================================================================
//  Centre feedback
// =============================================================================

/** A transport glyph to flash in the centre (side = -1 left, 0 centre, 1 right). */
internal class HudFeedback(val icon: ImageVector, val side: Int)

@Composable
private fun FeedbackFlash(feedback: HudFeedback?, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        launch {
            scale.snapTo(0.78f)
            scale.animateTo(1f, IpxMotion.focusSpring())
        }
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(110, easing = IpxMotion.EaseOut))
        delay(320)
        alpha.animateTo(0f, tween(420, easing = IpxMotion.EaseStandard))
    }
    if (feedback == null) return
    Box(
        modifier = modifier,
        contentAlignment = when (feedback.side) {
            -1   -> Alignment.CenterStart
            1    -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 200.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .size(108.dp)
                .clip(CircleShape)
                .background(Color(0x73000000))
                .border(1.dp, GlassStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Icon(
                imageVector = feedback.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

// =============================================================================
//  Error / ended cards
// =============================================================================

@Composable
private fun ErrorOverlay(
    message:     String,
    focusTarget: FocusRequester,
    onRetry:     () -> Unit,
    onClose:     () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceElevated)
                .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Can't play this right now", style = IpxTypography.HeadlineMedium, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = IpxTypography.BodyLarge,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PremiumButton(label = "Try Again", icon = null, onClick = onRetry, modifier = Modifier.focusRequester(focusTarget))
                PremiumButton(label = "Close", icon = null, onClick = onClose)
            }
        }
    }
}

/**
 * End card. For a series with a next episode it becomes "Up Next": a countdown ring
 * fills around the play button and starts the episode on its own (when autoplay is
 * on in Settings). Otherwise it offers Watch Again / Close.
 */
@Composable
private fun EndedOverlay(
    title:       String,
    nextLabel:   String?,
    autoPlay:    Boolean,
    focusTarget: FocusRequester,
    onPlayNext:  () -> Unit,
    onReplay:    () -> Unit,
    onClose:     () -> Unit
) {
    val countdownSeconds = 10
    val ring = remember { Animatable(0f) }
    var cancelled by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(countdownSeconds) }

    if (nextLabel != null && autoPlay && !cancelled) {
        LaunchedEffect(Unit) {
            launch {
                while (isActive && secondsLeft > 0) {
                    delay(1_000)
                    secondsLeft -= 1
                }
            }
            ring.animateTo(1f, tween(countdownSeconds * 1_000, easing = androidx.compose.animation.core.LinearEasing))
            onPlayNext()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Color(0x66000000),
                    0.55f to Color(0xB3000000),
                    1f to Color(0xF2000000)
                )
            ),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = Modifier
                .padding(end = 72.dp, bottom = 72.dp)
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (nextLabel != null) {
                Text("UP NEXT", style = IpxTypography.Eyebrow, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = nextLabel,
                    style = IpxTypography.HeadlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (autoPlay && !cancelled) "Starts in $secondsLeft s" else title,
                    style = IpxTypography.BodyLarge.copy(fontFeatureSettings = "tnum"),
                    color = TextSecondary
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (autoPlay && !cancelled) {
                        PremiumButton(label = "Cancel", icon = null, onClick = { cancelled = true })
                    } else {
                        PremiumButton(label = "Close", icon = null, onClick = onClose)
                    }
                    Box(
                        modifier = Modifier.drawBehind {
                            if (autoPlay && !cancelled && ring.value > 0f) {
                                val stroke = 3.dp.toPx()
                                val inset = -6.dp.toPx()
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius((size.height - 2 * inset) / 2),
                                    alpha = 0.25f,
                                    style = Stroke(stroke)
                                )
                                // Countdown sweeps left→right along the capsule.
                                val w = (size.width - 2 * inset) * ring.value
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(inset, size.height - inset - stroke),
                                    size = androidx.compose.ui.geometry.Size(w, stroke),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke / 2)
                                )
                            }
                        }
                    ) {
                        PremiumButton(
                            label = "Play Next",
                            icon = Icons.Rounded.SkipNext,
                            onClick = onPlayNext,
                            modifier = Modifier.focusRequester(focusTarget)
                        )
                    }
                }
            } else {
                Text("FINISHED", style = IpxTypography.Eyebrow, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = IpxTypography.HeadlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PremiumButton(label = "Close", icon = null, onClick = onClose)
                    PremiumButton(
                        label = "Watch Again",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = onReplay,
                        modifier = Modifier.focusRequester(focusTarget)
                    )
                }
            }
        }
    }
}
