package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.GameNameFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as server/leaderboard/test/rules.test.js, so the app and the server agree. */
class GameNameFilterTest {

    @Test fun offensiveNamesAreBlocked() {
        for (bad in listOf("fuck you", "F.U.C.K", "sh1t", "big dick", "زب", "يا شرموط", "قحبة")) {
            assertTrue(bad, GameNameFilter.isBlocked(bad))
        }
    }

    @Test fun ordinaryNamesPass() {
        for (ok in listOf("Grape", "Dickens", "زبيدة", "اكسترا", "Mighty Rocket 96", "الصقر السريع 42", "Yaser")) {
            assertFalse(ok, GameNameFilter.isBlocked(ok))
        }
    }

    @Test fun randomNameWordsArePassable() {
        // Every generated name must get through, or a player who skips naming could never post.
        assertFalse(GameNameFilter.isBlocked("Sneaky Dragon 77"))
        assertFalse(GameNameFilter.isBlocked("التنين الخفي 13"))
    }
}
