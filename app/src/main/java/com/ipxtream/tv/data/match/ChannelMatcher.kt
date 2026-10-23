package com.ipxtream.tv.data.match

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ipxtream.tv.data.model.StreamItem
import java.text.Normalizer
import java.util.Locale

/**
 * Guesses which of the provider's live channels shows a match. Provider names are free text
 * ("AR| beIN SPORTS 1 ᴴᴰ", "|UK| Sky Sports Premier League FHD"), so each name is normalised once
 * ([ChannelIndex]) and scored per match:
 * - both team names in the channel name (event channels such as "PL: Arsenal vs Chelsea") win;
 * - otherwise the channel's family (beIN, Sky Sports…) ranked by the competition's entry in
 *   `assets/match_rights.json`;
 * - small nudges for the viewer's region prefix, picture quality and the lower channel number;
 *   news/backup/radio channels are pushed down.
 * Pure (no Android calls) apart from [MatchRights.load], so it is unit-tested on real names.
 */
object ChannelMatcher {

    /** Family id → spellings as they appear after [normalize] (longest first is not required). */
    val FAMILIES: Map<String, List<String>> = mapOf(
        "bein"           to listOf("bein sports", "bein sport", "bein", "be in sports", "بي ان سبورت", "بي ان", "بين سبورت"),
        "ssc"            to listOf("ssc"),
        "ad sports"      to listOf("ad sports", "ad sport", "abu dhabi sports"),
        "alkass"         to listOf("alkass", "al kass"),
        "shahid"         to listOf("shahid"),
        "thmanyah"       to listOf("thmanyah", "ثمانية"),
        "sky sports"     to listOf("sky sports", "sky sport"),
        "tnt sports"     to listOf("tnt sports", "tnt sport", "bt sport"),
        "canal+"         to listOf("canal plus"),
        "movistar"       to listOf("movistar", "m laliga", "m liga de campeones"),
        "laliga tv"      to listOf("laliga tv", "la liga tv"),
        "dazn"           to listOf("dazn"),
        "espn"           to listOf("espn"),
        "premier sports" to listOf("premier sports"),
        "viaplay"        to listOf("viaplay"),
        "nbc"            to listOf("nbc sports", "peacock", "usa network"),
        "cbs"            to listOf("cbs sports", "paramount plus"),
        "amazon prime"   to listOf("prime video", "amazon prime"),
        "rmc sport"      to listOf("rmc sport"),
        "sport tv"       to listOf("sport tv")
    )

    /** Region prefixes that suit an Arabic-speaking viewer. */
    val ARABIC_REGIONS = setOf("ar", "arab", "mena", "me", "ksa", "sa", "uae", "ae", "qa", "eg", "kw", "bh", "om", "jo")
    val ENGLISH_REGIONS = setOf("uk", "gb", "en", "eng", "us", "usa", "ie", "ca", "au")

    private val QUALITY = mapOf(
        "4k" to 3, "uhd" to 3, "fhd" to 3, "hd" to 2, "hq" to 1, "sd" to -3,
        "hevc" to 0, "h265" to 0, "h264" to 0, "50fps" to 0, "60fps" to 0, "raw" to 0
    )
    /** Country tags seen in provider names ("AR_beIN…", "TR ★ BEIN…", "UK: Sky…"). */
    private val FOREIGN_REGIONS = setOf(
        "tr", "fr", "es", "de", "it", "pt", "nl", "pl", "ru", "gr", "ro", "se", "dk", "fi", "br", "mx", "latino", "ir", "ku", "pk"
    )
    /** Tags recognised anywhere in the first few words; short words that are also real words ("al", "in", "me") only as a "XX|" prefix. */
    private val SCANNED_REGIONS: Set<String> by lazy { (ARABIC_REGIONS + ENGLISH_REGIONS + FOREIGN_REGIONS) - setOf("me", "ca", "ie") }
    /** Words that mark a broadcaster's non-sports channel (beIN DRAMA, beIN MOVIES…). */
    private val ENTERTAINMENT = setOf(
        "drama", "movies", "movie", "series", "cinema", "film", "films", "box", "office", "junior", "jeem", "baraem",
        "gourmet", "fatafeat", "kids", "documentary", "docs", "doc", "music", "entertainment", "comedy"
    )
    private val SPORT_WORDS = setOf("sport", "sports", "رياضة", "رياضية", "سبورت")
    private val PENALTY = setOf("news", "backup", "test", "replay", "radio", "fm", "24")
    private val VERSUS = setOf("vs", "v", "x", "ضد")
    private val PREFIX = Regex("""^[^\p{L}\p{N}]*([a-z]{2,4})\s*(?:[|:\]]|\s-\s)\s*""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val NUMBER = Regex("""^\d{1,2}$""")
    private val FAMILY_WORDS: Set<String> by lazy { FAMILIES.values.flatten().map { it.substringBefore(' ') }.toSet() }
    private val TEAM_FILLER =setOf("fc", "cf", "afc", "sc", "ac", "club", "cd", "ud", "sd", "rc", "rcd")

    data class Normalized(val text: String, val region: String?, val quality: Int)

    /** Lower-case words separated by single spaces, region prefix and quality tags taken out. */
    fun normalize(raw: String): Normalized {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
        var region: String? = null
        PREFIX.find(s)?.let { m ->
            // "DAZN | LaLiga 1" or "beIN: …" start with a brand, not a region.
            if (m.groupValues[1] !in FAMILY_WORDS) {
                region = m.groupValues[1]
                s = s.substring(m.range.last + 1)
            }
        }
        s = s.replace("+", " plus ")
        var words = s.split(NON_WORD).filter { it.isNotEmpty() }
        if (region == null) {
            // "ⒶⓂ AR_beIN_SPORTS_1" / "TR ★ BEIN SPORTS 1": a country tag among the first words,
            // after decorations (the circled letters fold to plain ones).
            val at = words.take(3).indexOfFirst { it in SCANNED_REGIONS }
            if (at >= 0) {
                region = words[at]
                words = words.drop(at + 1)
            }
        }
        var quality = 0
        val kept = words.filter { w ->
            val q = QUALITY[w]
            if (q != null) { if (quality == 0 || q > quality) quality = q; false } else true
        }
        return Normalized(kept.joinToString(" "), region, quality)
    }

    /** A team's name forms worth looking for in channel names ("real madrid", "barcelona"). */
    fun teamForms(team: MatchTeam): List<String> =
        listOf(team.name, team.shortName)
            .map { name -> normalize(name).text.split(' ').filter { it !in TEAM_FILLER }.joinToString(" ") }
            .filter { it.length >= 4 }
            .distinct()

    class Entry(
        val stream:  StreamItem,
        val padded:  String,     // " text " for whole-word lookups
        val region:  String?,
        val quality: Int,
        val family:  String?,
        val number:  Int?,
        val sport:   Boolean,
        val penalty: Boolean,
        val versus:  Boolean
    )

    /** Every live channel, normalised once per channel list. */
    class ChannelIndex(channels: List<StreamItem>) {
        val entries: List<Entry> = channels.map { entry(it) }
    }

    fun entry(stream: StreamItem): Entry {
        val n = normalize(stream.name)
        val padded = " ${n.text} "
        var family: String? = null
        var number: Int? = null
        loop@ for ((id, spellings) in FAMILIES) {
            for (spelling in spellings) {
                val at = padded.indexOf(" $spelling ")
                if (at >= 0) {
                    family = id
                    // The channel number is the first small number in the next few words.
                    number = padded.substring(at + spelling.length + 1).trim().split(' ')
                        .take(3).firstOrNull { NUMBER.matches(it) }?.toIntOrNull()
                    break@loop
                }
            }
        }
        val words = n.text.split(' ').toSet()
        return Entry(
            stream  = stream,
            padded  = padded,
            region  = n.region,
            quality = n.quality,
            // beIN DRAMA / MOVIES / SERIES share the brand but never show football.
            family  = family.takeIf { words.none { it in ENTERTAINMENT } },
            sport   = words.any { it in SPORT_WORDS },
            number  = number,
            penalty = words.any { it in PENALTY },
            versus  = words.any { it in VERSUS }
        )
    }

    /**
     * Best channels for [match], best first, at most [limit].
     *
     * @param families         The competition's families from the rights table, best first.
     * @param preferredRegions Region prefixes that suit the viewer (see [ARABIC_REGIONS]).
     */
    fun candidates(
        match:            Match,
        index:            ChannelIndex,
        families:         List<String>,
        preferredRegions: Set<String>,
        limit:            Int = 8
    ): List<StreamItem> {
        val home = teamForms(match.home)
        val away = teamForms(match.away)
        return index.entries
            .mapNotNull { e -> score(e, home, away, families, preferredRegions)?.let { e to it } }
            .sortedWith(
                compareByDescending<Pair<Entry, Int>> { it.second }
                    .thenBy { it.first.stream.name.length }
                    .thenBy { it.first.stream.streamId }
            )
            .take(limit)
            .map { it.first.stream }
    }

    private fun score(
        e: Entry, home: List<String>, away: List<String>,
        families: List<String>, preferredRegions: Set<String>
    ): Int? {
        val hasHome = home.any { e.padded.contains(" $it ") }
        val hasAway = away.any { e.padded.contains(" $it ") }
        val rank = e.family?.let { families.indexOf(it) } ?: -1
        var score = when {
            hasHome && hasAway                                  -> 100
            (hasHome || hasAway) && (e.versus || rank >= 0)     -> 35
            else                                                -> 0
        }
        if (rank >= 0) score += maxOf(15, 60 - 8 * rank)
        if (score == 0) return null
        if (e.region != null) score += if (e.region in preferredRegions) 8 else -4
        if (e.sport) score += 4
        score += e.quality
        e.number?.let { score += maxOf(0, 6 - it) }
        if (e.penalty) score -= 25
        return score
    }
}

/** `assets/match_rights.json`: competition slug → channel families, best first. */
data class MatchRights(
    @SerializedName("season")       val season:       String?,
    @SerializedName("competitions") val competitions: Map<String, List<String>>?
) {
    fun familiesFor(slug: String): List<String> = competitions?.get(slug).orEmpty()

    companion object {
        fun parse(json: String): MatchRights =
            runCatching { Gson().fromJson(json, MatchRights::class.java) }.getOrNull()
                ?: MatchRights(null, emptyMap())

        fun load(context: Context): MatchRights =
            runCatching {
                context.assets.open("match_rights.json").bufferedReader().use { parse(it.readText()) }
            }.getOrDefault(MatchRights(null, emptyMap()))
    }
}
