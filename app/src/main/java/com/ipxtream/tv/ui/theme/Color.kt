package com.ipxtream.tv.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Canvas & Surfaces (cinematic black, Apple-style neutral greys) ──────────

/** Pure black canvas — primary background of every screen. */
val SlateDeep        = Color(0xFF000000)
/** Main surface background for content areas — pure black. */
val SlatePrimary     = Color(0xFF000000)
/** Resting card surface on the black canvas. */
val SlateCard        = Color(0xFF161618)
/** Raised glass surface — dialogs, focused panels. */
val SlateGlass       = Color(0xEE1C1C1E)
/** Sidebar / nav panel background. */
val SlateNav         = Color(0xFF000000)
/** Elevated surface, one step above [SlateCard]. */
val SurfaceElevated  = Color(0xFF1C1C1E)
/** Elevated surface, two steps above [SlateCard]. */
val SurfaceElevated2 = Color(0xFF2C2C2E)

// ─── Glass material ───────────────────────────────────────────────────────────

/** Translucent fill for glass buttons and chips at rest. */
val GlassFill        = Color(0x2EFFFFFF)
/** Translucent fill for glass elements while focused. */
val GlassFillFocused = Color(0x4DFFFFFF)
/** 1dp stroke that defines the edge of a glass element. */
val GlassStroke      = Color(0x33FFFFFF)
/** Hairline separators and resting card outlines. */
val Hairline         = Color(0x1AFFFFFF)

// ─── Accent ───────────────────────────────────────────────────────────────────

/** Primary accent (system blue) — active states, progress, links. */
val AccentCyan       = Color(0xFF0A84FF)
/** Dimmed accent — unfocused active item indicator. */
val AccentCyanDim    = Color(0xFF0060C7)
/** Semi-transparent accent glow. */
val AccentCyanGlow   = Color(0x550A84FF)
/** Warm yellow — ratings and warnings. */
val AccentAmber      = Color(0xFFFFD60A)
/** Green — "Live" / "New" badges. */
val AccentGreen      = Color(0xFF30D158)
/** Red — live indicator dot and destructive actions. */
val AccentRed        = Color(0xFFFF453A)

// ─── Text ─────────────────────────────────────────────────────────────────────

/** Primary text — soft white, easier on the eye than #FFFFFF at 10 feet. */
val TextPrimary      = Color(0xFFF5F5F7)
/** Secondary text — metadata, subtitles, descriptions. */
val TextSecondary    = Color(0xFFA1A1A6)
/** Muted text — timestamps, less important labels. */
val TextMuted        = Color(0xFF6E6E73)
/** Text on filled accent elements. */
val TextOnAccent     = Color(0xFFFFFFFF)

// ─── UI Elements ─────────────────────────────────────────────────────────────

/** Divider and border lines. */
val BorderSubtle     = Color(0xFF2C2C2E)
/** Focused element border colour. */
val BorderFocus      = Color(0xFFFFFFFF)
/** Unfocused category chip fill. */
val ChipBackground   = Color(0xFF2C2C2E)
/** Focused chip fill. */
val ChipFocused      = Color(0xFFFFFFFF)
/** Watched-progress fill on posters and the player scrubber. */
val ProgressWatched  = Color(0xFFFFFFFF)
/** Track behind [ProgressWatched]. */
val ProgressRail     = Color(0x4DFFFFFF)
/** Skeleton loading shimmer base. */
val ShimmerBase      = Color(0xFF161618)
/** Skeleton loading shimmer highlight. */
val ShimmerHighlight = Color(0xFF2C2C2E)
