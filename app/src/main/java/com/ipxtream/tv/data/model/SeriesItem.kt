package com.ipxtream.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single TV Series entry from:
 *   player_api.php?action=get_series
 *   player_api.php?action=get_series&category_id={id}
 *
 * To retrieve the full season/episode breakdown, pass [seriesId] to
 * [XtreamApiService.getSeriesInfo] which returns a [SeriesInfoResponse].
 *
 * @param seriesId        Unique identifier — used in `get_series_info` calls.
 * @param name            Series title.
 * @param cover           Poster/cover art URL.
 * @param plot            Long synopsis text.
 * @param cast            Comma-separated cast string.
 * @param director        Director name(s).
 * @param genre           Genre string (e.g. "Action, Drama").
 * @param releaseDate     Year or full date string (format varies by portal).
 * @param lastModified    Unix timestamp string of last metadata update.
 * @param rating          String rating value.
 * @param rating5based    Rating normalised to 0–5.
 * @param backdropPath    List of backdrop/banner image URLs.
 * @param youtubeTrailer  YouTube video ID or full URL, if available.
 * @param episodeRunTime  Typical runtime per episode in minutes (string).
 * @param categoryId      Parent category used for filtering.
 */
data class SeriesItem(
    @SerializedName("series_id")        val seriesId:       Int,
    @SerializedName("name")             val name:           String,
    @SerializedName("cover")            val cover:          String?,
    @SerializedName("plot")             val plot:           String?,
    @SerializedName("cast")             val cast:           String?,
    @SerializedName("director")         val director:       String?,
    @SerializedName("genre")            val genre:          String?,
    @SerializedName("releaseDate")      val releaseDate:    String?,
    @SerializedName("last_modified")    val lastModified:   String?,
    @SerializedName("rating")           val rating:         String?,
    @SerializedName("rating_5based")    val rating5based:   Double?,
    @SerializedName("backdrop_path")    val backdropPath:   List<String>?,
    @SerializedName("youtube_trailer")  val youtubeTrailer: String?,
    @SerializedName("episode_run_time") val episodeRunTime: String?,
    @SerializedName("category_id")      val categoryId:     String?
)
