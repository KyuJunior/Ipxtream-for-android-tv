package com.ipxtream.tv.data.model

/**
 * A parsed, UI-ready grouping of episodes for a single season.
 *
 * This is **not** a direct API model — it is produced by
 * [SeriesInfoResponse.toSeasons] which groups and sorts the raw
 * `Map<String, List<EpisodeItem>>` returned by the Xtream Codes API.
 *
 * @param seasonNumber  1-based season number, derived from the Map key.
 * @param episodes      Episodes within this season, sorted by [EpisodeItem.episodeNum].
 */
data class SeasonItem(
    val seasonNumber: Int,
    val episodes:     List<EpisodeItem>
) {
    /** Convenience display label for tab headers / spinner entries. */
    val displayTitle: String get() = "Season $seasonNumber"

    /** Total number of episodes in this season. */
    val episodeCount: Int get() = episodes.size
}
