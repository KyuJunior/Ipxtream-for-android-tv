package com.ipxtream.tv.data.local

import android.content.Context
import com.ipxtream.tv.data.api.ApiClient
import java.io.File

/**
 * Keeps the last few crashes on the device (never uploaded) so they can be read in
 * Settings → About. Passwords in URLs are masked before anything is written.
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val KEEP = 5

    data class Entry(val timeMs: Long, val text: String)

    fun record(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val text = buildString {
            appendLine("Thread: ${thread.name}")
            appendLine(error.stackTraceToString())
        }
        File(dir, "crash_$now.txt").writeText(ApiClient.redactCredentials(text))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    fun latest(context: Context): Entry? {
        val file = File(context.filesDir, DIR).listFiles()?.maxByOrNull { it.name } ?: return null
        val time = file.name.removePrefix("crash_").removeSuffix(".txt").toLongOrNull() ?: file.lastModified()
        // Masked again on read: crash files written before 2.0.13 only had `password=` masked.
        return runCatching { Entry(time, ApiClient.redactCredentials(file.readText())) }.getOrNull()
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }
}
