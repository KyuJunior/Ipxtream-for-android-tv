package com.ipxtream.tv.ui.dashboard

import com.ipxtream.tv.R
import com.ipxtream.tv.data.local.ResolutionStore
import com.ipxtream.tv.data.local.VideoRes
import com.ipxtream.tv.data.repository.ResolutionScanner
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
import com.ipxtream.tv.data.match.ChannelMatcher
import com.ipxtream.tv.data.match.Match
import com.ipxtream.tv.data.match.MatchChannelStore
import com.ipxtream.tv.data.match.MatchRepository
import com.ipxtream.tv.data.match.MatchRights
import com.ipxtream.tv.data.match.MatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val credentialStore = com.ipxtream.tv.di.AppContainer.from(context).credentialStore
    val appSettingsStore = com.ipxtream.tv.data.local.AppSettingsStore.getInstance(context)

    private val _uiState = MutableStateFlow(DashboardUiState(settings = appSettingsStore.settings.value))

    // ─── 4K collection: measured resolutions ───
    private val resolutionScanner by lazy {
        ResolutionScanner(context, repository, ResolutionStore(context, repository.accountKey))
    }
    private var uhdAllMovies: List<StreamItem> = emptyList()
    private var uhdAllSeries: List<SeriesItem> = emptyList()
    private var uhdScanJob: kotlinx.coroutines.Job? = null
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

    /**
     * True when a section's catalog is old enough to refresh in the background. Uses the
     * cache file's age, so a fresh app launch no longer re-downloads every catalog.
     */
    fun shouldRevalidateCache(section: ContentSection): Boolean {
        sectionLastFetchTime[section]?.let { last ->
            if (System.currentTimeMillis() - last <= CACHE_REVALIDATION_TTL_MS) return false
        }
        val base = when (section) {
            ContentSection.LIVE   -> "live_streams"
            ContentSection.VOD    -> "vod_streams"
            ContentSection.SERIES -> "series"
            else                  -> return false
        }
        val age = repository.catalogAgeMs(base) ?: return true
        return age > CACHE_REVALIDATION_TTL_MS
    }

    init {
        viewModelScope.launch {
            appSettingsStore.settings.collect { newSettings ->
                _uiState.update { it.copy(settings = newSettings) }
            }
        }
        // Favorites and history are live: the player's progress saves and any toggle show up
        // without reloading (this also fills Continue Watching on a cold start).
        viewModelScope.launch {
            libraryStore.favorites.collect { list -> _uiState.update { it.copy(favoritesList = list) } }
        }
        viewModelScope.launch {
            libraryStore.history.collect { list -> _uiState.update { it.copy(historyList = list) } }
        }
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
        matchesJob?.cancel()

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
        } else if (section == ContentSection.UHD) {
            loadUhdCatalog()
        } else if (section == ContentSection.MATCHES) {
            loadMatches()
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
                isLoadingVodInfo = true,
                detailVodResolution = resolutionScanner.store.movie(stream.streamId)?.takeIf { r -> r.isKnown }
            ) 
        }
        viewModelScope.launch {
            repository.getVodInfo(stream.streamId).also { r ->
                // Free measurement: remember what the portal reports for this file, unless
                // playback already measured it (the decoder is the more reliable source).
                VideoRes.fromPanelJson(r.getOrNull()?.info?.video)?.let { panel ->
                    if (resolutionScanner.store.movie(stream.streamId)?.isKnown != true) {
                        resolutionScanner.store.putMovie(stream.streamId, panel)
                    }
                    _uiState.update { s ->
                        if (s.detailVodItem?.streamId == stream.streamId && s.detailVodResolution == null)
                            s.copy(detailVodResolution = panel) else s
                    }
                }
            }
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
        _uiState.update { it.copy(selectedStream = null, selectedEpisode = null, playFullScreen = false) }
        // The player just saved where the user stopped; show it in Continue Watching.
        refreshLibraryLists()
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
        } else if (section == ContentSection.MATCHES) {
            loadMatches(force = true)
        } else {
            loadSectionData(section, forceRefresh = true)
        }
    }

    // =========================================================================
    //  Matches
    // =========================================================================

    private val matchRepository by lazy { MatchRepository(context) }
    private val matchChannelStore by lazy { MatchChannelStore(context, repository.accountKey) }
    private val matchRights by lazy { MatchRights.load(context) }
    private val beinGuide by lazy { com.ipxtream.tv.data.match.BeinGuideSource(context) }
    /** Sports-channel index plus the channel and category lists it was built from (main thread only). */
    private var channelIndex: Triple<List<StreamItem>, List<com.ipxtream.tv.data.model.Category>, ChannelMatcher.ChannelIndex>? = null
    private var matchesJob: Job? = null

    /** The leagues Matches shows (from the gear screen); reloads the list. */
    fun setFollowedLeagues(slugs: Collection<String>) {
        appSettingsStore.setFollowedLeagues(com.ipxtream.tv.data.match.FollowedCompetitions.ordered(slugs))
        _uiState.update { it.copy(matches = emptyList()) }
        loadMatches()
    }

    fun selectMatchDay(offset: Int) {
        if (_uiState.value.matchDayOffset == offset) return
        _uiState.update { it.copy(matchDayOffset = offset, matches = emptyList()) }
        loadMatches()
    }

    /**
     * Loads the shown day's fixtures and matches them to live channels. While something on
     * today's list is playing (or due within 15 min), it re-checks every minute as long as
     * Matches stays open and no video is playing.
     */
    fun loadMatches(force: Boolean = false) {
        matchesJob?.cancel()
        val offset = _uiState.value.matchDayOffset
        matchesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMatches = it.matches.isEmpty()) }
            var refresh = force
            while (true) {
                val leagues = appSettingsStore.settings.value.followedLeagues
                val day = async { matchRepository.matchesFor(offset, competitions = leagues, force = refresh) }
                val guide = async {
                    val tz = java.util.TimeZone.getDefault()
                    beinGuide.liveProgrammes(com.ipxtream.tv.data.match.MatchDays.localDay(System.currentTimeMillis(), offset, tz))
                }
                val channels = repository.getLiveStreams().getOrNull().orEmpty()
                val categories = repository.getLiveCategories().getOrNull().orEmpty()
                val result = day.await()
                val programmes = guide.await()
                // Rebuilt only when the channel or category list changes. The new index is stored here, on the
                // main thread, so a cancelled run still finishing on Default can't overwrite it.
                val cachedIndex = channelIndex?.takeIf { it.first === channels && it.second === categories }?.third
                val index = cachedIndex ?: withContext(Dispatchers.Default) {
                    ChannelMatcher.ChannelIndex(ChannelMatcher.sportsChannels(channels, categories))
                }
                channelIndex = Triple(channels, categories, index)
                val listings = withContext(Dispatchers.Default) { buildMatchListings(result.matches, channels, index, programmes) }
                _uiState.update { it.copy(
                    matches             = listings,
                    isLoadingMatches    = false,
                    matchesUpdatedAt    = result.fetchedAtMs,
                    matchesOffline      = result.offline,
                    matchPickerChannels = channels
                ) }
                refresh = false
                val soon = System.currentTimeMillis() + 15 * 60_000L
                val inPlay = listings.any {
                    it.match.state == MatchState.LIVE ||
                        (it.match.state == MatchState.UPCOMING && it.match.kickoffMs <= soon)
                }
                if (offset != 0 || !inPlay) break
                do { delay(60_000) } while (_uiState.value.hasActivePlayback)
            }
        }
    }

    /**
     * Suggestions come only from [index] (the provider's sports categories; owner request: no "beIN DRAMA").
     * The picker still offers every channel, and a remembered pick can be any channel.
     */
    private fun buildMatchListings(
        matches:    List<Match>,
        channels:   List<StreamItem>,
        index:      ChannelMatcher.ChannelIndex,
        programmes: List<com.ipxtream.tv.data.match.GuideProgramme>
    ): List<MatchListing> {
        val regions = ChannelMatcher.preferredRegions(
            arabicApp  = _uiState.value.settings.appLanguage == com.ipxtream.tv.data.local.AppLanguage.ARABIC,
            timeZoneId = java.util.TimeZone.getDefault().id
        )
        val byId by lazy { channels.associateBy { it.streamId } }
        val guideOf = com.ipxtream.tv.data.match.BeinGuide
        return matches.map { m ->
            // beIN's guide names the exact channel (beIN SPORTS 3); the matcher puts the provider's copy of it first.
            val onGuide = guideOf.channelsFor(m, programmes)
            val guesses = ChannelMatcher.candidates(
                m, index, matchRights.familiesFor(m.competition.slug), regions, guideChannels = onGuide
            )
            // The guide channel the top suggestion actually is (beIN SPORTS EN 1 when only that copy exists).
            val top = guesses.firstOrNull()?.let { s -> guideOf.parseChannel(s.name) }
            val guideHit = onGuide.firstOrNull { top != null && guideOf.parseChannel(it) == top }
            // A pick for this very match wins, unless the guide knows the channel and the pick is one of a
            // broadcaster's non-sports channels (versions before 2.0.4 suggested "beIN DRAMA"). Event channels
            // and anything else the user chose stay. A competition-wide pick counts only when the guide has nothing.
            val saved = matchChannelStore.matchChannel(m)?.let { byId[it] }
                ?.takeIf { guideHit == null || !ChannelMatcher.isEntertainmentChannel(it) }
                ?: matchChannelStore.competitionChannel(m)?.takeIf { guideHit == null }?.let { byId[it] }
            MatchListing(
                match        = m,
                channels     = listOfNotNull(saved) + guesses.filter { it.streamId != saved?.streamId },
                remembered   = saved != null,
                guideChannel = guideHit.takeIf { saved == null }
            )
        }
    }

    /**
     * Plays [stream] for a match in full screen; CH+/CH- follow [channels] (the match's channels).
     * The caller also loads it in the player.
     */
    fun startMatchPlayback(stream: StreamItem, channels: List<StreamItem>) {
        _uiState.update { it.copy(matchChannelQueue = channels, playFullScreen = true) }
        startPlayback(stream)
    }

    /**
     * Saves the user's pick for [match] (and every match of its competition when
     * [forCompetition]) and moves it to the front of the affected listings.
     */
    fun rememberMatchChannel(match: Match, stream: StreamItem, forCompetition: Boolean) {
        matchChannelStore.remember(match, stream.streamId, forCompetition)
        _uiState.update { state ->
            val updated = state.matches.map { l ->
                // Same rule as buildMatchListings: a competition-wide pick doesn't replace a guide channel.
                val affected = l.match.id == match.id ||
                    (forCompetition && l.match.competition.slug == match.competition.slug && l.guideChannel == null)
                if (!affected) l
                else l.copy(
                    channels     = listOf(stream) + l.channels.filter { it.streamId != stream.streamId },
                    remembered   = true,
                    guideChannel = null
                )
            }
            state.copy(
                matches           = updated,
                matchChannelQueue = updated.firstOrNull { it.match.id == match.id }?.channels ?: listOf(stream)
            )
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
        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
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

    private var startupCheckScheduled = false

    /**
     * Startup check, 30 s after launch so it never competes with loading the catalog or
     * steals focus; a found update shows a banner, not a dialog. Survives Activity recreation.
     */
    fun scheduleStartupUpdateCheck() {
        if (startupCheckScheduled) return
        startupCheckScheduled = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(30_000)
            checkForUpdates(manual = false)
        }
    }

    /** [manual]: the user asked, so an available update opens the dialog. */
    fun checkForUpdates(manual: Boolean = true) {
        if (_uiState.value.isCheckingForUpdate) return
        _uiState.update { it.copy(isCheckingForUpdate = true, updateErrorMessage = null) }
        viewModelScope.launch {
            val release = updateManager.checkForUpdates()
            _uiState.update { it.copy(
                isCheckingForUpdate = false,
                updateRelease = release,
                showUpdateDialog = manual && release != null
            ) }
        }
    }

    fun downloadAndInstallUpdate() {
        val release = _uiState.value.updateRelease ?: return
        // Every install (debug or release) updates to the release APK; both share the signing key.
        val apkAsset = release.assets.firstOrNull { it.name.equals("app-release.apk", ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return

        _uiState.update { it.copy(updateDownloadProgress = 0f, updateErrorMessage = null) }

        viewModelScope.launch {
            // Private storage: other apps can't swap the file before it's installed.
            val destinationFile = java.io.File(java.io.File(context.cacheDir, "updates").apply { mkdirs() }, "ipxtream_update.apk")
            if (destinationFile.exists()) destinationFile.delete()

            val success = updateManager.downloadUpdate(
                downloadUrl = apkAsset.downloadUrl,
                destinationFile = destinationFile,
                onProgress = { progress ->
                    _uiState.update { it.copy(updateDownloadProgress = progress) }
                }
            )

            if (success && !updateManager.isTrustedUpdate(destinationFile)) {
                destinationFile.delete()
                _uiState.update { it.copy(
                    updateDownloadProgress = null,
                    updateErrorMessage = text(R.string.update_verify_failed)
                ) }
            } else if (success) {
                _uiState.update { it.copy(updateDownloadProgress = null) }
                if (!updateManager.installApk(destinationFile)) {
                    _uiState.update { it.copy(
                        updateErrorMessage = text(R.string.update_installer_failed)
                    ) }
                }
            } else {
                _uiState.update { it.copy(
                    updateDownloadProgress = null,
                    updateErrorMessage = text(R.string.update_download_failed)
                ) }
            }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateRelease = null, showUpdateDialog = false, updateErrorMessage = null) }
    }

    /** UI text resolved in the in-app language (application context alone uses the system language). */
    private fun text(id: Int, vararg args: Any): String =
        com.ipxtream.tv.util.LocaleHelper.localized(context).getString(id, *args)

    // ─── New Home & Cache Logic ───

    /**
     * Builds the 4K collection from *measured* resolution, never from labels: portals
     * often tag 1080p files as "4K". Titles labelled 4K/UHD/2160p (by name or category)
     * are verified first, falling back to reading the file header when the portal has no
     * probe data. The full library can then be checked on request (see [startUhdLibraryScan]).
     */
    fun loadUhdCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, error = null) }

            val vodCats    = repository.getVodCategories(forceRefresh = false).getOrNull().orEmpty()
            val seriesCats = repository.getSeriesCategories(forceRefresh = false).getOrNull().orEmpty()
            uhdAllMovies   = repository.getVodStreams(categoryId = null, forceRefresh = false).getOrNull().orEmpty()
            uhdAllSeries   = repository.getSeries(categoryId = null, forceRefresh = false).getOrNull().orEmpty()

            val (labelledMovies, labelledSeries) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val vodCatIds    = vodCats.filter { UHD_TAG.containsMatchIn(it.categoryName) }.mapTo(HashSet()) { it.categoryId }
                val seriesCatIds = seriesCats.filter { UHD_TAG.containsMatchIn(it.categoryName) }.mapTo(HashSet()) { it.categoryId }
                uhdAllMovies.filter { UHD_TAG.containsMatchIn(it.name) || it.categoryId in vodCatIds } to
                    uhdAllSeries.filter { UHD_TAG.containsMatchIn(it.name) || it.categoryId in seriesCatIds }
            }

            publishUhdResults()
            _uiState.update { it.copy(isLoadingContent = false) }

            // A library scan already running keeps going; otherwise verify the labelled titles.
            if (uhdScanJob?.isActive != true) {
                uhdScanJob = viewModelScope.launch {
                    runUhdScan(labelledMovies, labelledSeries, allowFileProbe = true, phase = UhdScanPhase.LABELLED)
                }
            }
        }
    }

    /**
     * The decoder's frame size is the final word on a title's resolution: store it, so a
     * title whose portal data claimed 4K but plays at 1080p leaves the 4K list (and vice versa).
     */
    fun recordPlaybackResolution(stream: StreamItem?, series: SeriesItem?, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val store = resolutionScanner.store
        val measured = VideoRes(width, height)
        val previous = when {
            stream != null && stream.isMovie -> store.movie(stream.streamId).also {
                store.putMovie(stream.streamId, measured.copy(hdr = it?.hdr == true, codec = it?.codec))
                _uiState.update { s ->
                    if (s.detailVodItem?.streamId == stream.streamId) s.copy(detailVodResolution = store.movie(stream.streamId)) else s
                }
            }
            series != null                   -> store.series(series.seriesId).also { store.putSeries(series.seriesId, measured.copy(hdr = it?.hdr == true, codec = it?.codec)) }
            else                             -> return
        }
        if (previous?.width == width && previous.height == height) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            store.flush()
            if (previous?.is4K != measured.is4K && uhdAllMovies.isNotEmpty()) publishUhdResults()
        }
    }

    /** Measures every title not yet measured, newest first. Resumable; results persist. */
    fun startUhdLibraryScan() {
        if (uhdScanJob?.isActive == true && _uiState.value.uhdScan.phase == UhdScanPhase.LIBRARY) return
        uhdScanJob?.cancel()
        uhdScanJob = viewModelScope.launch {
            runUhdScan(
                movies = uhdAllMovies.sortedByDescending { it.streamId },
                series = uhdAllSeries.sortedByDescending { it.seriesId },
                allowFileProbe = false,
                phase = UhdScanPhase.LIBRARY
            )
        }
    }

    fun stopUhdScan() {
        uhdScanJob?.cancel()
        resolutionScanner.store.flush()
        _uiState.update { it.copy(uhdScan = it.uhdScan.copy(phase = UhdScanPhase.IDLE)) }
        viewModelScope.launch { publishUhdResults() }
    }

    private suspend fun runUhdScan(
        movies: List<StreamItem>,
        series: List<SeriesItem>,
        allowFileProbe: Boolean,
        phase: UhdScanPhase
    ) {
        val scanner = resolutionScanner
        val todo: List<Any> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            movies.filter { scanner.needsCheck(scanner.store.movie(it.streamId), allowFileProbe) } +
                series.filter { scanner.needsCheck(scanner.store.series(it.seriesId), allowFileProbe) }
        }
        if (todo.isEmpty()) {
            _uiState.update { it.copy(uhdScan = it.uhdScan.copy(phase = UhdScanPhase.IDLE)) }
            publishUhdResults()
            return
        }

        val checked = java.util.concurrent.atomic.AtomicInteger(0)
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        _uiState.update { it.copy(uhdScan = UhdScanState(phase, checked = 0, total = todo.size, found = it.uhdScan.found)) }

        try {
            kotlinx.coroutines.coroutineScope {
                // A few lightweight API calls in parallel; file probes serialise inside the scanner.
                repeat(UHD_SCAN_WORKERS) {
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        while (true) {
                            val i = next.getAndIncrement()
                            if (i >= todo.size) break
                            // Never compete with playback for the account's connection.
                            while (_uiState.value.hasActivePlayback) kotlinx.coroutines.delay(2_000)
                            val res = when (val item = todo[i]) {
                                is StreamItem -> scanner.measureMovie(item, allowFileProbe)
                                is SeriesItem -> scanner.measureSeries(item, allowFileProbe)
                                else -> null
                            }
                            val done = checked.incrementAndGet()
                            if (res?.is4K == true) publishUhdResults()
                            if (done % 10 == 0 || done == todo.size) {
                                _uiState.update { it.copy(uhdScan = it.uhdScan.copy(checked = done)) }
                            }
                            if (done % 200 == 0) scanner.store.flush()
                        }
                    }
                }
            }
        } finally {
            scanner.store.flush()
        }
        _uiState.update { it.copy(uhdScan = it.uhdScan.copy(phase = UhdScanPhase.IDLE, checked = todo.size)) }
        publishUhdResults()
    }

    /** Rebuilds the visible 4K lists from the measurement store. */
    private suspend fun publishUhdResults() {
        val store = resolutionScanner.store
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            // Portals sometimes list a title in several categories; the grid needs unique keys.
            val movies = uhdAllMovies.filter { store.movie(it.streamId)?.is4K == true }.distinctBy { it.streamId }
            val series = uhdAllSeries.filter { store.series(it.seriesId)?.is4K == true }.distinctBy { it.seriesId }
            val res = HashMap<String, VideoRes>(movies.size + series.size)
            movies.forEach { m -> store.movie(m.streamId)?.let { res["m${m.streamId}"] = it } }
            series.forEach { s -> store.series(s.seriesId)?.let { res["s${s.seriesId}"] = it } }
            val unchecked = uhdAllMovies.count { store.movie(it.streamId) == null } +
                uhdAllSeries.count { store.series(it.seriesId) == null }
            UhdPublish(movies, series, res, unchecked)
        }
        _uiState.update {
            it.copy(
                uhdMovies = result.movies,
                uhdSeries = result.series,
                uhdResolutions = result.resolutions,
                uhdScan = it.uhdScan.copy(found = result.movies.size + result.series.size, unchecked = result.unchecked)
            )
        }
    }

    private class UhdPublish(
        val movies: List<StreamItem>,
        val series: List<SeriesItem>,
        val resolutions: Map<String, VideoRes>,
        val unchecked: Int
    )

    fun loadWhatsNew() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
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
                            name = movie.name ?: text(R.string.untitled),
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
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
                    android.widget.Toast.makeText(context, text(R.string.cache_all_done), android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("DashboardViewModel", "Error prefetching cache: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, text(R.string.cache_all_failed, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
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
                ContentSection.UHD -> return@launch
                ContentSection.MATCHES -> return@launch
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
                ContentSection.UHD -> { /* loaded by loadUhdCatalog() */ }
                ContentSection.MATCHES -> { /* loaded by loadMatches() */ }
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
                            // Don't replace a category the user picked with the full list.
                            _uiState.update { it.copy(
                                categories = cats ?: it.categories,
                                streams    = if (it.selectedCategoryId == null) streams else it.streams
                            ) }
                            sectionLastFetchTime[section] = System.currentTimeMillis()
                        }
                    }
                    ContentSection.VOD -> {
                        val cats = repository.getVodCategories(forceRefresh = true).getOrNull()
                        val streams = repository.getVodStreams(categoryId = null, forceRefresh = true).getOrNull()
                        if (streams != null && _uiState.value.activeSection == ContentSection.VOD) {
                            // Don't replace a category the user picked with the full list.
                            _uiState.update { it.copy(
                                categories = cats ?: it.categories,
                                streams    = if (it.selectedCategoryId == null) streams else it.streams
                            ) }
                            sectionLastFetchTime[section] = System.currentTimeMillis()
                        }
                    }
                    ContentSection.SERIES -> {
                        val cats = repository.getSeriesCategories(forceRefresh = true).getOrNull()
                        val series = repository.getSeries(categoryId = null, forceRefresh = true).getOrNull()
                        if (series != null && _uiState.value.activeSection == ContentSection.SERIES) {
                            // Don't replace a category the user picked with the full list.
                            _uiState.update { it.copy(
                                categories = cats ?: it.categories,
                                seriesList = if (it.selectedCategoryId == null) series else it.seriesList
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
            error            = error.localizedMessage ?: text(R.string.content_load_failed)
        ) }
    }

    // ─── App Settings API ─────────────────────────────────────────────────────

    fun setAppLanguage(language: com.ipxtream.tv.data.local.AppLanguage) {
        appSettingsStore.setAppLanguage(language)
    }

    fun setShowWhatsNewSection(show: Boolean) {
        appSettingsStore.setShowWhatsNewSection(show)
    }

    fun setShowDownloadsSection(show: Boolean) {
        appSettingsStore.setShowDownloadsSection(show)
    }

    fun setShowLibrarySection(show: Boolean) {
        appSettingsStore.setShowLibrarySection(show)
    }

    fun setShowSpotlightBackdrop(show: Boolean) {
        appSettingsStore.setShowSpotlightBackdrop(show)
    }

    fun setShowChannelNumbers(show: Boolean) {
        appSettingsStore.setShowChannelNumbers(show)
    }

    fun setIs24HourClockFormat(is24: Boolean) {
        appSettingsStore.setIs24HourClockFormat(is24)
    }

    fun setPerformanceMode(mode: com.ipxtream.tv.data.local.PerformanceMode) {
        appSettingsStore.setPerformanceMode(mode)
    }

    fun setBufferProfile(profile: com.ipxtream.tv.data.local.BufferProfile) {
        appSettingsStore.setBufferProfile(profile)
    }

    fun setAutoPlayNextEpisode(autoPlay: Boolean) {
        appSettingsStore.setAutoPlayNextEpisode(autoPlay)
    }

    fun setDefaultAspectRatio(mode: com.ipxtream.tv.data.local.AspectRatioMode) {
        appSettingsStore.setDefaultAspectRatio(mode)
    }

    fun setDecoderMode(mode: com.ipxtream.tv.data.local.DecoderMode) {
        appSettingsStore.setDecoderMode(mode)
    }

    fun setKeepScreenAwake(keepAwake: Boolean) {
        appSettingsStore.setKeepScreenAwake(keepAwake)
    }

    fun setPreferredAudioLanguage(langCode: String) {
        appSettingsStore.setPreferredAudioLanguage(langCode)
    }

    fun setDefaultSubtitlesEnabled(enabled: Boolean) {
        appSettingsStore.setDefaultSubtitlesEnabled(enabled)
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        appSettingsStore.setPreferredSubtitleLanguage(langCode)
    }

    fun setSubtitleTextSize(size: com.ipxtream.tv.data.local.SubtitleTextSize) {
        appSettingsStore.setSubtitleTextSize(size)
    }

    fun setAutoRefreshCacheOnStartup(enabled: Boolean) {
        appSettingsStore.setAutoRefreshCacheOnStartup(enabled)
    }

    fun clearImageCache(): Long {
        return appSettingsStore.clearImageCache()
    }

    fun clearWatchHistory() {
        libraryStore.clearHistory()
        _uiState.update { it.copy(historyList = emptyList()) }
    }
}

/**
 * Labels that make a title worth measuring ("4K", "8K", "UHD", "2160p", "3840", "Ultra HD").
 * Only a hint — nothing is shown as 4K until its file is measured.
 */
private val UHD_TAG = Regex("""(?i)(?<![a-z0-9])(4k|8k|uhd|2160p?|4320p?|3840|ultra\s*hd)(?![a-z0-9])""")

/** Parallel portal lookups during a 4K scan; kept low to stay polite to the provider. */
private const val UHD_SCAN_WORKERS = 3
