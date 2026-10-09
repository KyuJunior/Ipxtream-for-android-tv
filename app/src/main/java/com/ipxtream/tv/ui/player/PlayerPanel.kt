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
                            Key.MediaPlayPause  -> { viewModel.togglePlayPause(); true }

                            Key.DirectionLeft,
                            Key.MediaRewind     -> { viewModel.seekRelative(-10_000L); true }

                            Key.DirectionRight,
                            Key.MediaFastForward -> { viewModel.seekRelative(10_000L); true }

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
                                }
                                viewModel.onHudInteraction()
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.MediaPlay -> {
                                viewModel.resume()
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
                                viewModel.seekRelative(-10_000L)
                                viewModel.onHudInteraction()
                                runCatching { playButtonFocus.requestFocus() }
                                true
                            }

                            Key.DirectionRight,
                            Key.MediaFastForward -> {
                                viewModel.seekRelative(10_000L)
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

        // ── Floating OSD Channel Banner (Live TV Rapid Surfing) ──────────────
        val isBannerVisible = showChannelBanner && !uiState.isHudVisible && uiState.isLive && uiState.activeStream != null
        AnimatedVisibility(
            visible  = isBannerVisible,
            enter    = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(250)) + fadeIn(tween(250)),
            exit     = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250)) + fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 28.dp, start = 28.dp)
        ) {
            val stream = uiState.activeStream
            if (stream != null) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .background(SlateGlass, RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, Color(0x3338BDF8)), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Channel Logo
                        if (!stream.streamIcon.isNullOrBlank()) {
                            AsyncImage(
                                model = stream.streamIcon,
                                contentDescription = stream.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF))
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (stream.num != null && stream.num > 0) {
                                    Text(
                                        text = "CH ${stream.num}",
                                        color = AccentCyan,
                                        style = IpxTypography.BodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE50914).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                        .border(BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.7f)), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE50914))
                                        )
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            style = IpxTypography.LabelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            Text(
                                text = stream.name,
                                style = IpxTypography.TitleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ── Connecting / loading indicator ────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.LOADING,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AccentCyan,
                    strokeWidth = 2.dp
                )
                Text("Connecting…", style = IpxTypography.TitleMedium, color = TextSecondary)
            }
        }

        // ── Custom HUD (unified lower-third overlay) ──────────────────────────
        PlayerHud(
            uiState            = uiState,
            onTogglePlayPause  = viewModel::togglePlayPause,
            onSeekBack10       = { viewModel.seekRelative(-10_000L) },
            onSeekForward10    = { viewModel.seekRelative(10_000L) },
            onPrev             = onPrevEpisode ?: { viewModel.seekTo(0L) },
            onNext             = onNextEpisode ?: { viewModel.seekRelative(10_000L) },
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

        // ── Track selection menu (slides over the HUD from right) ─────────────
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

        // ── Error overlay ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.ERROR,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ErrorOverlay(
                message = uiState.error ?: "Unknown playback error.",
                onRetry = { exoPlayer.prepare() },
                onClose = onStop
            )
        }

        // ── Ended overlay ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.ENDED,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            EndedOverlay(
                onReplay = { exoPlayer.seekTo(0); exoPlayer.play() },
                onClose  = onStop
            )
        }
    }
}

// =============================================================================
//  Overlays (error / ended)
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        Text(message, style = IpxTypography.BodyMedium, color = TextSecondary)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onRetry) { Text("Retry") }
            Button(onClick = onClose) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EndedOverlay(onReplay: () -> Unit, onClose: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = onReplay) { Text("Replay") }
        Button(onClick = onClose)  { Text("Close")  }
    }
}
