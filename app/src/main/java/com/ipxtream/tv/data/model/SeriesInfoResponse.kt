package com.ipxtream.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Full series detail response from:
 *   player_api.php?action=get_series_info&series_id={id}
 *
 * The [episodesBySeason] map uses the **season number as a String key**
 * (e.g. "1", "2", "3") — this is the raw Xtream Codes format.
 *
 * Use [toSeasons] to get an ordered, UI-ready [List<SeasonItem>] instead
 * of working with the raw map directly.
 *
 * @param info             Series-level metadata (reuses [SeriesItem] structure).
 * @param episodesBySeason Raw map from season-number-string → episode list.
 */
data class SeriesInfoResponse(
    @SerializedName("info")     val info:             SeriesItem?,
    @SerializedName("episodes") val episodesBySeason: Map<String, List<EpisodeItem>>?
) {
    /**
     * Converts the raw `Map<String, List<EpisodeItem>>` from the API into an
     * ordered [List<SeasonItem>] suitable for direct use in a RecyclerView or
     * tab layout.
     *
     * Parsing rules applied:
     * 1. Keys that cannot be parsed as integers are silently skipped.
     * 2. Episodes within each season are sorted by [EpisodeItem.episodeNum].
     * 3. Seasons are sorted ascending by [SeasonItem.seasonNumber].
     * 4. Returns an empty list if [episodesBySeason] is null (server returned no episodes).
     *
     * Example (raw API):
     * ```json
     * { "1": [ { "episode_num": 2 ... }, { "episode_num": 1 ... } ], "2": [...] }
     * ```
     * Becomes:
     * ```
     * [ SeasonItem(1, [ep1, ep2]), SeasonItem(2, [...]) ]
     * ```
     */
    fun toSeasons(): List<SeasonItem> {
        return episodesBySeason
            ?.entries
            ?.mapNotNull { (key, episodes) ->
                val seasonNumber = key.toIntOrNull() ?: return@mapNotNull null
                SeasonItem(
                    seasonNumber = seasonNumber,
                    episodes     = episodes.sortedBy { it.episodeNum }
                )
            }
            ?.sortedBy { it.seasonNumber }
            ?: emptyList()
    }

    /**
     * Flattens all seasons into a single episode list ordered by season then
     * episode number. Useful for "continue watching" logic.
     */
    fun allEpisodes(): List<EpisodeItem> = toSeasons().flatMap { it.episodes }

    /**
     * Finds the [EpisodeItem] matching the given [episodeId] across all seasons.
     * Returns null if not found.
     */
    fun findEpisode(episodeId: String): EpisodeItem? =
        allEpisodes().firstOrNull { it.id == episodeId }
}
