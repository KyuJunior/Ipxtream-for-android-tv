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
 * Always use `.m3u8` for Live — the server may also serve `.ts` but HLS
 * gives better buffering and adaptive bitrate support via ExoPlayer's
 * `HlsMediaSource`.
 *
 * For VOD and Series, the `containerExtension` from the API determines the
 * format. ExoPlayer's `DefaultMediaSourceFactory` auto-detects the source
 * type from the URL extension, so no explicit `MediaSource` override is needed
 * for common formats (mp4, mkv, avi, m3u8, mpd).
 */
object StreamUrlBuilder {

    /**
     * Builds the correct playback URL for a [StreamItem] (Live or VOD).
     */
    fun buildForStream(credentials: AuthCredentials, stream: StreamItem): String {
        val base = credentials.server.trimEnd('/')
        val u    = credentials.username
        val p    = credentials.password
        return when {
            stream.isLive  -> "$base/$u/$p/${stream.streamId}.m3u8"
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
        val u    = credentials.username
        val p    = credentials.password
        return "$base/series/$u/$p/${episode.id}.${episode.containerExtension}"
    }
}
