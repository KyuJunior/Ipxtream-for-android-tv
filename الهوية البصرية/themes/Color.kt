package com.ipxtream.tv.ui.theme

// IPXtream brand colours — generated from الهوية البصرية/_source/build-brand.js.
// Not wired into the app yet: replacing the current Color.kt is a separate decision.

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Raw palette ──────────────────────────────────────────────────────────────

object IpxPalette {
    // Ember (جمرة) — Primary. Brand colour: primary buttons, progress, the X. Dark text on top (Night 950), never white.
    val Ember50 = Color(0xFFFFF1EA)
    val Ember100 = Color(0xFFFFDCCB)
    val Ember200 = Color(0xFFFFBB9B)
    val Ember300 = Color(0xFFFF9A6B)
    val Ember400 = Color(0xFFFF8048)
    val Ember500 = Color(0xFFFF6A2B)
    val Ember600 = Color(0xFFE8521A)
    val Ember700 = Color(0xFFB83D12)
    val Ember800 = Color(0xFF86300F)
    val Ember900 = Color(0xFF4D1C0B)

    // Orchid (أوركيد) — Secondary. Secondary actions, selected chips, series/collection tags.
    val Orchid50 = Color(0xFFF6F0FF)
    val Orchid100 = Color(0xFFEADCFF)
    val Orchid200 = Color(0xFFD6BCFF)
    val Orchid300 = Color(0xFFC39BFF)
    val Orchid400 = Color(0xFFB488FF)
    val Orchid500 = Color(0xFFA574FF)
    val Orchid600 = Color(0xFF8A55F0)
    val Orchid700 = Color(0xFF6E3BD0)
    val Orchid800 = Color(0xFF4F2A99)
    val Orchid900 = Color(0xFF2C1A52)

    // Halo (هالة) — Accent / focus. Warm light used for the focus platter and the play triangle. Replaces pure white.
    val Halo100 = Color(0xFFFFF4EA)
    val Halo200 = Color(0xFFFFE6D1)
    val Halo300 = Color(0xFFFFD2AE)

    // Night (ليل) — Neutrals (dark). Plum-tinted neutrals for the dark theme: canvas, surfaces, borders, text.
    val Night50 = Color(0xFFF8F4F6)
    val Night100 = Color(0xFFEEE8EF)
    val Night200 = Color(0xFFD9D0DC)
    val Night300 = Color(0xFFB8ACBE)
    val Night400 = Color(0xFF8F8296)
    val Night500 = Color(0xFF6A5C74)
    val Night600 = Color(0xFF4B3D56)
    val Night700 = Color(0xFF362A40)
    val Night800 = Color(0xFF271C2F)
    val Night850 = Color(0xFF1D1424)
    val Night900 = Color(0xFF150E1B)
    val Night950 = Color(0xFF0D0811)

    // Paper (ورق) — Neutrals (light). Warm paper neutrals for the light theme.
    val Paper0 = Color(0xFFFFFFFF)
    val Paper50 = Color(0xFFFBF7F4)
    val Paper100 = Color(0xFFF4EDE9)
    val Paper200 = Color(0xFFEAE1DD)
    val Paper300 = Color(0xFFDCD0CD)

    // Jade (يَشْم) — Success. 400 on dark, 700 on light.
    val Jade400 = Color(0xFF34D69C)
    val Jade700 = Color(0xFF0E7A55)

    // Saffron (زعفران) — Warning. 400 on dark, 700 on light. Ratings stars use it too.
    val Saffron400 = Color(0xFFFFC23D)
    val Saffron700 = Color(0xFF8F5B00)

    // Rose (ورد) — Error / Live. 400 on dark, 700 on light. Also the LIVE dot.
    val Rose400 = Color(0xFFFF5470)
    val Rose700 = Color(0xFFC21E3D)
}

// ─── Theme roles ──────────────────────────────────────────────────────────────

@Immutable
data class IpxColors(
    /** Screen canvas. */
    val background: Color,
    /** Panels, side menu. */
    val surface: Color,
    /** Resting cards and posters. */
    val card: Color,
    /** Dialogs, raised panels. */
    val surfaceRaised: Color,
    /** Dividers, resting outlines. */
    val outline: Color,
    /** Input borders, strong dividers. */
    val outlineStrong: Color,
    /** Titles and body text. */
    val textPrimary: Color,
    /** Metadata, descriptions. */
    val textSecondary: Color,
    /** Timestamps, hints. */
    val textMuted: Color,
    /** Disabled labels. */
    val textDisabled: Color,
    /** Primary buttons, brand highlights. */
    val primary: Color,
    /** Text/icons on primary. */
    val onPrimary: Color,
    /** Tinted primary background. */
    val primaryContainer: Color,
    /** Text on primaryContainer. */
    val onPrimaryContainer: Color,
    /** Primary-coloured text and links on the canvas. */
    val primaryText: Color,
    /** Secondary actions, selected chips. */
    val secondary: Color,
    /** Text on secondary. */
    val onSecondary: Color,
    /** Tinted secondary background. */
    val secondaryContainer: Color,
    /** Text on secondaryContainer. */
    val onSecondaryContainer: Color,
    /** Focused button / chip platter. */
    val focus: Color,
    /** Text on the focus platter. */
    val onFocus: Color,
    /** Focus outline on posters. */
    val focusRing: Color,
    /** Success, "New" badges. */
    val success: Color,
    /** Warnings, rating stars. */
    val warning: Color,
    /** Errors, destructive actions. */
    val error: Color,
    /** LIVE indicator. */
    val live: Color,
    /** Text on filled status badges. */
    val onStatus: Color,
    /** Watched progress, scrubber. */
    val progress: Color,
    /** Track behind progress. */
    val progressTrack: Color,
    /** Overlay behind dialogs / over video. */
    val scrim: Color,
    /** Translucent chip/button fill. */
    val glass: Color,
    /** Hairline separators. */
    val hairline: Color,
    val isDark: Boolean,
)

/** Primary theme on TV. */
val IpxDarkColors = IpxColors(
    background = IpxPalette.Night950,
    surface = IpxPalette.Night900,
    card = IpxPalette.Night850,
    surfaceRaised = IpxPalette.Night800,
    outline = IpxPalette.Night700,
    outlineStrong = IpxPalette.Night600,
    textPrimary = IpxPalette.Night50,
    textSecondary = IpxPalette.Night300,
    textMuted = IpxPalette.Night400,
    textDisabled = IpxPalette.Night500,
    primary = IpxPalette.Ember500,
    onPrimary = IpxPalette.Night950,
    primaryContainer = Color(0xFF4D1C0B),
    onPrimaryContainer = IpxPalette.Ember200,
    primaryText = IpxPalette.Ember400,
    secondary = IpxPalette.Orchid400,
    onSecondary = IpxPalette.Night950,
    secondaryContainer = IpxPalette.Orchid900,
    onSecondaryContainer = IpxPalette.Orchid100,
    focus = IpxPalette.Halo200,
    onFocus = IpxPalette.Night950,
    focusRing = IpxPalette.Halo200,
    success = IpxPalette.Jade400,
    warning = IpxPalette.Saffron400,
    error = IpxPalette.Rose400,
    live = IpxPalette.Rose400,
    onStatus = IpxPalette.Night950,
    progress = IpxPalette.Ember500,
    progressTrack = Color(0x40FFE6D1),
    scrim = Color(0xCC0D0811),
    glass = Color(0x1FFFE6D1),
    hairline = Color(0x1AFFE6D1),
    isDark = true,
)

/** Light theme (settings preview, phone companion, daytime use). */
val IpxLightColors = IpxColors(
    background = IpxPalette.Paper50,
    surface = IpxPalette.Paper0,
    card = IpxPalette.Paper100,
    surfaceRaised = IpxPalette.Paper200,
    outline = IpxPalette.Paper300,
    outlineStrong = IpxPalette.Night300,
    textPrimary = IpxPalette.Night900,
    textSecondary = IpxPalette.Night600,
    textMuted = IpxPalette.Night500,
    textDisabled = IpxPalette.Night300,
    primary = IpxPalette.Ember600,
    onPrimary = IpxPalette.Night950,
    primaryContainer = IpxPalette.Ember100,
    onPrimaryContainer = IpxPalette.Ember800,
    primaryText = IpxPalette.Ember700,
    secondary = IpxPalette.Orchid700,
    onSecondary = IpxPalette.Paper0,
    secondaryContainer = IpxPalette.Orchid100,
    onSecondaryContainer = IpxPalette.Orchid800,
    focus = IpxPalette.Night900,
    onFocus = IpxPalette.Halo100,
    focusRing = IpxPalette.Night900,
    success = IpxPalette.Jade700,
    warning = IpxPalette.Saffron700,
    error = IpxPalette.Rose700,
    live = IpxPalette.Rose700,
    onStatus = IpxPalette.Paper0,
    progress = IpxPalette.Ember600,
    progressTrack = Color(0x26150E1B),
    scrim = Color(0x99150E1B),
    glass = Color(0x0F150E1B),
    hairline = Color(0x1A150E1B),
    isDark = false,
)

/** Read with LocalIpxColors.current; provide IpxLightColors at the theme root for the light theme. */
val LocalIpxColors = staticCompositionLocalOf { IpxDarkColors }
