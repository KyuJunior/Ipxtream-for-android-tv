package com.ipxtream.tv.data.download

import java.io.File

// ─── Content type ─────────────────────────────────────────────────────────────

enum class DownloadContentType { VOD, SERIES_EPISODE }

// ─── Status ───────────────────────────────────────────────────────────────────

/**
 * Lifecycle states a [DownloadItem] passes through.
 *
 * State transitions:
 * ```
 * PENDING ──► DOWNLOADING ──► COMPLETED
 *    ▲             │
 *    │             ▼
 *    └──────── PAUSED ◄──────► DOWNLOADING (resume)
 *                  │
 *                  ▼
 *               FAILED ──► DOWNLOADING (retry)
 * ```
 */
enum class DownloadStatus {
    /** In the queue but not yet started (concurrency limit reached). */
    PENDING,
    /** Bytes are actively flowing from the server. */
    DOWNLOADING,
    /** User-initiated pause. The partial `.part` file is preserved on disk. */
    PAUSED,
    /** File download is 100% complete and has been renamed to its final name. */
    COMPLETED,
    /** A fatal I/O or HTTP error occurred. User can retry. */
    FAILED
}

// ─── Download spec ────────────────────────────────────────────────────────────

/**
 * Immutable specification for a new download job.
 * Passed to [DownloadController.enqueue] to queue a new job.
 */
data class DownloadSpec(
    val downloadId:    String = java.util.UUID.randomUUID().toString(),
    val streamId:      Int,
    val contentType:   DownloadContentType,
    val title:         String,
    val url:           String,
    val fileExtension: String,
    val destinationDir: File
)

// ─── Download item (live state) ───────────────────────────────────────────────

/**
 * Mutable snapshot of a single download job.
 *
 * Emitted via [DownloadRepository.downloads] StateFlow. The UI observes
 * this to drive the [com.ipxtream.tv.ui.dashboard.components.DownloadTray].
 *
 * @param id               UUID identifying this job.
 * @param streamId         Source stream or episode ID (for deduplication).
 * @param contentType      VOD or Series episode.
 * @param title            Human-readable title shown in the tray.
 * @param url              Full HTTPS download URL (built by [StreamUrlBuilder]).
 * @param destinationPath  Absolute path to the final file (`.mkv`, `.mp4`, etc.).
 * @param totalBytes       Content-Length from the HTTP response; 0 = unknown.
 * @param downloadedBytes  Bytes successfully written to disk (used for Range resume).
 * @param status           Current [DownloadStatus].
 * @param speedBytesPerSec Transfer rate measured over the last second, or 0 when idle.
 * @param errorMessage     Non-null only when [status] == [DownloadStatus.FAILED].
 * @param addedAt          Epoch-ms timestamp when the job was enqueued.
 */
data class DownloadItem(
    val id:               String,
    val streamId:         Int,
    val contentType:      DownloadContentType,
    val title:            String,
    val url:              String,
    val destinationPath:  String,
    val totalBytes:       Long           = 0L,
    val downloadedBytes:  Long           = 0L,
    val status:           DownloadStatus = DownloadStatus.PENDING,
    val speedBytesPerSec: Long           = 0L,
    val errorMessage:     String?        = null,
    val addedAt:          Long           = System.currentTimeMillis()
) {
    // ── Derived properties used directly in the UI ───────────────────────────

    /** 0.0–1.0 completion fraction. 0 when [totalBytes] is unknown. */
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                else 0f

    /** 0–100 integer percentage for the notification progress bar. */
    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    /** Estimated seconds remaining. -1 when speed is 0 or size is unknown. */
    val etaSeconds: Long
        get() {
            if (speedBytesPerSec <= 0L || totalBytes <= 0L) return -1L
            val remaining = totalBytes - downloadedBytes
            return remaining / speedBytesPerSec
        }

    /** The `.part` extension is used while downloading; renamed on completion. */
    val partFilePath: String
        get() = "$destinationPath.part"

    val isActive:    Boolean get() = status == DownloadStatus.DOWNLOADING
    val isPaused:    Boolean get() = status == DownloadStatus.PAUSED
    val isFinished:  Boolean get() = status == DownloadStatus.COMPLETED
    val hasFailed:   Boolean get() = status == DownloadStatus.FAILED
    val canResume:   Boolean get() = isPaused || hasFailed

    companion object {
        /** Creates a [DownloadItem] from a [DownloadSpec]. */
        fun fromSpec(spec: DownloadSpec): DownloadItem {
            val safeName = spec.title
                .replace(Regex("[^a-zA-Z0-9 ._-]"), "")
                .trim()
                .replace(" ", "_")
                .take(120)
            val destPath = "${spec.destinationDir.absolutePath}/$safeName.${spec.fileExtension}"
            return DownloadItem(
                id              = spec.downloadId,
                streamId        = spec.streamId,
                contentType     = spec.contentType,
                title           = spec.title,
                url             = spec.url,
                destinationPath = destPath
            )
        }
    }
}
