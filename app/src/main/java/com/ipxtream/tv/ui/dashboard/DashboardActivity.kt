package com.ipxtream.tv.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ipxtream.tv.data.local.CredentialStore
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.ui.player.PlayerViewModel
import com.ipxtream.tv.ui.theme.IpxTvTheme

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

    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(applicationContext, resolveCredentials())
    }

    /**
     * [PlayerViewModel] uses the default [ViewModelProvider.AndroidViewModelFactory]
     * because it only requires [Application], which the framework provides automatically.
     */
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = resolveCredentials()
        dashboardViewModel.checkForUpdates()

        setContent {
            IpxTvTheme {
                val dashState     by dashboardViewModel.uiState.collectAsState()
                val playerState   by playerViewModel.uiState.collectAsState()
                val downloadItems by dashboardViewModel.downloadItems.collectAsState()

                DashboardScreen(
                    uiState            = dashState,
                    playerUiState      = playerState,
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

                    onDownloadPause    = { id -> dashboardViewModel.pauseDownload(this, id) },
                    onDownloadResume   = { id -> dashboardViewModel.resumeDownload(this, id) },
                    onDownloadCancel   = { id -> dashboardViewModel.cancelDownload(this, id) },
                    onDownloadRetry    = { id -> dashboardViewModel.retryDownload(this, id) },
                    onDownloadClearDone = dashboardViewModel::clearFinishedDownloads,
                    onCloseDetails      = dashboardViewModel::closeDetails,

                    onToggleFavoriteStream = dashboardViewModel::toggleFavoriteStream,
                    onToggleFavoriteSeries = dashboardViewModel::toggleFavoriteSeries,
                    isStreamFavorite   = dashboardViewModel::isStreamFavorite,
                    isSeriesFavorite   = dashboardViewModel::isSeriesFavorite,
                    onCheckForUpdates  = dashboardViewModel::checkForUpdates,
                    onDownloadUpdate   = dashboardViewModel::downloadAndInstallUpdate,
                    onDismissUpdate    = dashboardViewModel::dismissUpdate,
                    onNextPage         = dashboardViewModel::nextPage,
                    onPrevPage         = dashboardViewModel::prevPage,
                    onSwitchAccount    = dashboardViewModel::switchAccount,
                    onSetDefaultAccount = dashboardViewModel::setDefaultAccount,
                    onRemoveAccount    = dashboardViewModel::removeAccount,
                    onAddAccount       = dashboardViewModel::addAccount,
                    onVodDownload      = { stream -> dashboardViewModel.downloadStream(this, stream, credentials) },
                    onEpisodeDownload  = { episode -> dashboardViewModel.downloadEpisode(this, episode, credentials) }
                )
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    // Pause/resume is handled inside PlayerPanel's DisposableEffect.
    // Release is handled in PlayerViewModel.onCleared().
    // No override needed here.

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun resolveCredentials(): AuthCredentials {
        val server   = intent.getStringExtra(EXTRA_SERVER)
        val username = intent.getStringExtra(EXTRA_USERNAME)
        val password = intent.getStringExtra(EXTRA_PASSWORD)

        if (server != null && username != null && password != null) {
            return AuthCredentials(server, username, password)
        }

        return CredentialStore(applicationContext).loadCredentials()
            ?: throw IllegalStateException(
                "DashboardActivity launched without credentials. " +
                "Redirect to LoginActivity before opening Dashboard."
            )
    }

    companion object {
        const val EXTRA_SERVER   = "extra_server"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"
    }
}
