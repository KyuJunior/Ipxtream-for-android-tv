package com.ipxtream.tv.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process singleton store for all [DownloadItem] jobs.
 *
 * Both [DownloadService] (writer) and [DashboardViewModel] (reader) access
 * this object directly. Because it is a Kotlin `object`, it lives for the
 * lifetime of the process, which suits a download queue well.
 *
 * ## Thread safety
 * [MutableStateFlow.update] is atomic, so concurrent writes from multiple
 * download coroutines are safe without additional locking.
 *
 * ## Persistence
 * Currently in-memory only. If the process is killed, PENDING / DOWNLOADING
 * jobs are lost (they appear as PAUSED on next launch via the `.part` file
 * presence). Full Room DB persistence is a recommended future enhancement.
 */
object DownloadRepository {

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())

    /**
     * All current download jobs, keyed by [DownloadItem.id].
     * Collect this in the ViewModel to drive the tray UI.
     */
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // ── Write API (called from DownloadService) ───────────────────────────────

    /** Adds a new item or replaces an existing one with the same id. */
    fun addOrUpdate(item: DownloadItem) {
        _downloads.update { it + (item.id to item) }
    }

    /**
     * Applies [transform] to the item with [id], if it exists.
     * No-op if the id is not found (safe to call from any coroutine).
     */
    fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { current ->
            val item = current[id] ?: return@update current
            current + (id to transform(item))
        }
    }

    /** Removes the item with [id] from the store (e.g. after cancel + delete). */
    fun remove(id: String) {
        _downloads.update { it - id }
    }

    // ── Read API ──────────────────────────────────────────────────────────────

    /** Returns a snapshot of the item with [id], or null if not found. */
    fun get(id: String): DownloadItem? = _downloads.value[id]

    /** Returns all items sorted by [DownloadItem.addedAt] (oldest first). */
    fun allItems(): List<DownloadItem> =
        _downloads.value.values.sortedBy { it.addedAt }

    /** Returns all items currently in [DownloadStatus.DOWNLOADING]. */
    fun activeItems(): List<DownloadItem> =
        _downloads.value.values.filter { it.isActive }

    /** Clears completed and failed jobs from the store (keeps active + paused). */
    fun clearFinished() {
        _downloads.update { current ->
            current.filter { (_, v) -> !v.isFinished && !v.hasFailed }
        }
    }
}
