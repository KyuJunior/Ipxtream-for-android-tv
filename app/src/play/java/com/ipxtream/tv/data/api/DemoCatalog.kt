package com.ipxtream.tv.data.api

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Play build: "Try the demo" on the sign-in screen (docs/play-store-plan.md, decision D4), so Google's
 * reviewers can use the app without an IPTV account. The demo "server" ([SERVER], a reserved `.invalid`
 * address that never resolves) is answered inside the app by [interceptor]; its streams are openly
 * licensed films and public test streams ([streamUrl]). Nothing here names a real provider.
 */
object DemoCatalog {

    const val SERVER = "http://demo.invalid/"
    const val USERNAME = "demo"
    const val PASSWORD = "demo"
    private const val HOST = "demo.invalid"

    /** stream id → the real HLS address. */
    private val STREAMS = mapOf(
        1 to "https://demo.unified-streaming.com/k8s/live/stable/live.isml/.m3u8",
        2 to "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
        101 to "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        102 to "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
    )

    fun isDemo(server: String): Boolean = ApiClient.normalizeUrl(server).equals(SERVER, ignoreCase = true)

    /** The real address of a demo stream, or null for any other server. */
    fun streamUrl(server: String, streamId: Int): String? = if (isDemo(server)) STREAMS[streamId] else null

    /** Answers `player_api.php` for [HOST] from the built-in catalogue; other hosts pass through. */
    val interceptor: Interceptor? = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != HOST) return@Interceptor chain.proceed(request)
        val body = when (request.url.queryParameter("action")) {
            null                    -> AUTH
            "get_live_categories"   -> """[{"category_id":"1","category_name":"Demo live (test streams)","parent_id":0}]"""
            "get_live_streams"      -> LIVE
            "get_vod_categories"    -> """[{"category_id":"2","category_name":"Open movies (CC BY)","parent_id":0}]"""
            "get_vod_streams"       -> MOVIES
            "get_vod_info"          -> vodInfo(request.url.queryParameter("vod_id")?.toIntOrNull())
            "get_series_info"       -> "{}"
            else                    -> "[]"
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private const val AUTH = """{"user_info":{"username":"demo","password":"demo","message":"Demo","auth":1,
        "status":"Active","exp_date":null,"is_trial":"1","active_cons":"0","created_at":"0","max_connections":"1",
        "allowed_output_formats":["m3u8"]},"server_info":{"url":"demo.invalid","port":"80","https_port":"",
        "server_protocol":"http","rtmp_port":"","timezone":"UTC","timestamp_now":0,"time_now":""}}"""

    private const val LIVE = """[
        {"num":1,"name":"Demo Live 1 (test pattern)","stream_type":"live","stream_id":1,"stream_icon":"",
         "epg_channel_id":null,"added":"1780000000","category_id":"1","custom_sid":"","tv_archive":0,
         "direct_source":"","tv_archive_duration":0},
        {"num":2,"name":"Demo Live 2 (test stream)","stream_type":"live","stream_id":2,"stream_icon":"",
         "epg_channel_id":null,"added":"1780000000","category_id":"1","custom_sid":"","tv_archive":0,
         "direct_source":"","tv_archive_duration":0}]"""

    private const val MOVIES = """[
        {"num":1,"name":"Big Buck Bunny (2008)","stream_type":"movie","stream_id":101,"stream_icon":"",
         "rating":"7.0","rating_5based":3.5,"added":"1780000100","category_id":"2","container_extension":"m3u8",
         "custom_sid":"","direct_source":""},
        {"num":2,"name":"Tears of Steel (2012)","stream_type":"movie","stream_id":102,"stream_icon":"",
         "rating":"6.5","rating_5based":3.3,"added":"1780000200","category_id":"2","container_extension":"m3u8",
         "custom_sid":"","direct_source":""}]"""

    /** Plot text carries the credit the films' CC BY 3.0 licence asks for. */
    private fun vodInfo(id: Int?): String = when (id) {
        101 -> """{"info":{"name":"Big Buck Bunny","releasedate":"2008-05-30","duration_secs":596,"genre":"Animation",
            "plot":"Open movie by the Blender Foundation (peach.blender.org), licensed CC BY 3.0. Shown in demo mode only."},
            "movie_data":{"stream_id":101,"name":"Big Buck Bunny (2008)","category_id":"2","container_extension":"m3u8"}}"""
        102 -> """{"info":{"name":"Tears of Steel","releasedate":"2012-09-26","duration_secs":734,"genre":"Science fiction",
            "plot":"Open movie by the Blender Foundation (mango.blender.org), licensed CC BY 3.0. Shown in demo mode only."},
            "movie_data":{"stream_id":102,"name":"Tears of Steel (2012)","category_id":"2","container_extension":"m3u8"}}"""
        else -> """{"info":null,"movie_data":null}"""
    }
}
