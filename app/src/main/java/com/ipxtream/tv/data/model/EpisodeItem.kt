package com.ipxtream.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * A single episode as returned inside [SeriesInfoResponse.episodesBySeason].
 *
 * The playback URL is constructed as:
 *   {server}/series/{username}/{password}/{id}.{containerExtension}
 *
 * Note: [id] (not [episodeNum]) is the correct identifier to use in the
 * stream URL — episode numbers are only for display ordering.
 *
 * @param id                  Unique stream identifier used in the playback URL.
 * @param episodeNum          Display episode number within the season.
 * @param title               Episode title.
 * @param containerExtension  File format extension, e.g. "mkv", "mp4".
 * @param info                Optional rich metadata block (thumbnail, plot, duration).
 * @param season              Season number (mirrors the Map key in [SeriesInfoResponse]).
 * @param added               Unix timestamp string of when the episode was added.
 * @param customSid           Internal portal SID, rarely needed.
 * @param directSource        Alternate stream URL if the portal provides one.
 */
data class EpisodeItem(
    @SerializedName("id")                  val id:                 String,
    @SerializedName("episode_num")         val episodeNum:         Int,
    @SerializedName("title")               val title:              String,
    @SerializedName("container_extension") val containerExtension: String,
    @SerializedName("info")                val info:               EpisodeInfo?,
    @SerializedName("season")             val season:             Int,
    @SerializedName("added")              val added:              String?,
    @SerializedName("custom_sid")         val customSid:          String?,
    @SerializedName("direct_source")      val directSource:       String?
)

/**
 * Rich metadata block nested inside each [EpisodeItem].
 *
 * All fields are nullable because many portals omit metadata for episodes.
 *
 * @param movieImage   Thumbnail / still frame URL for this episode.
 * @param plot         Episode synopsis.
 * @param releaseDate  Air date string in "YYYY-MM-DD" format (not always present).
 * @param rating       Rating string (e.g. "8.2").
 * @param durationSecs Total runtime in seconds — use for progress bar math.
 * @param duration     Human-readable duration string (e.g. "45:00").
 */
data class EpisodeInfo(
    @SerializedName("movie_image")   val movieImage:   String?,
    @SerializedName("plot")          val plot:         String?,
    @SerializedName("releasedate")   val releaseDate:  String?,
    @SerializedName("rating")        val rating:       String?,
    @SerializedName("duration_secs") val durationSecs: Int?,
    @SerializedName("duration")      val duration:     String?
)
