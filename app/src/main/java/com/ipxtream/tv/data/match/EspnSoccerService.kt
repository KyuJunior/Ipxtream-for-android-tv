package com.ipxtream.tv.data.match

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * ESPN's public scoreboard JSON (no key, no account). Undocumented, so every field below is
 * nullable and [EspnMapper] skips anything it can't read instead of failing the whole day.
 *
 * `dates` is one day, `YYYYMMDD` (a range returns nothing for soccer).
 */
interface EspnSoccerService {

    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun scoreboard(
        @Path("league") league: String,
        @Query("dates") dates:  String
    ): EspnScoreboard

    companion object {
        private const val BASE_URL = "https://site.api.espn.com/"

        fun create(debug: Boolean): EspnSoccerService {
            val logging = HttpLoggingInterceptor { android.util.Log.d("OkHttp", it) }.apply {
                level = if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EspnSoccerService::class.java)
        }
    }
}

// ─── Raw response (only the fields we read) ──────────────────────────────────

data class EspnScoreboard(
    @SerializedName("leagues") val leagues: List<EspnLeague>?,
    @SerializedName("events")  val events:  List<EspnEvent>?
)

data class EspnLeague(
    @SerializedName("slug")  val slug:  String?,
    @SerializedName("name")  val name:  String?,
    @SerializedName("logos") val logos: List<EspnLogo>?
)

data class EspnLogo(@SerializedName("href") val href: String?)

data class EspnEvent(
    @SerializedName("id")           val id:           String?,
    @SerializedName("date")         val date:         String?,
    @SerializedName("status")       val status:       EspnStatus?,
    @SerializedName("competitions") val competitions: List<EspnCompetition>?
)

data class EspnCompetition(
    @SerializedName("status")      val status:      EspnStatus?,
    @SerializedName("competitors") val competitors: List<EspnCompetitor>?
)

data class EspnCompetitor(
    @SerializedName("homeAway") val homeAway: String?,
    @SerializedName("score")    val score:    String?,
    @SerializedName("team")     val team:     EspnTeam?
)

data class EspnTeam(
    @SerializedName("displayName")      val displayName:      String?,
    @SerializedName("shortDisplayName") val shortDisplayName: String?,
    @SerializedName("logo")             val logo:             String?
)

data class EspnStatus(
    @SerializedName("displayClock") val displayClock: String?,
    @SerializedName("type")         val type:         EspnStatusType?
)

data class EspnStatusType(
    @SerializedName("name")      val name:      String?,
    @SerializedName("state")     val state:     String?,
    @SerializedName("completed") val completed: Boolean?,
    @SerializedName("shortDetail") val shortDetail: String?
)

/** ESPN response → [Match]. Pure, so it is unit-tested with saved responses. */
object EspnMapper {

    fun toMatches(board: EspnScoreboard, slug: String): List<Match> {
        val league = board.leagues?.firstOrNull()
        val competition = Competition(
            slug     = slug,
            name     = league?.name ?: slug,
            crestUrl = league?.logos?.firstOrNull()?.href
        )
        return board.events.orEmpty().mapNotNull { event -> toMatch(event, competition) }
    }

    private fun toMatch(event: EspnEvent, competition: Competition): Match? {
        val id = event.id ?: return null
        val kickoff = parseUtc(event.date) ?: return null
        val comp = event.competitions?.firstOrNull() ?: return null
        val home = comp.competitors?.firstOrNull { it.homeAway == "home" } ?: return null
        val away = comp.competitors.firstOrNull { it.homeAway == "away" } ?: return null
        val status = comp.status ?: event.status
        val state = stateOf(status?.type)
        return Match(
            id          = id,
            competition = competition,
            kickoffMs   = kickoff,
            state       = state,
            clock       = if (state == MatchState.LIVE) liveClock(status) else null,
            home        = team(home, state),
            away        = team(away, state)
        )
    }

    private fun team(c: EspnCompetitor, state: MatchState): MatchTeam {
        val name = c.team?.displayName?.takeIf { it.isNotBlank() } ?: "?"
        return MatchTeam(
            name      = name,
            shortName = c.team?.shortDisplayName?.takeIf { it.isNotBlank() } ?: name,
            crestUrl  = c.team?.logo?.takeIf { it.isNotBlank() },
            // ESPN sends "0" before kick-off; only show a score once the match has started.
            score     = c.score.takeIf { state == MatchState.LIVE || state == MatchState.FINISHED }
        )
    }

    fun stateOf(type: EspnStatusType?): MatchState {
        val name = type?.name.orEmpty()
        return when {
            name.contains("POSTPONED") || name.contains("CANCELED") ||
                name.contains("ABANDONED") || name.contains("SUSPENDED") -> MatchState.POSTPONED
            type?.state == "in"                                       -> MatchState.LIVE
            type?.state == "post" || type?.completed == true          -> MatchState.FINISHED
            else                                                      -> MatchState.UPCOMING
        }
    }

    private fun liveClock(status: EspnStatus?): String? {
        val name = status?.type?.name.orEmpty()
        return when {
            name.contains("HALFTIME") -> "HT"
            else -> status?.displayClock?.takeIf { it.isNotBlank() && it != "0'" }
        }
    }

    /** "2026-10-21T16:45Z" or "2026-10-21T16:45:00Z" → epoch ms. */
    fun parseUtc(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val pattern = if (text.length > 17) "yyyy-MM-dd'T'HH:mm:ss'Z'" else "yyyy-MM-dd'T'HH:mm'Z'"
        return runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(text)?.time
        }.getOrNull()
    }
}
