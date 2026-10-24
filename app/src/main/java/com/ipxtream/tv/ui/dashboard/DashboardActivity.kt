package com.ipxtream.tv.ui.dashboard

import com.ipxtream.tv.ui.player.summary
import com.ipxtream.tv.ui.player.PlaybackSummary
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ipxtream.tv.data.local.AppLanguage
import com.ipxtream.tv.data.local.CredentialStore
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.ui.login.LoginActivity
import com.ipxtream.tv.ui.player.PlayerViewModel
import com.ipxtream.tv.ui.theme.IpxTvTheme
import com.ipxtream.tv.ui.theme.screenReaderOnlySemantics
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Host Activity for the Dashboard + embedded Player.
 *
 * Owns two ViewModels:
 * - [DashboardViewModel] — content browsing state (categories, streams, series).
 * - [PlayerViewModel]    — ExoPlayer lifecycle and playback state.
 *
 * Both ViewModels outlive configuration changes. [PlayerViewModel] uses
 * [AndroidViewModel] so the ExoPlayer is created with Application context.
 *
 * ## Wiring
 * When the user selects a card in the grid:
 * 1. [DashboardViewModel.selectStream] updates [DashboardUiState.selectedStream]
 *    → triggers the split layout in [DashboardScreen].
 * 2. [PlayerViewModel.loadStream] builds the URL and starts ExoPlayer.
 *
 * UI ← StateFlow ← DashboardViewModel
 * UI ← StateFlow ← PlayerViewModel
 * ExoPlayer ← PlayerViewModel.exoPlayer (passed directly to PlayerPanel)
 */
class DashboardActivity : ComponentActivity() {

    /** Set in [onCreate] before anything touches [dashboardViewModel]. */
    private lateinit var credentials: AuthCredentials

    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(applicationContext, credentials)
    }

    /**
     * [PlayerViewModel] uses the default [ViewModelProvider.AndroidViewModelFactory]
     * because it only requires [Application], which the framework provides automatically.
     */
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No saved account (e.g. storage was cleared): send the user to Login instead of crashing.
        credentials = resolveCredentials() ?: run {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }
        dashboardViewModel.scheduleStartupUpdateCheck()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_MATCHES, false)) {
            dashboardViewModel.selectSection(com.ipxtream.tv.ui.dashboard.ContentSection.MATCHES)
        }

        // Android 13+: ask for the notification permission the first time Matches opens while
        // reminders are on, so the question comes with context instead of at launch.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            lifecycleScope.launch {
                dashboardViewModel.uiState
                    .map { it.activeSection == com.ipxtream.tv.ui.dashboard.ContentSection.MATCHES && it.settings.matchReminders }
                    .distinctUntilChanged()
                    .collect { if (it) maybeAskNotificationPermission() }
            }
        }

        // Feed the real decoded resolution back into the 4K measurements. Collected here, not in
        // composition, so the player's per-second ticks never recompose the dashboard.
        lifecycleScope.launch {
            playerViewModel.uiState
                .map { listOf(it.activeStream, it.activeEpisode, it.activeSeries, it.videoWidth, it.videoHeight) to it }
                .distinctUntilChangedBy { it.first }
                .collect { (_, s) ->
                    dashboardViewModel.recordPlaybackResolution(
                        stream = s.activeStream,
                        series = if (s.activeEpisode != null) s.activeSeries else null,
                        width  = s.videoWidth,
                        height = s.videoHeight
                    )
                }
        }
        val playbackFlow = playerViewModel.uiState.map { it.summary() }.distinctUntilChanged()

        // Compose paints its own opaque background; the window's would be a second
        // full-screen fill under it on every frame (expensive on TV GPUs).
        window.setBackgroundDrawable(null)

        setContent {
            IpxTvTheme {
                val dashState     by dashboardViewModel.uiState.collectAsState()
                val playback      by playbackFlow.collectAsState(PlaybackSummary())
                val downloadItems by dashboardViewModel.downloadItems.collectAsState()

                // Cheap accessibility tree unless a screen reader is on (see screenReaderOnlySemantics).
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().screenReaderOnlySemantics()
                ) {
                DashboardScreen(
                    uiState            = dashState,
                    playback           = playback,
                    exoPlayer          = playerViewModel.exoPlayer,
                    playerViewModel    = playerViewModel,
                    downloadItems      = downloadItems,

                    onSectionSelected  = dashboardViewModel::selectSection,
                    onCategorySelected = dashboardViewModel::selectCategory,
                    onSearchQueryChange= dashboardViewModel::updateSearchQuery,
                    onRefresh          = dashboardViewModel::refresh,

                    onStreamSelected   = { stream ->
                        if (stream.isLive) {
                            dashboardViewModel.startPlayback(stream)
                            playerViewModel.loadStream(stream, credentials)
                        } else {
                            dashboardViewModel.showVodDetails(stream)
                        }
                    },

                    onSeriesSelected   = dashboardViewModel::showSeriesDetails,

                    onMatchDaySelected = dashboardViewModel::selectMatchDay,
                    onMatchLeaguesChanged = dashboardViewModel::setFollowedLeagues,
                    onPlayMatch        = { listing, stream ->
                        dashboardViewModel.startMatchPlayback(stream, listing.channels)
                        playerViewModel.loadStream(stream, credentials)
                    },
                    onPickMatchChannel = { listing, stream, forCompetition ->
                        dashboardViewModel.rememberMatchChannel(listing.match, stream, forCompetition)
                        dashboardViewModel.startMatchPlayback(stream, dashboardViewModel.uiState.value.matchChannelQueue)
                        playerViewModel.loadStream(stream, credentials)
                    },

                    onVodPlay = { stream ->
                        dashboardViewModel.startPlayback(stream)
                        playerViewModel.loadStream(stream, credentials)
                    },
                    onEpisodePlay = { series: com.ipxtream.tv.data.model.SeriesItem, episode: com.ipxtream.tv.data.model.EpisodeItem ->
                        dashboardViewModel.startEpisodePlayback(episode)
                        playerViewModel.loadEpisode(episode, series, credentials)
                    },

                    onPlayerStop       = {
                        playerViewModel.stop()
                        dashboardViewModel.clearActivePlayback()
                    },

                    onDownloadPause    = { id -> dashboardViewModel.pauseDownload(this@DashboardActivity, id) },
                    onDownloadResume   = { id -> dashboardViewModel.resumeDownload(this@DashboardActivity, id) },
                    onDownloadCancel   = { id -> dashboardViewModel.cancelDownload(this@DashboardActivity, id) },
                    onDownloadRetry    = { id -> dashboardViewModel.retryDownload(this@DashboardActivity, id) },
                    onDownloadClearDone = dashboardViewModel::clearFinishedDownloads,
                    onCloseDetails      = dashboardViewModel::closeDetails,

                    onToggleFavoriteStream = dashboardViewModel::toggleFavoriteStream,
                    onToggleFavoriteSeries = dashboardViewModel::toggleFavoriteSeries,
                    isStreamFavorite   = dashboardViewModel::isStreamFavorite,
                    isSeriesFavorite   = dashboardViewModel::isSeriesFavorite,
                    // Play build: "check for updates" opens the app's Play Store page instead.
                    onCheckForUpdates  = {
                        if (com.ipxtream.tv.BuildConfig.SELF_UPDATE) dashboardViewModel.checkForUpdates()
                        else dashboardViewModel.openStorePage()
                    },
                    onDownloadUpdate   = dashboardViewModel::downloadAndInstallUpdate,
                    onDismissUpdate    = dashboardViewModel::dismissUpdate,
                    onNextPage         = dashboardViewModel::nextPage,
                    onPrevPage         = dashboardViewModel::prevPage,
                    onSwitchAccount    = dashboardViewModel::switchAccount,
                    onSetDefaultAccount = dashboardViewModel::setDefaultAccount,
                    onRemoveAccount    = dashboardViewModel::removeAccount,
                    onAddAccount       = dashboardViewModel::addAccount,
                    onVodDownload      = { stream -> dashboardViewModel.downloadStream(this@DashboardActivity, stream, credentials) },
                    onEpisodeDownload  = { episode -> dashboardViewModel.downloadEpisode(this@DashboardActivity, episode, credentials) },
                    onCacheAll         = dashboardViewModel::cacheAllContent,
                    onLogout           = dashboardViewModel::logout,

                    onAppLanguageChange = { lang ->
                        dashboardViewModel.setAppLanguage(lang)
                        com.ipxtream.tv.util.LocaleHelper.applyLocale(this@DashboardActivity, lang.code)
                    },
                    onToggleShowWhatsNew = dashboardViewModel::setShowWhatsNewSection,
                    onToggleShowDownloads = dashboardViewModel::setShowDownloadsSection,
                    onToggleShowLibrary = dashboardViewModel::setShowLibrarySection,
                    onToggleShowSpotlight = dashboardViewModel::setShowSpotlightBackdrop,
                    onToggleShowChannelNumbers = dashboardViewModel::setShowChannelNumbers,
                    onToggleMatchReminders = dashboardViewModel::setMatchReminders,
                    onToggle24HourClock = dashboardViewModel::setIs24HourClockFormat,
                    onBufferProfileChange = dashboardViewModel::setBufferProfile,
                    onPerformanceModeChange = dashboardViewModel::setPerformanceMode,
                    onToggleAutoPlayNextEpisode = dashboardViewModel::setAutoPlayNextEpisode,
                    onAspectRatioChange = dashboardViewModel::setDefaultAspectRatio,
                    onDecoderModeChange = dashboardViewModel::setDecoderMode,
                    onToggleKeepScreenAwake = dashboardViewModel::setKeepScreenAwake,
                    onPreferredAudioLanguageChange = dashboardViewModel::setPreferredAudioLanguage,
                    onToggleDefaultSubtitles = dashboardViewModel::setDefaultSubtitlesEnabled,
                    onPreferredSubtitleLanguageChange = dashboardViewModel::setPreferredSubtitleLanguage,
                    onSubtitleTextSizeChange = dashboardViewModel::setSubtitleTextSize,
                    onToggleAutoRefreshCache = dashboardViewModel::setAutoRefreshCacheOnStartup,
                    onClearImageCache = dashboardViewModel::clearImageCache,
                    onClearWatchHistory = dashboardViewModel::clearWatchHistory,
                    onUhdScanLibrary = dashboardViewModel::startUhdLibraryScan,
                    onUhdStopScan = dashboardViewModel::stopUhdScan
                )
                }
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val appSettingsStore = com.ipxtream.tv.data.local.AppSettingsStore.getInstance(newBase)
        val langCode = appSettingsStore.settings.value.appLanguage.code
        val localizedContext = com.ipxtream.tv.util.LocaleHelper.wrap(newBase, langCode)
        super.attachBaseContext(localizedContext)
    }

    private var lastRepeatHandledAt = 0L

    /**
     * Holding a D-Pad key on a slow TV queues repeats faster than frames are drawn, so focus
     * keeps sliding after the key is released. Repeats that are stale or closer together than
     * [REPEAT_INTERVAL_MS] are dropped; single presses always go through.
     */
    private fun isExcessRepeat(event: android.view.KeyEvent): Boolean {
        if (event.action != android.view.KeyEvent.ACTION_DOWN || event.repeatCount == 0) return false
        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> return false
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - event.eventTime > STALE_REPEAT_MS || now - lastRepeatHandledAt < REPEAT_INTERVAL_MS) return true
        lastRepeatHandledAt = now
        return false
    }

    // Phones and tablets (Play build): taps reach the TV controls, which only react to keys.
    private val touchBridge by lazy { com.ipxtream.tv.ui.theme.TouchBridge(this) }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean =
        touchBridge.dispatch(event) { super.dispatchTouchEvent(it) }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isExcessRepeat(event)) return true
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_DOWN) {
            val state = dashboardViewModel.uiState.value
            val isFullScreenPlaying = state.selectedEpisode != null ||
                (state.selectedStream != null && (state.selectedStream.streamType == "movie" || state.playFullScreen))

            if (isFullScreenPlaying) {
                return super.dispatchKeyEvent(event)
            }

            if (state.detailVodItem != null || state.detailSeriesItem != null) {
                dashboardViewModel.closeDetails()
                return true
            }
            if (state.isUpdateDialogOpen) {
                dashboardViewModel.dismissUpdate()
                return true
            }
            if (state.searchQuery.isNotBlank()) {
                dashboardViewModel.updateSearchQuery("")
                return true
            }
        }
        return dispatchSafely(event)
    }

    /**
     * Compose throws when a focus jump names a FocusRequester that isn't on screen right now
     * (e.g. a section without a category row). A D-Pad press must never crash the app: drop it.
     */
    private fun dispatchSafely(event: android.view.KeyEvent): Boolean = try {
        super.dispatchKeyEvent(event)
    } catch (e: IllegalStateException) {
        if (e.message?.contains("FocusRequester is not initialized") != true) throw e
        android.util.Log.w("DashboardActivity", "Ignored focus move to a missing target", e)
        true
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    // Pause/resume is handled inside PlayerPanel's DisposableEffect.
    // Release is handled in PlayerViewModel.onCleared().
    // No override needed here.

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Asks once; after a refusal the reminders simply stay silent (Settings can't re-ask). */
    private fun maybeAskNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("match_reminders", MODE_PRIVATE)
        if (prefs.getBoolean("asked_permission", false)) return
        prefs.edit().putBoolean("asked_permission", true).apply()
        requestPermissions(arrayOf(permission), 0)
    }

    private fun resolveCredentials(): AuthCredentials? {
        val server   = intent.getStringExtra(EXTRA_SERVER)
        val username = intent.getStringExtra(EXTRA_USERNAME)
        val password = intent.getStringExtra(EXTRA_PASSWORD)

        if (server != null && username != null && password != null) {
            return AuthCredentials(server, username, password)
        }

        return com.ipxtream.tv.di.AppContainer.from(this).credentialStore.loadCredentials()
    }

    companion object {
        const val EXTRA_SERVER   = "extra_server"
        const val EXTRA_OPEN_MATCHES = "extra_open_matches"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"

        private const val REPEAT_INTERVAL_MS = 110L   // ~9 moves per second while a key is held
        private const val STALE_REPEAT_MS    = 200L   // repeat waited too long in the queue
    }
}
