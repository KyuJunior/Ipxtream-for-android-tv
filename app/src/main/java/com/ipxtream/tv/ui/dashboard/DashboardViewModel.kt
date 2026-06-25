package com.ipxtream.tv.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipxtream.tv.data.download.DownloadController
import com.ipxtream.tv.data.download.DownloadItem
import com.ipxtream.tv.data.download.DownloadRepository
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.repository.ContentRepository
import com.ipxtream.tv.ui.player.StreamUrlBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ipxtream.tv.data.local.LibraryStore
import com.ipxtream.tv.data.local.LibraryItem

/**
 * ViewModel for the [DashboardActivity].
 *
 * Orchestrates:
 * 1. Section switching (Live / VOD / Series) — reloads categories + content.
 * 2. Category filtering — fetches content scoped to one category.
 * 3. Pull-to-refresh / force-refresh.
 *
 * The ViewModel keeps the last active `Job` for category and content loads so
 * that switching sections quickly cancels the in-flight request and starts a
 * fresh one, preventing stale data from overwriting the new section's state.
 *
 * @param repository Injected [ContentRepository] (network + cache).
 */
class DashboardViewModel(
    private val repository: ContentRepository,
    private val libraryStore: LibraryStore,
    private val context: Context
) : ViewModel() {

    private val updateManager = com.ipxtream.tv.data.api.UpdateManager(context)
    private val credentialStore = com.ipxtream.tv.data.local.CredentialStore(context)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Live list of all download jobs, derived from [DownloadRepository].
     * The tray UI collects this to render progress items.
     */
    val downloadItems: StateFlow<List<DownloadItem>> = DownloadRepository.downloads
        .map { map -> map.values.sortedBy { it.addedAt } }
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList()
        )

    // Active load jobs — cancelled when the section/category changes.
    private var categoryJob: Job? = null
    private var contentJob:  Job? = null

    init {
        // Load the default section on first creation.
        loadSectionData(ContentSection.LIVE)
        refreshAccountsState()
    }

    fun refreshAccountsState() {
        _uiState.update { it.copy(
            accounts = credentialStore.getAccounts(),
            defaultAccount = credentialStore.getDefaultAccount(),
            activeAccount = credentialStore.loadCredentials()
        ) }
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Switches the active content section and triggers a full reload.
     * Cancels any in-flight network/cache jobs before starting new ones.
     */
    fun selectSection(section: ContentSection) {
        if (_uiState.value.activeSection == section) return

        _uiState.update { state ->
            state.copy(
                activeSection      = section,
                selectedCategoryId = null,
                searchQuery        = "",
                categories         = emptyList(),
                streams            = emptyList(),
                seriesList         = emptyList(),
                error              = null,
                currentPage        = 0
            )
        }
        if (section == ContentSection.MY_LIBRARY) {
            refreshLibraryLists()
        } else if (section == ContentSection.SETTINGS || section == ContentSection.DOWNLOADS) {
            // No data loading needed for Settings or Downloads
        } else {
            loadSectionData(section)
        }
    }

    /**
     * Applies a category filter to the current section's content.
     * Pass `null` to show all content (the "All" chip).
     */
    fun selectCategory(categoryId: String?) {
        if (_uiState.value.selectedCategoryId == categoryId) return

        _uiState.update { it.copy(selectedCategoryId = categoryId, searchQuery = "", currentPage = 0) }
        loadContent(
            section    = _uiState.value.activeSection,
            categoryId = categoryId
        )
    }

    // =========================================================================
    //  Phase 7: Detail Overlay Navigation
    // =========================================================================

    /** Displays the VOD (Movie) details full-screen overlay. */
    fun showVodDetails(stream: StreamItem) {
        _uiState.update { it.copy(detailVodItem = stream) }
    }

    /** Displays the Series detail screen and fetches full season/episode metadata. */
    fun showSeriesDetails(series: SeriesItem) {
        _uiState.update { it.copy(detailSeriesItem = series, isLoadingSeriesInfo = true) }
        
        viewModelScope.launch {
            repository.getSeriesInfo(series.seriesId)
                .onSuccess { infoRes ->
                    _uiState.update { 
                        it.copy(seriesInfo = infoRes, isLoadingSeriesInfo = false) 
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingSeriesInfo = false) }
                    // Ignoring hard failure here; the UI will show an empty season list.
                }
        }
    }

    /** Dismisses the active detail overlay, returning to the grid. */
    fun closeDetails() {
        _uiState.update { 
            it.copy(
                detailVodItem = null,
                detailSeriesItem = null,
                seriesInfo = null,
                isLoadingSeriesInfo = false
            ) 
        }
    }

    // =========================================================================
    //  Playback Navigation
    // =========================================================================

    /**
     * Records which [StreamItem] the user has selected for playback.
     * Updates [DashboardUiState.selectedStream] which triggers the split layout.
     * The caller (Activity) is responsible for also calling [PlayerViewModel.loadStream].
     */
    fun startPlayback(stream: StreamItem) {
        // Automatically close details if starting playback directly from them
        closeDetails()
        _uiState.update { it.copy(selectedStream = stream, selectedEpisode = null) }
    }

    /**
     * Records which [EpisodeItem] the user has selected for playback (Series section).
     */
    fun startEpisodePlayback(episode: EpisodeItem) {
        // Detail screen remains behind the player panel (or closed, up to preference).
        // For TV, it's nice to leave details up underneath the split layout.
        _uiState.update { it.copy(selectedEpisode = episode, selectedStream = null) }
    }

    /**
     * Clears the active playback selection, collapsing the player panel.
     * Call when the user presses the Stop / Back button in the player overlay.
     */
    fun clearActivePlayback() {
        _uiState.update { it.copy(selectedStream = null, selectedEpisode = null) }
    }

    /**
     * Force-refreshes the current section by clearing the category filter
     * and re-fetching everything from the network.
     * Useful for a "Refresh" action on the remote.
     */
    fun refresh() {
        val section = _uiState.value.activeSection
        _uiState.update { it.copy(selectedCategoryId = null, searchQuery = "", error = null) }
        if (section == ContentSection.MY_LIBRARY) {
            refreshLibraryLists()
        } else {
            loadSectionData(section, forceRefresh = true)
        }
    }

    // =========================================================================
    //  Search management
    // =========================================================================

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, currentPage = 0) }
    }

    // =========================================================================
    //  Pagination
    // =========================================================================

    /** Advance to the next page if available. */
    fun nextPage() {
        _uiState.update { state ->
            if (state.hasNextPage) state.copy(currentPage = state.currentPage + 1) else state
        }
    }

    /** Go back to the previous page if available. */
    fun prevPage() {
        _uiState.update { state ->
            if (state.hasPrevPage) state.copy(currentPage = state.currentPage - 1) else state
        }
    }

    // =========================================================================
    //  Library / Favorites Management
    // =========================================================================

    fun refreshLibraryLists() {
        _uiState.update { it.copy(
            favoritesList = libraryStore.getFavorites(),
            historyList = libraryStore.getHistory()
        ) }
    }

    fun toggleFavoriteStream(stream: StreamItem) {
        val itemId = stream.streamId.toString()
        val type = if (stream.isLive) "live" else "movie"
        if (libraryStore.isFavorite(itemId, type)) {
            libraryStore.removeFavorite(itemId, type)
        } else {
            val item = LibraryItem(
                id = itemId,
                name = stream.name,
                type = type,
                iconUrl = stream.streamIcon,
                categoryId = stream.categoryId,
                rating = stream.rating,
                containerExtension = stream.containerExtension
            )
            libraryStore.addFavorite(item)
        }
        refreshLibraryLists()
    }

    fun toggleFavoriteSeries(series: SeriesItem) {
        val itemId = series.seriesId.toString()
        val type = "series"
        if (libraryStore.isFavorite(itemId, type)) {
            libraryStore.removeFavorite(itemId, type)
        } else {
            val item = LibraryItem(
                id = itemId,
                name = series.name,
                type = type,
                iconUrl = series.cover,
                categoryId = series.categoryId,
                rating = series.rating
            )
            libraryStore.addFavorite(item)
        }
        refreshLibraryLists()
    }

    fun isStreamFavorite(streamId: Int, type: String): Boolean {
        return libraryStore.isFavorite(streamId.toString(), type)
    }

    fun isSeriesFavorite(seriesId: Int): Boolean {
        return libraryStore.isFavorite(seriesId.toString(), "series")
    }

    // =========================================================================
    //  Download management (Phase 6)
    // =========================================================================

    /**
     * Enqueues a VOD [StreamItem] for background download.
     *
     * Builds the download URL via [StreamUrlBuilder] and delegates to
     * [DownloadController.enqueue]. The [DownloadRepository] is updated
     * immediately so the tray shows the new item before the service starts.
     *
     * @param context     Used to resolve the external storage directory and
     *                    to start [DownloadService].
     * @param stream      The VOD item to download.
     * @param credentials Used to build the authenticated download URL.
     */
    fun downloadStream(
        context:     android.content.Context,
        stream:      StreamItem,
        credentials: com.ipxtream.tv.data.model.AuthCredentials
    ) {
        val spec = DownloadController.buildSpec(
            context     = context,
            streamId    = stream.streamId,
            title       = stream.name,
            url         = StreamUrlBuilder.buildForStream(credentials, stream),
            extension   = stream.containerExtension ?: "mp4",
            contentType = com.ipxtream.tv.data.download.DownloadContentType.VOD
        )
        DownloadController.enqueue(context, spec)
    }

    /**
     * Enqueues a series episode download in the background downloader.
     */
    fun downloadEpisode(
        context:     android.content.Context,
        episode:     EpisodeItem,
        credentials: com.ipxtream.tv.data.model.AuthCredentials
    ) {
        val spec = DownloadController.buildSpec(
            context     = context,
            streamId    = episode.id.toIntOrNull() ?: 0,
            title       = episode.title,
            url         = StreamUrlBuilder.buildForEpisode(credentials, episode),
            extension   = episode.containerExtension ?: "mp4",
            contentType = com.ipxtream.tv.data.download.DownloadContentType.SERIES_EPISODE
        )
        DownloadController.enqueue(context, spec)
    }

    /** Pauses the download with [downloadId]. Preserves the partial file. */
    fun pauseDownload(context: android.content.Context, downloadId: String) =
        DownloadController.pause(context, downloadId)

    /** Resumes a paused or failed download with [downloadId]. */
    fun resumeDownload(context: android.content.Context, downloadId: String) =
        DownloadController.resume(context, downloadId)

    /** Cancels and deletes the download with [downloadId]. */
    fun cancelDownload(context: android.content.Context, downloadId: String) =
        DownloadController.cancel(context, downloadId)

    /** Retries a failed download from byte 0. */
    fun retryDownload(context: android.content.Context, downloadId: String) =
        DownloadController.retry(context, downloadId)

    /** Clears completed and failed items from [DownloadRepository]. */
    fun clearFinishedDownloads() = DownloadRepository.clearFinished()

    // =========================================================================
    //  In-App Updater management
    // =========================================================================

    fun checkForUpdates() {
        if (_uiState.value.isCheckingForUpdate) return
        _uiState.update { it.copy(isCheckingForUpdate = true, updateErrorMessage = null) }
        viewModelScope.launch {
            val release = updateManager.checkForUpdates()
            _uiState.update { it.copy(
                isCheckingForUpdate = false,
                updateRelease = release
            ) }
        }
    }

    fun downloadAndInstallUpdate() {
        val release = _uiState.value.updateRelease ?: return
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return

        _uiState.update { it.copy(updateDownloadProgress = 0f, updateErrorMessage = null) }

        viewModelScope.launch {
            val destinationFile = java.io.File(context.externalCacheDir ?: context.cacheDir, "ipxtream_update.apk")
            if (destinationFile.exists()) destinationFile.delete()

            val success = updateManager.downloadUpdate(
                downloadUrl = apkAsset.downloadUrl,
                destinationFile = destinationFile,
                onProgress = { progress ->
                    _uiState.update { it.copy(updateDownloadProgress = progress) }
                }
            )

            if (success) {
                _uiState.update { it.copy(updateDownloadProgress = null) }
                updateManager.installApk(destinationFile)
            } else {
                _uiState.update { it.copy(
                    updateDownloadProgress = null,
                    updateErrorMessage = "Failed to download update file. Please try again."
                ) }
            }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateRelease = null, updateErrorMessage = null) }
    }

    // =========================================================================
    //  Multi-Account management
    // =========================================================================

    fun switchAccount(server: String, username: String) {
        if (credentialStore.setActiveAccount(server, username)) {
            val intent = android.content.Intent(context, DashboardActivity::class.java).apply {
                val activeCreds = credentialStore.loadCredentials()
                if (activeCreds != null) {
                    putExtra(DashboardActivity.EXTRA_SERVER, activeCreds.server)
                    putExtra(DashboardActivity.EXTRA_USERNAME, activeCreds.username)
                    putExtra(DashboardActivity.EXTRA_PASSWORD, activeCreds.password)
                }
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    fun setDefaultAccount(server: String, username: String) {
        credentialStore.setDefaultAccount(server, username)
        refreshAccountsState()
    }

    fun removeAccount(server: String, username: String) {
        val active = credentialStore.loadCredentials()
        val isActiveRemoved = active != null && 
                active.server.equals(server, ignoreCase = true) && 
                active.username.equals(username, ignoreCase = true)

        credentialStore.removeAccount(server, username)
        refreshAccountsState()

        if (isActiveRemoved || credentialStore.getAccounts().isEmpty()) {
            val intent = android.content.Intent(context, com.ipxtream.tv.ui.login.LoginActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    fun addAccount() {
        val intent = android.content.Intent(context, com.ipxtream.tv.ui.login.LoginActivity::class.java).apply {
            putExtra(com.ipxtream.tv.ui.login.LoginActivity.EXTRA_ADD_ACCOUNT, true)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // =========================================================================
    //  Private loading logic
    // =========================================================================

    /** Full section load: categories first, then all content (no category filter). */
    private fun loadSectionData(section: ContentSection, forceRefresh: Boolean = false) {
        loadCategories(section, forceRefresh)
        loadContent(section, categoryId = null, forceRefresh = forceRefresh)
    }

    private fun loadCategories(section: ContentSection, forceRefresh: Boolean = false) {
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, error = null) }

            val result = when (section) {
                ContentSection.LIVE   -> repository.getLiveCategories(forceRefresh)
                ContentSection.VOD    -> repository.getVodCategories(forceRefresh)
                ContentSection.SERIES -> repository.getSeriesCategories(forceRefresh)
                ContentSection.MY_LIBRARY -> return@launch // handled by refreshLibraryLists()
                ContentSection.SETTINGS -> return@launch
                ContentSection.DOWNLOADS -> return@launch
            }

            result
                .onSuccess { cats ->
                    _uiState.update { it.copy(
                        categories         = cats,
                        isLoadingCategories = false
                    ) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(
                        isLoadingCategories = false,
                        // Don't surface category errors as top-level errors —
                        // the content grid is still usable without categories.
                        error = if (_uiState.value.streams.isEmpty() &&
                                    _uiState.value.seriesList.isEmpty())
                                    err.localizedMessage else null
                    ) }
                }
        }
    }

    private fun loadContent(section: ContentSection, categoryId: String?, forceRefresh: Boolean = false) {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            _uiState.update { it.copy(
                isLoadingContent = true,
                streams          = emptyList(),
                seriesList       = emptyList(),
                error            = null
            ) }

            when (section) {
                ContentSection.LIVE -> {
                    repository.getLiveStreams(categoryId, forceRefresh)
                        .onSuccess { items ->
                            _uiState.update { it.copy(
                                streams          = items,
                                isLoadingContent = false,
                                isFromCache      = false
                            ) }
                        }
                        .onFailure(::handleContentError)
                }
                ContentSection.VOD -> {
                    repository.getVodStreams(categoryId, forceRefresh)
                        .onSuccess { items ->
                            _uiState.update { it.copy(
                                streams          = items,
                                isLoadingContent = false,
                                isFromCache      = false
                            ) }
                        }
                        .onFailure(::handleContentError)
                }
                ContentSection.SERIES -> {
                    repository.getSeries(categoryId, forceRefresh)
                        .onSuccess { items ->
                            _uiState.update { it.copy(
                                seriesList       = items,
                                isLoadingContent = false,
                                isFromCache      = false
                            ) }
                        }
                        .onFailure(::handleContentError)
                }
                ContentSection.MY_LIBRARY -> { /* handled by refreshLibraryLists() */ }
                ContentSection.SETTINGS -> { /* no content */ }
                ContentSection.DOWNLOADS -> { /* no content */ }
            }
        }
    }

    private fun handleContentError(error: Throwable) {
        _uiState.update { it.copy(
            isLoadingContent = false,
            error            = error.localizedMessage ?: "Failed to load content."
        ) }
    }
}
