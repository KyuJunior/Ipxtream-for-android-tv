package com.ipxtream.tv.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Slate & Glass Palette (Netflix/Prime Pitch Black) ─────────────────────────

/** Pure Black — primary background of every screen. */
val SlateDeep        = Color(0xFF000000)
/** Main surface background for content areas — deep charcoal. */
val SlatePrimary     = Color(0xFF0F0F0F)
/** Slightly lighter — used as card/surface background. */
val SlateCard        = Color(0xFF1C1C1C)
/** Glass card tint — dark charcoal for highlighted surfaces. */
val SlateGlass       = Color(0xEE141414)
/** Sidebar / nav panel background. */
val SlateNav         = Color(0xDD000000)

// ─── Accent ───────────────────────────────────────────────────────────────────

/** Primary Prime Blue accent — focus rings, active states, logos. */
val AccentCyan       = Color(0xFF00A8E1)
/** Dimmed Blue — unfocused active item indicator. */
val AccentCyanDim    = Color(0xFF007A99)
/** Semi-transparent Blue glow for card elevation colour. */
val AccentCyanGlow   = Color(0x5500A8E1)
/** Amber — used for ratings and warnings. */
val AccentAmber      = Color(0xFFFFB347)
/** Soft green — for "Live" / "New" badges. */
val AccentGreen      = Color(0xFF4ADE80)

// ─── Text ─────────────────────────────────────────────────────────────────────

/** Primary text — near-white for maximum readability on dark. */
val TextPrimary      = Color(0xFFE2EAF3)
/** Secondary text — metadata, subtitles, descriptions. */
val TextSecondary    = Color(0xFF7A8FA6)
/** Muted text — timestamps, less important labels. */
val TextMuted        = Color(0xFF4A5F73)
/** Text on focused/filled elements (e.g. chip label when selected). */
val TextOnAccent     = Color(0xFF0B1520)

// ─── UI Elements ─────────────────────────────────────────────────────────────

/** Divider and border lines. */
val BorderSubtle     = Color(0xFF262626)
/** Focused element border colour. */
val BorderFocus      = AccentCyan
/** Unfocused category chip fill. */
val ChipBackground   = Color(0xFF262626)
/** Focused chip fill. */
val ChipFocused      = AccentCyan
/** Skeleton loading shimmer base. */
val ShimmerBase      = Color(0xFF1C1C1C)
/** Skeleton loading shimmer highlight. */
val ShimmerHighlight = Color(0xFF2A2A2A)
