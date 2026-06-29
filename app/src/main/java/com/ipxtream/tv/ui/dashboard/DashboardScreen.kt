package com.ipxtream.tv.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import com.ipxtream.tv.ui.dashboard.components.TopHeader
import com.ipxtream.tv.ui.dashboard.components.QuickAccessCard
import com.ipxtream.tv.ui.dashboard.components.ContinueWatchingRow
import com.ipxtream.tv.ui.dashboard.components.ContinueWatchingCard
import com.ipxtream.tv.ui.dashboard.components.HomeHighlightsRow
import com.ipxtream.tv.ui.dashboard.components.HomeMoviesRow
import com.ipxtream.tv.ui.dashboard.components.HomeSeriesRow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.data.download.DownloadItem
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.dashboard.components.CategoryRow
import com.ipxtream.tv.ui.dashboard.components.DownloadTray
import com.ipxtream.tv.ui.dashboard.components.DynamicSpotlight
import com.ipxtream.tv.ui.dashboard.components.LiveChannelCard
import com.ipxtream.tv.ui.dashboard.components.SearchBar
import com.ipxtream.tv.ui.dashboard.components.SeriesPosterCard
import com.ipxtream.tv.ui.dashboard.components.SideNavBar
import com.ipxtream.tv.ui.dashboard.components.VodPosterCard
import com.ipxtream.tv.ui.player.PlayerPanel
import com.ipxtream.tv.ui.player.PlayerUiState
import com.ipxtream.tv.ui.player.PlayerViewModel
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.SlatePrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextMuted
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.StrokeCap
import coil.compose.AsyncImage
import com.ipxtream.tv.data.local.LibraryItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusable
import androidx.compose.ui.unit.sp
import com.ipxtream.tv.ui.theme.SlateGlass

private val GRID_COLUMNS_LIVE   = GridCells.Fixed(3)          // narrower when player is visible
private val GRID_COLUMNS_POSTER = GridCells.Adaptive(minSize = 140.dp)

/**
 * Root composable for the Dashboard screen.
 *
 * ## Split Panel Layout
 *
 * When no stream is selected:
 * ```
 * ┌──────────────────────────────────────────────────────────┐
 * │ SideNavBar │ CategoryRow + ContentGrid (full width)      │
 * └──────────────────────────────────────────────────────────┘
 * ```
 *
 * When a stream/episode is selected:
 * ```
 * ┌──────────────────────────────────────────────────────────┐
 * │ SideNavBar │ CategoryRow + Grid (55%) │ PlayerPanel (45%)│
 * └──────────────────────────────────────────────────────────┘
 * ```
 * The PlayerPanel slides in from the right with a 300ms tween.
 *
 * ## D-Pad navigation notes
 * - DPAD_RIGHT from the grid's last column enters the PlayerPanel.
 * - DPAD_LEFT from PlayerPanel returns to the grid (geometric proximity).
 * - PlayerView's built-in controller handles DPAD_UP/DOWN/OK within the player.
 *
 * @param uiState            Dashboard state from [DashboardViewModel].
 * @param playerUiState      Player state from [PlayerViewModel].
 * @param exoPlayer          Live ExoPlayer instance (null only before first stream load).
 * @param onSectionSelected  Section nav callback.
 * @param onCategorySelected Category chip callback.
 * @param onStreamSelected   Called when a Live/VOD card is pressed.
 * @param onSeriesSelected   Called when a Series card is pressed.
 * @param onPlayerStop       Called when the user closes the player panel.
 * @param onRefresh          Force-refresh the current section.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState:            DashboardUiState,
    playerUiState:      PlayerUiState,
    exoPlayer:          ExoPlayer?,
    playerViewModel:    PlayerViewModel,
    downloadItems:      List<DownloadItem>,
    onSectionSelected:  (ContentSection) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onStreamSelected:   (StreamItem) -> Unit,
    onSeriesSelected:   (SeriesItem) -> Unit,
    onPlayerStop:       () -> Unit,
    onRefresh:          () -> Unit,
    onDownloadPause:    (String) -> Unit,
    onDownloadResume:   (String) -> Unit,
    onDownloadCancel:   (String) -> Unit,
    onDownloadRetry:    (String) -> Unit,
    onDownloadClearDone: () -> Unit,
    
    // Playback callbacks from Detail Screens
    onVodPlay:          (StreamItem) -> Unit,
    onEpisodePlay:      (com.ipxtream.tv.data.model.SeriesItem, com.ipxtream.tv.data.model.EpisodeItem) -> Unit,
    onCloseDetails:     () -> Unit,

    onToggleFavoriteStream: (StreamItem) -> Unit,
    onToggleFavoriteSeries: (SeriesItem) -> Unit,
    isStreamFavorite:   (Int, String) -> Boolean,
    isSeriesFavorite:   (Int) -> Boolean,
    onCheckForUpdates:  () -> Unit,
    onDownloadUpdate:   () -> Unit,
    onDismissUpdate:    () -> Unit,
    onNextPage:         () -> Unit,
    onPrevPage:         () -> Unit,
    onSwitchAccount:    (String, String) -> Unit = { _, _ -> },
    onSetDefaultAccount: (String, String) -> Unit = { _, _ -> },
    onRemoveAccount:    (String, String) -> Unit = { _, _ -> },
    onAddAccount:       () -> Unit = {},
    onVodDownload:      (StreamItem) -> Unit = {},
    onEpisodeDownload:  (EpisodeItem) -> Unit = {},
    onCacheAll:         () -> Unit = {},
    onLogout:           () -> Unit = {}
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val updateDialogFocusRequester = remember { FocusRequester() }
    val sideNavFocusRequester = remember { FocusRequester() }
    var isSideNavFocused by remember { mutableStateOf(false) }

    val isOverlayOpen = uiState.detailVodItem != null || uiState.detailSeriesItem != null || uiState.updateRelease != null
    androidx.activity.compose.BackHandler(enabled = !isOverlayOpen && !isSideNavFocused) {
        runCatching { sideNavFocusRequester.requestFocus() }
    }

    var focusedStreamItem by remember { mutableStateOf<StreamItem?>(null) }
    var focusedSeriesItem by remember { mutableStateOf<SeriesItem?>(null) }

    LaunchedEffect(uiState.activeSection, uiState.isLoading) {
        if (!uiState.isLoading && !isSideNavFocused) {
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(uiState.updateRelease) {
        if (uiState.updateRelease != null) {
            runCatching { updateDialogFocusRequester.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SlateDeep)) {
        val showSplitPlayer      = uiState.hasActivePlayback && playerUiState.isLive && exoPlayer != null
        val showFullScreenPlayer = uiState.hasActivePlayback && !playerUiState.isLive && exoPlayer != null

        val isOverlayOpen = uiState.detailVodItem != null || uiState.detailSeriesItem != null || uiState.updateRelease != null

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 66.dp) // Margin for the collapsed side nav
                .focusProperties {
                    canFocus = !isOverlayOpen
                    left = sideNavFocusRequester
                }
        ) {
        // ─── Centre: Category row + content grid + download tray ───────────────
        Box(
            modifier = Modifier
                .weight(if (showSplitPlayer) 0.55f else 1f)
                .fillMaxHeight()
        ) {
            // ─── Phase 10: Dynamic Spotlight Behind Content ───────────────
            DynamicSpotlight(
                streamItem = focusedStreamItem,
                seriesItem = focusedSeriesItem,
                modifier   = Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.TopCenter)
            )

            // Content area grows to fill space above the tray
            Column(modifier = Modifier.fillMaxSize()) {
                TopHeader(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    activeAccount = uiState.activeAccount,
                    isCheckingForUpdate = uiState.isCheckingForUpdate,
                    isCachingAll = uiState.isCachingAll,
                    onCacheAll = onCacheAll,
                    onRefresh = onRefresh,
                    onSettings = { onSectionSelected(ContentSection.SETTINGS) },
                    onSwitchAccount = onAddAccount,
                    onLogout = onLogout,
                    onCheckForUpdates = onCheckForUpdates
                )

                when (uiState.activeSection) {
                    ContentSection.HOME -> {
                        val recentHistory = remember(uiState.historyList) {
                            uiState.historyList.take(10)
                        }
                        if (uiState.searchQuery.isNotBlank()) {
                            // Search Results Mode
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Search Results for \"${uiState.searchQuery}\"",
                                        style = IpxTypography.TitleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                if (uiState.isSearchingHome) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LoadingIndicator()
                                        }
                                    }
                                } else {
                                    // Movies Results Row
                                    item {
                                        Column {
                                            Text(
                                                text = "Movies (${uiState.searchResultsMovies.size} found)",
                                                style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            if (uiState.paginatedSearchMovies.isEmpty()) {
                                                Text(
                                                    text = "No movies match your search.",
                                                    style = IpxTypography.BodyMedium,
                                                    color = TextMuted,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                            } else {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    items(uiState.paginatedSearchMovies.size) { index ->
                                                        val movie = uiState.paginatedSearchMovies[index]
                                                        com.ipxtream.tv.ui.dashboard.components.VodPosterCard(
                                                            stream = movie,
                                                            onClick = { onStreamSelected(movie) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Series Results Row
                                    item {
                                        Column {
                                            Text(
                                                text = "TV Series (${uiState.searchResultsSeries.size} found)",
                                                style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            if (uiState.paginatedSearchSeries.isEmpty()) {
                                                Text(
                                                    text = "No series match your search.",
                                                    style = IpxTypography.BodyMedium,
                                                    color = TextMuted,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                            } else {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    items(uiState.paginatedSearchSeries.size) { index ->
                                                        val series = uiState.paginatedSearchSeries[index]
                                                        com.ipxtream.tv.ui.dashboard.components.SeriesPosterCard(
                                                            series = series,
                                                            onClick = { onSeriesSelected(series) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Pagination Bar
                                    if (uiState.totalSearchPages > 1) {
                                        item {
                                            PaginationBar(
                                                hasPrevPage = uiState.hasPrevPage,
                                                hasNextPage = uiState.hasNextPage,
                                                currentPage = uiState.currentPage,
                                                totalPages = uiState.totalSearchPages,
                                                onPrevPage = onPrevPage,
                                                onNextPage = onNextPage
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Normal Home Layout
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        QuickAccessCard(
                                            title = "LIVE TV",
                                            subtitle = "Stream live broadcasts & channels",
                                            icon = Icons.Rounded.Tv,
                                            themeColor = Color(0xFFE50914),
                                            onClick = { onSectionSelected(ContentSection.LIVE) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(firstItemFocusRequester)
                                        )
                                        QuickAccessCard(
                                            title = "MOVIES",
                                            subtitle = "Watch blockbuster movies on demand",
                                            icon = Icons.Rounded.Movie,
                                            themeColor = Color(0xFF007DFE),
                                            onClick = { onSectionSelected(ContentSection.VOD) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        QuickAccessCard(
                                            title = "TV SERIES",
                                            subtitle = "Binge-watch episodic shows",
                                            icon = Icons.Rounded.Slideshow,
                                            themeColor = Color(0xFF9D3FE7),
                                            onClick = { onSectionSelected(ContentSection.SERIES) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (recentHistory.isNotEmpty()) {
                                    item {
                                        ContinueWatchingRow(
                                            items = recentHistory,
                                            onStreamSelected = onStreamSelected,
                                            onSeriesSelected = onSeriesSelected,
                                            onEpisodePlay = onEpisodePlay
                                        )
                                    }
                                }

                                if (uiState.homeLiveHighlights.isNotEmpty()) {
                                    item {
                                        HomeHighlightsRow(
                                            title = "Live TV Highlights",
                                            items = uiState.homeLiveHighlights,
                                            onStreamSelected = onStreamSelected
                                        )
                                    }
                                }

                                if (uiState.homeHotMovies.isNotEmpty()) {
                                    item {
                                        HomeMoviesRow(
                                            title = "Hot Movies",
                                            items = uiState.homeHotMovies,
                                            onStreamSelected = onStreamSelected
                                        )
                                    }
                                }

                                if (uiState.homePopularSeries.isNotEmpty()) {
                                    item {
                                        HomeSeriesRow(
                                            title = "Popular TV Series",
                                            items = uiState.homePopularSeries,
                                            onSeriesSelected = onSeriesSelected
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ContentSection.WHATS_NEW -> {
                        if (uiState.isLoadingContent) {
                            LoadingIndicator()
                        } else if (uiState.whatsNewItems.isEmpty()) {
                            EmptyPromptMessage("No new high-rated releases found.")
                        } else {
                            WhatsNewGrid(
                                items = uiState.whatsNewItems,
                                onStreamSelected = onStreamSelected,
                                onSeriesSelected = onSeriesSelected,
                                onEpisodePlay = onEpisodePlay
                            )
                        }
                    }
                    ContentSection.SETTINGS -> {
                        com.ipxtream.tv.ui.dashboard.components.SettingsScreen(
                            uiState = uiState,
                            onCheckForUpdates = onCheckForUpdates,
                            onDownloadUpdate = onDownloadUpdate,
                            onDismissUpdate = onDismissUpdate,
                            onSwitchAccount = onSwitchAccount,
                            onSetDefaultAccount = onSetDefaultAccount,
                            onRemoveAccount = onRemoveAccount,
                            onAddAccount = onAddAccount,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    ContentSection.DOWNLOADS -> {
                        com.ipxtream.tv.ui.dashboard.components.DownloadsScreen(
                            downloads = downloadItems,
                            onPause = onDownloadPause,
                            onResume = onDownloadResume,
                            onCancel = onDownloadCancel,
                            onRetry = onDownloadRetry,
                            onClearDone = onDownloadClearDone,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        if (uiState.categories.isNotEmpty()) {
                            CategoryRow(
                                categories         = uiState.categories,
                                selectedCategoryId = uiState.selectedCategoryId,
                                onCategorySelected = onCategorySelected,
                                modifier           = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                uiState.isLoadingContent -> LoadingIndicator()
                                uiState.error != null && uiState.itemCount == 0 ->
                                    ErrorMessage(message = uiState.error, onRetry = onRefresh)
                                uiState.activeSection == ContentSection.MY_LIBRARY ->
                                    LibrarySection(
                                        uiState = uiState,
                                        onStreamSelected = onStreamSelected,
                                        onSeriesSelected = onSeriesSelected,
                                        onEpisodePlay = onEpisodePlay,
                                        onCheckForUpdates = onCheckForUpdates,
                                        onDownloadUpdate = onDownloadUpdate,
                                        onRefresh = onRefresh
                                    )
                                uiState.selectedCategoryId == null && uiState.searchQuery.isBlank() ->
                                    EmptyPromptMessage("Use the search bar above to find content, or select a category.")
                                uiState.activeSection == ContentSection.SERIES ->
                                    SeriesGrid(
                                        seriesList              = uiState.paginatedSeries,
                                        onSeriesSelected        = onSeriesSelected,
                                        firstItemFocusRequester = firstItemFocusRequester,
                                        onItemFocused           = { focusedSeriesItem = it; focusedStreamItem = null },
                                        hasPrevPage             = uiState.hasPrevPage,
                                        hasNextPage             = uiState.hasNextPage,
                                        currentPage             = uiState.currentPage,
                                        totalPages              = uiState.totalPages,
                                        onPrevPage              = onPrevPage,
                                        onNextPage              = onNextPage
                                    )
                                else ->
                                    StreamGrid(
                                        streams                 = uiState.paginatedStreams,
                                        section                 = uiState.activeSection,
                                        onStreamSelected        = onStreamSelected,
                                        firstItemFocusRequester = firstItemFocusRequester,
                                        onItemFocused           = { focusedStreamItem = it; focusedSeriesItem = null },
                                        hasPrevPage             = uiState.hasPrevPage,
                                        hasNextPage             = uiState.hasNextPage,
                                        currentPage             = uiState.currentPage,
                                        totalPages              = uiState.totalPages,
                                        onPrevPage              = onPrevPage,
                                        onNextPage              = onNextPage
                                    )
                            }
                        }
                    }
                }
            }

            // ── Download tray — docked at bottom of centre box ───────────
            if (uiState.activeSection != ContentSection.DOWNLOADS) {
                DownloadTray(
                    downloads    = downloadItems,
                    onPause      = onDownloadPause,
                    onResume     = onDownloadResume,
                    onCancel     = onDownloadCancel,
                    onRetry      = onDownloadRetry,
                    onClearDone  = onDownloadClearDone,
                    modifier     = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // ─── Right: Split Player panel (Live TV only) ────────────────────────
        AnimatedVisibility(
            visible = showSplitPlayer,
            enter   = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec  = tween(durationMillis = 300)
            ),
            exit    = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
        ) {
            if (exoPlayer != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)     // thin divider gap
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                ) {
                    PlayerPanel(
                        exoPlayer = exoPlayer,
                        uiState   = playerUiState,
                        viewModel = playerViewModel,
                        onStop    = onPlayerStop,
                        modifier  = Modifier.fillMaxSize()
                    )
                }
            }
        }
        } // Close Grid/Player Row

        // ─── Floating Sidebar (OVERLAYS content gracefully) ──────────────────
        SideNavBar(
            activeSection     = uiState.activeSection,
            onSectionSelected = onSectionSelected,
            onRefresh         = onRefresh,
            sideNavFocusRequester = sideNavFocusRequester,
            firstItemFocusRequester = firstItemFocusRequester,
            onFocusChanged    = { isSideNavFocused = it },
            modifier          = Modifier
                .align(Alignment.CenterStart)
                .focusProperties { canFocus = !isOverlayOpen }
        )
                
        // ─── Overlay Phase: Detail Screens (Phase 7) ─────────────────────────
        if (uiState.detailVodItem != null) {
            com.ipxtream.tv.ui.dashboard.components.VodDetailScreen(
                streamItem    = uiState.detailVodItem,
                onPlay        = { onVodPlay(uiState.detailVodItem) },
                onDownload    = { onVodDownload(uiState.detailVodItem) },
                onClose       = onCloseDetails,
                isFavorite    = isStreamFavorite(uiState.detailVodItem.streamId, "movie"),
                onToggleFavorite = { onToggleFavoriteStream(uiState.detailVodItem) }
            )
        } else if (uiState.detailSeriesItem != null) {
            com.ipxtream.tv.ui.dashboard.components.SeriesDetailScreen(
                seriesItem          = uiState.detailSeriesItem,
                seriesInfo          = uiState.seriesInfo,
                isLoadingSeriesInfo = uiState.isLoadingSeriesInfo,
                onEpisodePlay       = { episode -> onEpisodePlay(uiState.detailSeriesItem, episode) },
                onEpisodeDownload   = onEpisodeDownload,
                onClose             = onCloseDetails,
                isFavorite    = isSeriesFavorite(uiState.detailSeriesItem.seriesId),
                onToggleFavorite = { onToggleFavoriteSeries(uiState.detailSeriesItem) }
            )
        }

        // ─── Overlay Phase: Full-Screen Player (VOD & Series) ─────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = showFullScreenPlayer,
            enter   = androidx.compose.animation.fadeIn(tween(300)),
            exit    = androidx.compose.animation.fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (exoPlayer != null) {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    PlayerPanel(
                        exoPlayer = exoPlayer,
                        uiState   = playerUiState,
                        viewModel = playerViewModel,
                        onStop    = onPlayerStop,
                        modifier  = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ─── Update Dialog overlay ───────────────────────────────────────────
        if (uiState.updateRelease != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlatePrimary)
                        .padding(32.dp)
                        .width(420.dp)
                ) {
                    Text("App Update Available", style = IpxTypography.TitleMedium, color = AccentCyan)

                    val release = uiState.updateRelease
                    Text(
                        text = "A new version (${release.tagName}) is available. Would you like to download and install it?",
                        style = IpxTypography.BodyMedium,
                        color = TextPrimary
                    )

                    if (release.body != null) {
                        Text(
                            text = release.body,
                            style = IpxTypography.BodySmall,
                            color = TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (uiState.updateDownloadProgress != null) {
                        val progress = uiState.updateDownloadProgress
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Downloading: ${(progress * 100).toInt()}%", style = IpxTypography.BodySmall, color = AccentCyan)
                            LinearProgressIndicator(
                                progress = { progress },
                                color = AccentCyan,
                                trackColor = Color.White.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }
                    } else {
                        if (uiState.updateErrorMessage != null) {
                            Text(uiState.updateErrorMessage, style = IpxTypography.BodySmall, color = Color.Red)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.tv.material3.Button(
                                onClick = onDownloadUpdate,
                                modifier = Modifier.focusRequester(updateDialogFocusRequester)
                            ) {
                                Text("Install Now")
                            }
                            androidx.tv.material3.Button(onClick = onDismissUpdate) {
                                Text("Later")
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
//  Grid sub-composables (unchanged from Phase 3, kept here for co-location)
// =============================================================================

@Composable
private fun StreamGrid(
    streams:                List<StreamItem>,
    section:                ContentSection,
    onStreamSelected:       (StreamItem) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onItemFocused:          (StreamItem?) -> Unit,
    hasPrevPage:            Boolean,
    hasNextPage:            Boolean,
    currentPage:            Int,
    totalPages:             Int,
    onPrevPage:             () -> Unit,
    onNextPage:             () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns        = if (section == ContentSection.LIVE) GRID_COLUMNS_LIVE else GRID_COLUMNS_POSTER,
            state          = rememberLazyGridState(),
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp, start = 12.dp, top = 24.dp, end = 12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(streams, key = { _, s -> s.streamId }) { index, stream ->
                var isFocused by remember { mutableStateOf(false) }
                val mod = Modifier
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (isFocused) onItemFocused(stream)
                    }
                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)

                if (section == ContentSection.LIVE) {
                    LiveChannelCard(stream = stream, onClick = { onStreamSelected(stream) }, modifier = mod)
                } else {
                    VodPosterCard(stream = stream, onClick = { onStreamSelected(stream) }, modifier = mod)
                }
            }
        }

        PaginationBar(
            hasPrevPage = hasPrevPage,
            hasNextPage = hasNextPage,
            currentPage = currentPage,
            totalPages  = totalPages,
            onPrevPage  = onPrevPage,
            onNextPage  = onNextPage
        )
    }
}

@Composable
private fun SeriesGrid(
    seriesList:             List<SeriesItem>,
    onSeriesSelected:       (SeriesItem) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onItemFocused:          (SeriesItem?) -> Unit,
    hasPrevPage:            Boolean,
    hasNextPage:            Boolean,
    currentPage:            Int,
    totalPages:             Int,
    onPrevPage:             () -> Unit,
    onNextPage:             () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns        = GRID_COLUMNS_POSTER,
            state          = rememberLazyGridState(),
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp, start = 12.dp, top = 24.dp, end = 12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(seriesList, key = { _, s -> s.seriesId }) { index, series ->
                var isFocused by remember { mutableStateOf(false) }
                val mod = Modifier
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (isFocused) onItemFocused(series)
                    }
                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                    
                SeriesPosterCard(series = series, onClick = { onSeriesSelected(series) }, modifier = mod)
            }
        }

        PaginationBar(
            hasPrevPage = hasPrevPage,
            hasNextPage = hasNextPage,
            currentPage = currentPage,
            totalPages  = totalPages,
            onPrevPage  = onPrevPage,
            onNextPage  = onNextPage
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, itemCount: Int, isFromCache: Boolean, currentPage: Int, totalPages: Int) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = IpxTypography.TitleLarge, color = AccentCyan)
        Spacer(Modifier.weight(1f))
        if (itemCount > 0) {
            Text(
                text  = "$itemCount items${if (isFromCache) " · cached" else ""}${if (totalPages > 1) " · Page ${currentPage + 1}/$totalPages" else ""}",
                style = IpxTypography.BodyMedium,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingIndicator() {
    Text("Loading…", style = IpxTypography.TitleMedium, color = TextSecondary,
        modifier = Modifier.wrapContentSize())
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Text("⚠ $message", style = IpxTypography.BodyMedium, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        androidx.tv.material3.Button(onClick = onRetry) { androidx.tv.material3.Text("Retry") }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyPromptMessage(message: String) {
    androidx.tv.material3.Text(
        text = "🔍 $message",
        style = IpxTypography.TitleMedium,
        color = TextSecondary,
        modifier = Modifier.wrapContentSize()
    )
}

@Composable
private fun LibrarySection(
    uiState: DashboardUiState,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (com.ipxtream.tv.data.model.SeriesItem, EpisodeItem) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRefresh: () -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, top = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // App Updates Card
        item {
            androidx.tv.material3.Card(
                onClick = {
                    if (uiState.updateRelease != null) {
                        onDownloadUpdate()
                    } else {
                        onCheckForUpdates()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(12.dp)),
                colors = androidx.tv.material3.CardDefaults.colors(
                    containerColor = SlatePrimary,
                    focusedContainerColor = SlateGlass
                ),
                border = androidx.tv.material3.CardDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(12.dp))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Updates", style = IpxTypography.TitleMedium, color = AccentCyan)
                        Spacer(Modifier.height(4.dp))
                        val currentVersion = com.ipxtream.tv.BuildConfig.VERSION_NAME
                        if (uiState.isCheckingForUpdate) {
                            Text("Checking for updates...", style = IpxTypography.BodySmall, color = TextSecondary)
                        } else if (uiState.updateRelease != null) {
                            Text("New version ${uiState.updateRelease.tagName} available! (Current: v$currentVersion)", style = IpxTypography.BodySmall, color = TextPrimary)
                        } else {
                            Text("Up to date (v$currentVersion)", style = IpxTypography.BodySmall, color = TextMuted)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    if (uiState.isCheckingForUpdate) {
                        Text("⏳", style = IpxTypography.TitleMedium)
                    } else if (uiState.updateRelease != null) {
                        androidx.tv.material3.Button(onClick = onDownloadUpdate) {
                            Text("Download Update")
                        }
                    } else {
                        androidx.tv.material3.Button(onClick = onCheckForUpdates) {
                            Text("Check for Updates")
                        }
                    }
                }
            }
        }

        if (uiState.favoritesList.isEmpty() && uiState.historyList.isEmpty()) {
            item {
                EmptyPromptMessage("Your library is empty. Mark items as favorite or play some content first!")
            }
        } else {
            if (uiState.historyList.isNotEmpty()) {
                item {
                    Text("Resume Playback", style = IpxTypography.TitleMedium, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        items(uiState.historyList) { item ->
                            LibraryCard(item = item, onStreamSelected = onStreamSelected, onSeriesSelected = onSeriesSelected, onEpisodePlay = onEpisodePlay)
                        }
                    }
                }
            }

            if (uiState.favoritesList.isNotEmpty()) {
                item {
                    Text("Favorites", style = IpxTypography.TitleMedium, color = AccentCyan)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        items(uiState.favoritesList) { item ->
                            LibraryCard(item = item, onStreamSelected = onStreamSelected, onSeriesSelected = onSeriesSelected, onEpisodePlay = onEpisodePlay)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryCard(
    item: LibraryItem,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (com.ipxtream.tv.data.model.SeriesItem, EpisodeItem) -> Unit
) {
    when (item.type) {
        "live" -> {
            val stream = remember(item) {
                StreamItem(
                    streamId = item.id.toInt(),
                    name = item.name,
                    streamType = "live",
                    streamIcon = item.iconUrl,
                    categoryId = item.categoryId,
                    added = null, num = null, rating = null, rating5based = null, containerExtension = null, epgChannelId = null, tvArchive = null, tvArchiveDuration = null, directSource = null, customSid = null
                )
            }
            LiveChannelCard(stream = stream, onClick = { onStreamSelected(stream) })
        }
        "movie" -> {
            val stream = remember(item) {
                StreamItem(
                    streamId = item.id.toInt(),
                    name = item.name,
                    streamType = "movie",
                    streamIcon = item.iconUrl,
                    categoryId = item.categoryId,
                    added = null, num = null, rating = item.rating, rating5based = null, containerExtension = item.containerExtension, epgChannelId = null, tvArchive = null, tvArchiveDuration = null, directSource = null, customSid = null
                )
            }
            VodPosterCard(stream = stream, onClick = { onStreamSelected(stream) })
        }
        "series" -> {
            val series = remember(item) {
                SeriesItem(
                    seriesId = item.id.toInt(),
                    name = item.name,
                    cover = item.iconUrl,
                    plot = null, cast = null, director = null, genre = null, releaseDate = null, lastModified = null, rating = item.rating, rating5based = null, backdropPath = null, youtubeTrailer = null, episodeRunTime = null, categoryId = item.categoryId
                )
            }
            SeriesPosterCard(series = series, onClick = { onSeriesSelected(series) })
        }
        "episode" -> {
            val series = remember(item) {
                SeriesItem(
                    seriesId = item.parentId?.toIntOrNull() ?: 0,
                    name = item.name.substringBefore(" - "),
                    cover = item.iconUrl,
                    plot = null, cast = null, director = null, genre = null, releaseDate = null, lastModified = null, rating = null, rating5based = null, backdropPath = null, youtubeTrailer = null, episodeRunTime = null, categoryId = null
                )
            }
            val episode = remember(item) {
                EpisodeItem(
                    id = item.id,
                    episodeNum = 1,
                    title = item.name.substringAfter(" - ", item.name),
                    containerExtension = item.containerExtension ?: "mp4",
                    info = null,
                    season = 1,
                    added = null, customSid = null, directSource = null
                )
            }
            
            androidx.tv.material3.Card(
                onClick = { onEpisodePlay(series, episode) },
                modifier = Modifier.size(240.dp, 135.dp),
                shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(12.dp)),
                colors = androidx.tv.material3.CardDefaults.colors(
                    containerColor = SlatePrimary,
                    focusedContainerColor = SlateGlass
                ),
                border = androidx.tv.material3.CardDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(12.dp))
                )
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (item.iconUrl != null) {
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xEE0B1520))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.name,
                            style = IpxTypography.LabelSmall,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (item.durationMs > 0L) {
                            val fraction = (item.lastWatchedPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                            Column {
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = AccentCyan,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${formatTime(item.lastWatchedPositionMs)} left",
                                    style = IpxTypography.LabelSmall.copy(fontSize = 10.sp),
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

// =============================================================================
//  Pagination Bar
// =============================================================================

/**
 * Horizontal navigation bar with Previous / Next buttons and page indicator.
 * Only renders when [totalPages] > 1 to avoid unnecessary UI clutter.
 * Fully D-pad navigable — each button is individually focusable.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PaginationBar(
    hasPrevPage: Boolean,
    hasNextPage: Boolean,
    currentPage: Int,
    totalPages:  Int,
    onPrevPage:  () -> Unit,
    onNextPage:  () -> Unit
) {
    if (totalPages <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // ── Previous Button ──
        androidx.tv.material3.Button(
            onClick  = onPrevPage,
            enabled  = hasPrevPage,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            androidx.tv.material3.Text("◀", modifier = Modifier.padding(end = 4.dp))
            androidx.tv.material3.Text("Previous")
        }

        // ── Page indicator ──
        Text(
            text  = "Page ${currentPage + 1} of $totalPages",
            style = IpxTypography.BodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // ── Next Button ──
        androidx.tv.material3.Button(
            onClick  = onNextPage,
            enabled  = hasNextPage,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            androidx.tv.material3.Text("Next")
            androidx.tv.material3.Text("▶", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun WhatsNewGrid(
    items: List<LibraryItem>,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (SeriesItem, EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, top = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]
            ContinueWatchingCard(
                item = item,
                onStreamSelected = onStreamSelected,
                onSeriesSelected = onSeriesSelected,
                onEpisodePlay = onEpisodePlay
            )
        }
    }
}
