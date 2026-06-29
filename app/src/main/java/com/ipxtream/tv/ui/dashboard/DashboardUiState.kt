package com.ipxtream.tv.ui.dashboard

import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.local.LibraryItem

// ─── Section Enum ────────────────────────────────────────────────────────────

/**
 * The three top-level content sections exposed on the Dashboard.
 * Drives which API endpoints are called and which card layout is rendered.
 */
enum class ContentSection(val displayName: String, val iconRes: String) {
    HOME("Home", "ic_home"),
    LIVE("Live TV",   "ic_live_tv"),
    VOD("Movies",    "ic_movie"),
    SERIES("Series", "ic_series"),
    MY_LIBRARY("My Library", "ic_library"),
    WHATS_NEW("What's New", "ic_whats_new"),
    SETTINGS("Settings", "ic_settings"),
    DOWNLOADS("Downloads", "ic_downloads")
}

// ─── UI State ─────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of everything the Dashboard screen needs to render itself.
 *
 * All mutations happen inside [DashboardViewModel] and are emitted as a new
 * state object via [DashboardViewModel.uiState] (StateFlow).
 *
 * @param activeSection       Which content section is currently displayed.
 * @param categories          Category list for the current section.
 * @param selectedCategoryId  Null means "All" — no category filter applied.
 * @param streams             Filtered live/VOD stream list (empty for Series section).
 * @param seriesList          Filtered series list (empty for Live/VOD sections).
 * @param isLoadingCategories True while the category list is being fetched.
 * @param isLoadingContent    True while the stream/series list is being fetched.
 * @param error               Non-null when a terminal error has occurred.
 * @param isFromCache         True if the current content was served from disk cache.
 */
data class DashboardUiState(
    val activeSection:       ContentSection  = ContentSection.HOME,
    val categories:          List<Category>  = emptyList(),
    val selectedCategoryId:  String?         = null,
    val streams:             List<StreamItem> = emptyList(),
    val seriesList:          List<SeriesItem> = emptyList(),
    val searchQuery:         String           = "",
    val isLoadingCategories: Boolean          = false,
    val isLoadingContent:    Boolean          = false,
    val error:               String?          = null,
    val isFromCache:         Boolean          = false,
    
    // ─── Phase 7 Detail Overlays ───
    val detailVodItem:       StreamItem?      = null,
    val detailSeriesItem:    SeriesItem?      = null,
    val seriesInfo:          SeriesInfoResponse? = null,
    val isLoadingSeriesInfo: Boolean          = false,

    /** The stream the user most recently selected for playback — drives split layout. */
    val selectedStream:      StreamItem?      = null,
    /** The episode the user most recently selected for playback. */
    val selectedEpisode:     EpisodeItem?     = null,
    
    // ─── Library Lists ───
    val favoritesList:       List<LibraryItem> = emptyList(),
    val historyList:         List<LibraryItem> = emptyList(),

    // ─── App Update State ───
    val isCheckingForUpdate: Boolean = false,
    val updateRelease:       com.ipxtream.tv.data.model.GitHubRelease? = null,
    val updateDownloadProgress: Float? = null, // null means not downloading, 0.0-1.0 progress
    val updateErrorMessage:  String? = null,

    // ─── New Home & Cache State ───
    val isCachingAll:        Boolean           = false,
    val whatsNewItems:       List<LibraryItem> = emptyList(),
    val homeLiveHighlights:  List<StreamItem>  = emptyList(),
    val homeHotMovies:       List<StreamItem>  = emptyList(),
    val homePopularSeries:   List<SeriesItem>  = emptyList(),
    val showAccountManager:  Boolean           = false,

    // ─── Multi-Account State ───
    val accounts:            List<com.ipxtream.tv.data.model.AuthCredentials> = emptyList(),
    val defaultAccount:      com.ipxtream.tv.data.model.AuthCredentials? = null,
    val activeAccount:       com.ipxtream.tv.data.model.AuthCredentials? = null,

    // ─── Pagination ───
    val currentPage:         Int = 0
) {
    companion object {
        /** Maximum items per page to keep the grid performant. */
        const val PAGE_SIZE = 15
    }

    /** True when either data layer is in-flight. */
    val isLoading: Boolean get() = isLoadingCategories || isLoadingContent

    val displayedStreams: List<StreamItem> get() {
        if (selectedCategoryId == null && searchQuery.isBlank()) return emptyList()
        return if (searchQuery.isBlank()) streams else streams.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val displayedSeries: List<SeriesItem> get() {
        if (selectedCategoryId == null && searchQuery.isBlank()) return emptyList()
        return if (searchQuery.isBlank()) seriesList else seriesList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // ─── Paginated slices ─────────────────────────────────────────────────────

    /** The subset of streams visible on the current page. */
    val paginatedStreams: List<StreamItem> get() {
        val all = displayedStreams
        val start = (currentPage * PAGE_SIZE).coerceAtMost(all.size)
        val end   = ((currentPage + 1) * PAGE_SIZE).coerceAtMost(all.size)
        return all.subList(start, end)
    }

    /** The subset of series visible on the current page. */
    val paginatedSeries: List<SeriesItem> get() {
        val all = displayedSeries
        val start = (currentPage * PAGE_SIZE).coerceAtMost(all.size)
        val end   = ((currentPage + 1) * PAGE_SIZE).coerceAtMost(all.size)
        return all.subList(start, end)
    }

    /** Total number of items across all pages (pre-pagination). */
    val totalItemCount: Int get() = if (activeSection == ContentSection.SERIES)
        displayedSeries.size else displayedStreams.size

    /** Number of items currently shown on this page. */
    val itemCount: Int get() = if (activeSection == ContentSection.SERIES)
        paginatedSeries.size else paginatedStreams.size

    /** Total number of pages. */
    val totalPages: Int get() {
        val total = totalItemCount
        return if (total == 0) 1 else (total + PAGE_SIZE - 1) / PAGE_SIZE
    }

    val hasNextPage: Boolean get() = currentPage < totalPages - 1
    val hasPrevPage: Boolean get() = currentPage > 0

    /** True when the player panel should be shown alongside the content grid. */
    val hasActivePlayback: Boolean get() = selectedStream != null || selectedEpisode != null
}
