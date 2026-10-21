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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import com.ipxtream.tv.ui.theme.Hairline
import com.ipxtream.tv.ui.theme.IpxMotion
import androidx.compose.foundation.border
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
        enter    = fadeIn(androidx.compose.animation.core.tween(IpxMotion.Standard)) +
            slideInHorizontally(androidx.compose.animation.core.tween(500, easing = IpxMotion.EaseApple)) { it / 3 },
        exit     = fadeOut(androidx.compose.animation.core.tween(IpxMotion.Fast)) +
            slideOutHorizontally(androidx.compose.animation.core.tween(IpxMotion.Standard)) { it / 4 },
        modifier = modifier
    ) {
        // Dark scrim behind the panel
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color(0x59000000),
                        1f to Color(0xB3000000)
                    )
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Floating glass sheet
            Column(
                modifier = Modifier
                    .padding(top = 32.dp, bottom = 32.dp, end = 32.dp)
                    .widthIn(min = 360.dp, max = 440.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF21C1C1E))
                    .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                    .padding(vertical = 28.dp)
            ) {
                Text(
                    text     = if (isAudioMenu) "AUDIO" else "SUBTITLES",
                    style    = IpxTypography.Eyebrow,
                    color    = TextSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text     = if (isAudioMenu) "Choose a language" else "Choose subtitles",
                    style    = IpxTypography.HeadlineMedium,
                    color    = Color.White,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(Modifier.height(20.dp))

                // Track list
                LazyColumn(
                    state   = listState,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    if (isAudioMenu && tracks.isEmpty()) {
                        item(key = "audio_default") {
                            TrackMenuItem(
                                label       = "Default Audio",
                                sublabel    = "Original stereo/surround stream",
                                isSelected  = true,
                                isSupported = true,
                                isFocusTarget = true,
                                focusRequester = firstFocuser,
                                onClick     = onDismiss
                            )
                        }
                    } else if (!isAudioMenu && tracks.isEmpty()) {
                        item(key = "sub_empty") {
                            TrackMenuItem(
                                label       = "No Subtitles",
                                sublabel    = "No subtitle streams detected",
                                isSelected  = false,
                                isSupported = false,
                                isFocusTarget = false,
                                focusRequester = null,
                                onClick     = onDismiss
                            )
                        }
                    }

                    items(tracks, key = { "${it.groupIndex}_${it.trackIndex}" }) { track ->
                        val isFirstAudioItem = isAudioMenu &&
                            track == tracks.firstOrNull()
                        TrackMenuItem(
                            label          = track.label,
                            sublabel       = if (!track.isSupported) "Not supported" else null,
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

    // White platter on focus; quiet text otherwise. Selection is the checkmark.
    val textColor = when {
        isFocused  -> Color.Black
        isSelected -> Color.White
        else       -> if (isSupported) TextPrimary.copy(alpha = 0.82f) else TextMuted
    }

    val rowMod = if (focusRequester != null)
        Modifier.focusRequester(focusRequester) else Modifier

    Surface(
        onClick  = onClick,
        modifier = rowMod
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color(0xFFD1D1D6)
        ),
        shape    = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border   = ClickableSurfaceDefaults.border(
            border        = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border.None
        )
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            Box(
                modifier = Modifier.width(20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    text      = label,
                    style     = IpxTypography.BodyLarge,
                    color     = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (!sublabel.isNullOrBlank()) {
                    Text(
                        text  = sublabel,
                        style = IpxTypography.LabelSmall,
                        color = if (isFocused) Color.Black.copy(alpha = 0.6f) else TextSecondary
                    )
                }
            }
        }
    }
}
