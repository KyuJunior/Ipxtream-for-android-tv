package com.ipxtream.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * A single content category as returned by the Xtream Codes category endpoints:
 *  - player_api.php?action=get_live_categories
 *  - player_api.php?action=get_vod_categories
 *  - player_api.php?action=get_series_categories
 *
 * The [categoryId] is always a String in the API JSON even though it looks
 * numeric — do not cast it to Int without a null-safe conversion.
 *
 * @param categoryId   Unique identifier used in stream/series filter queries.
 * @param categoryName Human-readable label, safe to display directly in UI.
 * @param parentId     Non-zero when this is a sub-category. Usually 0.
 */
data class Category(
    @SerializedName("category_id")   val categoryId:   String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id")     val parentId:     Int = 0
)
