package com.ipxtream.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.BorderSubtle
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.SlateNav
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

private const val SUBTITLE_NONE_GROUP = -1
private const val SUBTITLE_NONE_TRACK = -1

/**
 * Slide-in track selection panel rendered alongside the [PlayerHud].
 *
 * ## D-Pad behaviour
 * - DPAD_UP / DPAD_DOWN navigate the [LazyColumn] of track items.
 * - DPAD_CENTER / ENTER selects the focused item.
 * - BACK is intercepted by [PlayerPanel]'s `onPreviewKeyEvent` to call
 *   [onDismiss] — the menu itself does not consume BACK.
 *
 * The panel slides in from the **right** edge over the video surface.
 * It is constrained to max 280dp wide to avoid covering the full video.
 *
 * @param menuType         Whether this shows AUDIO or SUBTITLE options.
 * @param audioTracks      Available audio renditions.
 * @param subtitleTracks   Available subtitle renditions.
 * @param areSubtitlesEnabled Whether subtitles are currently active.
 * @param onSelectAudio      Callback with (groupIndex, trackIndex).
 * @param onSelectSubtitle   Callback with (groupIndex, trackIndex).
 * @param onDisableSubtitles Called when the user picks "(None)".
 * @param onDismiss          Called when the panel should close.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackSelectionMenu(
    menuType:            TrackMenuType,
    audioTracks:         List<TrackInfo>,
    subtitleTracks:      List<TrackInfo>,
    areSubtitlesEnabled: Boolean,
    onSelectAudio:       (Int, Int) -> Unit,
    onSelectSubtitle:    (Int, Int) -> Unit,
    onDisableSubtitles:  () -> Unit,
    onDismiss:           () -> Unit,
    modifier:            Modifier = Modifier
) {
    val isVisible    = menuType != TrackMenuType.NONE
    val isAudioMenu  = menuType == TrackMenuType.AUDIO
    val title        = if (isAudioMenu) "Audio Track" else "Subtitles"
    val firstFocuser = remember { FocusRequester() }
    val listState    = rememberLazyListState()

    // Auto-focus the currently selected item (or first item) when the menu opens.
    LaunchedEffect(isVisible, menuType) {
        if (isVisible) {
            runCatching { firstFocuser.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible  = isVisible,
        enter    = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit     = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        // Dark scrim behind the panel
        Box(
            modifier         = Modifier.fillMaxSize().background(Color(0x88000000)),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Panel itself
            Column(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(SlateNav)
                    .padding(vertical = 16.dp)
            ) {
                // Header
                Text(
                    text     = title,
                    style    = IpxTypography.TitleMedium,
                    color    = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 12.dp)
                        .background(BorderSubtle)
                )
                Spacer(Modifier.height(8.dp))

                // Track list
                LazyColumn(
                    state   = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isAudioMenu) {
                        // "(None)" — disable subtitles option (subtitle menu only)
                        item(key = "none") {
                            TrackMenuItem(
                                label       = "(None)",
                                sublabel    = "Turn off subtitles",
                                isSelected  = !areSubtitlesEnabled,
                                isSupported = true,
                                isFocusTarget = true,
                                focusRequester = firstFocuser,
                                onClick     = onDisableSubtitles
                            )
                        }
                    }

                    val tracks = if (isAudioMenu) audioTracks else subtitleTracks
                    items(tracks, key = { "${it.groupIndex}_${it.trackIndex}" }) { track ->
                        val isFirstAudioItem = isAudioMenu &&
                            track == tracks.firstOrNull()
                        TrackMenuItem(
                            label          = track.label,
                            sublabel       = if (!track.isSupported) "⚠ Not supported" else null,
                            isSelected     = track.isSelected,
                            isSupported    = track.isSupported,
                            isFocusTarget  = isFirstAudioItem,
                            focusRequester = if (isFirstAudioItem) firstFocuser else null,
                            onClick        = {
                                if (isAudioMenu) onSelectAudio(track.groupIndex, track.trackIndex)
                                else             onSelectSubtitle(track.groupIndex, track.trackIndex)
                            }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
//  Single track item
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackMenuItem(
    label:          String,
    sublabel:       String?,
    isSelected:     Boolean,
    isSupported:    Boolean,
    isFocusTarget:  Boolean,
    focusRequester: FocusRequester?,
    onClick:        () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor   = when {
        isSelected && isFocused -> AccentCyan
        isSelected              -> SlateGlass
        isFocused               -> SlateCard
        else                    -> Color.Transparent
    }
    val textColor = when {
        isSelected && isFocused -> TextOnAccent
        isSelected              -> AccentCyan
        isFocused               -> TextPrimary
        else                    -> if (isSupported) TextSecondary else TextMuted
    }

    val rowMod = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier

    Surface(
        onClick  = onClick,
        modifier = rowMod
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors   = ClickableSurfaceDefaults.colors(containerColor = bgColor),
        shape    = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator dot
            Text(
                text      = if (isSelected) "●" else "○",
                style     = IpxTypography.BodyMedium,
                color     = if (isSelected) AccentCyan else TextMuted,
                modifier  = Modifier.width(20.dp)
            )
            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    text      = label,
                    style     = IpxTypography.BodyMedium,
                    color     = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (!sublabel.isNullOrBlank()) {
                    Text(
                        text  = sublabel,
                        style = IpxTypography.LabelSmall,
                        color = if (isSelected && isFocused) TextOnAccent.copy(0.7f) else TextMuted
                    )
                }
            }
        }
    }
}
