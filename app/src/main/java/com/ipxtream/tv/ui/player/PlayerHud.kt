package com.ipxtream.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import java.util.Locale

/**
 * Unified lower-third HUD overlay for the media player.
 *
 * Implements a single bottom vignette containing:
 * 1. Title & Metadata (Series/Movie title, Season/Episode subtitle, stream resolution badge).
 * 2. Primary Transport Controls (Play/Pause, SkipPrev, Rewind 10s, FastFwd 10s, SkipNext, Replay).
 * 3. Progress Bar (Netflix Red with buffered indication and white thumb scrubber).
 * 4. Secondary Actions & Time (Back, CC, Audio, Favorite, Episodes menu on left; time on right).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerHud(
    uiState:            PlayerUiState,
    onTogglePlayPause:  () -> Unit,
    onSeekBack10:       () -> Unit,
    onSeekForward10:    () -> Unit,
    onPrev:             () -> Unit,
    onNext:             () -> Unit,
    onReplay:           () -> Unit,
    onShowAudioMenu:    () -> Unit,
    onShowSubMenu:      () -> Unit,
    onToggleFavorite:   () -> Unit,
    onEpisodesClick:    (() -> Unit)? = null,
    onClose:            () -> Unit,
    playButtonFocus:    FocusRequester,
    onHideAndFocusRoot: () -> Unit,
    modifier:           Modifier = Modifier
) {
    val transportFocusRequester = remember { FocusRequester() }
    val secondaryFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible  = uiState.isHudVisible,
            enter    = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
            exit     = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x33000000),
                                Color(0xBB000000),
                                Color(0xF2000000)
                            )
                        )
                    )
                    .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── 1. Title & Metadata Block ──────────────────────────────────────────
                    val mainTitle = remember(uiState.activeSeries, uiState.activeStreamName) {
                        uiState.activeSeries?.name ?: uiState.activeStreamName ?: ""
                    }
                    val isArabic = remember { Locale.getDefault().language == "ar" }
                    val subtitle = remember(uiState.activeEpisode, uiState.isLive, uiState.activeStream, uiState.activeMimeHint, isArabic) {
                        val ep = uiState.activeEpisode
                        when {
                            ep != null -> {
                                val seasonPart = if (isArabic) "الموسم ${ep.season}" else "Season ${ep.season}"
                                val epPart = if (isArabic) "الحلقة ${ep.episodeNum}" else "Episode ${ep.episodeNum}"
                                if (ep.title.isNotBlank() && !ep.title.equals(mainTitle, ignoreCase = true)) {
                                    "$seasonPart - $epPart • ${ep.title}"
                                } else {
                                    "$seasonPart - $epPart"
                                }
                            }
                            uiState.isLive -> "LIVE STREAM"
                            else -> uiState.activeStream?.containerExtension?.uppercase() ?: uiState.activeMimeHint ?: ""
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = mainTitle,
                            style = IpxTypography.TitleLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Normal),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = IpxTypography.BodyMedium.copy(fontSize = 14.sp),
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            uiState.resolutionText?.let { res ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = res,
                                        style = IpxTypography.LabelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = AccentCyan
                                    )
                                }
                            }
                        }
                    }

                    // ── 2. Primary Transport Controls (Row 1) ──────────────────────────────
                    Row(
                        modifier = Modifier
                            .focusRequester(transportFocusRequester)
                            .focusProperties {
                                down = secondaryFocusRequester
                            }
                            .focusGroup()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    onHideAndFocusRoot()
                                    true
                                } else false
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play / Pause
                        HudIconButton(
                            icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            onClick = onTogglePlayPause,
                            focusRequester = playButtonFocus,
                            buttonSize = 48.dp,
                            iconSize = 26.dp
                        )

                        // Previous
                        HudIconButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            onClick = onPrev,
                            buttonSize = 44.dp,
                            iconSize = 22.dp
                        )

                        // Rewind 10s
                        HudIconButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            onClick = onSeekBack10,
                            buttonSize = 44.dp,
                            iconSize = 22.dp
                        )

                        // Fast Forward 10s
                        HudIconButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            onClick = onSeekForward10,
                            buttonSize = 44.dp,
                            iconSize = 22.dp
                        )

                        // Next
                        HudIconButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            onClick = onNext,
                            buttonSize = 44.dp,
                            iconSize = 22.dp
                        )

                        // Replay
                        HudIconButton(
                            icon = Icons.Default.Replay,
                            contentDescription = "Replay from start",
                            onClick = onReplay,
                            buttonSize = 44.dp,
                            iconSize = 22.dp
                        )
                    }

                    // ── 3. Progress Bar (Row 2, VOD only) ──────────────────────────────────
                    if (!uiState.isLive) {
                        PlayerProgressBar(
                            progress = uiState.progressFraction,
                            buffered = uiState.bufferedPercent / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    // ── 4. Secondary Actions & Time (Row 3) ────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(secondaryFocusRequester)
                            .focusProperties {
                                up = playButtonFocus
                            }
                            .focusGroup()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    onHideAndFocusRoot()
                                    true
                                } else false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Back
                            HudIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onClose,
                                buttonSize = 40.dp,
                                iconSize = 20.dp
                            )

                            // Subtitles
                            HudIconButton(
                                icon = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                onClick = onShowSubMenu,
                                isActive = uiState.areSubtitlesEnabled,
                                buttonSize = 40.dp,
                                iconSize = 20.dp
                            )

                            // Audio
                            HudIconButton(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Audio Tracks",
                                onClick = onShowAudioMenu,
                                buttonSize = 40.dp,
                                iconSize = 20.dp
                            )

                            // Favorite
                            HudIconButton(
                                icon = if (uiState.isCurrentFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                onClick = onToggleFavorite,
                                isActive = uiState.isCurrentFavorite,
                                buttonSize = 40.dp,
                                iconSize = 20.dp
                            )

                            // Episodes Menu (if series)
                            if (onEpisodesClick != null && uiState.activeEpisode != null) {
                                HudIconButton(
                                    icon = Icons.Default.Menu,
                                    contentDescription = "Episodes",
                                    onClick = onEpisodesClick,
                                    buttonSize = 40.dp,
                                    iconSize = 20.dp
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        if (uiState.isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentGreen)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = Color(0xFF0B1520),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "${formatTime(uiState.currentPositionMs)} / ${formatTime(uiState.durationMs)}",
                                style = IpxTypography.BodyMedium.copy(fontSize = 14.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
//  Shared sub-composables
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HudIconButton(
    icon:               ImageVector,
    contentDescription: String,
    onClick:            () -> Unit,
    modifier:           Modifier = Modifier,
    focusRequester:     FocusRequester? = null,
    buttonSize:         Dp = 44.dp,
    iconSize:           Dp = 22.dp,
    isActive:           Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(180, easing = EaseInOutCubic),
        label         = "btnScale"
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
        if (isFocused) {
            Box(
                modifier = Modifier
                    .size(buttonSize + 8.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                            radius = (buttonSize.value + 8).coerceAtLeast(1f) * 1.5f
                        )
                    )
            )
        }

        val buttonModifier = Modifier
            .size(buttonSize)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }

        val containerBgColor = when {
            isFocused -> Color.White.copy(alpha = 0.28f)
            isActive  -> Color.White.copy(alpha = 0.15f)
            else      -> Color.Transparent
        }
        val iconColor = when {
            isFocused -> Color.White
            isActive  -> AccentCyan
            else      -> Color.White.copy(alpha = 0.88f)
        }

        Button(
            onClick  = onClick,
            modifier = buttonModifier,
            shape    = ButtonDefaults.shape(shape = CircleShape),
            colors   = ButtonDefaults.colors(
                containerColor        = containerBgColor,
                focusedContainerColor = Color.White.copy(alpha = 0.28f),
                contentColor          = iconColor,
                focusedContentColor   = Color.White
            ),
            border   = ButtonDefaults.border(
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = if (isFocused) Color.White else Color.Transparent
                    ),
                    shape  = CircleShape
                )
            ),
            scale    = ButtonDefaults.scale(focusedScale = 1.0f)
        ) {
            androidx.compose.material3.Icon(
                imageVector        = icon,
                contentDescription = contentDescription,
                modifier           = Modifier.size(iconSize),
                tint               = if (isFocused) Color.White else iconColor
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
        modifier         = modifier.fillMaxWidth().height(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF))
        )
        // Buffered progress
        Box(
            modifier = Modifier
                .fillMaxWidth(buffered.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x66FFFFFF))
        )
        // Active progress - Netflix Red
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE50914))
        )
        // Thumb container - white circle thumb
        Box(
            modifier = Modifier.fillMaxWidth(progress.coerceIn(0.001f, 1f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3_600
    val m = (totalSec % 3_600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
