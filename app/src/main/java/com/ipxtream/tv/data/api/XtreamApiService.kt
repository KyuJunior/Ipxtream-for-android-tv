package com.ipxtream.tv.data.api

import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.model.XtreamAuthResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for the Xtream Codes `player_api.php` endpoint.
 *
 * All functions are `suspend` and must be called from a Coroutine scope.
 * Authentication parameters ([username], [password]) are passed explicitly on
 * every call — the server looks them up per-request, there is no session token.
 *
 * ## URL structure
 * Every request hits the same endpoint:
 *   `{baseUrl}/player_api.php?username=…&password=…&action=…[&extra_params]`
 *
 * ## Content type mapping
 * | Content Type | Endpoint constant   |
 * |---|---|
 * | Authenticate | (no action param)   |
 * | Live TV      | ACTION_LIVE_*       |
 * | VOD / Movies | ACTION_VOD_*        |
 * | TV Series    | ACTION_SERIES_*     |
 */
interface XtreamApiService {

    // =========================================================================
    //  Authentication
    // =========================================================================

    /**
     * Authenticates the user. Equivalent to the Phase 1 call.
     * [auth][com.ipxtream.tv.data.model.UserInfo.auth] == 1 means success.
     */
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAuthResponse

    // =========================================================================
    //  Live TV
    // =========================================================================

    /**
     * Returns all live channel categories available on the portal.
     */
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action")   action:   String = "get_live_categories"
    ): List<Category>

    /**
     * Returns all live channel streams.
     *
     * @param categoryId When provided, filters streams to this category only.
     *                   Omit (pass null) to fetch all channels across all categories.
     */
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username")    username:   String,
        @Query("password")    password:   String,
        @Query("action")      action:     String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<StreamItem>

    // =========================================================================
    //  VOD (Movies)
    // =========================================================================

    /**
     * Returns all VOD / movie categories available on the portal.
     */
    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action")   action:   String = "get_vod_categories"
    ): List<Category>

    /**
     * Returns all VOD / movie streams.
     *
     * @param categoryId Optional filter by category.
     */
    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username")    username:   String,
        @Query("password")    password:   String,
        @Query("action")      action:     String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): List<StreamItem>

    // =========================================================================
    //  Series
    // =========================================================================

    /**
     * Returns all TV series categories available on the portal.
     */
    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action")   action:   String = "get_series_categories"
    ): List<Category>

    /**
     * Returns the series index list (titles, cover art, metadata).
     * Does **not** include episode details — use [getSeriesInfo] for that.
     *
     * @param categoryId Optional filter by category.
     */
    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username")    username:   String,
        @Query("password")    password:   String,
        @Query("action")      action:     String = "get_series",
        @Query("category_id") categoryId: String? = null
    ): List<SeriesItem>

    /**
     * Returns the full season/episode detail for a single series.
     *
     * The response includes the series metadata ([SeriesInfoResponse.info])
     * and a Map of season-number → episode-list ([SeriesInfoResponse.episodesBySeason]).
     * Call [SeriesInfoResponse.toSeasons] to get a clean sorted list.
     *
     * @param seriesId [SeriesItem.seriesId] from the index list.
     */
    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username")  username: String,
        @Query("password")  password: String,
        @Query("action")    action:   String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): SeriesInfoResponse
}
