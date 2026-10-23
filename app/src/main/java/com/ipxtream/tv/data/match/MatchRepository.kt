package com.ipxtream.tv.data.match

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.ipxtream.tv.data.local.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * @param fetchedAtMs When the oldest part of this day was downloaded; null if nothing was ever.
 * @param offline     True when at least one request failed and cached (or no) data was used.
 */
data class MatchDay(
    val matches:     List<Match>,
    val fetchedAtMs: Long?,
    val offline:     Boolean
)

/**
 * Fixtures for one local day across the followed competitions.
 *
 * ESPN's `dates=` is a US day, so a local day can span two of them: each local day reads the
 * ESPN days before, of and after it and keeps the matches whose kick-off falls on that local
 * day. Each (competition, ESPN day) is cached on its own, so neighbouring days share requests.
 * Not account-specific: fixtures are the same for everyone.
 */
class MatchRepository(
    context: Context,
    private val service: EspnSoccerService = EspnSoccerService.create(com.ipxtream.tv.BuildConfig.DEBUG),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val cache = CacheManager(File(context.cacheDir, "matches").apply { mkdirs() })
    private val listType = object : TypeToken<List<Match>>() {}.type

    suspend fun matchesFor(
        dayOffset:    Int,
        competitions: List<String> = FollowedCompetitions.DEFAULT,
        force:        Boolean = false,
        timeZone:     TimeZone = TimeZone.getDefault()
    ): MatchDay = withContext(Dispatchers.IO) {
        val nowMs = now()
        val day = MatchDays.localDay(nowMs, dayOffset, timeZone)
        val today = MatchDays.localDay(nowMs, 0, timeZone)
        val espnDates = MatchDays.espnDatesAround(day)
        val parts = coroutineScope {
            competitions.flatMap { slug -> espnDates.map { date -> async { load(slug, date, today, force, nowMs) } } }
                .awaitAll()
        }
        val matches = parts.flatMap { it.matches }
            .distinctBy { it.id }
            .filter { MatchDays.dayKey(it.kickoffMs, timeZone) == MatchDays.dayKey(day) }
            .sortedWith(compareBy<Match> { it.kickoffMs }.thenBy { it.competition.slug })
        MatchDay(
            matches     = matches,
            fetchedAtMs = parts.mapNotNull { it.fetchedAtMs }.minOrNull(),
            offline     = parts.any { it.offline }
        )
    }

    private suspend fun load(slug: String, date: String, today: Calendar, force: Boolean, nowMs: Long): MatchDay {
        val key = "espn_${slug}_$date"
        val cached: List<Match>? = cache.load(key, listType, Long.MAX_VALUE)
        val age = cache.ageMs(key)
        if (!force && cached != null && age != null && age <= MatchDays.ttlMs(date, today, cached, nowMs)) {
            return MatchDay(cached, nowMs - age, offline = false)
        }
        return try {
            val fresh = EspnMapper.toMatches(service.scoreboard(slug, date), slug)
            cache.save(key, fresh)
            MatchDay(fresh, nowMs, offline = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MatchRepository", "ESPN $slug $date failed: ${e.message}")
            MatchDay(cached.orEmpty(), age?.let { nowMs - it }, offline = true)
        }
    }
}

/** Day arithmetic and cache lifetimes, kept pure for unit tests. */
object MatchDays {

    private const val MINUTE = 60_000L

    fun localDay(nowMs: Long, offset: Int, tz: TimeZone): Calendar =
        Calendar.getInstance(tz, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, offset)
        }

    fun dayKey(day: Calendar): Int =
        day.get(Calendar.YEAR) * 10_000 + (day.get(Calendar.MONTH) + 1) * 100 + day.get(Calendar.DAY_OF_MONTH)

    fun dayKey(ms: Long, tz: TimeZone): Int =
        dayKey(Calendar.getInstance(tz, Locale.US).apply { timeInMillis = ms })

    /** The ESPN `dates` values (yyyyMMdd) whose matches can fall on [day] in any time zone. */
    fun espnDatesAround(day: Calendar): List<String> = (-1..1).map { delta ->
        val c = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, delta) }
        "%04d%02d%02d".format(Locale.US, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * How long a cached ESPN day stays fresh: a minute while anything in it is (or should be)
     * playing, 10 minutes around today, 3 hours for later days, a month once it's in the past.
     */
    fun ttlMs(espnDate: String, today: Calendar, cached: List<Match>, nowMs: Long): Long {
        val dateKey = espnDate.toIntOrNull() ?: return 10 * MINUTE
        val yesterdayKey = dayKey((today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) })
        val tomorrowKey = dayKey((today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) })
        val inPlay = cached.any {
            it.state == MatchState.LIVE || (it.state == MatchState.UPCOMING && it.kickoffMs <= nowMs)
        }
        return when {
            inPlay                -> MINUTE
            dateKey < yesterdayKey -> 30L * 24 * 60 * MINUTE
            dateKey <= tomorrowKey -> 10 * MINUTE
            else                   -> 3 * 60 * MINUTE
        }
    }
}
