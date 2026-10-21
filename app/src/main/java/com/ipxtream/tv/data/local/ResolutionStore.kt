package com.ipxtream.tv.data.local

import android.content.Context
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * A title's real video stream size, as measured from the file itself
 * (the portal's ffprobe data, or the container header when that's missing).
 *
 * [PANEL_EMPTY] / [FILE_FAILED] record titles that were checked but couldn't be measured.
 */
data class VideoRes(
    val width:  Int,
    val height: Int,
    val hdr:    Boolean = false,
    val codec:  String? = null
) {
    val isKnown: Boolean get() = width > 0 && height > 0

    /** Scope/flat 4K masters are often 3840×1600, so width counts as much as height. */
    val is4K: Boolean get() = width >= 3200 || height >= 2000

    val label: String? get() = when {
        !isKnown                          -> null
        is4K                              -> "4K"
        width >= 1800 || height >= 1000   -> "1080p"
        width >= 1200 || height >= 700    -> "720p"
        else                              -> "SD"
    }

    companion object {
        /** Portal has no probe data for this title (the file itself wasn't read). */
        val PANEL_EMPTY = VideoRes(0, 0)
        /** Neither the portal nor the file header gave a size. */
        val FILE_FAILED = VideoRes(-1, -1)

        /**
         * Reads the `video` block Xtream panels attach to vod/episode info.
         * Returns null when the portal has no probe data (often an empty array).
         */
        fun fromPanelJson(video: JsonElement?): VideoRes? {
            val obj = (video as? JsonObject) ?: return null
            // MKVs often carry the poster as an "attached picture" stream; that's not the video.
            val codecName = obj.get("codec_name")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase()
            if (codecName in IMAGE_CODECS) return null
            val attachedPic = (obj.get("disposition") as? JsonObject)?.intOrNull("attached_pic") == 1
            if (attachedPic) return null
            val w = obj.intOrNull("width") ?: obj.intOrNull("coded_width") ?: return null
            val h = obj.intOrNull("height") ?: obj.intOrNull("coded_height") ?: return null
            if (w <= 0 || h <= 0) return null
            val transfer = obj.get("color_transfer")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val hdr = transfer == "smpte2084" || transfer == "arib-std-b67"
            val codec = obj.get("codec_name")?.takeIf { it.isJsonPrimitive }?.asString
            return VideoRes(w, h, hdr, codec)
        }

        val IMAGE_CODECS = setOf("png", "mjpeg", "jpeg", "jpg", "bmp", "gif", "webp")

        private fun JsonObject.intOrNull(key: String): Int? =
            get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
    }
}

/**
 * Persistent map of measured resolutions per account ("m123" movies, "s456" series).
 * Measuring is slow and talks to the portal, so every result is kept and a full
 * library scan only ever has to run once; new uploads are picked up incrementally.
 */
class ResolutionStore(context: Context, accountKey: String) {

    private val file = File(context.filesDir, "resolutions_${accountKey.md5()}.tsv")
    private val map = ConcurrentHashMap<String, VideoRes>()
    @Volatile private var dirty = false

    init {
        runCatching {
            if (file.exists()) {
                file.forEachLine { line ->
                    val p = line.split('\t')
                    // Entries measured from a cover image (older builds) are dropped so they get re-measured.
                    if (p.size >= 3 && p.getOrNull(4)?.lowercase() !in VideoRes.IMAGE_CODECS) {
                        map[p[0]] = VideoRes(
                            width  = p[1].toIntOrNull() ?: 0,
                            height = p[2].toIntOrNull() ?: 0,
                            hdr    = p.getOrNull(3) == "1",
                            codec  = p.getOrNull(4)?.ifBlank { null }
                        )
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Could not read resolution cache", it) }
    }

    fun movie(streamId: Int): VideoRes? = map["m$streamId"]
    fun series(seriesId: Int): VideoRes? = map["s$seriesId"]

    fun putMovie(streamId: Int, res: VideoRes) { map["m$streamId"] = res; dirty = true }
    fun putSeries(seriesId: Int, res: VideoRes) { map["s$seriesId"] = res; dirty = true }

    @Synchronized
    fun flush() {
        if (!dirty) return
        dirty = false
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                map.forEach { (k, v) ->
                    w.append(k).append('\t').append(v.width.toString()).append('\t').append(v.height.toString())
                        .append('\t').append(if (v.hdr) "1" else "0").append('\t').append(v.codec.orEmpty()).append('\n')
                }
            }
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "Could not write resolution cache", it) }
    }

    private companion object {
        const val TAG = "ResolutionStore"
        fun String.md5(): String =
            MessageDigest.getInstance("MD5").digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
