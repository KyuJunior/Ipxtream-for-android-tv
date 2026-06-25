package com.ipxtream.tv.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Invoked every [PROGRESS_INTERVAL_MS] ms during the download.
 *
 * @param downloadedBytes Total bytes written to the `.part` file so far
 *                        (including bytes from previous sessions if resuming).
 * @param totalBytes      Full content length in bytes, or 0 if not known.
 * @param speedBytesPerSec  Bytes per second over the last measurement window.
 */
typealias ProgressCallback = suspend (
    downloadedBytes:  Long,
    totalBytes:       Long,
    speedBytesPerSec: Long
) -> Unit

/**
 * Core download engine.
 *
 * Wraps OkHttp to perform resumable binary HTTP downloads using the
 * standard `Range: bytes={start}-` request header.
 *
 * ## Resume protocol
 * If [startByte] > 0, the request includes:
 * ```
 * Range: bytes=12345678-
 * ```
 * A compliant server responds with **HTTP 206 Partial Content** and a body
 * containing only the requested byte range. The engine opens the destination
 * file in **append mode** and writes the partial body to the end of the
 * existing data.
 *
 * If the server does NOT support Range (responds 200 instead of 206), the
 * engine clears the partial file and restarts from byte 0, so the download
 * is still correct (just not resumed).
 *
 * ## Cancellation
 * The coroutine that calls [download] can be cancelled at any time.
 * [ensureActive] is called before every buffer read, so cancellation takes
 * effect within one buffer-read cycle (at most ~16 ms for a 16 KB buffer at
 * a 1 GB/s local connection). The partial `.part` file is preserved on disk
 * for future resumption.
 *
 * ## Speed measurement
 * Speed is computed over a rolling 1-second window:
 * ```
 * speed = bytesInLastWindow / elapsedMs × 1000
 * ```
 * The callback is invoked at most once per [PROGRESS_INTERVAL_MS] (500 ms)
 * to avoid overwhelming the StateFlow with updates.
 *
 * @param client An OkHttpClient configured with **no read timeout**
 *               (`readTimeout(0, SECONDS)`). The default read timeout would
 *               break large VOD downloads. See [buildDownloadClient].
 */
class HttpDownloader(private val client: OkHttpClient = buildDownloadClient()) {

    // ─── Public result type ────────────────────────────────────────────────────

    sealed class DownloadResult {
        data class Success(val totalBytesWritten: Long) : DownloadResult()
        data class Error(val message: String, val isRangeUnsupported: Boolean = false) : DownloadResult()
    }

    // =========================================================================
    //  Core download function
    // =========================================================================

    /**
     * Downloads [url] to [partFile], starting from byte [startByte].
     *
     * This is a **suspending** function that runs on [Dispatchers.IO].
     * The coroutine can be cancelled at any time; the partial file is left
     * intact for resumption.
     *
     * @param url        Full HTTPS URL to the media file.
     * @param partFile   The `.part` destination file. In append mode if [startByte] > 0.
     * @param startByte  0 for a fresh download; `downloadedBytes` from the saved
     *                   [DownloadItem] for a resume.
     * @param onProgress Called periodically with current progress data.
     * @return [DownloadResult.Success] on completion, [DownloadResult.Error] on failure.
     */
    suspend fun download(
        url:        String,
        partFile:   File,
        startByte:  Long           = 0L,
        onProgress: ProgressCallback
    ): DownloadResult = withContext(Dispatchers.IO) {

        // ── Build request ──────────────────────────────────────────────────────
        val request = Request.Builder()
            .url(url)
            .apply {
                // Only add Range header if we have something to skip.
                if (startByte > 0L) {
                    addHeader("Range", "bytes=$startByte-")
                }
            }
            .build()

        // ── Execute (blocking on Dispatchers.IO) ───────────────────────────────
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            return@withContext DownloadResult.Error("Connection failed: ${e.message}")
        }

        response.use { resp ->
            // ── Validate response ──────────────────────────────────────────────
            val isPartial = resp.code == 206
            val isOk      = resp.code == 200

            when {
                // Server doesn't support Range — restart from zero.
                startByte > 0L && isOk -> {
                    partFile.delete()
                    return@withContext DownloadResult.Error(
                        "Server does not support Range requests — restarting.",
                        isRangeUnsupported = true
                    )
                }
                !isPartial && !isOk -> {
                    return@withContext DownloadResult.Error(
                        "HTTP ${resp.code}: ${resp.message}"
                    )
                }
            }

            val body = resp.body
                ?: return@withContext DownloadResult.Error("Empty response body.")

            // ── Calculate total bytes ──────────────────────────────────────────
            val contentLength = body.contentLength()           // bytes in THIS response
            val totalBytes    = when {
                isPartial   -> startByte + contentLength       // full file size when resuming
                contentLength >= 0 -> contentLength            // known from Content-Length
                else        -> 0L                              // unknown (chunked encoding)
            }

            // ── Open the output file ───────────────────────────────────────────
            // Append mode when resuming, overwrite mode for a fresh start.
            val appendMode = startByte > 0L && isPartial
            partFile.parentFile?.mkdirs()

            val outputStream = try {
                FileOutputStream(partFile, /* append = */ appendMode)
            } catch (e: Exception) {
                return@withContext DownloadResult.Error("Cannot open file: ${e.message}")
            }

            // ── Stream the body ────────────────────────────────────────────────
            var writtenInSession = 0L              // bytes written in THIS session
            var downloadedTotal  = startByte       // running total including previous
            var speedWindowBytes = 0L              // bytes since last speed window open
            var speedWindowStart = System.currentTimeMillis()
            var lastProgressTime = System.currentTimeMillis()
            var currentSpeed     = 0L

            val buffer = ByteArray(BUFFER_SIZE)

            try {
                body.byteStream().use { inputStream ->
                    outputStream.use { output ->
                        while (isActive) {
                            // Check for coroutine cancellation BEFORE the blocking read.
                            ensureActive()

                            val bytesRead = try {
                                inputStream.read(buffer)
                            } catch (e: InterruptedIOException) {
                                // Thread interrupted by coroutine cancellation.
                                break
                            }

                            if (bytesRead == -1) break  // end of stream

                            output.write(buffer, 0, bytesRead)
                            writtenInSession += bytesRead
                            downloadedTotal  += bytesRead
                            speedWindowBytes += bytesRead

                            // ── Speed + progress update ────────────────────────
                            val now     = System.currentTimeMillis()
                            val elapsed = now - speedWindowStart

                            if (elapsed >= SPEED_WINDOW_MS) {
                                currentSpeed     = (speedWindowBytes * 1000L) / elapsed
                                speedWindowBytes = 0L
                                speedWindowStart = now
                            }

                            if (now - lastProgressTime >= PROGRESS_INTERVAL_MS) {
                                onProgress(downloadedTotal, totalBytes, currentSpeed)
                                lastProgressTime = now
                            }
                        }

                        // Flush remaining bytes to disk
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                // Any I/O error during streaming
                return@withContext DownloadResult.Error(e.message ?: "I/O error during download.")
            }

            // Final progress update to ensure 100% is reported.
            onProgress(downloadedTotal, totalBytes.coerceAtLeast(downloadedTotal), 0L)

            DownloadResult.Success(downloadedTotal)
        }
    }

    // =========================================================================
    //  Constants
    // =========================================================================

    private companion object {
        /** Read buffer size: 32 KB — good balance of memory vs. system call overhead. */
        const val BUFFER_SIZE = 32 * 1024

        /** Speed is measured over a rolling 1-second window. */
        const val SPEED_WINDOW_MS = 1_000L

        /** Progress callback minimum interval (prevents StateFlow saturation). */
        const val PROGRESS_INTERVAL_MS = 500L

        /** Build a dedicated OkHttpClient for downloads. CRITICAL: no read timeout. */
        fun buildDownloadClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // ← MUST be 0; default would break large files
                .writeTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}
