package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.BouncerGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class LogicBouncerFixTest {
    @Test fun theRuleOnlyChangesBetweenCards() {
        val game = BouncerGame(Random(1))
        val first = game.rule
        repeat(10) { // 1.6 s a card: the rule comes due inside the last one
            repeat(16) {
                game.step(0.1f)
                assertEquals(first, game.rule)
            }
            game.judge(game.rule.accepts(game.card))
        }
        assertNotEquals(first, game.rule)
    }
}
