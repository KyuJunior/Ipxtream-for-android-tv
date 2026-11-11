package com.ipxtream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

// ─── Typography ───────────────────────────────────────────────────────────────
// Using system default font family; swap for a custom font by replacing
// FontFamily.Default with FontFamily(Font(R.font.your_font))

private val IpxFontFamily = FontFamily.Default

private class TypeScale(
    val heroTitle: TextStyle, val displayLarge: TextStyle, val headlineMedium: TextStyle,
    val titleLarge: TextStyle, val rowTitle: TextStyle, val titleMedium: TextStyle,
    val bodyLarge: TextStyle, val bodyMedium: TextStyle, val bodySmall: TextStyle,
    val labelSmall: TextStyle, val eyebrow: TextStyle
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Float = 0f) =
    TextStyle(fontFamily = IpxFontFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

// Display sizes use tight negative tracking, body sizes stay neutral.
private val EmberType = TypeScale(
    heroTitle = style(FontWeight.Bold, 52, 56, -1.2f), displayLarge = style(FontWeight.Bold, 48, 54, -1.0f),
    headlineMedium = style(FontWeight.SemiBold, 28, 34, -0.4f), titleLarge = style(FontWeight.SemiBold, 22, 28, -0.2f),
    rowTitle = style(FontWeight.SemiBold, 20, 26, -0.2f), titleMedium = style(FontWeight.Medium, 16, 22, -0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24), bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Normal, 12, 16), labelSmall = style(FontWeight.Medium, 11, 16, 0.4f),
    eyebrow = style(FontWeight.SemiBold, 12, 16, 1.6f)
)

// Apple's scale: Large Title 34/41 bold, Title 22/28 bold, Body 17/22, Subheadline 15/20, Footnote 13/18.
private val AppleType = TypeScale(
    heroTitle = style(FontWeight.Bold, 52, 56, 0.3f), displayLarge = style(FontWeight.Bold, 46, 52, 0.3f),
    headlineMedium = style(FontWeight.Bold, 34, 41, 0.4f), titleLarge = style(FontWeight.Bold, 22, 28, -0.26f),
    rowTitle = style(FontWeight.Bold, 20, 25, -0.45f), titleMedium = style(FontWeight.SemiBold, 17, 22, -0.43f),
    bodyLarge = style(FontWeight.Normal, 17, 22, -0.43f), bodyMedium = style(FontWeight.Normal, 15, 20, -0.23f),
    bodySmall = style(FontWeight.Normal, 13, 18, -0.08f), labelSmall = style(FontWeight.SemiBold, 11, 13, 0.06f),
    eyebrow = style(FontWeight.SemiBold, 13, 18, 0.2f)
)

/** Type styles for a 10-foot TV viewing distance. They follow the design chosen in Settings. */
object IpxTypography {
    private val scale get() = if (isAppleDesign) AppleType else EmberType
    val HeroTitle get() = scale.heroTitle
    val DisplayLarge get() = scale.displayLarge
    val HeadlineMedium get() = scale.headlineMedium
    val TitleLarge get() = scale.titleLarge
    val RowTitle get() = scale.rowTitle
    val TitleMedium get() = scale.titleMedium
    val BodyLarge get() = scale.bodyLarge
    val BodyMedium get() = scale.bodyMedium
    val BodySmall get() = scale.bodySmall
    val LabelSmall get() = scale.labelSmall
    /** Small label above a title ("MOVIE", "NEW SEASON"). */
    val Eyebrow get() = scale.eyebrow
}

// ─── Colour Scheme ────────────────────────────────────────────────────────────

private fun ipxDarkColors() = darkColorScheme(
    primary          = Accent,
    onPrimary        = TextOnAccent,
    primaryContainer = SlateGlass,
    background       = SlateDeep,
    surface          = SlatePrimary,
    surfaceVariant   = SlateCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline          = BorderSubtle,
    error            = AccentRed,
    onError          = TextOnAccent
)

// ─── Theme Composable ─────────────────────────────────────────────────────────

// The smallest layout space (dp) the screens are built for. 1080p TVs have more, so they never scale.
private const val MIN_LAYOUT_WIDTH = 1000f
private const val MIN_LAYOUT_HEIGHT = 440f

/** Lowers [density] on small or narrow screens, such as phones, until the layout fits. */
internal fun fittedDensity(density: Float, widthPx: Float, heightPx: Float): Float =
    minOf(density, widthPx / MIN_LAYOUT_WIDTH, heightPx / MIN_LAYOUT_HEIGHT)

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
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val scaledDensity = remember(currentDensity, configuration.screenWidthDp, configuration.screenHeightDp) {
        androidx.compose.ui.unit.Density(
            density = fittedDensity(
                density = currentDensity.density * 0.85f,
                widthPx = configuration.screenWidthDp * currentDensity.density,
                heightPx = configuration.screenHeightDp * currentDensity.density
            ),
            fontScale = currentDensity.fontScale * 0.85f
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = remember(context) { com.ipxtream.tv.data.local.AppSettingsStore.getInstance(context) }
    val perfMode by androidx.compose.runtime.produceState(settingsStore.settings.value.performanceMode, settingsStore) {
        settingsStore.settings.collect { value = it.performanceMode }
    }
    val reducedEffects = remember(perfMode) { reducedEffectsFor(context, perfMode) }
    val appTheme = settingsStore.settings.collectAsState().value.appTheme
    val colors = if (appTheme == com.ipxtream.tv.data.local.AppTheme.GLASS) AppleColors else EmberColors
    // Written before the content reads it, so the first frame already has the chosen palette.
    if (activeColors !== colors) activeColors = colors
    val design = if (appTheme == com.ipxtream.tv.data.local.AppTheme.GLASS) IpxDesign.APPLE else IpxDesign.EMBER
    if (activeDesign != design) activeDesign = design

    // Rows and grids stay still while focus moves across visible items (see EdgeBringIntoViewSpec).
    val bringIntoView = remember(scaledDensity) { EdgeBringIntoViewSpec(edgePx = with(scaledDensity) { 48.dp.toPx() }) }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides scaledDensity,
        LocalReducedEffects provides reducedEffects,
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides bringIntoView
    ) {
        // Standard M3 theme (for non-TV composables like TextField, SnackBar)
        MaterialTheme(
            colorScheme = remember(colors) { ipxDarkColors() }
        ) {
            // TV M3 theme (for Card, Surface focus states, etc.)
            TvMaterialTheme(
                colorScheme = androidx.tv.material3.darkColorScheme(
                    primary          = Accent,
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
}
