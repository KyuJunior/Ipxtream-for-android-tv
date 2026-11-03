package com.ipxtream.tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ipxtream.tv.ui.theme.AppleColors
import com.ipxtream.tv.ui.theme.EmberColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun bothPalettesKeepTextReadable() {
        for ((name, c) in listOf("ember" to EmberColors, "apple" to AppleColors)) {
            assertTrue("$name body text", contrast(c.textPrimary, c.slateCard) >= 4.5f)
            assertTrue("$name secondary text", contrast(c.textSecondary, c.slateCard) >= 4.5f)
            assertTrue("$name muted text", contrast(c.textMuted, c.slateDeep) >= 4.5f)
            assertTrue("$name focus platter", contrast(c.textOnFocus, c.chipFocused) >= 4.5f)
            // Labels on accent fills are bold/large, so 3:1 applies.
            assertTrue("$name text on accent", contrast(c.textOnAccent, c.accent) >= 3f)
        }
    }
}
