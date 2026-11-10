package com.ipxtream.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The design language: shapes, materials, layout and type. Colours live in Color.kt.
// Ember = the brand look. Apple = Settings → General → Theme "Apple": continuous large corners,
// glass only on floating controls (menu capsule, header buttons, sheets), opaque grouped content.
// [activeDesign] is snapshot state like [activeColors]: read it inside composition or draw, never
// in a top-level val or a remember without a key.

enum class IpxDesign { EMBER, APPLE }

/** The design in use. Set by [IpxTvTheme] together with [activeColors]. */
var activeDesign by mutableStateOf(IpxDesign.EMBER)

val isAppleDesign: Boolean get() = activeDesign == IpxDesign.APPLE

/** Apple corner for an Ember corner of [dp]: hairline radii stay, the rest double, up to 32. */
internal fun appleCornerDp(dp: Float): Float = if (dp <= 4f) dp else minOf(dp * 2f, 32f)

/** Corner shape for cards, panels and rows: [radius] in Ember, the larger Apple corner in Apple. */
fun ipxShape(radius: Dp): RoundedCornerShape =
    RoundedCornerShape(if (isAppleDesign) appleCornerDp(radius.value).dp else radius)

// ─── Apple neutrals that have no Ember counterpart ────────────────────────────

/** Separator between rows of a grouped list. */
val AppleSeparator = Color(0xFF38383A)
/** Tertiary fill: secondary buttons, text fields, segmented-control track. */
val AppleFill = Color(0x3D767680)
/** Selected pill of a segmented control. */
val AppleSegmentSelected = Color(0xFF636366)
/** Lens under the selected item of a floating bar. */
val AppleLens = Color(0x24FFFFFF)
/** Floating glass fill (dark). */
val AppleGlassFill = Color(0xC71C1E1D)
/** Dim behind a sheet. */
val AppleSheetDim = Color(0x61000000)

/**
 * Liquid-glass material for floating controls: translucent fill, a specular rim that is brighter at
 * the top-start and bottom-end, and a soft sheen on the upper half. No backdrop blur (Android can't
 * blur what is behind a view cheaply), so the fill is dense enough to keep text readable.
 * With reduced effects it is a plain fill and a hairline.
 */
@Composable
fun Modifier.appleGlass(shape: Shape, fill: Color = AppleGlassFill): Modifier {
    if (LocalReducedEffects.current) {
        return clip(shape).background(fill.copy(alpha = 0.94f)).border(1.dp, Hairline, shape)
    }
    return clip(shape)
        .background(fill)
        .drawWithContent {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.5f to Color.Transparent
                )
            )
            drawContent()
        }
        .border(
            1.5.dp,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.42f),
                0.35f to Color.White.copy(alpha = 0.06f),
                0.65f to Color.White.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = 0.26f),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape
        )
}
