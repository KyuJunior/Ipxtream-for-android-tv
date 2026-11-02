package com.ipxtream.tv

import androidx.compose.ui.geometry.Offset
import com.ipxtream.tv.ui.dashboard.components.SlingBody
import com.ipxtream.tv.ui.dashboard.components.SlingMaterial
import com.ipxtream.tv.ui.dashboard.components.SlingPhase
import com.ipxtream.tv.ui.dashboard.components.SlingShot
import com.ipxtream.tv.ui.dashboard.components.SlingWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SlingPhysicsTest {

    private val ground = SlingWorld.GROUND

    /** A Gloomlet far off to the side keeps the level from counting as won. */
    private fun world(vararg bodies: SlingBody, queue: List<SlingShot> = listOf(SlingShot.EMBER)) =
        SlingWorld(1, listOf(*bodies, SlingBody(SlingMaterial.GLOOM, 760f, ground - 11f, 22f, 22f)), queue)

    private fun pillar(material: SlingMaterial) = SlingBody(material, 200f, ground - 60f, 10f, 120f)

    @Test fun aPillarHitHighFallsOver() {
        val post = SlingBody(SlingMaterial.STONE, 250f, ground - 50f, 10f, 100f)
        val world = world(post)
        assertEquals(0f, post.angle, 0.01f)
        world.launch(Offset(-SlingWorld.MAX_PULL, 0f))
        repeat(300) { world.step(0.01f) }
        assertTrue("stone survives an Ember", post.alive)
        assertTrue("angle ${post.angle}", abs(post.angle) > 1.2f)
        assertTrue("lying on the ground, not sunk into it: ${post.y}", post.y in ground - 8f..ground - 3f)
    }

    @Test fun stoneTakesHitsThatBreakWood() {
        fun hitHealth(material: SlingMaterial, shot: SlingShot): Float {
            val target = pillar(material)
            val world = world(target, queue = listOf(shot))
            world.launch(Offset(-SlingWorld.MAX_PULL, 0f))
            repeat(100) { world.step(0.01f) }
            return if (target.alive) target.health else 0f
        }
        val wood = hitHealth(SlingMaterial.WOOD, SlingShot.EMBER)
        val stone = hitHealth(SlingMaterial.STONE, SlingShot.EMBER)
        val stoneByMeteor = hitHealth(SlingMaterial.STONE, SlingShot.METEOR)
        assertTrue("wood $wood stone $stone", wood < stone)
        assertTrue("stone survives an Ember: $stone", stone > 0f)
        assertTrue("a Meteor cracks stone harder: $stoneByMeteor vs $stone", stoneByMeteor < stone)
    }

    @Test fun eachThrowableHasItsTrick() {
        val trio = world(queue = listOf(SlingShot.TRIO))
        trio.launch(Offset(-SlingWorld.MAX_PULL, 20f))
        trio.step(0.05f)
        assertTrue(trio.activate())
        assertEquals(3, trio.shots.size)
        assertFalse("only once", trio.activate())

        val streak = world(queue = listOf(SlingShot.STREAK))
        streak.launch(Offset(-SlingWorld.MAX_PULL, 20f))
        streak.step(0.05f)
        val before = hypot(streak.shots[0].vx, streak.shots[0].vy)
        assertTrue(streak.activate())
        assertTrue(hypot(streak.shots[0].vx, streak.shots[0].vy) > before * 1.5f)

        val ember = world(queue = listOf(SlingShot.EMBER))
        ember.launch(Offset(-SlingWorld.MAX_PULL, 20f))
        assertFalse(ember.activate())
    }

    @Test fun aPulsarBlastShattersCrystalNearby() {
        val block = SlingBody(SlingMaterial.CRYSTAL, 300f, ground - 11f, 22f, 22f)
        val world = world(block, queue = listOf(SlingShot.PULSAR))
        world.launch(Offset(-30f, 0f))
        while (world.shots.firstOrNull()?.let { it.x < 270f } == true) world.step(0.01f)
        assertTrue(world.activate())
        world.step(0.05f)
        assertFalse(block.alive)
    }

    @Test fun aBrokenCellBlowsUpWhatIsNextToIt() {
        val cell = SlingBody(SlingMaterial.CELL, 400f, ground - 8f, 16f, 16f)
        val victim = SlingBody(SlingMaterial.GLOOM, 430f, ground - 11f, 22f, 22f)
        val world = world(cell, victim)
        cell.health = 0f
        repeat(10) { world.step(0.01f) }
        assertFalse(victim.alive)
        assertEquals(SlingPhase.READY, world.phase)
    }

    @Test fun towersDontRotateWhileTheyWait() {
        for (level in 1..12) {
            val world = SlingWorld(level)
            repeat(600) { world.step(0.016f) }
            for (body in world.bodies) if (!body.round) assertEquals("level $level", 0f, body.angle, 0.05f)
        }
    }
}
