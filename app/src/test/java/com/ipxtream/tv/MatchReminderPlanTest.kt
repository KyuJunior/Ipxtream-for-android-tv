package com.ipxtream.tv

import com.ipxtream.tv.data.match.Competition
import com.ipxtream.tv.data.match.Match
import com.ipxtream.tv.data.match.MatchReminderPlan
import com.ipxtream.tv.data.match.MatchState
import com.ipxtream.tv.data.match.MatchTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchReminderPlanTest {

    private val now = 1_800_000_000_000L
    private val min = 60_000L

    private fun match(id: String, inMinutes: Long, state: MatchState = MatchState.UPCOMING) = Match(
        id = id,
        competition = Competition("eng.1", "Premier League", null),
        kickoffMs = now + inMinutes * min,
        state = state,
        clock = null,
        home = MatchTeam("Home $id", "H$id", null, null),
        away = MatchTeam("Away $id", "A$id", null, null)
    )

    @Test fun groupsMatchesByKickoffAndNotifiesFifteenMinutesBefore() {
        val slots = MatchReminderPlan.slots(listOf(match("1", 60), match("2", 60), match("3", 120)), now, emptySet())
        assertEquals(listOf(now + 60 * min, now + 120 * min), slots.map { it.kickoffMs })
        assertEquals(now + 45 * min, slots[0].notifyAtMs)
    }

    @Test fun kickoffCloserThanTheLeadTimeNotifiesNow() {
        val slots = MatchReminderPlan.slots(listOf(match("1", 5)), now, emptySet())
        assertEquals(now, slots.single().notifyAtMs)
    }

    @Test fun skipsStartedFarAwayAndAlreadyAnnouncedMatches() {
        val matches = listOf(
            match("live", 30, MatchState.LIVE),
            match("past", -10),
            match("far", 4 * 60),
            match("done", 30)
        )
        assertTrue(MatchReminderPlan.slots(matches, now, setOf("done")).isEmpty())
    }

    @Test fun dueNamesOnlyThatKickoffsUnannouncedUpcomingMatches() {
        val matches = listOf(match("1", 15), match("2", 15), match("3", 15, MatchState.POSTPONED), match("4", 30))
        val due = MatchReminderPlan.due(matches, now + 15 * min, now, setOf("2"))
        assertEquals(listOf("1"), due.map { it.id })
        assertTrue(MatchReminderPlan.due(matches, now - min, now, emptySet()).isEmpty())
    }
}
