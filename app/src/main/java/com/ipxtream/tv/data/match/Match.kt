package com.ipxtream.tv.data.match

/**
 * One football match, independent of the fixture source (ESPN today).
 * Lives under `com.ipxtream.tv.data` so R8 keeps the field names Gson uses for the disk cache.
 *
 * @param kickoffMs Kick-off as epoch milliseconds (UTC); the UI formats it in the TV's time zone.
 * @param clock     Live minute as the source shows it ("67'", "45'+2'", "HT"); null when not live.
 */
data class Match(
    val id:          String,
    val competition: Competition,
    val kickoffMs:   Long,
    val state:       MatchState,
    val clock:       String?,
    val home:        MatchTeam,
    val away:        MatchTeam
)

data class MatchTeam(
    val name:      String,
    val shortName: String,
    val crestUrl:  String?,
    val score:     String?
)

data class Competition(
    val slug:     String,
    val name:     String,
    val crestUrl: String?
)

enum class MatchState { UPCOMING, LIVE, FINISHED, POSTPONED }

/** The competitions Matches can follow, as ESPN league slugs. */
object FollowedCompetitions {
    /** On by default (owner decision, docs/matches-section-plan.md §7). */
    val DEFAULT: List<String> = listOf("uefa.champions", "eng.1", "esp.1")

    /** Every league the user can switch on, in display order (most popular for the region first). */
    val CATALOG: List<String> = listOf(
        "uefa.champions", "eng.1", "esp.1", "ksa.1", "ita.1", "ger.1", "fra.1",
        "uefa.europa", "uefa.europa.conf", "afc.champions", "caf.champions",
        "ksa.kings.cup", "eng.fa", "esp.copa_del_rey", "por.1", "ned.1", "usa.1",
        "uefa.nations", "fifa.world", "fifa.worldq.afc"
    )

    /** [selected] in catalog order, so lists and groups don't depend on the order of toggling. */
    fun ordered(selected: Collection<String>): List<String> = CATALOG.filter { it in selected }
}
