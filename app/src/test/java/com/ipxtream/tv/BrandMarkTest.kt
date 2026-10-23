package com.ipxtream.tv

import com.ipxtream.tv.ui.intro.BrandMark
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandMarkTest {

    @Test
    fun `arms stay inside the mark box`() {
        for ((x, y) in BrandMark.armDown + BrandMark.armUp) {
            assertTrue(x in -BrandMark.HALF_W..BrandMark.HALF_W)
            assertTrue(y in -BrandMark.HALF_H..BrandMark.HALF_H)
        }
    }

    @Test
    fun `triangle points right at the crossing and sits in the left counter`() {
        val (left1, tip, left2) = BrandMark.triangle
        assertTrue(tip.first > left1.first && tip.second == 0f)
        assertTrue(left1.first == left2.first && left1.second == -left2.second)
        assertTrue(tip.first < 0f)                                  // never reaches the crossing
        assertTrue(left1.first - BrandMark.TRIANGLE_RADIUS >= -BrandMark.HALF_W - 1e-3f)
    }
}
