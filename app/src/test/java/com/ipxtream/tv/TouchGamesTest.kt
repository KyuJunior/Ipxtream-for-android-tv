package com.ipxtream.tv

import androidx.compose.ui.geometry.Offset
import com.ipxtream.tv.ui.dashboard.components.BouncerCard
import com.ipxtream.tv.ui.dashboard.components.BouncerColor
import com.ipxtream.tv.ui.dashboard.components.BouncerGame
import com.ipxtream.tv.ui.dashboard.components.BouncerRule
import com.ipxtream.tv.ui.dashboard.components.BouncerShape
import com.ipxtream.tv.ui.dashboard.components.DriftWorld
import com.ipxtream.tv.ui.dashboard.components.GameId
import com.ipxtream.tv.ui.dashboard.components.NovaKind
import com.ipxtream.tv.ui.dashboard.components.NovaWorld
import com.ipxtream.tv.ui.dashboard.components.SlicePhase
import com.ipxtream.tv.ui.dashboard.components.SliceUpgrade
import com.ipxtream.tv.ui.dashboard.components.SlicerGame
import com.ipxtream.tv.ui.dashboard.components.SlingPhase
import com.ipxtream.tv.ui.dashboard.components.SlingWorld
import com.ipxtream.tv.ui.dashboard.components.SynergyCell
import com.ipxtream.tv.ui.dashboard.components.SynergyGame
import com.ipxtream.tv.ui.dashboard.components.SynergyRules
import com.ipxtream.tv.ui.dashboard.components.SynergySymbol
import com.ipxtream.tv.ui.dashboard.components.VoidArena
import com.ipxtream.tv.ui.dashboard.components.WreckWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TouchGamesTest {

    @Test fun onlyTheOneButtonGamesReachTheRemote() {
        assertEquals(listOf(GameId.STACK, GameId.PENALTY, GameId.DRIFT), GameId.entries.filter { !it.touchOnly })
    }

    // Comet Sling

    private fun SlingWorld.settle() {
        var limit = 0
        while (phase == SlingPhase.FLYING && limit++ < 2000) step(0.016f)
    }

    private fun aimed(degrees: Double) = Offset(
        (-SlingWorld.MAX_PULL * cos(Math.toRadians(degrees))).toFloat(),
        (SlingWorld.MAX_PULL * sin(Math.toRadians(degrees))).toFloat()
    )

    @Test fun towersStandStillUntilSomethingHitsThem() {
        for (level in 1..12) {
            val world = SlingWorld(level)
            val before = world.bodies.map { it.x to it.y }
            repeat(1000) { world.step(0.016f) }
            assertEquals(before.size, world.bodies.size)
            world.bodies.zip(before).forEach { (body, start) ->
                assertEquals(start.first, body.x, 1.5f)
                assertEquals(start.second, body.y, 1.5f)
            }
        }
    }

    @Test fun aTinyPullFiresNothing() {
        val world = SlingWorld(1)
        val shots = world.shotsLeft
        assertFalse(world.launch(Offset(-3f, 2f)))
        assertEquals(shots, world.shotsLeft)
        assertEquals(SlingPhase.READY, world.phase)
    }

    @Test fun thePullIsClampedSoADragCantFireHarder() {
        assertEquals(SlingWorld.MAX_PULL, SlingWorld.clampPull(Offset(-500f, 0f)).getDistance(), 0.01f)
    }

    @Test fun everyLevelCanBeHit() {
        for (level in 1..12) {
            val hit = (5..60 step 5).any { degrees ->
                val world = SlingWorld(level)
                val glooms = world.glooms
                assertTrue(world.launch(aimed(degrees.toDouble())))
                world.settle()
                world.glooms < glooms
            }
            assertTrue("level $level", hit)
        }
    }

    @Test fun theFirstLevelCanBeWonAndRunningOutOfShotsLoses() {
        val won = (5..60 step 5).any { degrees ->
            val world = SlingWorld(1)
            while (world.phase == SlingPhase.READY) {
                world.launch(aimed(degrees.toDouble()))
                world.settle()
            }
            world.phase == SlingPhase.WON
        }
        assertTrue(won)

        val world = SlingWorld(1)
        while (world.phase == SlingPhase.READY) {
            world.launch(Offset(SlingWorld.MAX_PULL, 0f))  // pulled forward, so it flies away from the towers
            world.settle()
        }
        assertEquals(SlingPhase.LOST, world.phase)
        assertEquals(0, world.shotsLeft)
    }

    // Jelly Drift

    private fun DriftWorld.autopilot(gates: Int) {
        var limit = 0
        while (!over && score < gates && limit++ < 200_000) {
            val next = (0 until DriftWorld.GATES)
                .filter { gateX[it] + DriftWorld.GATE_WIDTH / 2f + DriftWorld.HIT_RADIUS >= DriftWorld.JELLY_X }  // until the jelly is fully out
                .minByOrNull { gateX[it] } ?: continue
            if (y > gateCenter[next] + 18f && speedY > 0f) pulse()
            step(DriftWorld.STEP)
        }
    }

    @Test fun everyDriftRunCanBeSwum() {
        for (seed in 0 until 30) {
            val world = DriftWorld(900f, Random(seed))
            world.autopilot(60)
            assertFalse("seed $seed stopped at ${world.score}", world.over)
            assertEquals(60, world.score)
        }
    }

    @Test fun doingNothingSinksTheJelly() {
        val world = DriftWorld(900f, Random(1))
        repeat(200) { world.step(0.016f) }
        assertTrue(world.over)
        assertEquals(0, world.score)
    }

    @Test fun theCeilingStopsTheJellyInsteadOfEndingTheRun() {
        val world = DriftWorld(900f, Random(1))
        repeat(40) {
            world.pulse()
            world.step(0.016f)
        }
        assertFalse(world.over)
        assertEquals(DriftWorld.RADIUS, world.y, 0.5f)
    }

    @Test fun aSlowFrameMovesTheJellyLikeManyFastOnes() {
        val slow = DriftWorld(900f, Random(2))
        val fast = DriftWorld(900f, Random(2))
        slow.step(0.05f)
        repeat(6) { fast.step(DriftWorld.STEP) }
        assertEquals(fast.y, slow.y, 0.01f)
    }

    @Test fun gatesStayInsideTheScreenAndNarrowDownToTheLimit() {
        val world = DriftWorld(900f, Random(3))
        world.autopilot(60)
        for (gate in 0 until DriftWorld.GATES) {
            assertTrue(world.gateGap[gate] >= DriftWorld.MIN_GAP)
            assertTrue(world.gateCenter[gate] - world.gateGap[gate] / 2f >= 0f)
            assertTrue(world.gateCenter[gate] + world.gateGap[gate] / 2f <= DriftWorld.HEIGHT)
        }
    }

    // Void Slicer

    @Test fun aCutRemovesOnlyTheSideWithoutBalls() {
        val arena = VoidArena(10, 4)
        val line = arena.span(4, 1, horizontal = false)
        assertEquals(4, line.size)
        val ballOnTheRight = 1 * 10 + 8
        val gone = arena.seal(line, listOf(ballOnTheRight))
        assertEquals(4 + 16, gone)  // the line and the 4 columns left of it
        assertFalse(arena.isOpen(0, 0))
        assertTrue(arena.isOpen(8, 3))
    }

    @Test fun aCutBetweenBallsKeepsBothSides() {
        val arena = VoidArena(10, 4)
        assertEquals(4, arena.seal(arena.span(4, 1, horizontal = false), listOf(11, 18)))
    }

    @Test fun spanStopsAtClosedCells() {
        val arena = VoidArena(10, 4)
        arena.seal(arena.span(4, 0, horizontal = false), listOf(0, 9))
        assertEquals(4, arena.span(1, 2, horizontal = true).size)
    }

    @Test fun ballsStayInsideTheArena() {
        val game = SlicerGame(Random(5))
        repeat(2000) { game.step(0.05f) }
        for (i in 0 until game.ballCount) {
            assertTrue(game.arena.isOpen(game.ballX[i].toInt(), game.ballY[i].toInt()))
        }
    }

    @Test fun aRunEndsWhenLivesRunOut() {
        val game = SlicerGame(Random(1))
        var steps = 0
        while (game.phase == SlicePhase.PLAYING && steps++ < 200_000) {
            // Cutting straight through a ball's row never finishes in time for long.
            game.swipe(game.ballX[0].toInt(), game.ballY[0].toInt(), horizontal = true)
            game.step(0.05f)
        }
        assertTrue(game.phase != SlicePhase.PLAYING)
    }

    @Test fun upgradesAreOfferedOnceClearedAndExtraLifeCounts() {
        val cleared = (1..60).asSequence().map { playARound(SlicerGame(Random(it))) }.first { it.phase == SlicePhase.CHOOSING }
        assertEquals(3, cleared.offers.size)
        val lives = cleared.lives
        cleared.choose(SliceUpgrade.EXTRA_LIFE)
        assertEquals(lives + 1, cleared.lives)
        assertEquals(2, cleared.round)
        assertEquals(SlicePhase.PLAYING, cleared.phase)
        assertEquals(0, cleared.arena.removed)
    }

    @Test fun aFinishingLineNeverWallsABallIn() {
        for (seed in 1..300) {
            val game = SlicerGame(Random(seed))
            var steps = 0
            while (game.phase == SlicePhase.PLAYING && steps++ < 400) {
                game.swipe(game.ballX[0].toInt(), SlicerGame.ROWS / 2, horizontal = false)
                game.step(0.5f)  // a big step lets a line finish over a ball it never touched before
                for (i in 0 until game.ballCount) {
                    assertTrue("seed $seed", game.arena.isOpen(game.ballX[i].toInt(), game.ballY[i].toInt()))
                }
            }
        }
    }

    /** Keeps cutting between the wall and the first ball until the round ends one way or the other. */
    private fun playARound(game: SlicerGame): SlicerGame {
        var steps = 0
        while (game.phase == SlicePhase.PLAYING && steps++ < 100_000) {
            val ball = game.ballX[0].toInt()
            val col = if (ball > SlicerGame.COLS / 2) ball - 3 else ball + 3
            game.swipe(col, SlicerGame.ROWS / 2, horizontal = false)
            game.step(0.05f)
        }
        return game
    }

    // Synergy Grid

    private fun grid(vararg cells: Pair<Int, SynergySymbol>): List<SynergyCell?> {
        val list = MutableList<SynergyCell?>(16) { null }
        for ((index, symbol) in cells) list[index] = SynergyCell(symbol)
        return list
    }

    @Test fun furnaceBurnsAdjacentMetal() {
        val (points, after) = SynergyRules.run(grid(0 to SynergySymbol.FURNACE, 1 to SynergySymbol.METAL, 3 to SynergySymbol.METAL))
        assertEquals(11L, points)
        assertNull(after[1])
        assertEquals(SynergySymbol.METAL, after[3]?.symbol)
    }

    @Test fun magnetsDoubleNeighboursAndStack() {
        val (points, _) = SynergyRules.run(
            grid(5 to SynergySymbol.CRYSTAL, 4 to SynergySymbol.MAGNET, 6 to SynergySymbol.MAGNET, 7 to SynergySymbol.METAL)
        )
        assertEquals(6L * 4 + 2, points)
    }

    @Test fun magnetsDoNotDoubleTheFurnaceBonus() {
        val (points, _) = SynergyRules.run(
            grid(0 to SynergySymbol.MAGNET, 1 to SynergySymbol.FURNACE, 2 to SynergySymbol.METAL)
        )
        assertEquals(10L, points)
    }

    @Test fun sparksFeedEachOtherAndSeedsGrow() {
        val (points, after) = SynergyRules.run(
            grid(0 to SynergySymbol.SPARK, 2 to SynergySymbol.SPARK, 8 to SynergySymbol.SEED)
        )
        assertEquals(4L + 4L + 1L, points)
        assertEquals(1, after[8]?.growth)
    }

    @Test fun aFullGridOfMagnetsAndCrystalsStaysCapped() {
        val cells = List<SynergyCell?>(16) { SynergyCell(if (it % 2 == 0) SynergySymbol.MAGNET else SynergySymbol.CRYSTAL) }
        assertTrue(SynergyRules.run(cells).first in 1..SynergyRules.SCORE_CAP)
        assertTrue(SynergyRules.SCORE_CAP <= Int.MAX_VALUE)
    }

    @Test fun everyDealIsAFullHand() {
        repeat(50_000) { assertEquals(3, SynergyGame(Random(it)).hand.size) }
    }

    @Test fun missingTheQuotaEndsTheGame() {
        val game = SynergyGame(Random(2))
        var turns = 0
        while (!game.over && turns++ < 20) {
            while (game.hand.isNotEmpty()) game.place(0)  // everything on one square scores almost nothing
            game.run()
        }
        assertTrue(game.over)
    }

    // Tethered Wreck

    @Test fun theSpringStaysStableOnASlowDevice() {
        val world = WreckWorld(800f, 400f, Random(1))
        repeat(600) { i ->
            world.targetX = if (i % 40 < 20) 20f else 780f
            world.step(0.05f)
            assertTrue(world.ballX[0].isFinite() && world.ballX[0] in 0f..800f)
        }
    }

    @Test fun theDotStaysOnScreenWhenTheFingerLeavesIt() {
        val world = WreckWorld(800f, 400f, Random(1))
        world.targetX = -500f
        world.targetY = 900f
        repeat(100) { world.step(0.05f) }
        assertEquals(0f, world.dotX, 0.01f)
        assertEquals(400f, world.dotY, 0.01f)
    }

    @Test fun aResizeBringsTheWreckWorldInside() {
        val world = WreckWorld(800f, 400f, Random(1))
        world.targetX = 790f
        world.targetY = 390f
        repeat(20) { world.step(0.05f) }
        world.resize(500f, 300f)
        assertEquals(500f, world.width)
        assertTrue(world.dotX <= 500f && world.dotY <= 300f)
        assertTrue(world.ballX[0] <= 500f && world.ballY[0] <= 300f)
    }

    @Test fun enemiesEventuallyCatchAStillDot() {
        val world = WreckWorld(800f, 400f, Random(1))
        var steps = 0
        while (!world.over && steps++ < 4000) world.step(0.05f)
        assertTrue(world.over)
        assertTrue(world.enemies <= WreckWorld.MAX_ENEMIES)
    }

    @Test fun swingingCrushesEnemies() {
        val world = WreckWorld(800f, 400f, Random(4))
        var steps = 0
        while (!world.over && steps++ < 3000) {
            val t = steps * 0.05f
            world.targetX = 400f + 160f * kotlin.math.cos(t * 5f)
            world.targetY = 200f + 120f * kotlin.math.sin(t * 5f)
            world.step(0.016f)
        }
        assertTrue(world.kills > 0)
    }

    // Logic Bouncer

    private fun card(shape: BouncerShape, color: BouncerColor, number: Int) = BouncerCard(shape, color, number, false, null)

    @Test fun compoundRulesReadAsWritten() {
        val rule = BouncerRule.EVEN_NOT_BLUE_TRIANGLES.accepts
        assertTrue(rule(card(BouncerShape.TRIANGLE, BouncerColor.RED, 4)))
        assertFalse(rule(card(BouncerShape.TRIANGLE, BouncerColor.BLUE, 4)))
        assertFalse(rule(card(BouncerShape.CIRCLE, BouncerColor.RED, 3)))
    }

    @Test fun theSameNumberOfRulesExistInBothTiers() {
        assertEquals(6, BouncerRule.entries.count { it.hard })
        assertEquals(6, BouncerRule.entries.count { !it.hard })
    }

    @Test fun aWrongSwipeCostsALifeAndARightOneScores() {
        val game = BouncerGame(Random(1))
        val accept = game.rule.accepts(game.card)
        assertTrue(game.judge(accept))
        assertEquals(1, game.score)
        val wrong = !game.rule.accepts(game.card)
        assertFalse(game.judge(wrong))
        assertEquals(BouncerGame.LIVES - 1, game.lives)
    }

    @Test fun lettingCardsReachTheGateEndsTheRun() {
        val game = BouncerGame(Random(1))
        repeat(1000) { game.step(0.05f) }
        assertTrue(game.over)
    }

    // One-Tap Nova

    @Test fun aBlastChainsThroughTouchingParticlesAndEnds() {
        val world = NovaWorld(1, 800f, 400f, Random(1))
        world.tap(world.x[0], world.y[0])
        repeat(400) { world.step(0.016f) }
        assertTrue(world.poppedCount >= 1)
        assertTrue(world.finished)
    }

    @Test fun aSecondTapIsIgnored() {
        val world = NovaWorld(1, 800f, 400f, Random(1))
        world.tap(10f, 10f)
        world.tap(500f, 300f)
        assertEquals(1, world.blasts)
    }

    @Test fun holesSwallowABlastAndSurvive() {
        val world = NovaWorld(5, 800f, 400f, Random(1))
        val hole = world.kind.indexOf(NovaKind.HOLE)
        world.tap(world.x[hole], world.y[hole])
        repeat(100) { world.step(0.016f) }
        assertFalse(world.popped[hole])
    }

    @Test fun aResizeBringsNovaParticlesInside() {
        val world = NovaWorld(1, 800f, 400f, Random(1))
        world.resize(300f, 200f)
        world.step(0.016f)
        assertTrue(world.x.all { it <= 300f } && world.y.all { it <= 200f })
    }

    @Test fun theBiggestLevelStaysWithinItsLimits() {
        val world = NovaWorld(200, 800f, 400f, Random(1))
        assertEquals(NovaWorld.MAX_PARTICLES, world.count)
        world.tap(400f, 200f)
        repeat(800) { world.step(0.05f) }
        assertTrue(world.blasts <= world.count + 1)
    }
}
