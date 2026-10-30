package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.StackBlock
import com.ipxtream.tv.ui.dashboard.components.StackRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackRulesTest {

    private val top = StackBlock(0.25f, 0.5f)

    @Test fun onlyTheOverlapStays() {
        val drop = StackRules.drop(StackBlock(0.35f, 0.5f), top, combo = 2)!!
        assertEquals(0.35f, drop.block.left, 0.001f)
        assertEquals(0.4f, drop.block.width, 0.001f)
        assertEquals(0, drop.combo)
    }

    @Test fun overlapOnTheOtherSideKeepsTheTopsLeftEdge() {
        val drop = StackRules.drop(StackBlock(0.15f, 0.5f), top, combo = 0)!!
        assertEquals(0.25f, drop.block.left, 0.001f)
        assertEquals(0.4f, drop.block.width, 0.001f)
    }

    @Test fun aCompleteMissEndsTheRun() {
        assertNull(StackRules.drop(StackBlock(0.8f, 0.2f), StackBlock(0.1f, 0.2f), combo = 0))
    }

    @Test fun perfectDropsKeepTheWidthAndCountCombo() {
        val drop = StackRules.drop(StackBlock(0.26f, 0.5f), top, combo = 0)!!
        assertEquals(top, drop.block)
        assertEquals(1, drop.combo)
    }

    @Test fun aComboWinsWidthBackUpToTheStartingWidth() {
        val narrow = StackBlock(0.4f, 0.2f)
        val grown = StackRules.drop(narrow, narrow, combo = 2)!!.block
        assertEquals(0.24f, grown.width, 0.001f)
        assertEquals(0.38f, grown.left, 0.001f)
        assertEquals(StackRules.START_WIDTH, StackRules.drop(top, top, combo = 5)!!.block.width, 0.001f)
    }

    @Test fun grownBlockStaysInsideTheTower() {
        val atEdge = StackBlock(0f, 0.2f)
        assertEquals(0f, StackRules.drop(atEdge, atEdge, combo = 2)!!.block.left, 0.001f)
    }

    @Test fun slideStaysInRangeAndSpeedsUpToACap() {
        for (ms in 0L..9000L step 41) assertTrue(StackRules.slideLeft(ms, 7, 0.3f) in 0f..0.7f)
        assertTrue(StackRules.speed(10) > StackRules.speed(0))
        assertEquals(1.1f, StackRules.speed(500), 0.001f)
    }

    @Test fun everySecondBlockStartsFromTheOtherSide() {
        assertEquals(0f, StackRules.slideLeft(0, 0, 0.5f), 0.001f)
        assertEquals(0.5f, StackRules.slideLeft(0, 1, 0.5f), 0.01f)
    }
}
