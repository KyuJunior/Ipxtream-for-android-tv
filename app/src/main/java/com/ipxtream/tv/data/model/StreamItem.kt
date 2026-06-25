package com.ipxtream.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Unified content item for **Live TV** and **VOD** (Movie) streams.
 *
 * Both content types share the same `player_api.php` JSON shape with minor
 * differences, so a single model avoids code duplication. Fields that only
 * apply to one type are marked with a comment and will be `null` for the other.
 *
 * Live stream URL pattern:
 *   {server}/{username}/{password}/{streamId}.{outputFormat}
 *
 * VOD stream URL pattern:
 *   {server}/movie/{username}/{password}/{streamId}.{containerExtension}
 *
 * @param streamId             Unique numeric identifier.
 * @param name                 Display name of the channel or movie.
 * @param streamType           "live" | "movie" — use to branch playback logic.
 * @param streamIcon           Poster/logo URL. May be empty or malformed.
 * @param categoryId           Parent category identifier for filtering.
 * @param added                Unix timestamp string of when it was added to the portal.
 * @param rating               String rating value (e.g. "7.5") — VOD only.
 * @param rating5based         Rating normalised to 0-5 scale — VOD only.
 * @param containerExtension   File extension for VOD files, e.g. "mkv" — VOD only.
 * @param epgChannelId         EPG identifier for Electronic Programme Guide — Live only.
 * @param tvArchive            1 if catch-up/time-shift is enabled — Live only.
 * @param tvArchiveDuration    Catch-up window in days — Live only.
 * @param directSource         Alternate direct stream URL if provided by the portal.
 * @param customSid            Internal portal SID, rarely needed.
 * @param num                  Ordinal position in the category list.
 */
data class StreamItem(
    @SerializedName("stream_id")             val streamId:            Int,
    @SerializedName("name")                  val name:                String,
    @SerializedName("stream_type")           val streamType:          String?,
    @SerializedName("stream_icon")           val streamIcon:          String?,
    @SerializedName("category_id")           val categoryId:          String?,
    @SerializedName("added")                 val added:               String?,
    @SerializedName("num")                   val num:                 Int?,

    // ── VOD-specific ─────────────────────────────────────────────────────────
    @SerializedName("rating")                val rating:              String?,
    @SerializedName("rating_5based")         val rating5based:        Double?,
    @SerializedName("container_extension")   val containerExtension:  String?,

    // ── Live-specific ─────────────────────────────────────────────────────────
    @SerializedName("epg_channel_id")        val epgChannelId:        String?,
    @SerializedName("tv_archive")            val tvArchive:           Int?,
    @SerializedName("tv_archive_duration")   val tvArchiveDuration:   Int?,

    // ── Common optional ───────────────────────────────────────────────────────
    @SerializedName("direct_source")         val directSource:        String?,
    @SerializedName("custom_sid")            val customSid:           String?
) {
    /** True when [tvArchive] == 1 (catch-up available). */
    val hasCatchUp: Boolean get() = tvArchive == 1

    /** True when this is a live channel. */
    val isLive: Boolean get() = streamType == "live"

    /** True when this is a VOD movie. */
    val isMovie: Boolean get() = streamType == "movie"
}
