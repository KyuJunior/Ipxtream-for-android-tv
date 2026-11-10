package com.ipxtream.tv

import com.ipxtream.tv.ui.theme.appleCornerDp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppleCornerTest {
    @Test
    fun hairlineRadiiStayAndTheRestDoubleUpToThirtyTwo() {
        assertEquals(2f, appleCornerDp(2f), 0f)
        assertEquals(4f, appleCornerDp(4f), 0f)
        assertEquals(12f, appleCornerDp(6f), 0f)
        assertEquals(24f, appleCornerDp(12f), 0f)
        assertEquals(32f, appleCornerDp(16f), 0f)
        assertEquals(32f, appleCornerDp(28f), 0f)
    }
}
