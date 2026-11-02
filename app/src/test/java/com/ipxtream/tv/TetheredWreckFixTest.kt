package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.WreckWorld
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TetheredWreckFixTest {
    @Test fun gameTimeMatchesRealTimeAtSixtyFrames() {
        val world = WreckWorld(800f, 400f, Random(1))
        // Frame times arrive as whole milliseconds; rounding each frame up ran the game a third too fast.
        repeat(180) { i -> world.step(if (i % 3 == 0) 0.016f else 0.017f) }
        assertEquals(3f, world.time, 0.02f)
    }

    @Test fun enemiesDontSpawnBesideADotInACorner() {
        for (seed in 0 until 40) {
            val world = WreckWorld(800f, 400f, Random(seed))
            world.targetX = 0f
            world.targetY = 0f
            while (world.enemies == 0) world.step(0.02f)
            val dx = world.enemyX[0] - world.dotX
            val dy = world.enemyY[0] - world.dotY
            assertTrue(sqrt(dx * dx + dy * dy) > WreckWorld.SPAWN_CLEARANCE - 5f)
        }
    }
}
