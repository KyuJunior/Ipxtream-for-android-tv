package com.ipxtream.tv.data.match

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.ipxtream.tv.data.local.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** One live broadcast on a beIN MENA channel ("beIN SPORTS 3", 21:50–00:15). */
data class GuideProgramme(
    val channel: String,
    val title:   String,
    val startMs: Long,
    val endMs:   Long
)

// Raw beIN guide file: {"responseObj":[{ "Live":"True", "ChannelName":"beIN SPORTS 1", "Title":{"English":…}, … }]}
data class BeinGuideFile(@SerializedName("responseObj") val items: List<BeinGuideItem>?)

data class BeinGuideItem(
    @SerializedName("Live")        val live:      String?,
    @SerializedName("ChannelName") val channel:   String?,
    @SerializedName("Title")       val title:     BeinText?,
    @SerializedName("StartTime")   val startTime: String?,
    @SerializedName("EndTime")     val endTime:   String?
)

data class BeinText(@SerializedName("English") val english: String?)

/**
 * Which beIN MENA channel shows a match, from beIN's own public TV guide (the JSON files behind
 * bein.com/tv-guide; one file per channel per day, each covering the day before and after too).
 * Provider guides don't help here: the owner's provider has no EPG for any beIN channel.
 */
class BeinGuideSource(context: Context) {

    private val cache = CacheManager(File(context.cacheDir, "bein_guide").apply { mkdirs() })
    private val listType = object : TypeToken<List<GuideProgramme>>() {}.type
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Live broadcasts listed in the guide files for [day] (local calendar day). Empty when unreachable. */
    suspend fun liveProgrammes(day: Calendar): List<GuideProgramme> = withContext(Dispatchers.IO) {
        val folder = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = day.timeZone }.format(day.time)
        val key = "bein_$folder"
        cache.load<List<GuideProgramme>>(key, listType, CACHE_MS)?.let { return@withContext it }
        val parts = coroutineScope {
            CHANNEL_FILES.map { file -> async { runCatching { fetch(folder, file) }.getOrNull() } }.awaitAll()
        }
        if (parts.all { it == null }) {
            // Offline or the guide moved: an old copy is still better than nothing.
            return@withContext cache.load<List<GuideProgramme>>(key, listType, Long.MAX_VALUE).orEmpty()
        }
        val programmes = parts.filterNotNull().flatten()
        cache.save(key, programmes)
        programmes
    }

    private fun fetch(folder: String, file: String): List<GuideProgramme> {
        val request = Request.Builder().url("$BASE_URL$folder/$file").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.charStream() ?: return emptyList()
            return BeinGuide.parse(gson.fromJson(body, BeinGuideFile::class.java))
        }
    }

    companion object {
        private const val BASE_URL = "https://storagebeincom-b4dvftgkaebcayar.z01.azurefd.net/epg/"
        private const val CACHE_MS = 3 * 60 * 60 * 1000L

        /** beIN SPORTS 1–9, beIN SPORTS EN 1–2 and beIN 4K: where the followed leagues are shown. */
        val CHANNEL_FILES = listOf(
            "5201.json", "5202.json", "5203.json", "10602.json", "10404.json", "10601.json",
            "10801.json", "10603.json", "10802.json", "10803.json", "10804.json", "179.json"
        )
    }
}

/** Pure parts of the beIN guide: parsing, finding a match, and recognising the same channel in a provider's list. */
object BeinGuide {

    /** beIN guide times are Doha local time. */
    private val GUIDE_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Qatar")
    private const val MINUTE = 60_000L

    fun parse(file: BeinGuideFile?): List<GuideProgramme> {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = GUIDE_ZONE }
        return file?.items.orEmpty().mapNotNull { item ->
            if (item.live != "True") return@mapNotNull null   // replays and highlights don't count
            val channel = item.channel?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = item.title?.english?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val start = runCatching { format.parse(item.startTime ?: "")?.time }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { format.parse(item.endTime ?: "")?.time }.getOrNull() ?: start
            GuideProgramme(channel, title, start, end)
        }
    }

    /**
     * The beIN channels showing [match] live, Arabic channels before English ones. A broadcast counts when
     * its title names both teams and it starts between 90 minutes before and 20 minutes after kick-off.
     */
    fun channelsFor(match: Match, programmes: List<GuideProgramme>): List<String> {
        val home = teamWords(match.home)
        val away = teamWords(match.away)
        return programmes
            .filter { it.startMs in (match.kickoffMs - 90 * MINUTE)..(match.kickoffMs + 20 * MINUTE) }
            .filter { p ->
                val title = " ${fold(p.title)} "
                home.any { title.contains(" $it ") } && away.any { title.contains(" $it ") }
            }
            .map { it.channel }
            .distinct()
            .sortedBy { if (parseChannel(it)?.variant == Variant.ENGLISH) 1 else 0 }
    }

    enum class Variant { MAIN, ENGLISH, XTRA, MAX, UHD }
    data class GuideChannel(val variant: Variant, val number: Int?)

    /** "beIN SPORTS 3" → MAIN 3, "beIN SPORTS EN 1" → ENGLISH 1, "beIN 4K" → UHD; null for other channels. */
    fun parseChannel(name: String): GuideChannel? {
        val words = fold(name).split(' ')
        if ("bein" !in words) return null
        val number = words.firstOrNull { it.length <= 2 && it.all(Char::isDigit) }?.toIntOrNull()
        val variant = when {
            words.any { it == "en" || it == "english" } -> Variant.ENGLISH
            words.any { it == "xtra" || it == "extra" } -> Variant.XTRA
            words.any { it == "max" }                   -> Variant.MAX
            number == null && words.any { it == "4k" }  -> Variant.UHD
            else                                        -> Variant.MAIN
        }
        return GuideChannel(variant, number)
    }

    /** Lower-case, accents and decorations removed, single spaces ("Alavés" → "alaves", "ⒶⓂ" → "am"). */
    fun fold(text: String): String =
        Normalizer.normalize(Normalizer.normalize(text, Normalizer.Form.NFKC), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.joinToString(" ")

    private val FILLER = setOf("fc", "cf", "afc", "sc", "ac", "club", "cd", "ud", "sd", "rc", "rcd", "de", "the")

    /** Full names plus each distinctive word ("nottingham forest", "forest") of a team. */
    private fun teamWords(team: MatchTeam): List<String> {
        val names = listOf(team.name, team.shortName).map { fold(it) }
        val words = names.flatMap { it.split(' ') }
            .filter { it.length >= 4 && it !in FILLER && it !in COMMON_WORDS }
        return (names.filter { it.length >= 4 } + words).distinct()
    }

    /** Words shared by many clubs; on their own they don't identify a team. */
    private val COMMON_WORDS = setOf("united", "city", "real", "athletic", "atletico", "sporting", "town", "rovers", "wanderers", "hotspur", "albion")
}
