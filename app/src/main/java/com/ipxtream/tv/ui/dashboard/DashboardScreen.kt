package com.ipxtream.tv.ui.dashboard

import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.SystemUpdate
import com.ipxtream.tv.ui.dashboard.components.PosterGrid
import com.ipxtream.tv.ui.theme.SurfaceElevated
import com.ipxtream.tv.ui.theme.AccentOrchid
import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.data.local.AppLanguage
import com.ipxtream.tv.data.local.AspectRatioMode
import com.ipxtream.tv.data.local.BufferProfile
import com.ipxtream.tv.data.local.DecoderMode
import com.ipxtream.tv.data.local.SubtitleTextSize
import com.ipxtream.tv.data.download.DownloadItem
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.dashboard.components.CategoryRow
import com.ipxtream.tv.ui.dashboard.components.DownloadTray
import com.ipxtream.tv.ui.dashboard.components.DynamicSpotlight
import com.ipxtream.tv.ui.dashboard.components.HeroBackdrop
import com.ipxtream.tv.ui.dashboard.components.HeroInfo
import com.ipxtream.tv.ui.dashboard.components.PremiumButton
import com.ipxtream.tv.ui.dashboard.components.buildHeroFeatures
import com.ipxtream.tv.ui.theme.IpxMotion
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.trackFocusTravel
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import com.ipxtream.tv.ui.dashboard.components.LiveChannelCard
import com.ipxtream.tv.ui.dashboard.components.PaginationBar
import com.ipxtream.tv.ui.dashboard.components.SearchBar
import com.ipxtream.tv.ui.dashboard.components.SeriesPosterCard
import com.ipxtream.tv.ui.dashboard.components.SideNavBar
import com.ipxtream.tv.ui.dashboard.components.VodPosterCard
import com.ipxtream.tv.ui.player.PlayerPanel
import com.ipxtream.tv.ui.player.PlayerUiState
import com.ipxtream.tv.ui.player.PlayerViewModel
import com.ipxtream.tv.ui.theme.Accent
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
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.BorderFocus
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.ProgressWatched

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
 * @param playback           What is playing (changes per title, not per second).
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
    playback:           com.ipxtream.tv.ui.player.PlaybackSummary,
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
    onLogout:           () -> Unit = {},
    onAppLanguageChange: (AppLanguage) -> Unit = {},
    onToggleShowWhatsNew: (Boolean) -> Unit = {},
    onToggleShowDownloads: (Boolean) -> Unit = {},
    onToggleShowLibrary: (Boolean) -> Unit = {},
    onToggleShowSpotlight: (Boolean) -> Unit = {},
    onPerformanceModeChange: (com.ipxtream.tv.data.local.PerformanceMode) -> Unit = {},
    onToggleShowChannelNumbers: (Boolean) -> Unit = {},
    onToggle24HourClock: (Boolean) -> Unit = {},
    onBufferProfileChange: (BufferProfile) -> Unit = {},
    onToggleAutoPlayNextEpisode: (Boolean) -> Unit = {},
    onAspectRatioChange: (AspectRatioMode) -> Unit = {},
    onDecoderModeChange: (DecoderMode) -> Unit = {},
    onToggleKeepScreenAwake: (Boolean) -> Unit = {},
    onPreferredAudioLanguageChange: (String) -> Unit = {},
    onToggleDefaultSubtitles: (Boolean) -> Unit = {},
    onPreferredSubtitleLanguageChange: (String) -> Unit = {},
    onSubtitleTextSizeChange: (SubtitleTextSize) -> Unit = {},
    onToggleAutoRefreshCache: (Boolean) -> Unit = {},
    onClearImageCache: () -> Unit = {},
    onClearWatchHistory: () -> Unit = {},
    onUhdScanLibrary: () -> Unit = {},
    onUhdStopScan: () -> Unit = {},
    onMatchDaySelected: (Int) -> Unit = {},
    onMatchLeaguesChanged: (Set<String>) -> Unit = {},
    onPlayMatch: (com.ipxtream.tv.ui.dashboard.MatchListing, StreamItem) -> Unit = { _, _ -> },
    onPickMatchChannel: (com.ipxtream.tv.ui.dashboard.MatchListing, StreamItem, Boolean) -> Unit = { _, _, _ -> }
) {
    val focusManager = LocalFocusManager.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val searchFirstCardFocusRequester = remember { FocusRequester() }
    val updateDialogFocusRequester = remember { FocusRequester() }
    val sideNavFocusRequester = remember { FocusRequester() }
    val categoryRowFocusRequester = remember { FocusRequester() }
    val liveTvCardFocusRequester = remember { FocusRequester() }
    val moviesCardFocusRequester = remember { FocusRequester() }
    val seriesCardFocusRequester = remember { FocusRequester() }
    val userProfileFocusRequester = remember { FocusRequester() }
    var isSideNavFocused by remember { mutableStateOf(false) }
    // Live/Movies/Series: true while the user was last on a poster rather than a category chip,
    // so Right from the side menu goes back to that poster instead of the chips.
    var lastContentInGrid by remember { mutableStateOf(false) }
    // Home and My Library: the last focused shelf card, for the same trip back from the menu.
    val homeFocusMemory = remember { com.ipxtream.tv.ui.dashboard.components.FocusMemory() }
    val libraryFocusMemory = remember { com.ipxtream.tv.ui.dashboard.components.FocusMemory() }
    val matchesFocusMemory = remember { com.ipxtream.tv.ui.dashboard.components.FocusMemory() }
    LaunchedEffect(uiState.activeSection) {
        lastContentInGrid = false
        homeFocusMemory.forget()
        libraryFocusMemory.forget()
        matchesFocusMemory.forget()
    }
    // Back to the page after details, the player or a dialog: the remembered shelf card on
    // Home / My Library (the hero or update card may have scrolled away), else the page's target.
    val restorePageFocus: () -> Unit = {
        val restored = when (uiState.activeSection) {
            ContentSection.HOME       -> homeFocusMemory.restore()
            ContentSection.MY_LIBRARY -> libraryFocusMemory.restore()
            ContentSection.MATCHES    -> matchesFocusMemory.restore()
            else                      -> false
        }
        if (!restored) runCatching { firstItemFocusRequester.requestFocus() }
    }

    // My Library's first card leaves composition when the list is scrolled; scroll back first.
    val libraryListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val focusScope = androidx.compose.runtime.rememberCoroutineScope()

    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val focusContentForSection: () -> Unit = {
        val ok: Boolean = when (uiState.activeSection) {
            ContentSection.MY_LIBRARY -> {
                if (!libraryFocusMemory.restore()) {
                    focusScope.launch {
                        libraryListState.scrollToItem(0)
                        runCatching { firstItemFocusRequester.requestFocus() }
                    }
                }
                true
            }
            ContentSection.HOME -> {
                if (uiState.searchQuery.isNotBlank()) {
                    runCatching { searchFirstCardFocusRequester.requestFocus() }.isSuccess
                } else {
                    homeFocusMemory.restore() ||
                        runCatching { liveTvCardFocusRequester.requestFocus() }.isSuccess
                }
            }
            ContentSection.MATCHES -> {
                matchesFocusMemory.restore() ||
                    runCatching { firstItemFocusRequester.requestFocus() }.isSuccess
            }
            ContentSection.LIVE, ContentSection.VOD, ContentSection.SERIES -> {
                val (first, second) =
                    if (lastContentInGrid) firstItemFocusRequester to categoryRowFocusRequester
                    else categoryRowFocusRequester to firstItemFocusRequester
                runCatching { first.requestFocus() }.isSuccess ||
                    runCatching { second.requestFocus() }.isSuccess
            }
            else -> {
                runCatching { firstItemFocusRequester.requestFocus() }.isSuccess
            }
        }
        if (!ok) {
            // Screens without that requester (Settings, Downloads): step from the menu toward
            // the content instead of leaving focus stuck in the menu.
            val moved = runCatching { firstItemFocusRequester.requestFocus() }.isSuccess
            if (!moved) {
                runCatching {
                    focusManager.moveFocus(
                        if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) FocusDirection.Left
                        else FocusDirection.Right
                    )
                }
            }
        }
    }

    // Movies/episodes, and a match started from Matches, play full screen; other live channels split.
    val showFullScreenPlayer = uiState.hasActivePlayback && (!playback.isLive || uiState.playFullScreen) && exoPlayer != null
    val isOverlayOpen = uiState.detailVodItem != null || uiState.detailSeriesItem != null || uiState.isUpdateDialogOpen || showFullScreenPlayer
    val isDetailsOpen = uiState.detailVodItem != null || uiState.detailSeriesItem != null

    androidx.activity.compose.BackHandler(enabled = showFullScreenPlayer) {
        onPlayerStop()
    }

    androidx.activity.compose.BackHandler(enabled = !isOverlayOpen && uiState.searchQuery.isNotBlank()) {
        onSearchQueryChange("")
    }

    androidx.activity.compose.BackHandler(enabled = !isOverlayOpen && !isSideNavFocused && uiState.searchQuery.isBlank()) {
        runCatching { sideNavFocusRequester.requestFocus() }
    }

    // Held as State objects and read only inside SpotlightLayer, so moving between posters
    // recomposes the backdrop alone instead of the whole dashboard.
    val focusedStreamItem = remember { mutableStateOf<StreamItem?>(null) }
    val focusedSeriesItem = remember { mutableStateOf<SeriesItem?>(null) }

    // ─── Home billboard ─────────────────────────────────────────────────────
    val heroFeatures = remember(uiState.homeHotMovies, uiState.homePopularSeries) {
        buildHeroFeatures(uiState.homeHotMovies, uiState.homePopularSeries)
    }
    var heroIndex by remember { mutableIntStateOf(0) }
    if (heroIndex >= heroFeatures.size && heroFeatures.isNotEmpty()) heroIndex = 0
    val homeListState = rememberLazyListState()
    val heroHeight = 430.dp
    val heroHeightPx = with(LocalDensity.current) { heroHeight.toPx() }
    // Read only inside draw (graphicsLayer) — reading it here would rebuild the whole
    // dashboard on every scroll frame.
    val heroScrollProgress = remember {
        derivedStateOf {
            if (homeListState.firstVisibleItemIndex > 0) 1f
            else (homeListState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
        }
    }
    val hasHero = uiState.activeSection == ContentSection.HOME && uiState.searchQuery.isBlank() && heroFeatures.isNotEmpty()

    // Auto-advance like the TV app's billboard; pauses once it has scrolled away.
    val heroScrolledAway by remember { derivedStateOf { heroScrollProgress.value >= 1f } }
    LaunchedEffect(hasHero, heroIndex, heroFeatures.size, heroScrolledAway, isOverlayOpen) {
        if (hasHero && heroFeatures.size > 1 && !heroScrolledAway && !isOverlayOpen) {
            delay(9_000)
            heroIndex = (heroIndex + 1) % heroFeatures.size
        }
    }

    // Billboard data usually lands after the first frame; the list would otherwise keep
    // its anchor on the tiles and leave the billboard scrolled out above the fold.
    LaunchedEffect(hasHero) {
        if (hasHero) {
            homeListState.scrollToItem(0)
            if (!isSideNavFocused && !isOverlayOpen) {
                runCatching { firstItemFocusRequester.requestFocus() }
            }
        }
    }

    // Each section fades up into place instead of snapping in.
    val sectionEntrance = remember { Animatable(1f) }
    val reducedEffects = com.ipxtream.tv.ui.theme.LocalReducedEffects.current
    LaunchedEffect(uiState.activeSection) {
        if (reducedEffects) return@LaunchedEffect
        sectionEntrance.snapTo(0f)
        sectionEntrance.animateTo(1f, tween(IpxMotion.Slow, easing = IpxMotion.EaseApple))
    }

    LaunchedEffect(uiState.activeSection, uiState.isLoading, uiState.searchQuery.isBlank()) {
        if (!uiState.isLoading && !isSideNavFocused && uiState.searchQuery.isBlank() && !isOverlayOpen) {
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    // Closing the full-screen player must hand focus back to the page; otherwise it falls to
    // the first focusable (the search field) and the keyboard pops up.
    var prevPlayerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showFullScreenPlayer) {
        if (prevPlayerOpen && !showFullScreenPlayer && !isDetailsOpen) {
            // Wait out the player's 300 ms fade-out; it holds focus until it leaves composition.
            kotlinx.coroutines.delay(360)
            restorePageFocus()
        }
        prevPlayerOpen = showFullScreenPlayer
    }

    var prevDetailsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isDetailsOpen) {
        // Details closed because playback started: the player owns focus; the player-close
        // effect above restores the page afterwards.
        if (prevDetailsOpen && !isDetailsOpen && !uiState.isLoading && !isSideNavFocused && !showFullScreenPlayer) {
            // Let the page become focusable again (it's disabled while an overlay is up).
            kotlinx.coroutines.delay(80)
            if (uiState.searchQuery.isNotBlank()) {
                runCatching { searchFirstCardFocusRequester.requestFocus() }
            } else {
                restorePageFocus()
            }
        }
        prevDetailsOpen = isDetailsOpen
    }

    var hadUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isUpdateDialogOpen) {
        if (uiState.isUpdateDialogOpen) {
            hadUpdateDialog = true
            runCatching { updateDialogFocusRequester.requestFocus() }
        } else if (hadUpdateDialog) {
            // Hand focus back to the page instead of letting it fall to the search field.
            hadUpdateDialog = false
            restorePageFocus()
        }
    }

    // A match playing from Matches steps through that match's channels (beIN 1 → beIN 2…).
    val liveChannelList = remember(uiState.displayedStreams, uiState.streams, uiState.activeSection, uiState.matchChannelQueue) {
        when {
            uiState.activeSection == ContentSection.MATCHES && uiState.matchChannelQueue.isNotEmpty() -> uiState.matchChannelQueue
            uiState.displayedStreams.isNotEmpty() -> uiState.displayedStreams
            else -> uiState.streams
        }
    }
    val currentLiveIndex = remember(playback.activeStream, uiState.selectedStream, liveChannelList) {
        val activeId = playback.activeStream?.streamId ?: uiState.selectedStream?.streamId
        if (activeId != null) {
            liveChannelList.indexOfFirst { it.streamId == activeId }
        } else -1
    }
    val nextChannelAction: (() -> Unit)? = if (playback.isLive && liveChannelList.isNotEmpty()) {
        {
            val nextIdx = if (currentLiveIndex >= 0) (currentLiveIndex + 1) % liveChannelList.size else 0
            onStreamSelected(liveChannelList[nextIdx])
        }
    } else null

    val prevChannelAction: (() -> Unit)? = if (playback.isLive && liveChannelList.isNotEmpty()) {
        {
            val prevIdx = if (currentLiveIndex > 0) currentLiveIndex - 1 else liveChannelList.size - 1
            onStreamSelected(liveChannelList[prevIdx])
        }
    } else null

    Box(modifier = Modifier.fillMaxSize().background(SlateDeep).trackFocusTravel()) {
        val showSplitPlayer = uiState.hasActivePlayback && playback.isLive && !uiState.playFullScreen && exoPlayer != null

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 66.dp) // Margin for the collapsed side nav
                .focusProperties {
                    canFocus = !isOverlayOpen
                }
        ) {
        // ─── Centre: Category row + content grid + download tray ───────────────
        Box(
            modifier = Modifier
                .weight(if (showSplitPlayer) 0.55f else 1f)
                .fillMaxHeight()
        ) {
            // ─── Phase 10: Dynamic Spotlight Behind Content ───────────────
            if (hasHero) {
                HeroBackdrop(
                    feature        = heroFeatures.getOrNull(heroIndex),
                    scrollProgress = { heroScrollProgress.value },
                    animate        = !isOverlayOpen && !heroScrolledAway,
                    modifier       = Modifier.fillMaxWidth().height(heroHeight + 120.dp).align(Alignment.TopCenter)
                )
            } else if (uiState.settings.showSpotlightBackdrop && !reducedEffects && uiState.activeSection != ContentSection.HOME) {
                SpotlightLayer(
                    stream   = focusedStreamItem,
                    series   = focusedSeriesItem,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.TopCenter)
                )
            }

            val isHomeSection = uiState.activeSection == ContentSection.HOME
            // Sections without a category row: the header's D-Pad Down goes to their first item.
            val isLibrarySection = uiState.activeSection == ContentSection.MY_LIBRARY ||
                uiState.activeSection == ContentSection.UHD ||
                uiState.activeSection == ContentSection.MATCHES
            val isHomeNormal = isHomeSection && uiState.searchQuery.isBlank()
            // Header → content: a requester that isn't attached in this section crashes focus
            // search, so only name one that exists; otherwise let geometry pick (Settings, Downloads).
            val hasCategoryRow = uiState.categories.isNotEmpty() && uiState.activeSection in
                setOf(ContentSection.LIVE, ContentSection.VOD, ContentSection.SERIES)
            val hasFirstItem = when (uiState.activeSection) {
                ContentSection.MY_LIBRARY -> true
                ContentSection.MATCHES -> true
                ContentSection.UHD -> !uiState.isLoadingContent
                ContentSection.WHATS_NEW -> uiState.whatsNewItems.isNotEmpty()
                ContentSection.LIVE, ContentSection.VOD, ContentSection.SERIES ->
                    !uiState.isLoadingContent && uiState.itemCount > 0
                else -> false
            }
            val headerDownTarget = when {
                hasCategoryRow -> categoryRowFocusRequester
                hasFirstItem   -> firstItemFocusRequester
                else           -> FocusRequester.Default
            }
            val hasSearchResults = isHomeSection && uiState.searchQuery.isNotBlank() && !uiState.isSearchingHome &&
                    (uiState.paginatedSearchMovies.isNotEmpty() || uiState.paginatedSearchSeries.isNotEmpty())

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
                    onCheckForUpdates = onCheckForUpdates,
                    updateRelease = uiState.updateRelease,
                    onSearchLeft = { runCatching { sideNavFocusRequester.requestFocus() } },
                    searchModifier = Modifier
                        .focusRequester(searchBarFocusRequester)
                        .focusProperties {
                            down = if (isHomeNormal) liveTvCardFocusRequester else headerDownTarget
                        },
                    actionButtonsModifier = Modifier.focusProperties {
                        down = if (isHomeNormal) {
                            if (hasHero) liveTvCardFocusRequester else moviesCardFocusRequester
                        } else headerDownTarget
                    },
                    userProfileModifier = Modifier
                        .focusRequester(userProfileFocusRequester)
                        .focusProperties {
                            down = if (isHomeNormal) {
                                if (hasHero) liveTvCardFocusRequester else seriesCardFocusRequester
                            } else headerDownTarget
                        },
                    onSearchDown = {
                        when {
                            hasSearchResults -> {
                                val ok = runCatching { searchFirstCardFocusRequester.requestFocus() }.isSuccess
                                if (!ok) {
                                    runCatching { focusManager.moveFocus(FocusDirection.Down) }
                                }
                            }
                            isHomeNormal -> {
                                val ok = runCatching { liveTvCardFocusRequester.requestFocus() }.isSuccess
                                if (!ok) {
                                    runCatching { focusManager.moveFocus(FocusDirection.Down) }
                                }
                            }
                            isLibrarySection -> {
                                val ok = runCatching { firstItemFocusRequester.requestFocus() }.isSuccess
                                if (!ok) {
                                    runCatching { focusManager.moveFocus(FocusDirection.Down) }
                                }
                            }
                            else -> {
                                val ok = runCatching { categoryRowFocusRequester.requestFocus() }.isSuccess
                                if (!ok) {
                                    val firstOk = runCatching { firstItemFocusRequester.requestFocus() }.isSuccess
                                    if (!firstOk) {
                                        runCatching { focusManager.moveFocus(FocusDirection.Down) }
                                    }
                                }
                            }
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val t = sectionEntrance.value
                            alpha = t
                            translationY = (1f - t) * 28.dp.toPx()
                        }
                ) {
                Column(modifier = Modifier.fillMaxSize()) {
                when (uiState.activeSection) {
                    ContentSection.HOME -> {
                        val recentHistory = remember(uiState.historyList) {
                            uiState.historyList.take(10)
                        }
                        if (uiState.searchQuery.isNotBlank()) {
                            com.ipxtream.tv.ui.dashboard.components.HomeSearchResultsView(
                                searchQuery = uiState.searchQuery,
                                isSearching = uiState.isSearchingHome,
                                movies = uiState.paginatedSearchMovies,
                                series = uiState.paginatedSearchSeries,
                                totalMoviesCount = uiState.searchResultsMovies.size,
                                totalSeriesCount = uiState.searchResultsSeries.size,
                                totalSearchPages = uiState.totalSearchPages,
                                currentPage = uiState.currentPage,
                                hasPrevPage = uiState.hasPrevPage,
                                hasNextPage = uiState.hasNextPage,
                                onPrevPage = onPrevPage,
                                onNextPage = onNextPage,
                                onStreamSelected = onStreamSelected,
                                onSeriesSelected = onSeriesSelected,
                                firstCardFocusRequester = searchFirstCardFocusRequester,
                                searchBarFocusRequester = searchBarFocusRequester,
                                sideNavFocusRequester = sideNavFocusRequester,
                                watchProgress = { uiState.getWatchProgress(it) }
                            )
                        } else {
                            // Normal Home Layout
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.ipxtream.tv.ui.dashboard.components.LocalFocusMemory provides homeFocusMemory
                            ) {
                            LazyColumn(
                                state = homeListState,
                                // The list itself must not take focus (TV scroll containers do):
                                // the menu's fallback move would land on it with nothing lit.
                                modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                if (hasHero) {
                                    item(key = "hero") {
                                        val feature = heroFeatures[heroIndex.coerceIn(heroFeatures.indices)]
                                        HeroInfo(
                                            features = heroFeatures,
                                            index = heroIndex,
                                            isFavorite = feature.stream?.let { isStreamFavorite(it.streamId, "movie") }
                                                ?: feature.series?.let { isSeriesFavorite(it.seriesId) } ?: false,
                                            onPrimary = { f ->
                                                f.stream?.let(onVodPlay) ?: f.series?.let(onSeriesSelected)
                                            },
                                            onDetails = { f -> f.stream?.let(onStreamSelected) },
                                            onToggleFavorite = { f ->
                                                f.stream?.let(onToggleFavoriteStream) ?: f.series?.let(onToggleFavoriteSeries)
                                            },
                                            onIndexChange = { heroIndex = it },
                                            height = heroHeight - 64.dp,
                                            modifier = Modifier.onFocusChanged { if (it.hasFocus) homeFocusMemory.forget() },
                                            primaryModifier = Modifier
                                                .focusRequester(liveTvCardFocusRequester)
                                                .focusRequester(firstItemFocusRequester)
                                                .focusProperties {
                                                    up = searchBarFocusRequester
                                                    start = sideNavFocusRequester
                                                }
                                        )
                                    }
                                }
                                item(key = "tiles") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { if (it.hasFocus) homeFocusMemory.forget() }
                                            .padding(horizontal = IpxSpacing.Gutter, vertical = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        QuickAccessCard(
                                            title = stringResource(R.string.home_tile_live),
                                            subtitle = stringResource(R.string.home_tile_live_sub),
                                            icon = Icons.Rounded.Tv,
                                            themeColor = AccentRed,
                                            onClick = { onSectionSelected(ContentSection.LIVE) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (hasHero) Modifier
                                                    else Modifier
                                                        .focusRequester(liveTvCardFocusRequester)
                                                        .focusRequester(firstItemFocusRequester)
                                                )
                                                .focusProperties {
                                                    up = if (hasHero) liveTvCardFocusRequester else searchBarFocusRequester
                                                    start = sideNavFocusRequester
                                                }
                                        )
                                        QuickAccessCard(
                                            title = stringResource(R.string.section_movies),
                                            subtitle = stringResource(R.string.home_tile_movies_sub),
                                            icon = Icons.Rounded.Movie,
                                            themeColor = AccentOrchid,
                                            onClick = { onSectionSelected(ContentSection.VOD) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(moviesCardFocusRequester)
                                                .focusProperties { up = if (hasHero) liveTvCardFocusRequester else searchBarFocusRequester }
                                        )
                                        QuickAccessCard(
                                            title = stringResource(R.string.home_tile_series),
                                            subtitle = stringResource(R.string.home_tile_series_sub),
                                            icon = Icons.Rounded.Slideshow,
                                            themeColor = Accent,
                                            onClick = { onSectionSelected(ContentSection.SERIES) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(seriesCardFocusRequester)
                                                .focusProperties { up = if (hasHero) liveTvCardFocusRequester else userProfileFocusRequester }
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
                                            title = stringResource(R.string.home_live_now),
                                            items = uiState.homeLiveHighlights,
                                            onStreamSelected = onStreamSelected,
                                            showChannelNumber = uiState.settings.showChannelNumbers
                                        )
                                    }
                                }

                                if (uiState.homeHotMovies.isNotEmpty()) {
                                    item {
                                        HomeMoviesRow(
                                            title = stringResource(R.string.home_top_movies),
                                            items = uiState.homeHotMovies,
                                            onStreamSelected = onStreamSelected,
                                            watchProgress = { uiState.getWatchProgress(it) }
                                        )
                                    }
                                }

                                if (uiState.homePopularSeries.isNotEmpty()) {
                                    item {
                                        HomeSeriesRow(
                                            title = stringResource(R.string.home_popular_series),
                                            items = uiState.homePopularSeries,
                                            onSeriesSelected = onSeriesSelected,
                                            watchProgress = { uiState.getWatchProgress(it) }
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                    ContentSection.WHATS_NEW -> {
                        if (uiState.isLoadingContent) {
                            LoadingIndicator()
                        } else if (uiState.whatsNewItems.isEmpty()) {
                            EmptyPromptMessage(stringResource(R.string.whats_new_empty))
                        } else {
                            WhatsNewGrid(
                                items = uiState.whatsNewItems,
                                onStreamSelected = onStreamSelected,
                                onSeriesSelected = onSeriesSelected,
                                onEpisodePlay = onEpisodePlay,
                                firstItemFocusRequester = firstItemFocusRequester,
                                sideNavFocusRequester = sideNavFocusRequester
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
                            onAppLanguageChange = onAppLanguageChange,
                            onToggleShowWhatsNew = onToggleShowWhatsNew,
                            onToggleShowDownloads = onToggleShowDownloads,
                            onToggleShowLibrary = onToggleShowLibrary,
                            onToggleShowSpotlight = onToggleShowSpotlight,
                            onPerformanceModeChange = onPerformanceModeChange,
                            onToggleShowChannelNumbers = onToggleShowChannelNumbers,
                            onToggle24HourClock = onToggle24HourClock,
                            onBufferProfileChange = onBufferProfileChange,
                            onToggleAutoPlayNextEpisode = onToggleAutoPlayNextEpisode,
                            onAspectRatioChange = onAspectRatioChange,
                            onDecoderModeChange = onDecoderModeChange,
                            onToggleKeepScreenAwake = onToggleKeepScreenAwake,
                            onPreferredAudioLanguageChange = onPreferredAudioLanguageChange,
                            onToggleDefaultSubtitles = onToggleDefaultSubtitles,
                            onPreferredSubtitleLanguageChange = onPreferredSubtitleLanguageChange,
                            onSubtitleTextSizeChange = onSubtitleTextSizeChange,
                            onToggleAutoRefreshCache = onToggleAutoRefreshCache,
                            onClearImageCache = onClearImageCache,
                            onClearWatchHistory = onClearWatchHistory,
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
                    ContentSection.MY_LIBRARY -> androidx.compose.runtime.CompositionLocalProvider(
                        com.ipxtream.tv.ui.dashboard.components.LocalFocusMemory provides libraryFocusMemory
                    ) {
                        LibrarySection(
                            uiState = uiState,
                            listState = libraryListState,
                            firstItemFocusRequester = firstItemFocusRequester,
                            searchBarFocusRequester = searchBarFocusRequester,
                            onStreamSelected = onStreamSelected,
                            onSeriesSelected = onSeriesSelected,
                            onEpisodePlay = onEpisodePlay,
                            onCheckForUpdates = onCheckForUpdates,
                            onDownloadUpdate = onDownloadUpdate,
                            onRefresh = onRefresh
                        )
                    }
                    ContentSection.MATCHES -> androidx.compose.runtime.CompositionLocalProvider(
                        com.ipxtream.tv.ui.dashboard.components.LocalFocusMemory provides matchesFocusMemory
                    ) {
                        com.ipxtream.tv.ui.dashboard.components.MatchesScreen(
                            matches                 = uiState.matches,
                            dayOffset               = uiState.matchDayOffset,
                            isLoading               = uiState.isLoadingMatches,
                            updatedAt               = uiState.matchesUpdatedAt,
                            offline                 = uiState.matchesOffline,
                            use24Hour               = uiState.settings.is24HourClockFormat,
                            allChannels             = uiState.matchPickerChannels,
                            onDaySelected           = onMatchDaySelected,
                            onPlay                  = onPlayMatch,
                            onPick                  = onPickMatchChannel,
                            firstItemFocusRequester = firstItemFocusRequester,
                            sideNavFocusRequester   = sideNavFocusRequester,
                            headerFocusRequester    = searchBarFocusRequester,
                            onNewDay                = onRefresh,
                            followedLeagues         = uiState.settings.followedLeagues,
                            onLeaguesChanged        = onMatchLeaguesChanged,
                            modifier                = Modifier.fillMaxSize()
                        )
                    }
                    ContentSection.UHD -> {
                        com.ipxtream.tv.ui.dashboard.components.UhdScreen(
                            movies                  = uiState.uhdMovies,
                            series                  = uiState.uhdSeries,
                            isLoading               = uiState.isLoadingContent,
                            onStreamSelected        = onStreamSelected,
                            onSeriesSelected        = onSeriesSelected,
                            firstItemFocusRequester = firstItemFocusRequester,
                            sideNavFocusRequester   = sideNavFocusRequester,
                            watchProgress           = { uiState.getWatchProgress(it) },
                            resolutions             = uiState.uhdResolutions,
                            scan                    = uiState.uhdScan,
                            onScanLibrary           = onUhdScanLibrary,
                            onStopScan              = onUhdStopScan,
                            modifier                = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        if (uiState.categories.isNotEmpty()) {
                            CategoryRow(
                                categories              = uiState.categories,
                                selectedCategoryId      = uiState.selectedCategoryId,
                                onCategorySelected      = onCategorySelected,
                                firstChipFocusRequester = categoryRowFocusRequester,
                                sideNavFocusRequester   = sideNavFocusRequester,
                                gridFocusRequester      = firstItemFocusRequester,
                                searchBarFocusRequester = searchBarFocusRequester,
                                modifier                = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.hasFocus) lastContentInGrid = false }
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
                                uiState.itemCount == 0 && !uiState.isLoadingContent ->
                                    EmptyPromptMessage(stringResource(R.string.section_empty))
                                uiState.activeSection == ContentSection.SERIES ->
                                    SeriesGrid(
                                        seriesList                = uiState.paginatedSeries,
                                        onSeriesSelected          = onSeriesSelected,
                                        firstItemFocusRequester   = firstItemFocusRequester,
                                        sideNavFocusRequester     = sideNavFocusRequester,
                                        categoryRowFocusRequester = categoryRowFocusRequester,
                                        onItemFocused             = {
                                            focusedSeriesItem.value = it; focusedStreamItem.value = null
                                            lastContentInGrid = true
                                        },
                                        hasPrevPage               = uiState.hasPrevPage,
                                        hasNextPage               = uiState.hasNextPage,
                                        currentPage               = uiState.currentPage,
                                        totalPages                = uiState.totalPages,
                                        onPrevPage                = onPrevPage,
                                        onNextPage                = onNextPage,
                                        watchProgress             = { uiState.getWatchProgress(it) }
                                    )
                                else ->
                                    StreamGrid(
                                        streams                   = uiState.paginatedStreams,
                                        section                   = uiState.activeSection,
                                        onStreamSelected          = onStreamSelected,
                                        firstItemFocusRequester   = firstItemFocusRequester,
                                        sideNavFocusRequester     = sideNavFocusRequester,
                                        categoryRowFocusRequester = categoryRowFocusRequester,
                                        onItemFocused             = {
                                            focusedStreamItem.value = it; focusedSeriesItem.value = null
                                            lastContentInGrid = true
                                        },
                                        hasPrevPage               = uiState.hasPrevPage,
                                        hasNextPage               = uiState.hasNextPage,
                                        currentPage               = uiState.currentPage,
                                        totalPages                = uiState.totalPages,
                                        onPrevPage                = onPrevPage,
                                        onNextPage                = onNextPage,
                                        watchProgress             = { uiState.getWatchProgress(it) },
                                        showChannelNumbers        = uiState.settings.showChannelNumbers
                                    )
                            }
                        }
                    }
                }
                }
                }
            }

            // -- Download tray: docked at bottom of centre box -----------
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
                    com.ipxtream.tv.ui.player.PlayerHost(
                        exoPlayer     = exoPlayer,
                        viewModel     = playerViewModel,
                        onStop        = onPlayerStop,
                        onNextChannel = nextChannelAction,
                        onPrevChannel = prevChannelAction,
                        modifier      = Modifier.fillMaxSize()
                    )
                }
            }
        }
        } // Close Grid/Player Row

        // Dimming backdrop scrim when side navigation is expanded/focused
        androidx.compose.animation.AnimatedVisibility(
            visible = isSideNavFocused && !isOverlayOpen,
            enter = androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        // ─── Floating Sidebar (OVERLAYS content gracefully) ──────────────────
        SideNavBar(
            activeSection         = uiState.activeSection,
            onSectionSelected     = onSectionSelected,
            onNavigateToContent   = focusContentForSection,
            onRefresh             = onRefresh,
            sideNavFocusRequester = sideNavFocusRequester,
            onFocusChanged        = { isSideNavFocused = it },
            settings              = uiState.settings,
            modifier              = Modifier
                .align(Alignment.CenterStart)
                .focusProperties { canFocus = !isOverlayOpen }
        )
                
        // ─── Overlay Phase: Detail Screens (Phase 7) ─────────────────────────
        if (uiState.detailVodItem != null) {
            com.ipxtream.tv.ui.dashboard.components.VodDetailScreen(
                streamItem       = uiState.detailVodItem,
                vodInfo          = uiState.detailVodInfo,
                isLoadingVodInfo = uiState.isLoadingVodInfo,
                onPlay           = { onVodPlay(uiState.detailVodItem) },
                onDownload       = { onVodDownload(uiState.detailVodItem) },
                onClose          = onCloseDetails,
                isFavorite       = isStreamFavorite(uiState.detailVodItem.streamId, "movie"),
                onToggleFavorite = { onToggleFavoriteStream(uiState.detailVodItem) },
                isPlayerOpen     = showFullScreenPlayer,
                resolution       = uiState.detailVodResolution
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
                onToggleFavorite = { onToggleFavoriteSeries(uiState.detailSeriesItem) },
                isPlayerOpen        = showFullScreenPlayer
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
                val allEpisodes = remember(uiState.seriesInfo) {
                    uiState.seriesInfo?.allEpisodes() ?: emptyList()
                }
                val currentEpIndex = remember(playback.activeEpisode, allEpisodes) {
                    if (playback.activeEpisode != null) {
                        allEpisodes.indexOfFirst { it.id == playback.activeEpisode.id }
                    } else -1
                }
                val seriesForEpisode = playback.activeSeries ?: uiState.detailSeriesItem

                val nextEpAction: (() -> Unit)? = if (currentEpIndex in 0 until allEpisodes.size - 1 && seriesForEpisode != null) {
                    val nextEp = allEpisodes[currentEpIndex + 1]
                    { onEpisodePlay(seriesForEpisode, nextEp) }
                } else null

                val nextEpLabel: String? = allEpisodes.getOrNull(currentEpIndex + 1)?.let { ep ->
                    val base = stringResource(R.string.episode_short, ep.season, ep.episodeNum)
                    if (ep.title.isNotBlank()) "$base  ${ep.title}" else base
                }

                val prevEpAction: (() -> Unit)? = if (currentEpIndex > 0 && seriesForEpisode != null) {
                    val prevEp = allEpisodes[currentEpIndex - 1]
                    { onEpisodePlay(seriesForEpisode, prevEp) }
                } else null

                val episodesClickAction: (() -> Unit)? = if (playback.activeEpisode != null) {
                    { onPlayerStop() }
                } else null

                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    com.ipxtream.tv.ui.player.PlayerHost(
                        exoPlayer       = exoPlayer,
                        viewModel       = playerViewModel,
                        onStop          = onPlayerStop,
                        onNextEpisode   = nextEpAction,
                        nextEpisodeLabel = nextEpLabel,
                        onPrevEpisode   = prevEpAction,
                        onEpisodesClick = episodesClickAction,
                        onNextChannel   = nextChannelAction,
                        onPrevChannel   = prevChannelAction,
                        modifier        = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ─── Update banner (automatic check): informs, never takes focus ─────
        var showUpdateBanner by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.updateRelease?.tagName) {
            if (uiState.updateRelease != null && !uiState.showUpdateDialog) {
                showUpdateBanner = true
                kotlinx.coroutines.delay(10_000)
            }
            showUpdateBanner = false
        }
        androidx.compose.animation.AnimatedVisibility(
            visible  = showUpdateBanner && !showFullScreenPlayer,
            enter    = androidx.compose.animation.fadeIn(tween(IpxMotion.Standard)),
            exit     = androidx.compose.animation.fadeOut(tween(IpxMotion.Standard)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceElevated.copy(alpha = 0xE6 / 255f))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text  = stringResource(R.string.update_banner, uiState.updateRelease?.tagName ?: ""),
                    style = IpxTypography.BodyMedium,
                    color = Color.White
                )
            }
        }

        // ─── Update Dialog overlay ───────────────────────────────────────────
        if (uiState.isUpdateDialogOpen && uiState.updateRelease != null) {
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
                        .clip(RoundedCornerShape(24.dp))
                        .background(com.ipxtream.tv.ui.theme.SurfaceElevated)
                        .border(1.dp, com.ipxtream.tv.ui.theme.Hairline, RoundedCornerShape(24.dp))
                        .padding(36.dp)
                        .width(460.dp)
                ) {
                    Text(stringResource(R.string.update_available), style = IpxTypography.HeadlineMedium, color = Color.White)

                    val release = uiState.updateRelease
                    Text(
                        text = stringResource(R.string.update_dialog_body, release.tagName),
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
                            Text(stringResource(R.string.downloading_pct, (progress * 100).toInt()), style = IpxTypography.BodyMedium, color = TextSecondary)
                            LinearProgressIndicator(
                                progress = { progress },
                                color = ProgressWatched,
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
                            PremiumButton(
                                label = stringResource(R.string.action_install_now),
                                icon = null,
                                onClick = onDownloadUpdate,
                                modifier = Modifier.focusRequester(updateDialogFocusRequester)
                            )
                            PremiumButton(
                                label = stringResource(R.string.action_later),
                                icon = null,
                                onClick = onDismissUpdate
                            )
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
private fun SpotlightLayer(
    stream:   androidx.compose.runtime.State<StreamItem?>,
    series:   androidx.compose.runtime.State<SeriesItem?>,
    modifier: Modifier
) {
    DynamicSpotlight(streamItem = stream.value, seriesItem = series.value, modifier = modifier)
}

@Composable
private fun StreamGrid(
    streams:                   List<StreamItem>,
    section:                   ContentSection,
    onStreamSelected:          (StreamItem) -> Unit,
    firstItemFocusRequester:   FocusRequester,
    sideNavFocusRequester:     FocusRequester? = null,
    categoryRowFocusRequester: FocusRequester? = null,
    onItemFocused:             (StreamItem?) -> Unit,
    hasPrevPage:               Boolean,
    hasNextPage:               Boolean,
    currentPage:               Int,
    totalPages:                Int,
    onPrevPage:                () -> Unit,
    onNextPage:                () -> Unit,
    watchProgress:             (String) -> Float? = { null },
    showChannelNumbers:        Boolean = true
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PosterGrid(
            items                   = streams,
            id                      = { "st_${it.streamId}" },
            columns                 = if (section == ContentSection.LIVE) GRID_COLUMNS_LIVE else GRID_COLUMNS_POSTER,
            firstItemFocusRequester = firstItemFocusRequester,
            sideNavFocusRequester   = sideNavFocusRequester,
            upFocusRequester        = categoryRowFocusRequester,
            onItemFocused           = onItemFocused,
            contentPadding          = PaddingValues(bottom = 24.dp, start = IpxSpacing.Gutter, top = 24.dp, end = IpxSpacing.Gutter),
            modifier                = Modifier.weight(1f)
        ) { stream, mod, open ->
            if (section == ContentSection.LIVE) {
                LiveChannelCard(
                    stream = stream,
                    onClick = { open(); onStreamSelected(stream) },
                    modifier = mod,
                    showChannelNumber = showChannelNumbers
                )
            } else {
                VodPosterCard(
                    stream = stream,
                    onClick = { open(); onStreamSelected(stream) },
                    modifier = mod,
                    watchProgress = watchProgress(stream.streamId.toString())
                )
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
    seriesList:                List<SeriesItem>,
    onSeriesSelected:          (SeriesItem) -> Unit,
    firstItemFocusRequester:   FocusRequester,
    sideNavFocusRequester:     FocusRequester? = null,
    categoryRowFocusRequester: FocusRequester? = null,
    onItemFocused:             (SeriesItem?) -> Unit,
    hasPrevPage:               Boolean,
    hasNextPage:               Boolean,
    currentPage:               Int,
    totalPages:                Int,
    onPrevPage:                () -> Unit,
    onNextPage:                () -> Unit,
    watchProgress:             (String) -> Float? = { null }
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PosterGrid(
            items                   = seriesList,
            id                      = { "se_${it.seriesId}" },
            columns                 = GRID_COLUMNS_POSTER,
            firstItemFocusRequester = firstItemFocusRequester,
            sideNavFocusRequester   = sideNavFocusRequester,
            upFocusRequester        = categoryRowFocusRequester,
            onItemFocused           = onItemFocused,
            contentPadding          = PaddingValues(bottom = 24.dp, start = IpxSpacing.Gutter, top = 24.dp, end = IpxSpacing.Gutter),
            modifier                = Modifier.weight(1f)
        ) { series, mod, open ->
            SeriesPosterCard(
                series = series,
                onClick = { open(); onSeriesSelected(series) },
                modifier = mod,
                watchProgress = watchProgress(series.seriesId.toString())
            )
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
        Text(title, style = IpxTypography.TitleLarge, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        if (itemCount > 0) {
            Text(
                text  = stringResource(R.string.section_items, itemCount) +
                    (if (isFromCache) stringResource(R.string.section_cached) else "") +
                    (if (totalPages > 1) stringResource(R.string.section_page, currentPage + 1, totalPages) else ""),
                style = IpxTypography.BodyMedium,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingIndicator() {
    androidx.compose.material3.CircularProgressIndicator(
        color = Color.White.copy(alpha = 0.8f),
        trackColor = Color.White.copy(alpha = 0.12f),
        strokeWidth = 3.dp,
        strokeCap = StrokeCap.Round,
        modifier = Modifier.size(40.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Text(message, style = IpxTypography.BodyMedium, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        androidx.tv.material3.Button(onClick = onRetry) { androidx.tv.material3.Text(stringResource(R.string.action_retry)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyPromptMessage(message: String) {
    androidx.tv.material3.Text(
        text = message,
        style = IpxTypography.TitleMedium,
        color = TextSecondary,
        modifier = Modifier.wrapContentSize()
    )
}

@Composable
private fun LibrarySection(
    uiState: DashboardUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstItemFocusRequester: FocusRequester,
    searchBarFocusRequester: FocusRequester,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (com.ipxtream.tv.data.model.SeriesItem, EpisodeItem) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRefresh: () -> Unit
) {
    val focusMemory = com.ipxtream.tv.ui.dashboard.components.LocalFocusMemory.current
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
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
                    .padding(bottom = 16.dp)
                    .onFocusChanged { if (it.isFocused) focusMemory?.forget() }
                    .focusRequester(firstItemFocusRequester)
                    .focusProperties {
                        up = searchBarFocusRequester
                    },
                shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(12.dp)),
                colors = androidx.tv.material3.CardDefaults.colors(
                    containerColor = SlatePrimary,
                    focusedContainerColor = SlateGlass
                ),
                scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.02f),
                border = androidx.tv.material3.CardDefaults.border(
                    border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = RoundedCornerShape(12.dp)),
                    focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, BorderFocus), shape = RoundedCornerShape(12.dp))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_app_updates), style = IpxTypography.TitleMedium, color = Accent)
                        Spacer(Modifier.height(4.dp))
                        val currentVersion = com.ipxtream.tv.BuildConfig.VERSION_NAME
                        if (uiState.isCheckingForUpdate) {
                            Text(stringResource(R.string.library_checking_updates), style = IpxTypography.BodySmall, color = TextSecondary)
                        } else if (uiState.updateRelease != null) {
                            Text(stringResource(R.string.library_update_new, uiState.updateRelease.tagName, currentVersion), style = IpxTypography.BodySmall, color = TextPrimary)
                        } else if (!com.ipxtream.tv.BuildConfig.SELF_UPDATE) {
                            Text(stringResource(R.string.update_via_play, currentVersion), style = IpxTypography.BodySmall, color = TextMuted)
                        } else {
                            Text(stringResource(R.string.library_up_to_date, currentVersion), style = IpxTypography.BodySmall, color = TextMuted)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    if (uiState.isCheckingForUpdate) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                    } else if (uiState.updateRelease != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Accent)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.update_download), color = TextOnAccent, style = IpxTypography.BodyMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(if (com.ipxtream.tv.BuildConfig.SELF_UPDATE) R.string.update_check else R.string.update_open_play),
                                color = Color.White, style = IpxTypography.BodyMedium
                            )
                        }
                    }
                }
            }
        }

        if (uiState.favoritesList.isEmpty() && uiState.historyList.isEmpty()) {
            item {
                EmptyPromptMessage(stringResource(R.string.library_empty))
            }
        } else {
            if (uiState.historyList.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.library_resume), style = IpxTypography.TitleMedium, color = Accent)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        itemsIndexed(uiState.historyList, key = { index, item -> "lib_hist_${item.type}_${item.id}_$index" }) { index, item ->
                            com.ipxtream.tv.ui.dashboard.components.FocusMemoryItem("lib_hist_${item.type}_${item.id}_$index") {
                                LibraryCard(item = item, onStreamSelected = onStreamSelected, onSeriesSelected = onSeriesSelected, onEpisodePlay = onEpisodePlay)
                            }
                        }
                    }
                }
            }

            if (uiState.favoritesList.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.library_favorites), style = IpxTypography.TitleMedium, color = Accent)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        itemsIndexed(uiState.favoritesList, key = { index, item -> "lib_fav_${item.type}_${item.id}_$index" }) { index, item ->
                            com.ipxtream.tv.ui.dashboard.components.FocusMemoryItem("lib_fav_${item.type}_${item.id}_$index") {
                                LibraryCard(item = item, onStreamSelected = onStreamSelected, onSeriesSelected = onSeriesSelected, onEpisodePlay = onEpisodePlay)
                            }
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
    val rawName = item.name ?: stringResource(R.string.untitled)
    when (item.type) {
        "live" -> {
            val stream = remember(item) {
                StreamItem(
                    streamId = item.id.toIntOrNull() ?: 0,
                    name = rawName,
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
                    streamId = item.id.toIntOrNull() ?: 0,
                    name = rawName,
                    streamType = "movie",
                    streamIcon = item.iconUrl,
                    categoryId = item.categoryId,
                    added = null, num = null, rating = item.rating, rating5based = null, containerExtension = item.containerExtension, epgChannelId = null, tvArchive = null, tvArchiveDuration = null, directSource = null, customSid = null
                )
            }
            val progress = if (item.durationMs > 0L) (item.lastWatchedPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else null
            VodPosterCard(stream = stream, onClick = { onStreamSelected(stream) }, watchProgress = progress)
        }
        "series" -> {
            val series = remember(item) {
                SeriesItem(
                    seriesId = item.id.toIntOrNull() ?: 0,
                    name = rawName,
                    cover = item.iconUrl,
                    plot = null, cast = null, director = null, genre = null, releaseDate = null, lastModified = null, rating = item.rating, rating5based = null, backdropPath = null, youtubeTrailer = null, episodeRunTime = null, categoryId = item.categoryId
                )
            }
            val progress = if (item.durationMs > 0L) (item.lastWatchedPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else null
            SeriesPosterCard(series = series, onClick = { onSeriesSelected(series) }, watchProgress = progress)
        }
        "episode" -> {
            val seriesTitle = if (rawName.contains(" - ")) rawName.substringBefore(" - ") else rawName
            val epTitle = if (rawName.contains(" - ")) rawName.substringAfter(" - ") else rawName
            val series = remember(item) {
                SeriesItem(
                    seriesId = item.parentId?.toIntOrNull() ?: 0,
                    name = seriesTitle,
                    cover = item.iconUrl,
                    plot = null, cast = null, director = null, genre = null, releaseDate = null, lastModified = null, rating = null, rating5based = null, backdropPath = null, youtubeTrailer = null, episodeRunTime = null, categoryId = null
                )
            }
            val episode = remember(item) {
                EpisodeItem(
                    id = item.id,
                    episodeNum = 1,
                    title = epTitle,
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
                    containerColor = com.ipxtream.tv.ui.theme.SlateCard,
                    focusedContainerColor = SlateGlass
                ),
                border = androidx.tv.material3.CardDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, BorderFocus), shape = RoundedCornerShape(12.dp))
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
                                    colors = listOf(Color.Transparent, SlateDeep.copy(alpha = 0xEE / 255f))
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
                            text = bidi(item.name),
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
                                    color = Accent,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.time_left, formatTime((item.durationMs - item.lastWatchedPositionMs).coerceAtLeast(0L))),
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


@Composable
private fun WhatsNewGrid(
    items: List<LibraryItem>,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (SeriesItem, EpisodeItem) -> Unit,
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    PosterGrid(
        items                   = items,
        id                      = { "wn_${it.type}_${it.id}" },
        columns                 = GridCells.Adaptive(minSize = 140.dp),
        firstItemFocusRequester = firstItemFocusRequester,
        sideNavFocusRequester   = sideNavFocusRequester,
        contentPadding          = PaddingValues(bottom = 120.dp, start = 16.dp, top = 16.dp, end = 16.dp),
        spacingH                = 16.dp,
        spacingV                = 16.dp,
        modifier                = modifier.fillMaxSize()
    ) { item, mod, open ->
        ContinueWatchingCard(
            item = item,
            onStreamSelected = { open(); onStreamSelected(it) },
            onSeriesSelected = { open(); onSeriesSelected(it) },
            onEpisodePlay = { s, e -> open(); onEpisodePlay(s, e) },
            modifier = mod
        )
    }
}
