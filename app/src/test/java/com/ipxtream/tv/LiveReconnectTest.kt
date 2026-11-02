package com.ipxtream.tv

import com.ipxtream.tv.ui.player.LiveReconnect
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveReconnectTest {

    @Test fun waitGrowsAndCapsAtThirtySeconds() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L),
            (0..7).map(LiveReconnect::delayMs))
    }

    @Test fun startAttemptsSwapHlsAndTransportStream() {
        assertEquals("http://h/u/p/7.ts", LiveReconnect.alternate("http://h/u/p/7.m3u8"))
        assertEquals("http://h/u/p/7.m3u8", LiveReconnect.alternate("http://h/u/p/7.ts"))
        assertEquals("http://h/movie/u/p/7.mkv", LiveReconnect.alternate("http://h/movie/u/p/7.mkv"))
    }
}
