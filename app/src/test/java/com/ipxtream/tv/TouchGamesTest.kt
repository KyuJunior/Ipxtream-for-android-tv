package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.BouncerCard
import com.ipxtream.tv.ui.dashboard.components.BouncerColor
import com.ipxtream.tv.ui.dashboard.components.BouncerGame
import com.ipxtream.tv.ui.dashboard.components.BouncerRule
import com.ipxtream.tv.ui.dashboard.components.BouncerShape
import com.ipxtream.tv.ui.dashboard.components.GameId
import com.ipxtream.tv.ui.dashboard.components.NovaKind
import com.ipxtream.tv.ui.dashboard.components.NovaWorld
import com.ipxtream.tv.ui.dashboard.components.SlicePhase
import com.ipxtream.tv.ui.dashboard.components.SliceUpgrade
import com.ipxtream.tv.ui.dashboard.components.SlicerGame
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
import kotlin.random.Random

class TouchGamesTest {

    @Test fun onlyStackAndPenaltyReachTheRemote() {
        assertEquals(listOf(GameId.STACK, GameId.PENALTY), GameId.entries.filter { !it.touchOnly })
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

    @Test fun theBiggestLevelStaysWithinItsLimits() {
        val world = NovaWorld(200, 800f, 400f, Random(1))
        assertEquals(NovaWorld.MAX_PARTICLES, world.count)
        world.tap(400f, 200f)
        repeat(800) { world.step(0.05f) }
        assertTrue(world.blasts <= world.count + 1)
    }
}
