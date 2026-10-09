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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    private var revalidationJob: Job? = null

    // Cache revalidation tracking (Phase 5)
    private val sectionLastFetchTime = java.util.concurrent.ConcurrentHashMap<ContentSection, Long>()
    private val CACHE_REVALIDATION_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours

    fun shouldRevalidateCache(section: ContentSection): Boolean {
        val lastTime = sectionLastFetchTime[section] ?: return true
        return (System.currentTimeMillis() - lastTime) > CACHE_REVALIDATION_TTL_MS
    }

    init {
        // Load the default section on first creation.
        selectSection(ContentSection.HOME)
        refreshAccountsState()
        loadHomeData()
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
                searchResultsMovies = emptyList(),
                searchResultsSeries = emptyList(),
                error              = null,
                currentPage        = 0
            )
        }
        if (section == ContentSection.HOME) {
            refreshLibraryLists()
            loadHomeData()
        } else if (section == ContentSection.MY_LIBRARY) {
            refreshLibraryLists()
        } else if (section == ContentSection.WHATS_NEW) {
            loadWhatsNew()
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

        _uiState.update { it.copy(
            selectedCategoryId = categoryId,
            searchQuery = "",
            currentPage = 0,
            streams = emptyList(),
            seriesList = emptyList()
        ) }
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
        _uiState.update { 
            it.copy(
                detailVodItem = stream,
                detailVodInfo = null,
                isLoadingVodInfo = true
            ) 
        }
        viewModelScope.launch {
            repository.getVodInfo(stream.streamId)
                .onSuccess { infoRes ->
                    _uiState.update { 
                        it.copy(detailVodInfo = infoRes, isLoadingVodInfo = false) 
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingVodInfo = false) }
                }
        }
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
                detailVodInfo = null,
                isLoadingVodInfo = false,
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

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, currentPage = 0) }
        if (_uiState.value.activeSection == ContentSection.HOME) {
            if (query.isNotBlank()) {
                performHomeSearch(query)
            } else {
                searchJob?.cancel()
                _uiState.update { it.copy(
                    searchResultsMovies = emptyList(),
                    searchResultsSeries = emptyList(),
                    isSearchingHome = false
                ) }
            }
        }
    }

    private fun performHomeSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // 300ms debounce for TV typing
            _uiState.update { it.copy(isSearchingHome = true) }
            
            val vodDeferred = async { repository.getVodStreams(categoryId = null, forceRefresh = false) }
            val seriesDeferred = async { repository.getSeries(categoryId = null, forceRefresh = false) }
            
            val vodResult = vodDeferred.await()
            val seriesResult = seriesDeferred.await()
            
            var movies = emptyList<StreamItem>()
            var series = emptyList<SeriesItem>()
            
            vodResult.onSuccess { allMovies ->
                movies = allMovies.filter { it.name.contains(query, ignoreCase = true) }
            }
            
            seriesResult.onSuccess { allSeries ->
                series = allSeries.filter { it.name.contains(query, ignoreCase = true) }
            }
            
            _uiState.update { it.copy(
                searchResultsMovies = movies,
                searchResultsSeries = series,
                isSearchingHome = false
            ) }
        }
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
        val targetApk = if (com.ipxtream.tv.BuildConfig.DEBUG) "app-debug.apk" else "app-release.apk"
        val apkAsset = release.assets.firstOrNull { it.name.equals(targetApk, ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return

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

    // ─── New Home & Cache Logic ───

    fun loadWhatsNew() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, error = null, whatsNewItems = emptyList()) }
            
            // Fetch VOD movies
            val vodResult = repository.getVodStreams(categoryId = null, forceRefresh = false)
            // Fetch TV Series
            val seriesResult = repository.getSeries(categoryId = null, forceRefresh = false)
            
            val newItems = mutableListOf<LibraryItem>()
            
            vodResult.onSuccess { movies ->
                val sortedMovies = movies.sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                val filteredMovies = sortedMovies.filter { movie ->
                    val ratingVal = movie.rating?.toDoubleOrNull() ?: 0.0
                    val title = movie.name ?: ""
                    (ratingVal >= 7.0 && (title.contains("(2026)") || title.contains("(2025)"))) ||
                    (movie.added?.toLongOrNull() ?: 0L) > 0L
                }.take(20)

                val effectiveMovies = if (filteredMovies.isNotEmpty()) filteredMovies else sortedMovies.take(20)

                effectiveMovies.forEach { movie ->
                    val addedTime = movie.added?.toLongOrNull() ?: movie.streamId.toLong()
                    newItems.add(
                        LibraryItem(
                            id = movie.streamId.toString(),
                            name = movie.name ?: "Untitled Movie",
                            type = "movie",
                            iconUrl = movie.streamIcon,
                            categoryId = movie.categoryId,
                            rating = movie.rating,
                            containerExtension = movie.containerExtension,
                            timestamp = addedTime * 1000L
                        )
                    )
                }
            }
            
            seriesResult.onSuccess { seriesList ->
                val sortedSeries = seriesList.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }
                val filteredSeries = sortedSeries.filter { series ->
                    val ratingVal = series.rating?.toDoubleOrNull() ?: 0.0
                    val seriesName = series.name ?: ""
                    val yearSuffix = series.releaseDate?.trim()
                    val formattedName = if (!yearSuffix.isNullOrEmpty() && !seriesName.contains(yearSuffix)) {
                        "$seriesName ($yearSuffix)"
                    } else {
                        seriesName
                    }
                    (ratingVal >= 7.0 && (formattedName.contains("(2026)") || formattedName.contains("(2025)"))) ||
                    (series.lastModified?.toLongOrNull() ?: 0L) > 0L
                }.take(20)

                val effectiveSeries = if (filteredSeries.isNotEmpty()) filteredSeries else sortedSeries.take(20)

                effectiveSeries.forEach { series ->
                    val seriesName = series.name ?: ""
                    val yearSuffix = series.releaseDate?.trim()
                    val formattedName = if (!yearSuffix.isNullOrEmpty() && !seriesName.contains(yearSuffix)) {
                        "$seriesName ($yearSuffix)"
                    } else {
                        seriesName
                    }
                    val modifiedTime = series.lastModified?.toLongOrNull() ?: series.seriesId.toLong()
                    newItems.add(
                        LibraryItem(
                            id = series.seriesId.toString(),
                            name = formattedName,
                            type = "series",
                            iconUrl = series.cover,
                            categoryId = series.categoryId,
                            rating = series.rating,
                            timestamp = modifiedTime * 1000L
                        )
                    )
                }
            }
            
            val sortedNewItems = newItems.sortedByDescending { it.timestamp }.take(30)
            
            _uiState.update { it.copy(
                isLoadingContent = false,
                whatsNewItems = sortedNewItems
            ) }
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // Load Live Highlights (Alwan channels, fallback to first 15 channels)
            repository.getLiveStreams(categoryId = null, forceRefresh = false).onSuccess { channels ->
                val alwan = channels.filter { it.name.contains("Alwan", ignoreCase = true) }
                val highlights = if (alwan.isNotEmpty()) alwan else channels.take(15)
                _uiState.update { it.copy(homeLiveHighlights = highlights) }
            }

            // Load Hot Movies (2026, 7+ rating, fallback to newest/top movies)
            repository.getVodStreams(categoryId = null, forceRefresh = false).onSuccess { movies ->
                val hot = movies.filter {
                    val ratingVal = it.rating?.toDoubleOrNull() ?: 0.0
                    val nameStr = it.name ?: ""
                    ratingVal >= 7.0 && nameStr.contains("(2026)")
                }
                val finalHot = if (hot.isNotEmpty()) hot else {
                    movies.sortedByDescending { it.added?.toLongOrNull() ?: 0L }.take(15)
                }
                _uiState.update { it.copy(homeHotMovies = finalHot) }
            }

            // Load Popular TV Series (2026, 7+ rating, fallback to newest/top series)
            repository.getSeries(categoryId = null, forceRefresh = false).onSuccess { seriesList ->
                val popular = seriesList.filter {
                    val ratingVal = it.rating?.toDoubleOrNull() ?: 0.0
                    val seriesName = it.name ?: ""
                    val yearSuffix = it.releaseDate?.trim()
                    val formattedName = if (!yearSuffix.isNullOrEmpty() && !seriesName.contains(yearSuffix)) {
                        "$seriesName ($yearSuffix)"
                    } else {
                        seriesName
                    }
                    ratingVal >= 7.0 && formattedName.contains("(2026)")
                }
                val finalPopular = if (popular.isNotEmpty()) popular else {
                    seriesList.sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }.take(15)
                }
                _uiState.update { it.copy(homePopularSeries = finalPopular) }
            }
        }
    }

    fun cacheAllContent() {
        if (_uiState.value.isCachingAll) return
        _uiState.update { it.copy(isCachingAll = true) }
        viewModelScope.launch {
            try {
                // Prefetch Live
                repository.getLiveCategories(forceRefresh = true)
                repository.getLiveStreams(categoryId = null, forceRefresh = true)
                
                // Prefetch Movies (VOD)
                repository.getVodCategories(forceRefresh = true)
                repository.getVodStreams(categoryId = null, forceRefresh = true)
                
                // Prefetch TV Series
                repository.getSeriesCategories(forceRefresh = true)
                repository.getSeries(categoryId = null, forceRefresh = true)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "All content cached successfully!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("DashboardViewModel", "Error prefetching cache: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error caching content: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _uiState.update { it.copy(isCachingAll = false) }
            }
        }
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

    fun logout() {
        credentialStore.clearCredentials()
        val intent = android.content.Intent(context, com.ipxtream.tv.ui.login.LoginActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }

    // =========================================================================
    //  Private loading logic
    // =========================================================================

    /** Full section load: fast cache-first load, then silent background revalidation if stale. */
    private fun loadSectionData(section: ContentSection, forceRefresh: Boolean = false) {
        val needsReval = forceRefresh || shouldRevalidateCache(section)
        loadCategories(section, forceRefresh = false)
        loadContent(section, categoryId = null, forceRefresh = false)
        if (needsReval) {
            triggerBackgroundRevalidation(section)
        }
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
                ContentSection.HOME -> return@launch
                ContentSection.WHATS_NEW -> return@launch
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
            val hasExistingItems = _uiState.value.streams.isNotEmpty() || _uiState.value.seriesList.isNotEmpty()
            _uiState.update { it.copy(
                isLoadingContent = !hasExistingItems,
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
                            if (!forceRefresh) sectionLastFetchTime[section] = System.currentTimeMillis()
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
                            if (!forceRefresh) sectionLastFetchTime[section] = System.currentTimeMillis()
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
                            if (!forceRefresh) sectionLastFetchTime[section] = System.currentTimeMillis()
                        }
                        .onFailure(::handleContentError)
                }
                ContentSection.MY_LIBRARY -> { /* handled by refreshLibraryLists() */ }
                ContentSection.SETTINGS -> { /* no-op */ }
                ContentSection.DOWNLOADS -> { /* no-op */ }
                ContentSection.HOME -> { /* no-op */ }
                ContentSection.WHATS_NEW -> { /* no-op */ }
            }
        }
    }

    private fun triggerBackgroundRevalidation(section: ContentSection) {
        revalidationJob?.cancel()
        revalidationJob = viewModelScope.launch {
            try {
                android.util.Log.d("DashboardViewModel", "Silent background cache revalidation for $section")
                when (section) {
                    ContentSection.LIVE -> {
                        val cats = repository.getLiveCategories(forceRefresh = true).getOrNull()
                        val streams = repository.getLiveStreams(categoryId = null, forceRefresh = true).getOrNull()
                        if (streams != null && _uiState.value.activeSection == ContentSection.LIVE) {
                            _uiState.update { it.copy(
                                categories = cats ?: it.categories,
                                streams    = streams
                            ) }
                            sectionLastFetchTime[section] = System.currentTimeMillis()
                        }
                    }
                    ContentSection.VOD -> {
                        val cats = repository.getVodCategories(forceRefresh = true).getOrNull()
                        val streams = repository.getVodStreams(categoryId = null, forceRefresh = true).getOrNull()
                        if (streams != null && _uiState.value.activeSection == ContentSection.VOD) {
                            _uiState.update { it.copy(
                                categories = cats ?: it.categories,
                                streams    = streams
                            ) }
                            sectionLastFetchTime[section] = System.currentTimeMillis()
                        }
                    }
                    ContentSection.SERIES -> {
                        val cats = repository.getSeriesCategories(forceRefresh = true).getOrNull()
                        val series = repository.getSeries(categoryId = null, forceRefresh = true).getOrNull()
                        if (series != null && _uiState.value.activeSection == ContentSection.SERIES) {
                            _uiState.update { it.copy(
                                categories = cats ?: it.categories,
                                seriesList = series
                            ) }
                            sectionLastFetchTime[section] = System.currentTimeMillis()
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "Silent revalidation failed for $section: ${e.message}")
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
