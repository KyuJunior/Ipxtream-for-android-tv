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

/** The competitions the Matches section follows (owner decision, docs/matches-section-plan.md §7). */
object FollowedCompetitions {
    val DEFAULT: List<String> = listOf("uefa.champions", "eng.1", "esp.1")
}
