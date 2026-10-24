package com.ipxtream.tv

import com.ipxtream.tv.data.local.LibraryItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.dashboard.HomeLive
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLiveTest {

    private fun ch(id: Int) = StreamItem(
        streamId = id, name = "Channel $id", streamType = "live", streamIcon = null, categoryId = "1", added = null,
        num = id, rating = null, rating5based = null, containerExtension = null, epgChannelId = null, tvArchive = 0,
        tvArchiveDuration = 0, directSource = null, customSid = null
    )

    private fun lib(id: Int, type: String = "live", at: Long = 0L) =
        LibraryItem(id = id.toString(), name = "x", type = type, iconUrl = null, categoryId = null, timestamp = at)

    private val channels = (1..30).map { ch(it) }

    @Test fun favouritesThenRecentLiveChannels() {
        val favorites = listOf(lib(7), lib(3), lib(100, type = "movie"))
        val history = listOf(lib(9, at = 1), lib(3, at = 5), lib(12, at = 9), lib(4, type = "episode", at = 99))
        assertEquals(listOf(7, 3, 12, 9), HomeLive.highlights(channels, favorites, history).map { it.streamId })
    }

    @Test fun channelsNoLongerListedDropOut() =
        assertEquals(listOf(2), HomeLive.highlights(channels, listOf(lib(999), lib(2)), emptyList()).map { it.streamId })

    @Test fun firstChannelsWhenNothingWatched() =
        assertEquals((1..15).toList(), HomeLive.highlights(channels, emptyList(), emptyList()).map { it.streamId })

    @Test fun limitApplies() =
        assertEquals(3, HomeLive.highlights(channels, (1..10).map { lib(it) }, emptyList(), limit = 3).size)
}
