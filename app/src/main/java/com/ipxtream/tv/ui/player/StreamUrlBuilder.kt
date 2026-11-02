package com.ipxtream.tv.ui.player

import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.StreamItem

/**
 * Constructs playback URLs for all three Xtream Codes content types.
 *
 * ## URL patterns (Xtream Codes spec)
 * | Type    | Pattern |
 * |---|---|
 * | Live    | `{server}/{user}/{pass}/{streamId}.m3u8` |
 * | VOD     | `{server}/movie/{user}/{pass}/{streamId}.{ext}` |
 * | Episode | `{server}/series/{user}/{pass}/{episodeId}.{ext}` |
 *
 * Live asks for `.m3u8` first (better buffering via `HlsMediaSource`); the player falls back to
 * [toTransportStream] when the provider or account doesn't serve HLS.
 *
 * For VOD and Series, the `containerExtension` from the API determines the
 * format. ExoPlayer's `DefaultMediaSourceFactory` auto-detects the source
 * type from the URL extension, so no explicit `MediaSource` override is needed
 * for common formats (mp4, mkv, avi, m3u8, mpd).
 *
 * Username and password are path segments, so they are percent-encoded: a `#`, `?`, `/` or
 * space in either would otherwise break every stream while sign-in (query parameters) works.
 */
object StreamUrlBuilder {

    /**
     * Builds the correct playback URL for a [StreamItem] (Live or VOD).
     */
    fun buildForStream(credentials: AuthCredentials, stream: StreamItem): String {
        com.ipxtream.tv.data.api.DemoCatalog.streamUrl(credentials.server, stream.streamId)?.let { return it }
        val base = credentials.server.trimEnd('/')
        val u    = segment(credentials.username)
        val p    = segment(credentials.password)
        return when {
            stream.isMovie -> {
                val ext = stream.containerExtension?.ifBlank { null } ?: "mp4"
                "$base/movie/$u/$p/${stream.streamId}.$ext"
            }
            else           -> "$base/$u/$p/${stream.streamId}.m3u8"
        }
    }

    /**
     * Builds the playback URL for a specific [EpisodeItem] in a Series.
     */
    fun buildForEpisode(credentials: AuthCredentials, episode: EpisodeItem): String {
        val base = credentials.server.trimEnd('/')
        val ext  = episode.containerExtension?.ifBlank { null } ?: "mp4"
        return "$base/series/${segment(credentials.username)}/${segment(credentials.password)}/${episode.id}.$ext"
    }

    /** The MPEG-TS form of a live HLS address, or null when [url] isn't one. */
    fun toTransportStream(url: String): String? =
        if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else null

    private fun segment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
