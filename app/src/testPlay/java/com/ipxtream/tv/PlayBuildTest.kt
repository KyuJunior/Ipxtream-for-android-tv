package com.ipxtream.tv

import com.ipxtream.tv.data.match.Competition
import com.ipxtream.tv.data.match.Match
import com.ipxtream.tv.data.match.MatchState
import com.ipxtream.tv.data.match.MatchTeam
import com.ipxtream.tv.data.match.TeamNames
import com.ipxtream.tv.data.model.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Play build (docs/play-store-plan.md): no self-updater, no broadcaster steering. */
class PlayBuildTest {

    private fun ch(id: Int, name: String) = StreamItem(
        streamId = id, name = name, streamType = "live", streamIcon = null, categoryId = "1", added = null, num = id,
        rating = null, rating5based = null, containerExtension = null, epgChannelId = null, tvArchive = 0,
        tvArchiveDuration = 0, directSource = null, customSid = null
    )

    private fun match(home: String, away: String, homeShort: String = home, awayShort: String = away) = Match(
        id = "1", competition = Competition("ksa.1", "Saudi Pro League", null), kickoffMs = 0L,
        state = MatchState.LIVE, clock = null,
        home = MatchTeam(home, homeShort, null, null), away = MatchTeam(away, awayShort, null, null)
    )

    @Test fun selfUpdateAndAutoPlayAreOff() {
        assertFalse(BuildConfig.SELF_UPDATE)
        assertFalse(BuildConfig.MATCH_AUTO_PLAY)
    }

    @Test fun noInstallPermissionOrFileProvider() {
        for (path in listOf("src/main/AndroidManifest.xml", "src/play/AndroidManifest.xml")) {
            val file = File(path)
            if (!file.exists()) continue
            val text = file.readText()
            assertFalse("$path asks to install packages", text.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
            assertFalse("$path declares a FileProvider", text.contains("androidx.core.content.FileProvider"))
        }
    }

    @Test fun githubOnlyCodeIsAbsent() {
        for (name in listOf("com.ipxtream.tv.data.match.BeinGuide", "com.ipxtream.tv.data.match.ChannelMatcher", "com.ipxtream.tv.data.match.MatchRights")) {
            assertNull("$name is in the Play build", runCatching { Class.forName(name) }.getOrNull())
        }
        assertFalse(File("src/main/assets/match_rights.json").exists())
    }

    @Test fun suggestsChannelsNamingBothTeams() {
        val names = TeamNames.index(listOf(
            ch(1, "ⒶⓂ AR: beIN SPORTS 1 FHD"),
            ch(2, "EVENT 3: Al Hilal vs Al Nassr 20:00"),
            ch(3, "Al Hilal TV"),
            ch(4, "SA| AL HILAL - AL NASSR (FHD)")
        ))
        val found = TeamNames.channelsNaming(match("Al Hilal", "Al Nassr"), names).map { it.streamId }
        assertEquals(listOf(4, 2), found)
    }

    @Test fun ignoresClubWordsAndShortForms() {
        val names = TeamNames.index(listOf(ch(1, "FC Barcelona x Real Madrid CF"), ch(2, "ES | LaLiga 1")))
        assertEquals(listOf(1), TeamNames.channelsNaming(match("FC Barcelona", "Real Madrid", "Barça", "R. Madrid"), names).map { it.streamId })
        assertTrue(TeamNames.forms(MatchTeam("FC", "FC", null, null)).isEmpty())
    }

    @Test fun demoServerPlaysOpenStreams() {
        val demo = com.ipxtream.tv.data.model.AuthCredentials(com.ipxtream.tv.data.api.DemoCatalog.SERVER, "demo", "demo")
        val url = com.ipxtream.tv.ui.player.StreamUrlBuilder.buildForStream(demo, ch(101, "Big Buck Bunny (2008)").copy(streamType = "movie"))
        assertTrue(url.startsWith("https://") && !url.contains("demo.invalid"))
        val other = com.ipxtream.tv.data.model.AuthCredentials("http://p.tv:8080/", "u", "p")
        assertEquals("http://p.tv:8080/u/p/101.m3u8", com.ipxtream.tv.ui.player.StreamUrlBuilder.buildForStream(other, ch(101, "x")))
    }

    @Test fun demoCatalogParses() {
        val chain = { action: String? ->
            val url = "http://demo.invalid/player_api.php?username=demo&password=demo" + (action?.let { "&action=$it" } ?: "")
            val request = okhttp3.Request.Builder().url(url).build()
            val response = com.ipxtream.tv.data.api.DemoCatalog.interceptor!!.intercept(object : okhttp3.Interceptor.Chain {
                override fun request() = request
                override fun proceed(request: okhttp3.Request) = error("demo must not reach the network")
                override fun connection() = null
                override fun call() = error("unused")
                override fun connectTimeoutMillis() = 0
                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun readTimeoutMillis() = 0
                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun writeTimeoutMillis() = 0
                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            })
            response.body!!.string()
        }
        val gson = com.google.gson.Gson()
        val auth = gson.fromJson(chain(null), com.ipxtream.tv.data.model.XtreamAuthResponse::class.java)
        assertEquals(1, auth.userInfo.auth)
        val live = gson.fromJson(chain("get_live_streams"), Array<StreamItem>::class.java)
        val movies = gson.fromJson(chain("get_vod_streams"), Array<StreamItem>::class.java)
        assertEquals(2, live.size)
        assertEquals(listOf(101, 102), movies.map { it.streamId })
        val info = gson.fromJson(chain("get_vod_info"), com.ipxtream.tv.data.model.VodInfoResponse::class.java)
        assertNull(info.info)
    }
}
