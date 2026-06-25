package com.ipxtream.tv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.TextSecondary

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
 * @param viewModel   The [PlayerViewModel] — passed directly so key events can
 *                    call VM methods without lambda parameter explosion.
 * @param onStop      Called when the user closes the panel (BACK when HUD hidden,
 *                    or ✕ button press).
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerPanel(
    exoPlayer: ExoPlayer,
    uiState:   PlayerUiState,
    viewModel: PlayerViewModel,
    onStop:    () -> Unit,
    modifier:  Modifier = Modifier
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── PlayerView — created ONCE, never recreated ────────────────────────────
    val playerView = remember {
        PlayerView(context).apply {
            player                  = exoPlayer
            useController           = false   // ← custom HUD replaces built-in controls
            layoutParams            = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable             = false
            isFocusableInTouchMode  = false
            descendantFocusability  = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
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
                    exoPlayer.pause()
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

    // ── Focus Requester to steal focus on launch ─────────────────────────────
    val focusRequester = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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

                // Step 1: Always wake the HUD on any key.
                viewModel.onHudInteraction()

                // Step 2: Route based on current UI mode.
                when {
                    // Track menu is open — only intercept BACK; menu list handles navigation.
                    uiState.isTrackMenuOpen -> {
                        if (event.key == Key.Back) {
                            viewModel.dismissTrackMenu()
                            true  // consumed
                        } else false
                    }

                    // HUD is visible — handle playback controls.
                    uiState.isHudVisible -> {
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
                            Key.MediaRewind     -> { viewModel.seekRelative(-30_000L); true }

                            Key.DirectionRight,
                            Key.MediaFastForward -> { viewModel.seekRelative(30_000L); true }

                            Key.Back -> {
                                viewModel.hideHud()
                                runCatching { focusRequester.requestFocus() }
                                true  // consume — don't propagate to Activity
                            }

                            else -> false
                        }
                    }

                    // HUD was not visible — this key just woke it (already done in Step 1).
                    // BACK when HUD is not visible closes the player panel.
                    else -> {
                        if (event.key == Key.Back) {
                            onStop()
                            true
                        } else {
                            // Consume D-Pad navigation keys so focus doesn't escape before HUD is shown
                            event.key in listOf(
                                Key.DirectionCenter, Key.Enter, Key.DirectionLeft, 
                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown
                            )
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

        // ── Connecting / loading indicator ────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.playbackState == PlaybackState.LOADING,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text("⏳ Connecting…", style = IpxTypography.TitleMedium, color = TextSecondary)
        }

        // ── Custom HUD (top + bottom bars) ────────────────────────────────────
        PlayerHud(
            uiState           = uiState,
            onTogglePlayPause = viewModel::togglePlayPause,
            onSeekBack        = { viewModel.seekRelative(-30_000L) },
            onSeekForward     = { viewModel.seekRelative(30_000L) },
            onShowAudioMenu   = { viewModel.showTrackMenu(TrackMenuType.AUDIO) },
            onShowSubMenu     = { viewModel.showTrackMenu(TrackMenuType.SUBTITLE) },
            onClose           = onStop,
            playButtonFocus   = playButtonFocus,
            onHideAndFocusRoot = {
                viewModel.hideHud()
                runCatching { focusRequester.requestFocus() }
            },
            onToggleFavorite  = viewModel::toggleCurrentFavorite,
            modifier          = Modifier
                .fillMaxSize()
                .focusGroup()
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
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
        Text("⚠ $message", style = IpxTypography.BodyMedium, color = TextSecondary)
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
        Button(onClick = onReplay) { Text("↺ Replay") }
        Button(onClick = onClose)  { Text("✕ Close")  }
    }
}
