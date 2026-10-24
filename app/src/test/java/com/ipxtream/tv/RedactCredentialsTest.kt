package com.ipxtream.tv

import com.ipxtream.tv.data.api.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** No log line, crash entry or error message may show the provider login. */
class RedactCredentialsTest {

    private fun masked(text: String) = ApiClient.redactCredentials(text)

    @Test fun queryParameters() = assertEquals(
        "GET http://p.tv:8080/player_api.php?username=***&password=***&action=get_live_streams",
        masked("GET http://p.tv:8080/player_api.php?username=alice&password=s3cret&action=get_live_streams")
    )

    @Test fun movieSeriesAndTimeshiftPaths() {
        assertEquals("http://p.tv:8080/movie/***/***/123.mkv", masked("http://p.tv:8080/movie/alice/s3cret/123.mkv"))
        assertEquals("http://p.tv/series/***/***/9.mp4", masked("http://p.tv/series/alice/s3cret/9.mp4"))
        assertEquals(
            "https://p.tv/timeshift/***/***/60/2026-10-24:20-00/7.ts",
            masked("https://p.tv/timeshift/alice/s3cret/60/2026-10-24:20-00/7.ts")
        )
    }

    @Test fun livePathWithoutType() = assertEquals(
        "Source error: http://p.tv:8080/***/***/3000.m3u8 (HTTP 403)",
        masked("Source error: http://p.tv:8080/alice/s3cret/3000.m3u8 (HTTP 403)")
    )

    @Test fun stackTraceKeepsEverythingElse() {
        val trace = "androidx.media3.datasource.HttpDataSource\$InvalidResponseCodeException: Response code: 404\n" +
            "\tat x.y(Z.kt:1) url=http://10.0.2.2:8898/movie/play_qa/mockpass/41.mp4"
        val out = masked(trace)
        assertFalse(out.contains("mockpass"))
        assertFalse(out.contains("play_qa"))
        assertEquals(trace.replace("play_qa/mockpass", "***/***"), out)
    }

    @Test fun ordinaryTextUnchanged() {
        val text = "Connection failed: timeout after 15 s on host p.tv"
        assertEquals(text, masked(text))
    }

    @Test fun httpsVariant() {
        assertEquals("https://p.tv:25463/", ApiClient.httpsVariant("http://p.tv:8080", "25463"))
        assertEquals("https://p.tv:443/", ApiClient.httpsVariant("p.tv:8080/", "443"))
        assertNull(ApiClient.httpsVariant("http://p.tv:8080", ""))
        assertNull(ApiClient.httpsVariant("http://p.tv:8080", "0"))
        assertNull(ApiClient.httpsVariant("http://p.tv:8080", null))
        assertNull(ApiClient.httpsVariant("https://p.tv:8443", "8443"))
    }
}
