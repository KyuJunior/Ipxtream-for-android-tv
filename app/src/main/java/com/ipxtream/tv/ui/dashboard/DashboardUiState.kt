package com.ipxtream.tv.ui.dashboard

import com.ipxtream.tv.R
import androidx.annotation.StringRes
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.model.VodInfoResponse
import com.ipxtream.tv.data.local.LibraryItem

// ─── Section Enum ────────────────────────────────────────────────────────────

/**
 * The three top-level content sections exposed on the Dashboard.
 * Drives which API endpoints are called and which card layout is rendered.
 */
enum class ContentSection(@StringRes val titleRes: Int, val iconRes: String) {
    HOME(R.string.section_home, "ic_home"),
    MATCHES(R.string.section_matches, "ic_matches"),
    LIVE(R.string.section_live,   "ic_live_tv"),
    VOD(R.string.section_movies,    "ic_movie"),
    SERIES(R.string.section_series, "ic_series"),
    UHD(R.string.section_uhd, "ic_4k"),
    MY_LIBRARY(R.string.section_library, "ic_library"),
    WHATS_NEW(R.string.section_whats_new, "ic_whats_new"),
    SETTINGS(R.string.section_settings, "ic_settings"),
    DOWNLOADS(R.string.section_downloads, "ic_downloads")
}

// ─── Matches ──────────────────────────────────────────────────────────────────

/**
 * A match with the user's live channels that may show it, best first: the channel the user
 * chose before (when [remembered]), then [com.ipxtream.tv.data.match.ChannelMatcher]'s guesses.
 */
data class MatchListing(
    val match:      com.ipxtream.tv.data.match.Match,
    val channels:   List<StreamItem>,
    val remembered: Boolean,
    /** The beIN channel beIN's own guide lists for this match ("beIN SPORTS 3"), when it is first in [channels]. */
    val guideChannel: String? = null
)

// ─── 4K scan progress ─────────────────────────────────────────────────────────

enum class UhdScanPhase { IDLE, LABELLED, LIBRARY }

/**
 * @param checked   titles measured so far in the current pass
 * @param total     titles queued in the current pass
 * @param found     verified 4K titles
 * @param unchecked titles in the library never measured yet
 */
data class UhdScanState(
    val phase:     UhdScanPhase = UhdScanPhase.IDLE,
    val checked:   Int = 0,
    val total:     Int = 0,
    val found:     Int = 0,
    val unchecked: Int = 0
) {
    val isRunning: Boolean get() = phase != UhdScanPhase.IDLE
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
    val detailVodInfo:       VodInfoResponse? = null,
    val isLoadingVodInfo:    Boolean          = false,
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
    /** True only for a check the user started; the automatic startup check shows a banner instead. */
    val showUpdateDialog:    Boolean = false,
    val updateDownloadProgress: Float? = null, // null means not downloading, 0.0-1.0 progress
    val updateErrorMessage:  String? = null,

    // ─── New Home & Cache State ───
    val isCachingAll:        Boolean           = false,
    val whatsNewItems:       List<LibraryItem> = emptyList(),
    /** Movies and series whose measured video is 4K (see ResolutionScanner). */
    val uhdMovies:           List<StreamItem> = emptyList(),
    val uhdSeries:           List<SeriesItem> = emptyList(),
    /** Measured resolution of each 4K title, keyed "m{streamId}" / "s{seriesId}". */
    val uhdResolutions:      Map<String, com.ipxtream.tv.data.local.VideoRes> = emptyMap(),
    val uhdScan:             UhdScanState = UhdScanState(),
    /** Best-known resolution of the movie shown in the detail overlay. */
    val detailVodResolution: com.ipxtream.tv.data.local.VideoRes? = null,
    val homeLiveHighlights:  List<StreamItem>  = emptyList(),
    val homeHotMovies:       List<StreamItem>  = emptyList(),
    val homePopularSeries:   List<SeriesItem>  = emptyList(),
    val showAccountManager:  Boolean           = false,
    val searchResultsMovies: List<StreamItem>  = emptyList(),
    val searchResultsSeries: List<SeriesItem>  = emptyList(),
    val isSearchingHome:     Boolean           = false,

    // ─── Multi-Account State ───
    val accounts:            List<com.ipxtream.tv.data.model.AuthCredentials> = emptyList(),
    val defaultAccount:      com.ipxtream.tv.data.model.AuthCredentials? = null,
    val activeAccount:       com.ipxtream.tv.data.model.AuthCredentials? = null,
    // ─── App Settings ───
    val settings:            com.ipxtream.tv.data.local.AppSettings = com.ipxtream.tv.data.local.AppSettings(),

    // ─── Matches ───
    /** Day shown in Matches, relative to today (-1 = yesterday). */
    val matchDayOffset:      Int = 0,
    val matches:             List<MatchListing> = emptyList(),
    val isLoadingMatches:    Boolean = false,
    /** When the shown fixtures were downloaded; null if never. */
    val matchesUpdatedAt:    Long? = null,
    /** True when the fixture source couldn't be reached and cached data is shown. */
    val matchesOffline:      Boolean = false,
    /** Every live channel, for the channel picker's search. */
    val matchPickerChannels: List<StreamItem> = emptyList(),
    /** What CH+/CH- steps through while a match plays: its channels, best first. */
    val matchChannelQueue:   List<StreamItem> = emptyList(),

    // ─── Pagination ───
    val currentPage:         Int = 0
) {
    val isUpdateDialogOpen: Boolean get() = updateRelease != null && showUpdateDialog

    companion object {
        /** Maximum items per page to keep the grid performant. */
        const val PAGE_SIZE = 15
    }

    /** True when either data layer is in-flight. */
    val isLoading: Boolean get() = isLoadingCategories || isLoadingContent

    val displayedStreams: List<StreamItem> get() {
        return if (searchQuery.isBlank()) streams else streams.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val displayedSeries: List<SeriesItem> get() {
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

    val paginatedSearchMovies: List<StreamItem> get() {
        val start = currentPage * 10
        val end = ((currentPage + 1) * 10).coerceAtMost(searchResultsMovies.size)
        if (start >= searchResultsMovies.size) return emptyList()
        return searchResultsMovies.subList(start, end)
    }

    val paginatedSearchSeries: List<SeriesItem> get() {
        val start = currentPage * 10
        val end = ((currentPage + 1) * 10).coerceAtMost(searchResultsSeries.size)
        if (start >= searchResultsSeries.size) return emptyList()
        return searchResultsSeries.subList(start, end)
    }

    val totalSearchPages: Int get() {
        val totalMovies = searchResultsMovies.size
        val totalSeries = searchResultsSeries.size
        val maxTotal = maxOf(totalMovies, totalSeries)
        return if (maxTotal == 0) 1 else (maxTotal + 9) / 10
    }

    /** Total number of pages. */
    val totalPages: Int get() {
        if (activeSection == ContentSection.HOME && searchQuery.isNotBlank()) {
            return totalSearchPages
        }
        val total = totalItemCount
        return if (total == 0) 1 else (total + PAGE_SIZE - 1) / PAGE_SIZE
    }

    val hasNextPage: Boolean get() = currentPage < totalPages - 1
    val hasPrevPage: Boolean get() = currentPage > 0

    /** True when the player panel should be shown alongside the content grid. */
    val hasActivePlayback: Boolean get() = selectedStream != null || selectedEpisode != null

    /** Watch progress fraction (0.0f..1.0f) for a movie or series, if in history. */
    fun getWatchProgress(id: String): Float? {
        val item = historyList.firstOrNull { it.id == id || it.parentId == id } ?: return null
        return if (item.durationMs > 0L) {
            (item.lastWatchedPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
        } else null
    }
}
