package com.ipxtream.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentCyanDim
import com.ipxtream.tv.ui.theme.AccentCyanGlow
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * Custom HUD overlay for the embedded player panel.
 *
 * Renders on top of the [PlayerPanel]'s video surface when [PlayerUiState.isHudVisible]
 * is true. Auto-hides after 3 seconds of inactivity (managed by [PlayerViewModel]).
 *
 * ## Layout
 * ```
 * ┌───── TopBar (slides in from top) ─────────────────────┐
 * │ ← Back  │  Stream Name / LIVE badge  │ 🔊 Audio  🔤 Sub│
 * └───────────────────────────────────────────────────────┘
 *
 *            (transparent video area)
 *
 * ┌───── BottomBar (slides in from bottom) ───────────────┐
 * │ ◀◀30s │  ⏸  │ ▶▶30s  │ ─────progress bar───── │ 3:42 / 45:00 │
 * └───────────────────────────────────────────────────────┘
 * ```
 *
 * The [TrackSelectionMenu] overlay is rendered by the caller (PlayerPanel)
 * on top of this HUD so it can be centred over the full panel.
 *
 * @param uiState           Reactive player+HUD state.
 * @param onTogglePlayPause Fired on ⏸/▶ button press.
 * @param onSeekBack        Fired on ◀◀ 30s button press.
 * @param onSeekForward     Fired on ▶▶ 30s button press.
 * @param onShowAudioMenu   Fired when the user presses the Audio track button.
 * @param onShowSubMenu     Fired when the user presses the Subtitle track button.
 * @param onClose           Fired when the user presses the close/X button.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerHud(
    uiState:           PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeekBack:        () -> Unit,
    onSeekForward:     () -> Unit,
    onShowAudioMenu:   () -> Unit,
    onShowSubMenu:     () -> Unit,
    onClose:           () -> Unit,
    playButtonFocus:   FocusRequester,
    onHideAndFocusRoot: () -> Unit,
    onToggleFavorite:  () -> Unit,
    modifier:          Modifier = Modifier
) {
    val topBarFocusGroup = remember { FocusRequester() }
    val bottomBarFocusGroup = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Screen dimming overlay ───────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.isHudVisible,
            enter   = fadeIn(tween(300)),
            exit    = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.isHudVisible,
            enter    = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit     = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            HudTopBar(
                streamName          = uiState.activeStreamName ?: "",
                mimeHint            = uiState.activeMimeHint,
                isLive              = uiState.isLive,
                showAudioButton     = uiState.hasMultipleAudioTracks,
                showSubtitleButton  = uiState.hasSubtitleTracks,
                subtitlesEnabled    = uiState.areSubtitlesEnabled,
                isFavorite          = uiState.isCurrentFavorite,
                onAudioClick        = onShowAudioMenu,
                onSubtitleClick     = onShowSubMenu,
                onFavoriteClick     = onToggleFavorite,
                onClose             = onClose,
                modifier            = Modifier
                    .focusRequester(topBarFocusGroup)
                    .focusProperties { down = bottomBarFocusGroup }
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            onHideAndFocusRoot()
                            true
                        } else false
                    }
            )
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.isHudVisible,
            enter    = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit     = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            HudBottomBar(
                isPlaying         = uiState.isPlaying,
                isLive            = uiState.isLive,
                progress          = uiState.progressFraction,
                buffered          = uiState.bufferedPercent / 100f,
                currentPositionMs = uiState.currentPositionMs,
                durationMs        = uiState.durationMs,
                onPlayPause       = onTogglePlayPause,
                onSeekBack        = onSeekBack,
                onSeekForward     = onSeekForward,
                playButtonFocus   = playButtonFocus,
                modifier          = Modifier
                    .focusRequester(bottomBarFocusGroup)
                    .focusProperties { up = topBarFocusGroup }
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onHideAndFocusRoot()
                            true
                        } else false
                    }
            )
        }
    }
}

// =============================================================================
//  Top bar
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun HudTopBar(
    streamName:         String,
    mimeHint:           String?,
    isLive:             Boolean,
    showAudioButton:    Boolean,
    showSubtitleButton: Boolean,
    subtitlesEnabled:   Boolean,
    isFavorite:         Boolean,
    onAudioClick:       () -> Unit,
    onSubtitleClick:    () -> Unit,
    onFavoriteClick:    () -> Unit,
    onClose:            () -> Unit,
    modifier:           Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xDD000000), Color.Transparent)
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Close button — left
        HudIconControl(
            icon = Icons.Default.Close,
            contentDescription = "Close",
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Stream name + live badge — centre
        Row(
            modifier          = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentGreen)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("● LIVE", color = Color(0xFF0B1520), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(streamName, style = IpxTypography.TitleMedium, color = TextPrimary,
                fontWeight = FontWeight.SemiBold)
            mimeHint?.let { hint ->
                Spacer(Modifier.width(8.dp))
                Text(hint, style = IpxTypography.LabelSmall, color = TextMuted)
            }
        }

        // Track buttons — right
        Row(
            modifier          = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudIconControl(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                onClick = onFavoriteClick,
                isActive = isFavorite
            )
            if (showAudioButton) {
                HudIconControl(
                    icon = Icons.Default.VolumeUp,
                    contentDescription = "Audio Tracks",
                    onClick = onAudioClick
                )
            }
            if (showSubtitleButton) {
                HudIconControl(
                    icon = Icons.Default.ClosedCaption,
                    contentDescription = "Subtitles",
                    onClick = onSubtitleClick,
                    isActive = subtitlesEnabled
                )
            }
        }
    }
}

// =============================================================================
//  Bottom bar
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun HudBottomBar(
    isPlaying:         Boolean,
    isLive:            Boolean,
    progress:          Float,
    buffered:          Float,
    currentPositionMs: Long,
    durationMs:        Long,
    onPlayPause:       () -> Unit,
    onSeekBack:        () -> Unit,
    onSeekForward:     () -> Unit,
    playButtonFocus:   FocusRequester,
    modifier:          Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xDD000000))
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Progress bar (VOD only) ───────────────────────────────────────────
        if (!isLive) {
            PlayerProgressBar(
                progress = progress,
                buffered = buffered,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }

        // ── Transport controls ────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seek back (VOD only)
            if (!isLive) {
                HudIconControl(
                    icon = Icons.Default.FastRewind,
                    contentDescription = "Seek Back 30s",
                    onClick = onSeekBack
                )
                Spacer(Modifier.width(16.dp))
            }

            // Play / Pause (primary larger button)
            HudIconControl(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                focusRequester = playButtonFocus,
                buttonSize = 52.dp,
                iconSize = 24.dp
            )

            // Seek forward (VOD only)
            if (!isLive) {
                Spacer(Modifier.width(16.dp))
                HudIconControl(
                    icon = Icons.Default.FastForward,
                    contentDescription = "Seek Forward 30s",
                    onClick = onSeekForward
                )
            }

            Spacer(Modifier.weight(1f))

            // Time display
            Text(
                text  = if (isLive) "● LIVE"
                        else "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                style = IpxTypography.BodyMedium,
                color = if (isLive) AccentGreen else TextSecondary
            )
        }
    }
}

// =============================================================================
//  Shared sub-composables
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HudIconControl(
    icon:               androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick:            () -> Unit,
    modifier:           Modifier = Modifier,
    focusRequester:     FocusRequester? = null,
    buttonSize:         androidx.compose.ui.unit.Dp = 44.dp,
    iconSize:           androidx.compose.ui.unit.Dp = 20.dp,
    isActive:           Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(220, easing = EaseInOutCubic),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        // Glowing cyan aura behind focused button
        AnimatedVisibility(
            visible = isFocused,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .size(buttonSize + 10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentCyanGlow.copy(alpha = 0.8f), Color.Transparent),
                            radius = (buttonSize.value + 10).coerceAtLeast(1f) * 1.5f
                        )
                    )
            )
        }

        val buttonModifier = Modifier
            .size(buttonSize)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }

        val containerBgColor = when {
            isFocused -> AccentCyan
            isActive  -> AccentCyanDim.copy(alpha = 0.4f)
            else      -> SlateGlass.copy(alpha = 0.6f)
        }
        val iconColor = when {
            isFocused -> Color(0xFF0B1520)
            isActive  -> AccentCyan
            else      -> TextPrimary
        }

        Button(
            onClick = onClick,
            modifier = buttonModifier,
            shape   = ButtonDefaults.shape(shape = CircleShape),
            colors  = ButtonDefaults.colors(
                containerColor        = containerBgColor,
                focusedContainerColor = AccentCyan,
                contentColor          = iconColor,
                focusedContentColor   = Color(0xFF0B1520)
            ),
            border  = ButtonDefaults.border(
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = if (isFocused) Color.White else Color.Transparent
                    ),
                    shape  = CircleShape
                )
            ),
            scale   = ButtonDefaults.scale(focusedScale = 1.0f) // handled manually by graphicsLayer
        ) {
            androidx.compose.material3.Icon(
                imageVector        = icon,
                contentDescription = contentDescription,
                modifier           = Modifier.size(iconSize),
                tint               = if (isFocused) Color(0xFF0B1520) else iconColor
            )
        }
    }
}

@Composable
private fun PlayerProgressBar(
    progress: Float,
    buffered: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier.fillMaxWidth().height(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x22FFFFFF))
        )
        // Buffered progress
        Box(
            modifier = Modifier
                .fillMaxWidth(buffered.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF))
        )
        // Active progress
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentCyan)
        )
        // Thumb container - matches active progress width, aligns thumb to the right
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.001f, 1f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentCyan)
                )
            }
        }
    }
}

// =============================================================================
//  Time formatter
// =============================================================================

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3_600
    val m = (totalSec % 3_600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
