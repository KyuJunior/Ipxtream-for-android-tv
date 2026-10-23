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

    /** Time zones of the Arab world: there the MENA feeds are wanted even with the app in English. */
    private val MENA_ZONES = setOf(
        "Asia/Riyadh", "Asia/Dubai", "Asia/Qatar", "Asia/Kuwait", "Asia/Bahrain", "Asia/Muscat", "Asia/Aden",
        "Asia/Baghdad", "Asia/Amman", "Asia/Beirut", "Asia/Damascus", "Asia/Gaza", "Asia/Hebron",
        "Africa/Cairo", "Africa/Khartoum", "Africa/Tripoli", "Africa/Tunis", "Africa/Algiers", "Africa/Casablanca",
        "Africa/El_Aaiun", "Africa/Nouakchott"
    )

    /** Region tags to prefer: Arabic feeds for an Arabic app or a TV set to an Arab time zone, else English ones. */
    fun preferredRegions(arabicApp: Boolean, timeZoneId: String): Set<String> =
        if (arabicApp || timeZoneId in MENA_ZONES) ARABIC_REGIONS else ENGLISH_REGIONS

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
    private val SPORT_WORDS = setOf(
        "sport", "sports", "spor", "رياضة", "رياضية", "سبورت", "football", "foot", "futbol", "laliga", "liga"
    )
    /** Broadcasters that also run movie/kids/food channels: only their channels named "sport…" count. */
    private val MIXED_FAMILIES = setOf("bein", "shahid", "canal+", "movistar", "viaplay", "amazon prime", "nbc", "cbs")
    private val GUIDE_FOREIGN = FOREIGN_REGIONS + setOf("au", "us", "usa", "ca")
    private val PENALTY = setOf("news", "backup", "test", "replay", "radio", "fm", "24")
    private val VERSUS = setOf("vs", "v", "x", "ضد")
    private val PREFIX = Regex("""^[^\p{L}\p{N}]*([a-z]{2,4})\s*(?:[|:\]]|\s-\s)\s*""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val NUMBER = Regex("""^\d{1,2}$""")
    private val FAMILY_WORDS: Set<String> by lazy { FAMILIES.values.flatten().map { it.substringBefore(' ') }.toSet() }
    private val TEAM_FILLER =setOf("fc", "cf", "afc", "sc", "ac", "club", "cd", "ud", "sd", "rc", "rcd")

    /** Words in a category name that mark it as sports (plus any broadcaster family spelling). */
    private val SPORTS_CATEGORY_WORDS = SPORT_WORDS + setOf(
        "football", "soccer", "كرة", "القدم", "مباريات", "league", "liga", "premier", "champions",
        "events", "event", "ppv", "match", "matches", "eurosport"
    )

    /** True for a live category that holds sports channels ("AR| BEIN SPORTS", "SPORTS 4K", "رياضة"). */
    fun isSportsCategory(name: String): Boolean {
        val words = normalize(name).text.split(' ').toSet()
        if (words.any { it in ENTERTAINMENT }) return false
        val padded = " ${words.joinToString(" ")} "
        return words.any { it in SPORTS_CATEGORY_WORDS } ||
            FAMILIES.values.flatten().any { padded.contains(" $it ") }
    }

    /**
     * The channels worth matching: those in the provider's sports categories. When a provider has
     * no recognisable sports category, every channel (the entertainment filter still applies).
     */
    fun sportsChannels(channels: List<StreamItem>, categories: List<com.ipxtream.tv.data.model.Category>): List<StreamItem> {
        val ids = categories.filter { isSportsCategory(it.categoryName) }.map { it.categoryId }.toSet()
        if (ids.isEmpty()) return channels
        return channels.filter { it.categoryId in ids }.ifEmpty { channels }
    }

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
        /** The beIN MENA channel this is, for matching the beIN guide; null for other channels and foreign beIN feeds. */
        val guide:   BeinGuide.GuideChannel?,
        val penalty: Boolean,
        val versus:  Boolean
    )

    /** A broadcaster's sports channel or a channel named "sport…" (not beIN DRAMA, not MBC 1). */
    fun isSportsChannel(stream: StreamItem): Boolean = entry(stream).let { it.family != null || it.sport }

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
        // beIN DRAMA / MOVIES / SERIES share the brand but never show football.
        val sportsFamily = family.takeIf {
            words.none { w -> w in ENTERTAINMENT } && (it !in MIXED_FAMILIES || words.any { w -> w in SPORT_WORDS })
        }
        return Entry(
            stream  = stream,
            padded  = padded,
            region  = n.region,
            quality = n.quality,
            family  = sportsFamily,
            sport   = words.any { it in SPORT_WORDS },
            // TR / FR / AU / US beIN are different networks with their own numbering.
            guide   = if (sportsFamily == "bein" && n.region !in GUIDE_FOREIGN) BeinGuide.parseChannel(stream.name) else null,
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
        limit:            Int = 8,
        guideChannels:    List<String> = emptyList()
    ): List<StreamItem> {
        val home = teamForms(match.home)
        val away = teamForms(match.away)
        val guide = guideChannels.mapNotNull { BeinGuide.parseChannel(it) }
        return index.entries
            .mapNotNull { e -> score(e, home, away, families, preferredRegions, guide)?.let { e to it } }
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
        families: List<String>, preferredRegions: Set<String>,
        guide: List<BeinGuide.GuideChannel> = emptyList()
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
        // beIN's own guide says this very channel (beIN SPORTS 3) shows the match: that beats every guess.
        val guideAt = e.guide?.let { guide.indexOf(it) } ?: -1
        if (guideAt >= 0) score += 300 - 10 * guideAt
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
