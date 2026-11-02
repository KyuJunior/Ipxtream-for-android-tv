package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.DriftWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JellyDriftFixTest {
    @Test fun distanceMovesWithTheGates() {
        val world = DriftWorld(900f, Random(1))
        val start = world.gateX[0]
        world.step(0.1f)
        assertEquals(start, world.gateX[0] + world.distance, 0.01f)
    }

    @Test fun theJellyClearsTheTopPillsAtPhoneSize() {
        val dpPerUnit = 440f / DriftWorld.HEIGHT  // 1000x440 dp is the smallest game window
        val pillsEnd = 262f  // 12 dp margin + about 250 dp of pills
        assertTrue((DriftWorld.JELLY_X - DriftWorld.RADIUS) * dpPerUnit > pillsEnd)
    }
}
