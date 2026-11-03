package com.ipxtream.tv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Colour tokens, named by role. Two palettes: Ember (the brand from الهوية البصرية/: Ember / Orchid /
// Halo / Night) and Apple (tvOS dark appearance: system greys, system blue, white focus).
// `Accent` takes `TextOnAccent`; focus platters are `ChipFocused` / `BorderFocus` with `TextOnFocus`.
// Use these tokens, not raw colour literals. Each token reads [activeColors], a snapshot state, so
// switching the theme in Settings recomposes and redraws everything that uses it.

class IpxColors(
    val slateDeep: Color, val slatePrimary: Color, val slateCard: Color, val slateGlass: Color,
    val surfaceDim: Color, val slateNav: Color, val surfaceElevated: Color, val surfaceElevated2: Color,
    val glassFill: Color, val glassFillFocused: Color, val glassStroke: Color, val hairline: Color,
    val accent: Color, val accentDim: Color, val accentGlow: Color, val accentOrchid: Color,
    val accentOrchidDeep: Color, val accentAmber: Color, val accentGreen: Color, val accentRed: Color,
    val textPrimary: Color, val textSecondary: Color, val textMuted: Color, val textOnAccent: Color,
    val textOnFocus: Color, val borderSubtle: Color, val borderFocus: Color, val chipBackground: Color,
    val chipFocused: Color, val focusPressed: Color, val progressWatched: Color, val progressRail: Color,
    val shimmerBase: Color, val shimmerHighlight: Color
)

/** Brand palette: plum-tinted Night neutrals, Ember accent, Halo focus. */
val EmberColors = IpxColors(
    slateDeep = Color(0xFF0D0811), slatePrimary = Color(0xFF0D0811), slateCard = Color(0xFF1D1424),
    slateGlass = Color(0xEE271C2F), surfaceDim = Color(0xFF150E1B), slateNav = Color(0xFF0D0811),
    surfaceElevated = Color(0xFF271C2F), surfaceElevated2 = Color(0xFF362A40),
    glassFill = Color(0x2EFFF4EA), glassFillFocused = Color(0x4DFFF4EA), glassStroke = Color(0x33FFE6D1),
    hairline = Color(0x1AFFE6D1),
    accent = Color(0xFFFF6A2B), accentDim = Color(0xFFB83D12), accentGlow = Color(0x55FF6A2B),
    accentOrchid = Color(0xFFB488FF), accentOrchidDeep = Color(0xFF6E3BD0), accentAmber = Color(0xFFFFC23D),
    accentGreen = Color(0xFF34D69C), accentRed = Color(0xFFFF5470),
    textPrimary = Color(0xFFF8F4F6), textSecondary = Color(0xFFB8ACBE), textMuted = Color(0xFF8F8296),
    textOnAccent = Color(0xFF0D0811), textOnFocus = Color(0xFF0D0811),
    borderSubtle = Color(0xFF362A40), borderFocus = Color(0xFFFFE6D1), chipBackground = Color(0xFF362A40),
    chipFocused = Color(0xFFFFE6D1), focusPressed = Color(0xFFFFD2AE),
    progressWatched = Color(0xFFFF6A2B), progressRail = Color(0x40FFE6D1),
    shimmerBase = Color(0xFF1D1424), shimmerHighlight = Color(0xFF362A40)
)

/**
 * Apple palette: the tvOS dark appearance. Neutral greys (#1C1C1E/#2C2C2E/#3A3A3C), system blue
 * #0A84FF for accents with white text on it, a white focus platter with near-black text, white
 * progress like the Apple TV app, and Apple's dark system green/red/yellow/purple.
 */
val AppleColors = IpxColors(
    slateDeep = Color(0xFF0B0B0D), slatePrimary = Color(0xFF0B0B0D), slateCard = Color(0xFF1C1C1E),
    slateGlass = Color(0xEE2C2C2E), surfaceDim = Color(0xFF141416), slateNav = Color(0xFF0B0B0D),
    surfaceElevated = Color(0xFF2C2C2E), surfaceElevated2 = Color(0xFF3A3A3C),
    glassFill = Color(0x2EFFFFFF), glassFillFocused = Color(0x4DFFFFFF), glassStroke = Color(0x33FFFFFF),
    hairline = Color(0x1AFFFFFF),
    accent = Color(0xFF0A84FF), accentDim = Color(0xFF0058B8), accentGlow = Color(0x550A84FF),
    accentOrchid = Color(0xFFBF5AF2), accentOrchidDeep = Color(0xFF8944AB), accentAmber = Color(0xFFFFD60A),
    accentGreen = Color(0xFF30D158), accentRed = Color(0xFFFF453A),
    textPrimary = Color(0xFFF5F5F7), textSecondary = Color(0xFFAEAEB2), textMuted = Color(0xFF8E8E93),
    textOnAccent = Color(0xFFFFFFFF), textOnFocus = Color(0xFF1D1D1F),
    borderSubtle = Color(0xFF3A3A3C), borderFocus = Color(0xFFFFFFFF), chipBackground = Color(0xFF2C2C2E),
    chipFocused = Color(0xFFFFFFFF), focusPressed = Color(0xFFD1D1D6),
    progressWatched = Color(0xFFFFFFFF), progressRail = Color(0x40FFFFFF),
    shimmerBase = Color(0xFF1C1C1E), shimmerHighlight = Color(0xFF2C2C2E)
)

/** The palette in use. Set by [IpxTvTheme] from Settings → General → Theme. */
var activeColors by mutableStateOf(EmberColors)

// ─── Canvas & Surfaces ───────────────────────────────────────────────────────

/** Canvas — primary background of every screen. Not #000: avoids blooming on LCDs. */
val SlateDeep get() = activeColors.slateDeep
/** Main surface background for content areas. */
val SlatePrimary get() = activeColors.slatePrimary
/** Resting card surface on the canvas. */
val SlateCard get() = activeColors.slateCard
/** Raised glass surface — dialogs, focused panels. */
val SlateGlass get() = activeColors.slateGlass
/** Panels a step below cards — expanded side menu, channel tiles. */
val SurfaceDim get() = activeColors.surfaceDim
/** Sidebar / nav panel background. */
val SlateNav get() = activeColors.slateNav
/** Elevated surface, one step above [SlateCard]. */
val SurfaceElevated get() = activeColors.surfaceElevated
/** Elevated surface, two steps above [SlateCard]. */
val SurfaceElevated2 get() = activeColors.surfaceElevated2

// ─── Glass material ───────────────────────────────────────────────────────────

/** Translucent fill for glass buttons and chips at rest. */
val GlassFill get() = activeColors.glassFill
/** Translucent fill for glass elements while focused. */
val GlassFillFocused get() = activeColors.glassFillFocused
/** 1dp stroke that defines the edge of a glass element. */
val GlassStroke get() = activeColors.glassStroke
/** Hairline separators and resting card outlines. */
val Hairline get() = activeColors.hairline

// ─── Accent ───────────────────────────────────────────────────────────────────

/** Primary accent. Active states, progress, primary buttons. Put [TextOnAccent] on it. */
val Accent get() = activeColors.accent
/** Dimmed accent — unfocused active item indicator. */
val AccentDim get() = activeColors.accentDim
/** Semi-transparent accent glow. */
val AccentGlow get() = activeColors.accentGlow
/** Secondary accent (Orchid / system purple). */
val AccentOrchid get() = activeColors.accentOrchid
/** Deep secondary accent — gradient ends. */
val AccentOrchidDeep get() = activeColors.accentOrchidDeep
/** Ratings and warnings. */
val AccentAmber get() = activeColors.accentAmber
/** "New" badges, success. */
val AccentGreen get() = activeColors.accentGreen
/** Live indicator dot and destructive actions. */
val AccentRed get() = activeColors.accentRed

// ─── Text ─────────────────────────────────────────────────────────────────────

/** Primary text, softer than #FFFFFF at 10 feet. */
val TextPrimary get() = activeColors.textPrimary
/** Secondary text — metadata, subtitles, descriptions. */
val TextSecondary get() = activeColors.textSecondary
/** Muted text — timestamps, less important labels. */
val TextMuted get() = activeColors.textMuted
/** Text on filled accent / status elements. */
val TextOnAccent get() = activeColors.textOnAccent
/** Text and icons on the focus platter. */
val TextOnFocus get() = activeColors.textOnFocus

// ─── UI Elements ─────────────────────────────────────────────────────────────

/** Divider and border lines. */
val BorderSubtle get() = activeColors.borderSubtle
/** Focused element border colour. */
val BorderFocus get() = activeColors.borderFocus
/** Unfocused category chip fill. */
val ChipBackground get() = activeColors.chipBackground
/** Focus platter for buttons, chips and menu items. */
val ChipFocused get() = activeColors.chipFocused
/** Focus platter while pressed. */
val FocusPressed get() = activeColors.focusPressed
/** Watched-progress fill on posters and the player scrubber. */
val ProgressWatched get() = activeColors.progressWatched
/** Track behind [ProgressWatched]. */
val ProgressRail get() = activeColors.progressRail
/** Skeleton loading shimmer base. */
val ShimmerBase get() = activeColors.shimmerBase
/** Skeleton loading shimmer highlight. */
val ShimmerHighlight get() = activeColors.shimmerHighlight
