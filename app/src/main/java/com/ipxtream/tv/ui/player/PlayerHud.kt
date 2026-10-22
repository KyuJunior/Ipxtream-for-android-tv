package com.ipxtream.tv.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.data.local.AppSettingsStore
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxMotion
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val HudSidePadding = 64.dp
private val TnumStyle = IpxTypography.BodyLarge.copy(fontFeatureSettings = "tnum")

/**
 * Playback overlay in the style of the big streaming apps' TV players.
 *
 * - Top: what's playing (eyebrow, title, episode name) and, on the right, the clock,
 *   the time this title ends, and quality badges.
 * - Bottom: a focusable scrubber (D-Pad Left/Right scrubs with acceleration, the
 *   seek commits after a short pause, a time bubble rides the thumb), elapsed and
 *   remaining time, then transport controls centred with secondary actions on the
 *   edges. Focused buttons turn into white platters and show their name underneath.
 * - Everything fades and glides in on the shared [IpxMotion] curves; the scrubber
 *   position is interpolated per frame so it never ticks in one-second jumps.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerHud(
    uiState:            PlayerUiState,
    onTogglePlayPause:  () -> Unit,
    onSeekBack10:       () -> Unit,
    onSeekForward10:    () -> Unit,
    onSeekTo:           (Long) -> Unit,
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
    val visible = uiState.isHudVisible && uiState.playbackState != PlaybackState.ENDED
    val scrubberFocus = remember { FocusRequester() }
    val smoothPosition = rememberSmoothPosition(
        positionMs = uiState.currentPositionMs,
        durationMs = uiState.durationMs,
        isPlaying  = uiState.isPlaying,
        animate    = visible && !uiState.isLive && !com.ipxtream.tv.ui.theme.LocalReducedEffects.current
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Cinematic scrim — darkens top and bottom, leaves the picture readable in the middle.
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(IpxMotion.Standard, easing = IpxMotion.EaseOut)),
            exit    = fadeOut(tween(IpxMotion.Slow, easing = IpxMotion.EaseStandard))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xC7000000),
                            0.24f to Color(0x40000000),
                            0.5f to Color(0x14000000),
                            0.72f to Color(0x80000000),
                            1f to Color(0xF0000000)
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible  = visible,
            enter    = fadeIn(tween(450, easing = IpxMotion.EaseOut)) +
                slideInVertically(tween(500, easing = IpxMotion.EaseApple)) { -it / 3 },
            exit     = fadeOut(tween(IpxMotion.Fast)) +
                slideOutVertically(tween(IpxMotion.Standard, easing = IpxMotion.EaseStandard)) { -it / 4 },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            HudTopBar(uiState)
        }

        AnimatedVisibility(
            visible  = visible,
            enter    = fadeIn(tween(450, delayMillis = 60, easing = IpxMotion.EaseOut)) +
                slideInVertically(tween(500, delayMillis = 60, easing = IpxMotion.EaseApple)) { it / 3 },
            exit     = fadeOut(tween(IpxMotion.Fast)) +
                slideOutVertically(tween(IpxMotion.Standard, easing = IpxMotion.EaseStandard)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = HudSidePadding, end = HudSidePadding, bottom = 36.dp)
            ) {
                if (!uiState.isLive) {
                    Scrubber(
                        smoothPosition    = smoothPosition,
                        positionMs        = uiState.currentPositionMs,
                        durationMs        = uiState.durationMs,
                        buffered          = uiState.bufferedPercent / 100f,
                        focusRequester    = scrubberFocus,
                        downTarget        = playButtonFocus,
                        onSeekTo          = onSeekTo,
                        onTogglePlayPause = onTogglePlayPause,
                        onExitUp          = onHideAndFocusRoot
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime(uiState.currentPositionMs), style = TnumStyle, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text  = "-" + formatTime((uiState.durationMs - uiState.currentPositionMs).coerceAtLeast(0L)),
                            style = TnumStyle,
                            color = TextSecondary
                        )
                    }
                } else {
                    LiveRail()
                }

                Spacer(Modifier.height(20.dp))

                ControlsRow(
                    uiState            = uiState,
                    onTogglePlayPause  = onTogglePlayPause,
                    onSeekBack10       = onSeekBack10,
                    onSeekForward10    = onSeekForward10,
                    onPrev             = onPrev,
                    onNext             = onNext,
                    onReplay           = onReplay,
                    onShowAudioMenu    = onShowAudioMenu,
                    onShowSubMenu      = onShowSubMenu,
                    onToggleFavorite   = onToggleFavorite,
                    onEpisodesClick    = onEpisodesClick,
                    onClose            = onClose,
                    playButtonFocus    = playButtonFocus,
                    scrubberFocus      = scrubberFocus,
                    onHideAndFocusRoot = onHideAndFocusRoot
                )
            }
        }
    }
}

// =============================================================================
//  Top bar
// =============================================================================

@Composable
private fun HudTopBar(uiState: PlayerUiState) {
    val context = LocalContext.current
    val settings by remember(context) { AppSettingsStore.getInstance(context).settings }.collectAsState()
    val isArabic = remember { Locale.getDefault().language == "ar" }

    val mainTitle = uiState.activeSeries?.name ?: uiState.activeStreamName ?: ""
    val ep = uiState.activeEpisode
    val eyebrow = when {
        ep != null -> if (isArabic) "الموسم ${ep.season} · الحلقة ${ep.episodeNum}" else "SEASON ${ep.season} · EPISODE ${ep.episodeNum}"
        uiState.isLive -> uiState.activeStream?.num?.takeIf { it > 0 }?.let { "CHANNEL $it" }
        else -> "MOVIE"
    }
    val episodeTitle = ep?.title?.takeIf { it.isNotBlank() && !it.equals(mainTitle, ignoreCase = true) }

    // Wall clock + "ends at", refreshed every 15 s while the bar is up.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val clockFormat = remember(settings.is24HourClockFormat) {
        SimpleDateFormat(if (settings.is24HourClockFormat) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val remainingMs = (uiState.durationMs - uiState.currentPositionMs).coerceAtLeast(0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HudSidePadding, end = HudSidePadding, top = 40.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isLive) {
                    LivePill()
                    if (eyebrow != null) Spacer(Modifier.width(10.dp))
                }
                if (eyebrow != null) {
                    Text(text = eyebrow, style = IpxTypography.Eyebrow, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text     = mainTitle,
                style    = IpxTypography.HeadlineMedium.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
                color    = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episodeTitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = episodeTitle,
                    style    = IpxTypography.BodyLarge.copy(fontSize = 18.sp),
                    color    = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(32.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text  = clockFormat.format(Date(now)),
                style = IpxTypography.TitleLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = Color.White
            )
            if (!uiState.isLive && uiState.durationMs > 0L) {
                Text(
                    text  = (if (isArabic) "ينتهي " else "Ends at ") + clockFormat.format(Date(now + remainingMs)),
                    style = IpxTypography.BodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = TextSecondary
                )
            }
            val badges = buildList {
                qualityLabel(uiState.videoWidth, uiState.videoHeight)?.let { add(it) }
                if (!uiState.isLive) {
                    (uiState.activeStream?.containerExtension ?: uiState.activeEpisode?.containerExtension)
                        ?.takeIf { it.isNotBlank() }?.uppercase()?.let { add(it) }
                }
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { OutlineBadge(it) }
                }
            }
        }
    }
}

/** Wide masters (e.g. 3840×1600) are 4K too, so width decides as much as height. */
private fun qualityLabel(width: Int, height: Int): String? = when {
    width <= 0 || height <= 0         -> null
    width >= 3200 || height >= 2000   -> "4K UHD"
    width >= 1800 || height >= 1000   -> "HD 1080p"
    width >= 1200 || height >= 700    -> "HD 720p"
    else                              -> "SD"
}

@Composable
private fun OutlineBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text  = text,
            style = IpxTypography.LabelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
internal fun LivePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(AccentRed)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(5.dp))
        Text("LIVE", style = IpxTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White)
    }
}

// =============================================================================
//  Scrubber
// =============================================================================

/**
 * Focusable timeline. Left/Right accumulate a pending seek that grows faster the
 * longer the key is held (10 s → 30 s → 60 s steps); it commits 650 ms after the
 * last press so rapid presses don't hammer the stream with seeks.
 */
@Composable
private fun Scrubber(
    smoothPosition:    State<Long>,
    positionMs:        Long,
    durationMs:        Long,
    buffered:          Float,
    focusRequester:    FocusRequester,
    downTarget:        FocusRequester,
    onSeekTo:          (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onExitUp:          () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var pendingMs by remember { mutableStateOf<Long?>(null) }
    var committedMs by remember { mutableStateOf<Long?>(null) }

    // Commit the scrub once the user stops pressing.
    LaunchedEffect(pendingMs) {
        val target = pendingMs ?: return@LaunchedEffect
        delay(650)
        committedMs = target
        pendingMs = null
        onSeekTo(target)
    }
    // Hold the committed position on screen until the player reports it (or 2 s pass).
    LaunchedEffect(committedMs) {
        if (committedMs != null) {
            delay(2_000)
            committedMs = null
        }
    }
    LaunchedEffect(positionMs) {
        val c = committedMs
        if (c != null && abs(positionMs - c) < 2_500) committedMs = null
    }

    val railHeight by animateDpAsState(if (focused) 8.dp else 4.dp, IpxMotion.focusSpring(), label = "railHeight")
    val thumbSize by animateDpAsState(if (focused) 22.dp else 12.dp, IpxMotion.focusSpring(), label = "thumbSize")
    val halo by animateFloatAsState(if (focused) 1f else 0f, tween(IpxMotion.Standard), label = "thumbHalo")

    fun fractionOf(ms: Long) = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayMs: () -> Long = { pendingMs ?: committedMs ?: smoothPosition.value }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRequester(focusRequester)
            .focusProperties { down = downTarget }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val repeats = e.nativeKeyEvent.repeatCount
                        val step = when {
                            repeats < 6  -> 10_000L
                            repeats < 18 -> 30_000L
                            else         -> 60_000L
                        }
                        val base = pendingMs ?: committedMs ?: positionMs
                        val dir = if (e.key == Key.DirectionRight) 1 else -1
                        pendingMs = (base + dir * step).coerceIn(0L, durationMs.coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        val target = pendingMs
                        if (target != null) {
                            pendingMs = null
                            committedMs = target
                            onSeekTo(target)
                        } else {
                            onTogglePlayPause()
                        }
                        true
                    }
                    Key.DirectionUp -> { onExitUp(); true }
                    else -> false
                }
            }
            .focusable()
            .drawBehind {
                val h = railHeight.toPx()
                val y = size.height / 2f
                val r = CornerRadius(h / 2f)
                val w = size.width
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, y - h / 2), Size(w, h), r)
                drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(0f, y - h / 2), Size(w * buffered.coerceIn(0f, 1f), h), r)
                val x = w * fractionOf(displayMs())
                drawRoundRect(Color.White, Offset(0f, y - h / 2), Size(x, h), r)
                val t = thumbSize.toPx() / 2f
                if (halo > 0f) drawCircle(Color.White.copy(alpha = 0.22f * halo), radius = t * 1.9f, center = Offset(x, y))
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = t + 1.5f, center = Offset(x, y + 1.5f))
                drawCircle(Color.White, radius = t, center = Offset(x, y))
            }
    ) {
        // Time bubble riding above the thumb while the timeline has focus.
        if (focused) {
            val bubbleMs = pendingMs ?: committedMs ?: positionMs
            val diff = pendingMs?.let { it - positionMs }
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-46).dp)
                    .layout { measurable, constraints ->
                        val p = measurable.measure(Constraints())
                        val x = (constraints.maxWidth * fractionOf(bubbleMs)).toInt()
                        layout(constraints.maxWidth, p.height) {
                            p.place((x - p.width / 2).coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0)), 0)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE61C1C1E))
                        .border(1.dp, GlassStroke, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTime(bubbleMs), style = TnumStyle.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    if (diff != null && diff != 0L) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = (if (diff > 0) "+" else "-") + formatTime(abs(diff)),
                            style = IpxTypography.BodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/** Live streams have no timeline; a full rail with the LIVE marker reads as "at the edge". */
@Composable
private fun LiveRail() {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .drawBehind {
                    val h = 4.dp.toPx()
                    val y = size.height / 2f
                    drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
                    drawCircle(AccentRed, radius = 7.dp.toPx(), center = Offset(size.width, y))
                }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text("Live", style = TnumStyle, color = TextSecondary)
        }
    }
}

/**
 * Interpolates the 1 Hz position updates on every frame so the progress fill glides.
 * Only the scrubber's draw phase reads it, so this never recomposes the HUD.
 */
@Composable
private fun rememberSmoothPosition(positionMs: Long, durationMs: Long, isPlaying: Boolean, animate: Boolean): State<Long> {
    val out = remember { mutableLongStateOf(positionMs) }
    val anchor = remember { longArrayOf(positionMs, SystemClock.uptimeMillis()) }
    LaunchedEffect(positionMs) {
        anchor[0] = positionMs
        anchor[1] = SystemClock.uptimeMillis()
        if (!isPlaying || !animate) out.longValue = positionMs
    }
    LaunchedEffect(isPlaying, animate, durationMs) {
        if (!isPlaying || !animate) {
            out.longValue = anchor[0]
            return@LaunchedEffect
        }
        val cap = if (durationMs > 0) durationMs else Long.MAX_VALUE
        while (isActive) {
            withFrameMillis {
                val drift = (SystemClock.uptimeMillis() - anchor[1]).coerceIn(0L, 1_500L)
                out.longValue = (anchor[0] + drift).coerceAtMost(cap)
            }
        }
    }
    return out
}

// =============================================================================
//  Controls
// =============================================================================

@Composable
private fun ControlsRow(
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
    onEpisodesClick:    (() -> Unit)?,
    onClose:            () -> Unit,
    playButtonFocus:    FocusRequester,
    scrubberFocus:      FocusRequester,
    onHideAndFocusRoot: () -> Unit
) {
    val isSeries = uiState.activeEpisode != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .focusGroup()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { onHideAndFocusRoot(); true }
                    // VOD: let the focus search climb to the timeline directly above.
                    Key.DirectionUp -> {
                        if (uiState.isLive) { onHideAndFocusRoot(); true } else false
                    }
                    else -> false
                }
            }
    ) {
        // Left: leave / start over
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HudButton(Icons.Rounded.Close, "Close", onClose)
            if (!uiState.isLive) HudButton(Icons.Rounded.RestartAlt, "Start over", onReplay)
        }

        // Centre: transport
        Row(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSeries) HudButton(Icons.Rounded.SkipPrevious, "Previous episode", onPrev)
            if (!uiState.isLive) HudButton(Icons.Rounded.Replay10, "Back 10s", onSeekBack10)
            HudButton(
                icon           = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                label          = if (uiState.isPlaying) "Pause" else "Play",
                onClick        = onTogglePlayPause,
                focusRequester = playButtonFocus,
                size           = 64.dp,
                iconSize       = 34.dp
            )
            if (!uiState.isLive) HudButton(Icons.Rounded.Forward10, "Forward 10s", onSeekForward10)
            if (isSeries) HudButton(Icons.Rounded.SkipNext, "Next episode", onNext)
        }

        // Right: episodes, language, library
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onEpisodesClick != null && isSeries) HudButton(Icons.Rounded.VideoLibrary, "Episodes", onEpisodesClick)
            HudButton(Icons.Rounded.Subtitles, "Subtitles", onShowSubMenu, active = uiState.areSubtitlesEnabled)
            HudButton(Icons.Rounded.GraphicEq, "Audio", onShowAudioMenu)
            HudButton(
                icon    = if (uiState.isCurrentFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label   = if (uiState.isCurrentFavorite) "In My Library" else "Add to My Library",
                onClick = onToggleFavorite
            )
        }
    }
}

/**
 * Round transport button. Transparent at rest, white platter with a black glyph on
 * focus, a spring scale, and its name revealed underneath without shifting layout.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HudButton(
    icon:           ImageVector,
    label:          String,
    onClick:        () -> Unit,
    focusRequester: FocusRequester? = null,
    size:           Dp = 52.dp,
    iconSize:       Dp = 26.dp,
    active:         Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        scale.animateTo(if (focused) 1.12f else 1f, if (focused) IpxMotion.focusSpring() else IpxMotion.releaseSpring())
    }
    val labelAlpha by animateFloatAsState(
        targetValue   = if (focused) 1f else 0f,
        animationSpec = tween(if (focused) IpxMotion.Standard else IpxMotion.Fast, easing = IpxMotion.EaseOut),
        label         = "hudLabel"
    )

    Box(contentAlignment = Alignment.TopCenter) {
        Surface(
            onClick  = onClick,
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .size(size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            shape  = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor        = Color.White.copy(alpha = if (size > 56.dp) 0.16f else 0f),
                contentColor          = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor   = Color.Black,
                pressedContainerColor = Color(0xFFD1D1D6),
                pressedContentColor   = Color.Black
            ),
            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
            glow   = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
                if (active && !focused) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 7.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
        // Zero-size layout so the caption never changes the row's spacing.
        Text(
            text     = label,
            style    = IpxTypography.LabelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
            color    = Color.White,
            maxLines = 1,
            modifier = Modifier
                .layout { measurable, _ ->
                    val p = measurable.measure(Constraints())
                    layout(0, 0) { p.place(-p.width / 2, (size + 10.dp).roundToPx()) }
                }
                .graphicsLayer { alpha = labelAlpha }
        )
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3_600
    val m = (totalSec % 3_600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
