package com.ipxtream.tv.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap


/**
 * Android Foreground Service that manages all download coroutines.
 *
 * ## Communication pattern
 * The service is a **started service** (not bound). All commands arrive via
 * [startForegroundService] + Intent with an action string and extras:
 *
 * | Action               | Required extras |
 * |---|---|
 * | [ACTION_START]       | all EXTRA_* fields |
 * | [ACTION_PAUSE]       | [EXTRA_DOWNLOAD_ID] |
 * | [ACTION_RESUME]      | [EXTRA_DOWNLOAD_ID] |
 * | [ACTION_CANCEL]      | [EXTRA_DOWNLOAD_ID] |
 * | [ACTION_RETRY]       | [EXTRA_DOWNLOAD_ID] |
 *
 * Results flow back via [DownloadRepository] StateFlow — the service is a
 * write-only producer; ViewModels and Composables are read-only consumers.
 *
 * ## Concurrency
 * At most [MAX_CONCURRENT] downloads run simultaneously. Extra jobs are
 * placed in [pendingQueue] and started automatically when a slot frees up.
 *
 * ## Lifecycle
 * The service calls [startForeground] immediately in [onStartCommand] using
 * a "starting…" notification. It calls [stopSelf] when both [activeJobs] and
 * [pendingQueue] are empty and there is nothing left to do.
 *
 * ## Manifest entries required
 * ```xml
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
 * <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 *
 * <service
 *     android:name=".data.download.DownloadService"
 *     android:exported="false"
 *     android:foregroundServiceType="dataSync" />
 * ```
 */
class DownloadService : Service() {

    // ─── Coroutine scope ───────────────────────────────────────────────────────
    // SupervisorJob — one child failure doesn't cancel sibling downloads.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Active/pending state ──────────────────────────────────────────────────
    /** Map of download ID → running coroutine Job. */
    private val activeJobs  = ConcurrentHashMap<String, Job>()
    /** Queue of download IDs waiting for a concurrency slot. */
    private val pendingQueue = ArrayDeque<String>()

    // ─── HTTP engine ───────────────────────────────────────────────────────────
    private val downloader = HttpDownloader()

    // =========================================================================
    //  Service lifecycle
    // =========================================================================

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start in foreground immediately to avoid ANR (Android requires this
        // within 5 seconds of startForegroundService()).
        startForeground(NOTIFICATION_ID, buildNotification("IPXtream Downloads", "Ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> handleStart(intent)
            ACTION_PAUSE  -> handlePause(intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_STICKY)
            ACTION_RESUME -> handleResume(intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_STICKY)
            ACTION_CANCEL -> handleCancel(intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_STICKY)
            ACTION_RETRY  -> handleRetry(intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_STICKY)
        }
        return START_STICKY  // Restart service if the system kills it
    }

    override fun onBind(intent: Intent?): IBinder? = null  // Not a bound service

    override fun onDestroy() {
        serviceScope.cancel()  // Cancels all running download coroutines
        super.onDestroy()
    }

    // =========================================================================
    //  Intent handlers
    // =========================================================================

    private fun handleStart(intent: Intent) {
        val id       = intent.getStringExtra(EXTRA_DOWNLOAD_ID)  ?: return
        val url      = intent.getStringExtra(EXTRA_URL)           ?: return
        val title    = intent.getStringExtra(EXTRA_TITLE)         ?: "Unknown"
        val destPath = intent.getStringExtra(EXTRA_DEST_PATH)     ?: return
        val streamId = intent.getIntExtra(EXTRA_STREAM_ID, -1)
        val typeOrd  = intent.getIntExtra(EXTRA_CONTENT_TYPE, 0)
        val ext      = intent.getStringExtra(EXTRA_FILE_EXT)      ?: "mp4"

        val existing = DownloadRepository.get(id)
        if (existing != null && existing.isActive) return  // already running

        val item = existing ?: DownloadItem(
            id              = id,
            streamId        = streamId,
            contentType     = DownloadContentType.values()[typeOrd],
            title           = title,
            url             = url,
            destinationPath = destPath
        )

        DownloadRepository.addOrUpdate(item.copy(status = DownloadStatus.PENDING))
        enqueueOrStartJob(item)
    }

    private fun handlePause(id: String) {
        activeJobs[id]?.cancel()   // cancellation triggers partFile preservation
        activeJobs.remove(id)
        DownloadRepository.update(id) { it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L) }
        // Don't stop the service — other downloads may still be running.
        startNextFromQueue()
    }

    private fun handleResume(id: String) {
        val item = DownloadRepository.get(id) ?: return
        if (item.isActive) return  // already running
        DownloadRepository.update(id) { it.copy(status = DownloadStatus.PENDING) }
        enqueueOrStartJob(item)
    }

    private fun handleCancel(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        pendingQueue.remove(id)

        val item = DownloadRepository.get(id)
        item?.let {
            // Delete the partial file
            File(it.partFilePath).delete()
            File(it.destinationPath).delete()
        }
        DownloadRepository.remove(id)
        stopSelfIfIdle()
    }

    private fun handleRetry(id: String) {
        val item = DownloadRepository.get(id) ?: return
        if (item.isActive) return
        // Delete the broken partial file and restart from zero.
        File(item.partFilePath).delete()
        DownloadRepository.update(id) {
            it.copy(status = DownloadStatus.PENDING, downloadedBytes = 0L, errorMessage = null)
        }
        enqueueOrStartJob(item.copy(downloadedBytes = 0L))
    }

    // =========================================================================
    //  Job scheduling
    // =========================================================================

    private fun enqueueOrStartJob(item: DownloadItem) {
        if (activeJobs.size >= MAX_CONCURRENT) {
            if (!pendingQueue.contains(item.id)) pendingQueue.add(item.id)
            return
        }
        launchDownloadJob(item)
    }

    private fun startNextFromQueue() {
        val nextId   = pendingQueue.removeFirstOrNull() ?: run { stopSelfIfIdle(); return }
        val nextItem = DownloadRepository.get(nextId)  ?: run { startNextFromQueue(); return }
        launchDownloadJob(nextItem)
    }

    // =========================================================================
    //  Core download coroutine
    // =========================================================================

    private fun launchDownloadJob(item: DownloadItem) {
        val job = serviceScope.launch {
            runDownload(item)
        }
        activeJobs[item.id] = job

        // When the job finishes (for any reason), clear it and start the next queued one.
        job.invokeOnCompletion { cause ->
            activeJobs.remove(item.id)
            if (cause == null || cause is CancellationException) {
                // Normal completion or user pause — start the next queued download.
                startNextFromQueue()
            }
        }
    }

    /**
     * The main download logic for a single [DownloadItem].
     *
     * 1. Updates status to [DownloadStatus.DOWNLOADING].
     * 2. Calculates the resume byte position from the existing `.part` file.
     * 3. Calls [HttpDownloader.download] with the progress callback.
     * 4. On success: renames `.part` → final file, marks [DownloadStatus.COMPLETED].
     * 5. On failure: marks [DownloadStatus.FAILED] and stores the error message.
     * 6. On CancellationException (user pause): marks [DownloadStatus.PAUSED],
     *    leaving the `.part` file intact for resumption.
     */
    private suspend fun runDownload(item: DownloadItem) {
        val partFile  = File(item.partFilePath)
        val finalFile = File(item.destinationPath)

        // Determine resume byte position from the existing partial file.
        val startByte = when {
            item.downloadedBytes > 0L -> item.downloadedBytes
            partFile.exists()         -> partFile.length()
            else                      -> 0L
        }

        DownloadRepository.update(item.id) {
            it.copy(
                status           = DownloadStatus.DOWNLOADING,
                downloadedBytes  = startByte,
                errorMessage     = null
            )
        }

        updateNotification(item.title, "Starting…")

        var lastNotificationMs = System.currentTimeMillis()

        val result = downloader.download(
            url       = item.url,
            partFile  = partFile,
            startByte = startByte
        ) { downloaded, total, speed ->
            // Progress callback — update the repository
            DownloadRepository.update(item.id) { current ->
                current.copy(
                    downloadedBytes  = downloaded,
                    totalBytes       = if (total > 0L) total else current.totalBytes,
                    speedBytesPerSec = speed
                )
            }

            // Throttle notification updates to 2 s
            val now = System.currentTimeMillis()
            if (now - lastNotificationMs > 2_000L) {
                val pct = if (total > 0L) ((downloaded * 100L) / total).toInt() else -1
                val speedStr = formatSpeed(speed)
                updateNotification(item.title, if (pct >= 0) "$pct% · $speedStr" else speedStr)
                lastNotificationMs = now
            }
        }

        // ── Handle result ──────────────────────────────────────────────────────
        when (result) {
            is HttpDownloader.DownloadResult.Success -> {
                // Rename .part → final file
                if (partFile.exists()) {
                    finalFile.parentFile?.mkdirs()
                    partFile.renameTo(finalFile)
                }
                DownloadRepository.update(item.id) {
                    it.copy(
                        status           = DownloadStatus.COMPLETED,
                        speedBytesPerSec = 0L,
                        downloadedBytes  = result.totalBytesWritten,
                        totalBytes       = result.totalBytesWritten
                    )
                }
                updateNotification(item.title, "Complete ✓")
            }

            is HttpDownloader.DownloadResult.Error -> {
                if (result.isRangeUnsupported) {
                    // Server doesn't support Range — delete partial and let the
                    // service auto-retry from zero via a recursive call.
                    partFile.delete()
                    DownloadRepository.update(item.id) { it.copy(downloadedBytes = 0L) }
                    runDownload(item.copy(downloadedBytes = 0L))
                } else {
                    DownloadRepository.update(item.id) {
                        it.copy(
                            status           = DownloadStatus.FAILED,
                            errorMessage     = result.message,
                            speedBytesPerSec = 0L
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    //  Notification helpers
    // =========================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IPXtream Downloads",
                NotificationManager.IMPORTANCE_LOW   // No sound or pop-up
            ).apply {
                description = "Shows active download progress"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String, progress: Int = -1) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (progress >= 0) setProgress(100, progress, false)
                else               setProgress(0, 0, true)   // indeterminate
            }
            .build()

    private fun updateNotification(title: String, text: String, progress: Int = -1) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, text, progress))
    }

    // =========================================================================
    //  Utilities
    // =========================================================================

    private fun stopSelfIfIdle() {
        if (activeJobs.isEmpty() && pendingQueue.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun formatSpeed(bps: Long): String = when {
        bps <= 0    -> "—"
        bps < 1_024 -> "$bps B/s"
        bps < 1_048_576  -> "${"%.1f".format(bps / 1_024.0)} KB/s"
        else             -> "${"%.1f".format(bps / 1_048_576.0)} MB/s"
    }

    // =========================================================================
    //  Constants
    // =========================================================================

    companion object {
        const val ACTION_START  = "com.ipxtream.tv.download.START"
        const val ACTION_PAUSE  = "com.ipxtream.tv.download.PAUSE"
        const val ACTION_RESUME = "com.ipxtream.tv.download.RESUME"
        const val ACTION_CANCEL = "com.ipxtream.tv.download.CANCEL"
        const val ACTION_RETRY  = "com.ipxtream.tv.download.RETRY"

        const val EXTRA_DOWNLOAD_ID   = "extra_download_id"
        const val EXTRA_URL           = "extra_url"
        const val EXTRA_TITLE         = "extra_title"
        const val EXTRA_DEST_PATH     = "extra_dest_path"
        const val EXTRA_STREAM_ID     = "extra_stream_id"
        const val EXTRA_CONTENT_TYPE  = "extra_content_type"
        const val EXTRA_FILE_EXT      = "extra_file_ext"

        private const val CHANNEL_ID      = "ipxtream_downloads"
        private const val NOTIFICATION_ID = 7001
        private const val MAX_CONCURRENT  = 2
    }
}
