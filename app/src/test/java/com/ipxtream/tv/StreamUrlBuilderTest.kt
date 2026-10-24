package com.ipxtream.tv

import com.google.gson.Gson
import com.ipxtream.tv.data.api.ApiClient
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.player.StreamUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamUrlBuilderTest {

    private val gson = Gson()
    private val creds = AuthCredentials("http://portal.example:8080/", "user", "secret")

    private fun stream(json: String): StreamItem = gson.fromJson(json, StreamItem::class.java)

    @Test fun liveUsesHls() {
        val url = StreamUrlBuilder.buildForStream(creds, stream("""{"stream_id":7,"name":"News","stream_type":"live"}"""))
        assertEquals("http://portal.example:8080/user/secret/7.m3u8", url)
    }

    @Test fun movieUsesContainerExtension() {
        val url = StreamUrlBuilder.buildForStream(
            creds, stream("""{"stream_id":42,"name":"Film","stream_type":"movie","container_extension":"mkv"}""")
        )
        assertEquals("http://portal.example:8080/movie/user/secret/42.mkv", url)
    }

    @Test fun movieWithBlankExtensionFallsBackToMp4() {
        val url = StreamUrlBuilder.buildForStream(
            creds, stream("""{"stream_id":42,"name":"Film","stream_type":"movie","container_extension":""}""")
        )
        assertEquals("http://portal.example:8080/movie/user/secret/42.mp4", url)
    }

    @Test fun episodeUsesSeriesPath() {
        val episode = gson.fromJson(
            """{"id":"9001","episode_num":3,"title":"Ep","container_extension":"mp4","season":1}""",
            EpisodeItem::class.java
        )
        assertEquals("http://portal.example:8080/series/user/secret/9001.mp4", StreamUrlBuilder.buildForEpisode(creds, episode))
    }

    @Test fun loggedUrlsHideTheLogin() {
        val line = "--> GET http://portal.example/player_api.php?username=user&password=secret&action=get_vod_streams"
        val redacted = ApiClient.redactCredentials(line)
        assertFalse(redacted.contains("secret"))
        assertEquals("--> GET http://portal.example/player_api.php?username=***&password=***&action=get_vod_streams", redacted)
    }
}
