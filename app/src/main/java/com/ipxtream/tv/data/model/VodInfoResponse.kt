package com.ipxtream.tv.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Full VOD (Movie) detail response from:
 *   player_api.php?action=get_vod_info&vod_id={id}
 */
data class VodInfoResponse(
    @SerializedName("info")       val info:      VodInfo?,
    @SerializedName("movie_data") val movieData: VodMovieData?
)

data class VodInfo(
    @SerializedName("name")                   val name: String? = null,
    @SerializedName("o_name")                 val originalName: String? = null,
    @SerializedName("cover_big")              val coverBig: String? = null,
    @SerializedName("movie_image")            val movieImage: String? = null,
    @SerializedName("releasedate")            val releaseDate: String? = null,
    @SerializedName("release_date")           val releaseDateAlt: String? = null,
    @SerializedName("episode_run_time")       val episodeRunTime: String? = null,
    @SerializedName("youtube_trailer")        val youtubeTrailer: String? = null,
    @SerializedName("director")               val director: String? = null,
    @SerializedName("actors")                 val actors: String? = null,
    @SerializedName("cast")                   val cast: String? = null,
    @SerializedName("description")            val description: String? = null,
    @SerializedName("plot")                   val plot: String? = null,
    @SerializedName("age")                    val age: String? = null,
    @SerializedName("mpaa_rating")            val mpaaRating: String? = null,
    @SerializedName("country")                val country: String? = null,
    @SerializedName("genre")                  val genre: String? = null,
    @SerializedName("backdrop_path")          val rawBackdrop: JsonElement? = null,
    @SerializedName("duration_secs")          val durationSecs: Long? = null,
    @SerializedName("duration")               val duration: String? = null,
    @SerializedName("rating")                 val rating: String? = null,
    @SerializedName("rating_5based")          val rating5based: Double? = null,
    @SerializedName("tmdb_id")                val tmdbId: JsonElement? = null
) {
    /** Safe list of backdrop image URLs parsed from whatever format the server returned */
    val backdrops: List<String>
        get() {
            if (rawBackdrop == null || rawBackdrop.isJsonNull) return emptyList()
            return try {
                if (rawBackdrop.isJsonArray) {
                    rawBackdrop.asJsonArray.mapNotNull {
                        if (it.isJsonPrimitive) it.asString.takeIf { s -> s.isNotBlank() } else null
                    }
                } else if (rawBackdrop.isJsonPrimitive) {
                    val s = rawBackdrop.asString
                    if (s.isNotBlank()) listOf(s) else emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val plotText: String?
        get() = plot?.trim()?.takeIf { it.isNotBlank() }
            ?: description?.trim()?.takeIf { it.isNotBlank() }

    val castText: String?
        get() = cast?.trim()?.takeIf { it.isNotBlank() }
            ?: actors?.trim()?.takeIf { it.isNotBlank() }

    val posterUrl: String?
        get() = movieImage?.trim()?.takeIf { it.isNotBlank() }
            ?: coverBig?.trim()?.takeIf { it.isNotBlank() }

    val backdropUrl: String?
        get() = backdrops.firstOrNull { it.isNotBlank() }

    val releaseYear: String?
        get() {
            val date = releaseDate?.trim()?.takeIf { it.isNotBlank() }
                ?: releaseDateAlt?.trim()?.takeIf { it.isNotBlank() }
                ?: return null
            return if (date.length >= 4) date.substring(0, 4) else date
        }

    val formattedDuration: String?
        get() {
            val secs = durationSecs
            if (secs != null && secs > 0) {
                val hours = secs / 3600
                val minutes = (secs % 3600) / 60
                return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            }
            val d = duration?.trim()
            if (!d.isNullOrBlank()) {
                val parts = d.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0
                    if (h > 0 || m > 0) {
                        return if (h > 0) "${h}h ${m}m" else "${m}m"
                    }
                }
                return d
            }
            val runTime = episodeRunTime?.trim()?.toIntOrNull()
            if (runTime != null && runTime > 0) {
                val hours = runTime / 60
                val minutes = runTime % 60
                return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            }
            return null
        }
}

data class VodMovieData(
    @SerializedName("stream_id")           val streamId: Int? = null,
    @SerializedName("name")                val name: String? = null,
    @SerializedName("title")               val title: String? = null,
    @SerializedName("year")                val year: String? = null,
    @SerializedName("added")               val added: String? = null,
    @SerializedName("category_id")         val categoryId: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
    @SerializedName("direct_source")       val directSource: String? = null
)
