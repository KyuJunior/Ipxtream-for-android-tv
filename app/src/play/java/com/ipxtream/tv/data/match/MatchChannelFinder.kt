package com.ipxtream.tv.data.match

import android.content.Context
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.dashboard.MatchListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

/**
 * Play build (docs/play-store-plan.md, decision D3): suggestions come only from the user's own
 * channel names, never from a list of broadcasters or a third-party guide. A channel is suggested
 * when its name holds both team names (event channels such as "Al Hilal vs Al Nassr"). Nothing
 * plays until the user confirms (BuildConfig.MATCH_AUTO_PLAY is false).
 */
class MatchChannelFinder(@Suppress("UNUSED_PARAMETER") context: Context, private val store: MatchChannelStore) {

    /** Nothing beyond the fixtures is needed per day. */
    class DayData

    /** Normalised channel names plus the list they were built from (caller's thread only). */
    private var nameIndex: Pair<List<StreamItem>, List<TeamNames.Named>>? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun dayData(dayOffset: Int): DayData = DayData()

    /** Call from the main thread: the name index cache is stored there. */
    suspend fun listings(
        matches:    List<Match>,
        channels:   List<StreamItem>,
        @Suppress("UNUSED_PARAMETER") categories: List<Category>,
        @Suppress("UNUSED_PARAMETER") day:        DayData,
        @Suppress("UNUSED_PARAMETER") arabicApp:  Boolean
    ): List<MatchListing> {
        val cached = nameIndex?.takeIf { it.first === channels }?.second
        val names = cached ?: withContext(Dispatchers.Default) { TeamNames.index(channels) }
        nameIndex = channels to names
        return withContext(Dispatchers.Default) {
            val byId by lazy { channels.associateBy { it.streamId } }
            matches.map { m ->
                val saved = (store.matchChannel(m) ?: store.competitionChannel(m))?.let { byId[it] }
                val found = TeamNames.channelsNaming(m, names)
                MatchListing(
                    match      = m,
                    channels   = listOfNotNull(saved) + found.filter { it.streamId != saved?.streamId },
                    remembered = saved != null
                )
            }
        }
    }

    companion object {
        /** No third-party guide in this build, so no guide hint. */
        const val GUIDE_HINT: Int = 0
    }
}

/** Finds the user's channels whose names hold both teams of a match. Pure, so it is unit-tested. */
object TeamNames {

    class Named(val stream: StreamItem, val padded: String)

    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    /** Club-type words that don't identify a team ("FC Barcelona" → "barcelona"). */
    private val FILLER = setOf("fc", "cf", "afc", "sc", "ac", "club", "cd", "ud", "sd", "rc", "rcd")

    /** Lower-case words separated by single spaces. */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .split(NON_WORD).filter { it.isNotEmpty() }.joinToString(" ")

    /** A team's name forms worth looking for ("real madrid", "barcelona"); short ones would match too much. */
    fun forms(team: MatchTeam): List<String> =
        listOf(team.name, team.shortName)
            .map { name -> normalize(name).split(' ').filter { it !in FILLER }.joinToString(" ") }
            .filter { it.length >= 4 }
            .distinct()

    fun index(channels: List<StreamItem>): List<Named> = channels.map { Named(it, " ${normalize(it.name)} ") }

    /** Channels naming both teams, shortest name first (the plainest event channel), at most [limit]. */
    fun channelsNaming(match: Match, names: List<Named>, limit: Int = 8): List<StreamItem> {
        val home = forms(match.home)
        val away = forms(match.away)
        if (home.isEmpty() || away.isEmpty()) return emptyList()
        return names.asSequence()
            .filter { n -> home.any { n.padded.contains(" $it ") } && away.any { n.padded.contains(" $it ") } }
            .map { it.stream }
            .sortedWith(compareBy<StreamItem> { it.name.length }.thenBy { it.streamId })
            .take(limit)
            .toList()
    }
}
