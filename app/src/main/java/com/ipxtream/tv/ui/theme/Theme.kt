package com.ipxtream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

// ─── Typography ───────────────────────────────────────────────────────────────
// Using system default font family; swap for a custom font by replacing
// FontFamily.Default with FontFamily(Font(R.font.your_font))

private val IpxFontFamily = FontFamily.Default

/** Base typography styles scaled for a 10-foot TV viewing distance. */
object IpxTypography {
    val DisplayLarge  = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.Bold,   fontSize = 48.sp, lineHeight = 56.sp)
    val HeadlineMedium = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp)
    val TitleLarge    = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val TitleMedium   = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.Medium,  fontSize = 16.sp, lineHeight = 24.sp)
    val BodyMedium    = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.Normal,  fontSize = 14.sp, lineHeight = 20.sp)
    val BodySmall     = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.Normal,  fontSize = 12.sp, lineHeight = 16.sp)
    val LabelSmall    = TextStyle(fontFamily = IpxFontFamily, fontWeight = FontWeight.Medium,  fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
}

// ─── Colour Scheme ────────────────────────────────────────────────────────────

private val IpxDarkColors = darkColorScheme(
    primary          = AccentCyan,
    onPrimary        = TextOnAccent,
    primaryContainer = SlateGlass,
    background       = SlateDeep,
    surface          = SlatePrimary,
    surfaceVariant   = SlateCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline          = BorderSubtle,
    error            = Color(0xFFFF5555),
    onError          = Color.White
)

// ─── Theme Composable ─────────────────────────────────────────────────────────

/**
 * Root theme wrapper for all IPXtream TV screens.
 *
 * Applies the Slate & Glass dark colour scheme and TV-scaled typography.
 * Wrap every screen's root composable with this.
 *
 * The [TvMaterialTheme] wrapper is required for TV Material 3 components
 * (Card, Surface, NavigationDrawer, etc.) to pick up the colour scheme.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IpxTvTheme(content: @Composable () -> Unit) {
    // Standard M3 theme (for non-TV composables like TextField, SnackBar)
    MaterialTheme(
        colorScheme = IpxDarkColors
    ) {
        // TV M3 theme (for Card, Surface focus states, etc.)
        TvMaterialTheme(
            colorScheme = androidx.tv.material3.darkColorScheme(
                primary          = AccentCyan,
                onPrimary        = TextOnAccent,
                background       = SlateDeep,
                surface          = SlatePrimary,
                onBackground     = TextPrimary,
                onSurface        = TextPrimary,
                onSurfaceVariant = TextSecondary,
            ),
            content = content
        )
    }
}
