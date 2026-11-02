package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.SlicePhase
import com.ipxtream.tv.ui.dashboard.components.SliceUpgrade
import com.ipxtream.tv.ui.dashboard.components.SlicerGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VoidSlicerFixTest {

    @Test fun aShieldSurvivesALineGrowingOntoABall() {
        val game = (1..60).asSequence().map { clearARound(SlicerGame(Random(it))) }
            .first { it.phase == SlicePhase.CHOOSING }
        game.choose(SliceUpgrade.SHIELD)
        assertTrue(game.shield)
        for (i in 1 until game.ballCount) {
            game.ballX[i] = 30.5f
            game.ballY[i] = 10.5f
        }
        // The line starts in this ball's cell, so reversing can't take it off the line in one move.
        game.ballX[0] = 10.5f
        game.ballY[0] = 0.5f
        val lives = game.lives
        game.swipe(10, 5, horizontal = false)
        repeat(5) { game.step(0.016f) }
        assertFalse(game.shield)
        assertEquals(lives, game.lives)
        assertTrue(game.lineActive)
    }

    private fun clearARound(game: SlicerGame): SlicerGame {
        var steps = 0
        while (game.phase == SlicePhase.PLAYING && steps++ < 100_000) {
            val ball = game.ballX[0].toInt()
            val col = if (ball > SlicerGame.COLS / 2) ball - 3 else ball + 3
            game.swipe(col, SlicerGame.ROWS / 2, horizontal = false)
            game.step(0.05f)
        }
        return game
    }
}
