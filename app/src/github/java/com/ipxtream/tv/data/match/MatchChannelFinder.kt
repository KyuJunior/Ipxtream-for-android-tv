package com.ipxtream.tv.data.match

import android.content.Context
import androidx.annotation.StringRes
import com.ipxtream.tv.R
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.dashboard.MatchListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub build: suggests channels for each match from the provider's sports channels, ranked by the
 * rights table and beIN's own guide (see [ChannelMatcher], [BeinGuide]). The Play build has its own
 * provider-neutral version of this class (src/play).
 */
class MatchChannelFinder(context: Context, private val store: MatchChannelStore) {

    /** What one day needs besides the fixtures: beIN's live programmes. */
    class DayData(val programmes: List<GuideProgramme>)

    private val matchRights by lazy { MatchRights.load(context) }
    private val beinGuide by lazy { BeinGuideSource(context) }
    /** Sports-channel index plus the channel and category lists it was built from (caller's thread only). */
    private var channelIndex: Triple<List<StreamItem>, List<Category>, ChannelMatcher.ChannelIndex>? = null

    suspend fun dayData(dayOffset: Int): DayData {
        val tz = java.util.TimeZone.getDefault()
        return DayData(beinGuide.liveProgrammes(MatchDays.localDay(System.currentTimeMillis(), dayOffset, tz)))
    }

    /**
     * Suggestions come only from the provider's sports categories (owner request: no "beIN DRAMA").
     * The picker still offers every channel, and a remembered pick can be any channel.
     * Call from the main thread: the index cache is stored there.
     */
    suspend fun listings(
        matches:    List<Match>,
        channels:   List<StreamItem>,
        categories: List<Category>,
        day:        DayData,
        arabicApp:  Boolean
    ): List<MatchListing> {
        // Rebuilt only when the channel or category list changes. The new index is stored here, on the
        // main thread, so a cancelled run still finishing on Default can't overwrite it.
        val cachedIndex = channelIndex?.takeIf { it.first === channels && it.second === categories }?.third
        val index = cachedIndex ?: withContext(Dispatchers.Default) {
            ChannelMatcher.ChannelIndex(ChannelMatcher.sportsChannels(channels, categories))
        }
        channelIndex = Triple(channels, categories, index)
        return withContext(Dispatchers.Default) { build(matches, channels, index, day.programmes, arabicApp) }
    }

    private fun build(
        matches:    List<Match>,
        channels:   List<StreamItem>,
        index:      ChannelMatcher.ChannelIndex,
        programmes: List<GuideProgramme>,
        arabicApp:  Boolean
    ): List<MatchListing> {
        val regions = ChannelMatcher.preferredRegions(arabicApp, java.util.TimeZone.getDefault().id)
        val byId by lazy { channels.associateBy { it.streamId } }
        return matches.map { m ->
            // beIN's guide names the exact channel (beIN SPORTS 3); the matcher puts the provider's copy of it first.
            val onGuide = BeinGuide.channelsFor(m, programmes)
            val guesses = ChannelMatcher.candidates(
                m, index, matchRights.familiesFor(m.competition.slug), regions, guideChannels = onGuide
            )
            // The guide channel the top suggestion actually is (beIN SPORTS EN 1 when only that copy exists).
            val top = guesses.firstOrNull()?.let { s -> BeinGuide.parseChannel(s.name) }
            val guideHit = onGuide.firstOrNull { top != null && BeinGuide.parseChannel(it) == top }
            // A pick for this very match wins, unless the guide knows the channel and the pick is one of a
            // broadcaster's non-sports channels (versions before 2.0.4 suggested "beIN DRAMA"). Event channels
            // and anything else the user chose stay. A competition-wide pick counts only when the guide has nothing.
            val saved = store.matchChannel(m)?.let { byId[it] }
                ?.takeIf { guideHit == null || !ChannelMatcher.isEntertainmentChannel(it) }
                ?: store.competitionChannel(m)?.takeIf { guideHit == null }?.let { byId[it] }
            MatchListing(
                match        = m,
                channels     = listOfNotNull(saved) + guesses.filter { it.streamId != saved?.streamId },
                remembered   = saved != null,
                guideChannel = guideHit.takeIf { saved == null }
            )
        }
    }

    companion object {
        /** Row hint for [MatchListing.guideChannel]: "On beIN SPORTS 3 (beIN guide)". */
        @get:StringRes
        val GUIDE_HINT: Int get() = R.string.matches_channel_guide
    }
}
