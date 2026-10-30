package com.ipxtream.tv

import com.ipxtream.tv.ui.theme.fittedDensity
import org.junit.Assert.assertEquals
import org.junit.Test

class FittedDensityTest {

    @Test fun tvKeepsItsDensity() {
        assertEquals(1.7f, fittedDensity(1.7f, 1920f, 1080f), 0.001f)
    }

    @Test fun narrowPhoneShrinksToTheMinimumWidth() {
        assertEquals(1.92f, fittedDensity(2.55f, 1920f, 1080f), 0.001f)
    }

    @Test fun shortScreenShrinksToTheMinimumHeight() {
        assertEquals(2f, fittedDensity(3f, 3000f, 880f), 0.001f)
    }
}
