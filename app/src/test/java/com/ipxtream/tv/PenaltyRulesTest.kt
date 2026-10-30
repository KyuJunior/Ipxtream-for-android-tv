package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.PenaltyRules
import com.ipxtream.tv.ui.dashboard.components.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenaltyRulesTest {

    @Test fun cornersBeatAKeeperWhoGuessedTheOtherSide() {
        assertEquals(ShotResult.GOAL, PenaltyRules.resultOf(aim = 0.1f, keeper = 0.8f))
    }

    @Test fun keeperSavesWithinReach() {
        assertEquals(ShotResult.SAVED, PenaltyRules.resultOf(aim = 0.5f, keeper = 0.4f))
    }

    @Test fun outsideThePostsIsWideEvenIfTheKeeperIsFarAway() {
        assertEquals(ShotResult.WIDE, PenaltyRules.resultOf(aim = 0.02f, keeper = 0.9f))
        assertEquals(ShotResult.WIDE, PenaltyRules.resultOf(aim = 0.98f, keeper = 0.1f))
    }

    @Test fun aimSweepsAcrossAndBackAndStaysInRange() {
        assertEquals(0f, PenaltyRules.aimAt(0, 0), 0.001f)
        assertEquals(1f, PenaltyRules.aimAt(900, 0), 0.001f)
        for (ms in 0L..5000L step 37) assertTrue(PenaltyRules.aimAt(ms, 4) in 0f..1f)
    }

    @Test fun laterShotsSweepFaster() {
        assertTrue(PenaltyRules.aimAt(300, 4) > PenaltyRules.aimAt(300, 0))
    }
}
