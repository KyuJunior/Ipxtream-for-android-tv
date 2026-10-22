package com.ipxtream.tv.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette from الهوية البصرية/ (Ember / Orchid / Halo / Night), named by role.
// `Accent` is Ember (put `TextOnAccent` on it); focus platters are `ChipFocused` / `BorderFocus`
// (Halo, with `TextOnFocus` on them). Use these tokens, not raw colour literals.

// ─── Canvas & Surfaces (plum-tinted Night neutrals) ──────────────────────────

/** Night 950 canvas — primary background of every screen. Not #000: avoids blooming on LCDs. */
val SlateDeep        = Color(0xFF0D0811)
/** Main surface background for content areas. */
val SlatePrimary     = Color(0xFF0D0811)
/** Resting card surface on the canvas (Night 850). */
val SlateCard        = Color(0xFF1D1424)
/** Raised glass surface — dialogs, focused panels (Night 800). */
val SlateGlass       = Color(0xEE271C2F)
/** Panels a step below cards — expanded side menu, channel tiles (Night 900). */
val SurfaceDim       = Color(0xFF150E1B)
/** Sidebar / nav panel background. */
val SlateNav         = Color(0xFF0D0811)
/** Elevated surface, one step above [SlateCard] (Night 800). */
val SurfaceElevated  = Color(0xFF271C2F)
/** Elevated surface, two steps above [SlateCard] (Night 700). */
val SurfaceElevated2 = Color(0xFF362A40)

// ─── Glass material (Halo-tinted) ─────────────────────────────────────────────

/** Translucent fill for glass buttons and chips at rest. */
val GlassFill        = Color(0x2EFFF4EA)
/** Translucent fill for glass elements while focused. */
val GlassFillFocused = Color(0x4DFFF4EA)
/** 1dp stroke that defines the edge of a glass element. */
val GlassStroke      = Color(0x33FFE6D1)
/** Hairline separators and resting card outlines. */
val Hairline         = Color(0x1AFFE6D1)

// ─── Accent ───────────────────────────────────────────────────────────────────

/** Primary accent — Ember. Active states, progress, primary buttons. Put [TextOnAccent] on it, never white. */
val Accent           = Color(0xFFFF6A2B)
/** Dimmed accent — unfocused active item indicator (Ember 700). */
val AccentDim        = Color(0xFFB83D12)
/** Semi-transparent accent glow. */
val AccentGlow       = Color(0x55FF6A2B)
/** Secondary accent — Orchid. */
val AccentOrchid     = Color(0xFFB488FF)
/** Deep Orchid (Orchid 700) — gradient ends. */
val AccentOrchidDeep = Color(0xFF6E3BD0)
/** Saffron — ratings and warnings. */
val AccentAmber      = Color(0xFFFFC23D)
/** Jade — "New" badges, success. */
val AccentGreen      = Color(0xFF34D69C)
/** Rose — live indicator dot and destructive actions. */
val AccentRed        = Color(0xFFFF5470)

// ─── Text ─────────────────────────────────────────────────────────────────────

/** Primary text — Night 50, softer than #FFFFFF at 10 feet. */
val TextPrimary      = Color(0xFFF8F4F6)
/** Secondary text — metadata, subtitles, descriptions (Night 300). */
val TextSecondary    = Color(0xFFB8ACBE)
/** Muted text — timestamps, less important labels (Night 400). */
val TextMuted        = Color(0xFF8F8296)
/** Text on filled Ember / status elements (Night 950). */
val TextOnAccent     = Color(0xFF0D0811)
/** Text and icons on the Halo focus platter (Night 950). */
val TextOnFocus      = Color(0xFF0D0811)

// ─── UI Elements ─────────────────────────────────────────────────────────────

/** Divider and border lines (Night 700). */
val BorderSubtle     = Color(0xFF362A40)
/** Focused element border colour (Halo). */
val BorderFocus      = Color(0xFFFFE6D1)
/** Unfocused category chip fill (Night 700). */
val ChipBackground   = Color(0xFF362A40)
/** Focus platter for buttons, chips and menu items (Halo). */
val ChipFocused      = Color(0xFFFFE6D1)
/** Focus platter while pressed (Halo 300). */
val FocusPressed     = Color(0xFFFFD2AE)
/** Watched-progress fill on posters and the player scrubber (Ember). */
val ProgressWatched  = Color(0xFFFF6A2B)
/** Track behind [ProgressWatched]. */
val ProgressRail     = Color(0x40FFE6D1)
/** Skeleton loading shimmer base. */
val ShimmerBase      = Color(0xFF1D1424)
/** Skeleton loading shimmer highlight. */
val ShimmerHighlight = Color(0xFF362A40)
