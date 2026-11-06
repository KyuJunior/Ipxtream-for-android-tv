package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.GamePlay
import com.ipxtream.tv.ui.dashboard.components.GameRecords
import org.junit.Assert.assertEquals
import org.junit.Test

class GameRecordsTest {

    @Test fun roundTripsAndSkipsBrokenEntries() {
        val plays = listOf(GamePlay(12, 1000L), GamePlay(0, 2000L))
        assertEquals(plays, GameRecords.parse(GameRecords.format(plays)))
        assertEquals(listOf(GamePlay(5, 9L)), GameRecords.parse("5:9;x:1;7;;3:4:5"))
        assertEquals(emptyList<GamePlay>(), GameRecords.parse(null))
    }

    @Test fun historyKeepsTheNewestFirstAndCapped() {
        var history = emptyList<GamePlay>()
        for (i in 1..15) history = GameRecords.addToHistory(history, GamePlay(i, i.toLong()))
        assertEquals(GameRecords.HISTORY, history.size)
        assertEquals(15, history.first().score)
    }

    @Test fun topKeepsTheTenBestAndTheEarlierOfEqualScores() {
        var top = emptyList<GamePlay>()
        for (i in 1..14) top = GameRecords.addToTop(top, GamePlay(i % 7, i.toLong()))
        assertEquals(GameRecords.TOP, top.size)
        assertEquals(GamePlay(6, 6L), top[0])
        assertEquals(GamePlay(6, 13L), top[1])
        top = GameRecords.addToTop(top, GamePlay(0, 99L))
        assertEquals("a low score doesn't push out a better one", 2, top.last().score)
    }
}
