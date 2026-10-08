package com.ipxtream.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import java.util.Locale

/**
 * Unified lower-third HUD overlay scaled accurately for 55"+ TV screens.
 *
 * Implements a single bottom vignette containing:
 * 1. Title & Metadata (Series/Movie title, Season/Episode subtitle, stream resolution badge).
 * 2. Primary Transport Controls (Play/Pause, [SkipPrev if series], FastRewind, FastFwd, [SkipNext if series], Replay).
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
    val isSeries = uiState.activeEpisode != null

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
                                Color(0x44000000),
                                Color(0xBB000000),
                                Color(0xF2000000)
                            )
                        )
                    )
                    .padding(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 36.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
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

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = mainTitle,
                            style = IpxTypography.TitleLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Normal),
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
                                    style = IpxTypography.BodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            uiState.resolutionText?.let { res ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = res,
                                        style = IpxTypography.LabelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
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
                        horizontalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // Play / Pause
                        HudIconButton(
                            icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            onClick = onTogglePlayPause,
                            focusRequester = playButtonFocus,
                            buttonSize = 54.dp,
                            iconSize = 32.dp
                        )

                        // Previous episode (|◀) - ONLY for Series
                        if (isSeries) {
                            HudIconButton(
                                icon = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Episode",
                                onClick = onPrev,
                                buttonSize = 54.dp,
                                iconSize = 32.dp
                            )
                        }

                        // Rewind (◀◀)
                        HudIconButton(
                            icon = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 seconds",
                            onClick = onSeekBack10,
                            buttonSize = 54.dp,
                            iconSize = 32.dp
                        )

                        // Fast Forward (▶▶)
                        HudIconButton(
                            icon = Icons.Default.FastForward,
                            contentDescription = "Forward 10 seconds",
                            onClick = onSeekForward10,
                            buttonSize = 54.dp,
                            iconSize = 32.dp
                        )

                        // Next episode (▶|) - ONLY for Series
                        if (isSeries) {
                            HudIconButton(
                                icon = Icons.Default.SkipNext,
                                contentDescription = "Next Episode",
                                onClick = onNext,
                                buttonSize = 54.dp,
                                iconSize = 32.dp
                            )
                        }

                        // Replay (↺)
                        HudIconButton(
                            icon = Icons.Default.Replay,
                            contentDescription = "Replay from start",
                            onClick = onReplay,
                            buttonSize = 54.dp,
                            iconSize = 30.dp
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
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Back
                            HudIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onClose,
                                buttonSize = 46.dp,
                                iconSize = 24.dp
                            )

                            // Subtitles
                            HudIconButton(
                                icon = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles",
                                onClick = onShowSubMenu,
                                isActive = uiState.areSubtitlesEnabled,
                                buttonSize = 46.dp,
                                iconSize = 24.dp
                            )

                            // Audio
                            HudIconButton(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Audio Tracks",
                                onClick = onShowAudioMenu,
                                buttonSize = 46.dp,
                                iconSize = 24.dp
                            )

                            // Favorite
                            HudIconButton(
                                icon = if (uiState.isCurrentFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                onClick = onToggleFavorite,
                                isActive = uiState.isCurrentFavorite,
                                buttonSize = 46.dp,
                                iconSize = 24.dp
                            )

                            // Episodes Menu (if series)
                            if (onEpisodesClick != null && isSeries) {
                                HudIconButton(
                                    icon = Icons.Default.Menu,
                                    contentDescription = "Episodes",
                                    onClick = onEpisodesClick,
                                    buttonSize = 46.dp,
                                    iconSize = 24.dp
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "${formatTime(uiState.currentPositionMs)} / ${formatTime(uiState.durationMs)}",
                                style = IpxTypography.BodyMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal),
                                color = Color.White.copy(alpha = 0.9f)
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
    buttonSize:         Dp = 54.dp,
    iconSize:           Dp = 32.dp,
    isActive:           Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(150, easing = EaseInOutCubic),
        label         = "btnScale"
    )

    val buttonModifier = Modifier
        .size(buttonSize)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .onFocusChanged { isFocused = it.isFocused }

    val containerBgColor = when {
        isFocused -> Color.White.copy(alpha = 0.22f)
        isActive  -> Color.White.copy(alpha = 0.12f)
        else      -> Color.Transparent
    }
    val iconColor = when {
        isFocused -> Color.White
        isActive  -> AccentCyan
        else      -> Color.White.copy(alpha = 0.88f)
    }

    Button(
        onClick        = onClick,
        modifier       = buttonModifier,
        shape          = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = PaddingValues(0.dp),
        colors         = ButtonDefaults.colors(
            containerColor        = containerBgColor,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor          = iconColor,
            focusedContentColor   = Color.White
        ),
        border         = ButtonDefaults.border(
            border        = Border(border = BorderStroke(0.dp, Color.Transparent), shape = CircleShape),
            focusedBorder = Border(border = BorderStroke(0.dp, Color.Transparent), shape = CircleShape)
        ),
        glow           = ButtonDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        scale          = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
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
        modifier         = modifier.fillMaxWidth().height(24.dp),
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
        // Scrubber thumb - solid white circle
        Box(
            modifier = Modifier.fillMaxWidth(progress.coerceIn(0.001f, 1f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
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
