package com.ipxtream.tv

import com.google.gson.JsonParser
import com.ipxtream.tv.data.local.VideoRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoResTest {

    private fun panel(json: String) = VideoRes.fromPanelJson(JsonParser.parseString(json))

    @Test fun scope4KCountsAs4K() {
        val res = VideoRes(3840, 1600)
        assertTrue(res.is4K)
        assertEquals("4K", res.label)
    }

    @Test fun labelsByMeasuredSize() {
        assertEquals("1080p", VideoRes(1920, 800).label)
        assertEquals("720p", VideoRes(1280, 536).label)
        assertEquals("SD", VideoRes(720, 400).label)
        assertNull(VideoRes.PANEL_EMPTY.label)
        assertNull(VideoRes.FILE_FAILED.label)
    }

    @Test fun readsPanelProbeWithHdr() {
        val res = panel("""{"codec_name":"hevc","width":3840,"height":2160,"color_transfer":"smpte2084"}""")!!
        assertTrue(res.is4K)
        assertTrue(res.hdr)
        assertEquals("hevc", res.codec)
    }

    @Test fun ignoresPosterAttachedAsVideoStream() {
        assertNull(panel("""{"codec_name":"mjpeg","width":3840,"height":2160}"""))
        assertNull(panel("""{"codec_name":"h264","width":1000,"height":1500,"disposition":{"attached_pic":1}}"""))
    }

    @Test fun emptyOrMissingProbeIsNull() {
        assertNull(panel("[]"))
        assertNull(panel("""{"codec_name":"h264"}"""))
        assertNull(VideoRes.fromPanelJson(null))
    }

    @Test fun fallsBackToCodedSize() {
        val res = panel("""{"codec_name":"h264","coded_width":1920,"coded_height":1088}""")!!
        assertFalse(res.is4K)
        assertEquals("1080p", res.label)
    }
}
