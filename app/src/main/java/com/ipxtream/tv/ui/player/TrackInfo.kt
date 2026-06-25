package com.ipxtream.tv.ui.player

// ─── Track data model ─────────────────────────────────────────────────────────

/**
 * UI-safe representation of a single audio or subtitle rendition.
 *
 * Created from an [androidx.media3.common.Tracks.Group] by [PlayerViewModel]'s
 * track parser. The raw [Tracks.Group] is NOT stored in this class — it lives
 * in the ViewModel so the Composable never touches Media3 directly.
 *
 * @param groupIndex  Index into [PlayerViewModel]'s `rawAudioGroups` or
 *                    `rawTextGroups` list. Used to reconstruct the
 *                    [TrackSelectionOverride] on selection.
 * @param trackIndex  Index within the [Tracks.Group] (the rendition index).
 * @param label       Human-readable display label, e.g. "English · AC3 · 5.1".
 * @param language    BCP-47 language tag, e.g. "en", "ar-SA". Null if unknown.
 * @param isSelected  Whether this rendition is currently active in the player.
 * @param isSupported Whether the device's codecs can decode this track.
 *                    Unsupported tracks should be shown greyed-out in the UI.
 */
data class TrackInfo(
    val groupIndex:  Int,
    val trackIndex:  Int,
    val label:       String,
    val language:    String?,
    val isSelected:  Boolean,
    val isSupported: Boolean
)

// ─── Track menu type enum ─────────────────────────────────────────────────────

/**
 * Which track-selection panel is currently visible inside the HUD.
 * [NONE] means the panel is closed.
 */
enum class TrackMenuType { NONE, AUDIO, SUBTITLE }
