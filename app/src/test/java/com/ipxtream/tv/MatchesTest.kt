package com.ipxtream.tv

import com.google.gson.Gson
import com.ipxtream.tv.data.match.ChannelMatcher
import com.ipxtream.tv.data.match.Competition
import com.ipxtream.tv.data.match.EspnMapper
import com.ipxtream.tv.data.match.EspnScoreboard
import com.ipxtream.tv.data.match.Match
import com.ipxtream.tv.data.match.MatchDays
import com.ipxtream.tv.data.match.MatchRights
import com.ipxtream.tv.data.match.MatchState
import com.ipxtream.tv.data.match.MatchTeam
import com.ipxtream.tv.data.model.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.TimeZone

class MatchesTest {

    private fun live(id: Int, name: String) = StreamItem(
        streamId = id, name = name, streamType = "live", streamIcon = null, categoryId = "1",
        added = null, num = id, rating = null, rating5based = null, containerExtension = null,
        epgChannelId = null, tvArchive = null, tvArchiveDuration = null, directSource = null, customSid = null
    )

    private fun match(home: String, away: String, slug: String = "eng.1") = Match(
        id = "1", competition = Competition(slug, slug, null), kickoffMs = 0L, state = MatchState.LIVE,
        clock = "10'", home = MatchTeam(home, home, null, "0"), away = MatchTeam(away, away, null, "0")
    )

    private val rights = MatchRights.parse(File("src/main/assets/match_rights.json").readText())

    // ── Normalisation ────────────────────────────────────────────────────────

    @Test fun stripsRegionPrefixAndQuality() {
        val n = ChannelMatcher.normalize("AR| BEIN SPORTS 1 FHD")
        assertEquals("bein sports 1", n.text)
        assertEquals("ar", n.region)
        assertEquals(3, n.quality)
    }

    @Test fun handlesPipesBracketsAndSuperscripts() {
        assertEquals("bein sports 1", ChannelMatcher.normalize("|AR| beIN Sports 1 ᴴᴰ").text)
        assertEquals("ar", ChannelMatcher.normalize("[AR] beIN SPORTS 2").region)
        assertEquals("uk", ChannelMatcher.normalize("UK: Sky Sports Premier League HD").region)
    }

    @Test fun brandBeforeColonIsNotARegion() {
        val n = ChannelMatcher.normalize("DAZN | LaLiga 1")
        assertNull(n.region)
        assertEquals("dazn laliga 1", n.text)
    }

    @Test fun detectsFamilyAndNumber() {
        val e = ChannelMatcher.entry(live(1, "AR| BEIN SPORTS 3 HD"))
        assertEquals("bein", e.family)
        assertEquals(3, e.number)
        assertEquals("canal+", ChannelMatcher.entry(live(2, "FR: CANAL+ SPORT")).family)
        assertEquals("bein", ChannelMatcher.entry(live(3, "بي ان سبورت 1")).family)
    }

    // ── Candidates ───────────────────────────────────────────────────────────

    private val channels = listOf(
        live(10, "AR| BEIN SPORTS 2 HD"),
        live(11, "AR| BEIN SPORTS 1 FHD"),
        live(12, "AR| BEIN SPORTS NEWS"),
        live(13, "UK| Sky Sports Premier League FHD"),
        live(14, "AR| MBC 1"),
        live(15, "|EN| PL: Arsenal vs Chelsea 20:00"),
        live(16, "AR| SSC 1 HD"),
        live(17, "Arsenal TV")
    )
    private val index = ChannelMatcher.ChannelIndex(channels)

    @Test fun eventChannelWithBothTeamsWins() {
        val best = ChannelMatcher.candidates(match("Arsenal", "Chelsea"), index, rights.familiesFor("eng.1"), ChannelMatcher.ARABIC_REGIONS)
        assertEquals(15, best.first().streamId)
    }

    @Test fun arabicViewerGetsBeinOneBeforeOthers() {
        val best = ChannelMatcher.candidates(match("Everton", "Fulham"), index, rights.familiesFor("eng.1"), ChannelMatcher.ARABIC_REGIONS)
        assertEquals(listOf(11, 10), best.take(2).map { it.streamId })
        assertTrue("news channel ranks below the main channels", best.indexOfFirst { it.streamId == 12 } > 1)
        assertTrue("unrelated channels are not suggested", best.none { it.streamId == 14 || it.streamId == 17 })
    }

    @Test fun englishViewerStillGetsRightsFamilies() {
        val best = ChannelMatcher.candidates(match("Everton", "Fulham"), index, rights.familiesFor("eng.1"), ChannelMatcher.ENGLISH_REGIONS)
        assertTrue(best.any { it.streamId == 13 })
        assertTrue("SSC has no Premier League rights", best.none { it.streamId == 16 })
    }

    // Names as the owner's provider writes them (seen on the TV, 2026-10-23).
    private val providerChannels = listOf(
        live(30, "ⒶⓂ AR_beIN_DRAMA_1_FHD"),
        live(31, "ⒶⓂ AR_beIN_MOVIES_1_FHD"),
        live(32, "ⒶⓂ AR_beIN_Series_1_FHD"),
        live(33, "ⒶⓂ TR: BEIN SPORTS 1 4K"),
        live(34, "ⒶⓂ TR ★ BEIN SPORTS 1 4K"),
        live(35, "ⒶⓂ BeIN SPORTS 4K 1 (SS)"),
        live(36, "ⒶⓂ AR_beIN_SPORTS_1_FHD"),
        live(37, "ⒶⓂ AR_beIN_SPORTS_2_FHD")
    )

    @Test fun readsDecoratedAndUnderscoredNames() {
        val n = ChannelMatcher.normalize("ⒶⓂ AR_beIN_SPORTS_1_FHD")
        assertEquals("ar", n.region)
        assertEquals("bein sports 1", n.text)
        assertEquals("tr", ChannelMatcher.normalize("ⒶⓂ TR ★ BEIN SPORTS 1 4K").region)
        assertEquals(1, ChannelMatcher.entry(live(1, "ⒶⓂ BeIN SPORTS 4K 1 (SS)")).number)
        assertNull("beIN DRAMA is not a sports channel", ChannelMatcher.entry(live(2, "ⒶⓂ AR_beIN_DRAMA_1_FHD")).family)
    }

    @Test fun providerNamesSuggestArabicBeinSportsFirst() {
        val best = ChannelMatcher.candidates(
            match("Alavés", "Málaga", slug = "esp.1"), ChannelMatcher.ChannelIndex(providerChannels),
            rights.familiesFor("esp.1"), ChannelMatcher.ARABIC_REGIONS
        )
        assertEquals(listOf(36, 37), best.take(2).map { it.streamId })
        assertTrue("entertainment channels are never suggested", best.none { it.streamId in 30..32 })
    }

    @Test fun arabTimeZonePrefersArabicFeedsWithEnglishApp() {
        val channels = ChannelMatcher.ChannelIndex(listOf(
            live(40, "ⒶⓂ AU: BEIN SPORTS AU 1 FHD"),
            live(41, "ⒶⓂ AR_beIN_SPORTS_1_FHD")
        ))
        val regions = ChannelMatcher.preferredRegions(arabicApp = false, timeZoneId = "Africa/Cairo")
        val best = ChannelMatcher.candidates(match("Everton", "Fulham"), channels, rights.familiesFor("eng.1"), regions)
        assertEquals(41, best.first().streamId)
        assertEquals(ChannelMatcher.ENGLISH_REGIONS, ChannelMatcher.preferredRegions(false, "Europe/London"))
    }

    @Test fun rightsTableCoversDefaultCompetitions() {
        listOf("uefa.champions", "eng.1", "esp.1").forEach { slug ->
            val families = rights.familiesFor(slug)
            assertEquals("bein", families.firstOrNull())
            assertTrue("$slug lists unknown families", families.all { it in ChannelMatcher.FAMILIES })
        }
    }

    // ── ESPN parsing ─────────────────────────────────────────────────────────

    private val espnJson = """
        {"leagues":[{"slug":"uefa.champions","name":"UEFA Champions League","logos":[{"href":"https://x/2.png"}]}],
         "events":[
          {"id":"401","date":"2026-10-21T16:45Z","competitions":[{"status":{"displayClock":"90'+4'","type":{"name":"STATUS_FULL_TIME","state":"post","completed":true}},
            "competitors":[{"homeAway":"home","score":"4","team":{"displayName":"Como","shortDisplayName":"Como","logo":"https://x/c.png"}},
                           {"homeAway":"away","score":"1","team":{"displayName":"Manchester United","shortDisplayName":"Man United"}}]}]},
          {"id":"402","date":"2026-10-21T19:00Z","competitions":[{"status":{"displayClock":"67'","type":{"name":"STATUS_IN_PROGRESS","state":"in"}},
            "competitors":[{"homeAway":"home","score":"1","team":{"displayName":"Lille"}},{"homeAway":"away","score":"0","team":{"displayName":"Galatasaray"}}]}]},
          {"id":"403","date":"2026-10-22T19:00:00Z","competitions":[{"status":{"displayClock":"0'","type":{"name":"STATUS_SCHEDULED","state":"pre"}},
            "competitors":[{"homeAway":"home","score":"0","team":{"displayName":"Real Madrid"}},{"homeAway":"away","score":"0","team":{"displayName":"Barcelona"}}]}]},
          {"id":"404","date":"bad","competitions":[]}
         ]}
    """.trimIndent()

    @Test fun mapsEspnScoreboard() {
        val matches = EspnMapper.toMatches(Gson().fromJson(espnJson, EspnScoreboard::class.java), "uefa.champions")
        assertEquals(3, matches.size)
        val (ft, inPlay, pre) = matches
        assertEquals(MatchState.FINISHED, ft.state)
        assertEquals("4", ft.home.score)
        assertEquals("Man United", ft.away.shortName)
        assertEquals("UEFA Champions League", ft.competition.name)
        assertEquals(MatchState.LIVE, inPlay.state)
        assertEquals("67'", inPlay.clock)
        assertEquals(MatchState.UPCOMING, pre.state)
        assertNull("no score before kick-off", pre.home.score)
        assertEquals(EspnMapper.parseUtc("2026-10-22T19:00Z"), pre.kickoffMs)
    }

    // ── Days and time zones ──────────────────────────────────────────────────

    @Test fun lateKickoffBelongsToTheLocalDay() {
        val riyadh = TimeZone.getTimeZone("Asia/Riyadh")
        // 21:30 UTC on the 23rd is 00:30 on the 24th in Riyadh.
        val kickoff = EspnMapper.parseUtc("2026-10-23T21:30Z")!!
        assertEquals(20261024, MatchDays.dayKey(kickoff, riyadh))
        assertEquals(20261023, MatchDays.dayKey(kickoff, TimeZone.getTimeZone("America/New_York")))
    }

    @Test fun readsTheEspnDaysAroundALocalDay() {
        val tz = TimeZone.getTimeZone("Asia/Riyadh")
        val now = EspnMapper.parseUtc("2026-10-31T22:00Z")!!   // already 1 Nov in Riyadh
        val day = MatchDays.localDay(now, 0, tz)
        assertEquals(listOf("20261031", "20261101", "20261102"), MatchDays.espnDatesAround(day))
    }

    @Test fun cacheLifetimes() {
        val tz = TimeZone.getTimeZone("UTC")
        val now = EspnMapper.parseUtc("2026-10-23T12:00Z")!!
        val today = MatchDays.localDay(now, 0, tz)
        val finished = match("A", "B").copy(state = MatchState.FINISHED)
        val minute = 60_000L
        assertEquals(minute, MatchDays.ttlMs("20261023", today, listOf(match("A", "B")), now))
        assertEquals(10 * minute, MatchDays.ttlMs("20261023", today, listOf(finished), now))
        assertEquals(180 * minute, MatchDays.ttlMs("20261027", today, emptyList(), now))
        assertTrue(MatchDays.ttlMs("20261001", today, listOf(finished), now) > 24 * 60 * minute)
    }
}
