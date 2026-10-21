package com.ipxtream.tv.data.repository

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import com.ipxtream.tv.data.local.ResolutionStore
import com.ipxtream.tv.data.local.VideoRes
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Measures the real resolution of movies and series.
 *
 * 1. The portal's own ffprobe data (`info.video` from get_vod_info / get_series_info).
 *    Cheap JSON, no stream connection.
 * 2. When the portal has none, the file's container header is read with Media3's
 *    [MetadataRetriever]. That opens a real stream connection, so it is only used when
 *    asked for and never more than one at a time (many IPTV plans allow one connection).
 *
 * Results land in [ResolutionStore] so nothing is measured twice.
 */
class ResolutionScanner(
    private val context:    Context,
    private val repository: ContentRepository,
    val store:              ResolutionStore
) {
    private val fileProbeLock = Mutex()

    /** True when [res] still needs work under the given probing policy. */
    fun needsCheck(res: VideoRes?, allowFileProbe: Boolean): Boolean = when {
        res == null                -> true
        res.isKnown                -> false
        res == VideoRes.PANEL_EMPTY -> allowFileProbe
        else                       -> false   // file probe already failed
    }

    /** @return the measured resolution, or null if the portal couldn't be reached (retry later). */
    suspend fun measureMovie(stream: StreamItem, allowFileProbe: Boolean): VideoRes? {
        val cached = store.movie(stream.streamId)
        val res = if (cached == VideoRes.PANEL_EMPTY) {
            if (!allowFileProbe) return cached
            probeFile(repository.movieUrl(stream)) ?: VideoRes.FILE_FAILED
        } else {
            val info = repository.fetchVodInfoUncached(stream.streamId).getOrElse { return null }
            VideoRes.fromPanelJson(info.info?.video)
                ?: if (allowFileProbe) probeFile(repository.movieUrl(stream)) ?: VideoRes.FILE_FAILED
                   else VideoRes.PANEL_EMPTY
        }
        store.putMovie(stream.streamId, res)
        return res
    }

    /** Series are judged by their first episode — portals encode a show at one quality. */
    suspend fun measureSeries(series: SeriesItem, allowFileProbe: Boolean): VideoRes? {
        val info = repository.fetchSeriesInfoUncached(series.seriesId).getOrElse { return null }
        val first = info.allEpisodes().firstOrNull() ?: run {
            store.putSeries(series.seriesId, VideoRes.FILE_FAILED)
            return VideoRes.FILE_FAILED
        }
        val res = VideoRes.fromPanelJson(first.info?.video)
            ?: if (allowFileProbe) probeFile(repository.episodeUrl(first)) ?: VideoRes.FILE_FAILED
               else VideoRes.PANEL_EMPTY
        store.putSeries(series.seriesId, res)
        return res
    }

    /** Reads only the container header (moov / EBML) — no decoding, no full download. */
    @OptIn(UnstableApi::class)
    private suspend fun probeFile(url: String): VideoRes? = fileProbeLock.withLock {
        withContext(Dispatchers.IO) {
            val future = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(url))
            try {
                val groups = future.get(25, TimeUnit.SECONDS)
                for (g in 0 until groups.length) {
                    val group = groups[g]
                    for (t in 0 until group.length) {
                        val f = group.getFormat(t)
                        if (MimeTypes.isVideo(f.sampleMimeType) && f.width > 0 && f.height > 0) {
                            val transfer = f.colorInfo?.colorTransfer
                            return@withContext VideoRes(
                                width  = f.width,
                                height = f.height,
                                hdr    = transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG,
                                codec  = f.sampleMimeType?.substringAfter('/')
                            )
                        }
                    }
                }
                null
            } catch (e: Exception) {
                future.cancel(true)
                Log.d("ResolutionScanner", "File probe failed: ${e.javaClass.simpleName}")
                null
            }
        }
    }
}
