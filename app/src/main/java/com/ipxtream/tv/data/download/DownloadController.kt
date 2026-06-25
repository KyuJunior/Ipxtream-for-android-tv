package com.ipxtream.tv.data.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment

/**
 * Thin facade that builds and sends Intents to [DownloadService].
 *
 * ViewModels and Composables call methods here instead of building
 * Intents manually, keeping all service API knowledge in one place.
 *
 * All methods are safe to call from any thread or coroutine.
 */
object DownloadController {

    /**
     * Enqueues a new download from a [DownloadSpec].
     *
     * If a download with the same [DownloadSpec.downloadId] is already
     * active or pending, [DownloadService] will silently ignore the request.
     *
     * @param context Any context (Application, Activity, or Service).
     * @param spec    Full [DownloadSpec] describing the item to download.
     */
    fun enqueue(context: Context, spec: DownloadSpec) {
        val item = DownloadItem.fromSpec(spec)
        // Register in the repository immediately so the tray shows it
        // even before the service has processed the intent.
        DownloadRepository.addOrUpdate(item)

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID,  spec.downloadId)
            putExtra(DownloadService.EXTRA_URL,          spec.url)
            putExtra(DownloadService.EXTRA_TITLE,        spec.title)
            putExtra(DownloadService.EXTRA_DEST_PATH,    item.destinationPath)
            putExtra(DownloadService.EXTRA_STREAM_ID,    spec.streamId)
            putExtra(DownloadService.EXTRA_CONTENT_TYPE, spec.contentType.ordinal)
            putExtra(DownloadService.EXTRA_FILE_EXT,     spec.fileExtension)
        }
        startService(context, intent)
    }

    /**
     * Pauses the active download with [downloadId].
     * The partial `.part` file is preserved for resumption.
     */
    fun pause(context: Context, downloadId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.startService(intent)
    }

    /**
     * Resumes a paused or failed download.
     * The service reads [DownloadItem.downloadedBytes] from [DownloadRepository]
     * to construct the correct `Range: bytes=N-` header.
     */
    fun resume(context: Context, downloadId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        startService(context, intent)
    }

    /**
     * Cancels the download and **deletes** the partial file from disk.
     * The item is removed from [DownloadRepository].
     */
    fun cancel(context: Context, downloadId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.startService(intent)
    }

    /**
     * Retries a failed download from the beginning.
     * The broken partial file is deleted before restarting.
     */
    fun retry(context: Context, downloadId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RETRY
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        startService(context, intent)
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    /**
     * Sends [intent] using [Context.startForegroundService] on API 26+
     * or [Context.startService] on lower API levels.
     */
    private fun startService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Builds a [DownloadSpec] for a Live/VOD [StreamItem].
     * Convenience method to avoid boilerplate at the call site.
     */
    fun buildSpec(
        context:     Context,
        streamId:    Int,
        title:       String,
        url:         String,
        extension:   String,
        contentType: DownloadContentType = DownloadContentType.VOD
    ): DownloadSpec {
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir  // Fallback to internal storage if external unavailable
        return DownloadSpec(
            streamId       = streamId,
            contentType    = contentType,
            title          = title,
            url            = url,
            fileExtension  = extension,
            destinationDir = destDir
        )
    }
}
