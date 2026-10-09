package com.ipxtream.tv.ui.dashboard.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.ipxtream.tv.BuildConfig
import com.ipxtream.tv.data.local.*
import com.ipxtream.tv.ui.dashboard.DashboardUiState
import com.ipxtream.tv.ui.theme.*

enum class SettingsCategory(val title: String, val icon: ImageVector) {
    GENERAL("General & UI", Icons.Rounded.Palette),
    PLAYBACK("Playback & Player", Icons.Rounded.PlayCircle),
    AUDIO_SUBS("Audio & Subtitles", Icons.Rounded.Subtitles),
    ACCOUNTS("Accounts Manager", Icons.Rounded.ManageAccounts),
    DATA("Data & Storage", Icons.Rounded.Storage),
    ABOUT("About & Updates", Icons.Rounded.Info)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: DashboardUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onSwitchAccount: (server: String, username: String) -> Unit,
    onSetDefaultAccount: (server: String, username: String) -> Unit,
    onRemoveAccount: (server: String, username: String) -> Unit,
    onAddAccount: () -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit = {},
    onToggleShowWhatsNew: (Boolean) -> Unit = {},
    onToggleShowDownloads: (Boolean) -> Unit = {},
    onToggleShowLibrary: (Boolean) -> Unit = {},
    onToggleShowSpotlight: (Boolean) -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }
    val leftNavFocusRequester = remember { FocusRequester() }
    val rightContentFocusRequester = remember { FocusRequester() }

    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            kotlinx.coroutines.delay(3500)
            feedbackMessage = null
        }
    }

    LaunchedEffect(Unit) {
        runCatching { leftNavFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDeep)
            .padding(top = 20.dp, start = 24.dp, end = 24.dp, bottom = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "System Settings",
                        style = IpxTypography.DisplayLarge.copy(fontSize = 28.sp),
                        color = TextPrimary
                    )
                }

                if (feedbackMessage != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = feedbackMessage ?: "",
                            style = IpxTypography.BodySmall.copy(fontWeight = FontWeight.Bold),
                            color = AccentGreen
                        )
                    }
                }
            }

            // 2-Pane Categorized Body
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Left Pane: Settings Categories Rail ────────────────────────
                Card(
                    onClick = { /* No-op container */ },
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = SlatePrimary,
                        focusedContainerColor = SlatePrimary
                    ),
                    border = CardDefaults.border(
                        border = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(12.dp)),
                        focusedBorder = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(12.dp))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingsCategory.values().forEachIndexed { index, category ->
                            val isSelected = category == selectedCategory
                            var isFocused by remember { mutableStateOf(false) }

                            Surface(
                                onClick = {
                                    selectedCategory = category
                                    runCatching { rightContentFocusRequester.requestFocus() }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .then(if (index == 0) Modifier.focusRequester(leftNavFocusRequester) else Modifier)
                                    .onFocusChanged { state ->
                                        isFocused = state.isFocused
                                        if (state.isFocused) {
                                            selectedCategory = category
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                            runCatching { rightContentFocusRequester.requestFocus() }
                                            true
                                        } else false
                                    },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.2f)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(
                                        if (isSelected) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)) else BorderStroke(0.dp, Color.Transparent),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = category.title,
                                        tint = if (isSelected || isFocused) AccentCyan else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = category.title,
                                        style = IpxTypography.BodyMedium,
                                        color = if (isSelected || isFocused) TextPrimary else TextSecondary,
                                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Right Pane: Category Content ───────────────────────────────
                Card(
                    onClick = { /* No-op container */ },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = SlatePrimary,
                        focusedContainerColor = SlatePrimary
                    ),
                    border = CardDefaults.border(
                        border = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(12.dp)),
                        focusedBorder = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(12.dp))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (selectedCategory) {
                            SettingsCategory.GENERAL -> {
                                GeneralSettingsContent(
                                    settings = uiState.settings,
                                    initialFocus = rightContentFocusRequester,
                                    leftNavFocus = leftNavFocusRequester,
                                    onLanguageChange = onAppLanguageChange,
                                    onToggleSpotlight = onToggleShowSpotlight,
                                    onToggleChannelNumbers = onToggleShowChannelNumbers,
                                    onToggle24HourClock = onToggle24HourClock,
                                    onToggleWhatsNew = onToggleShowWhatsNew,
                                    onToggleDownloads = onToggleShowDownloads,
                                    onToggleLibrary = onToggleShowLibrary
                                )
                            }
                            SettingsCategory.PLAYBACK -> {
                                PlaybackSettingsContent(
                                    settings = uiState.settings,
                                    initialFocus = rightContentFocusRequester,
                                    leftNavFocus = leftNavFocusRequester,
                                    onBufferChange = onBufferProfileChange,
                                    onAutoPlayNext = onToggleAutoPlayNextEpisode,
                                    onAspectRatioChange = onAspectRatioChange,
                                    onDecoderChange = onDecoderModeChange,
                                    onKeepScreenAwake = onToggleKeepScreenAwake
                                )
                            }
                            SettingsCategory.AUDIO_SUBS -> {
                                AudioSubsSettingsContent(
                                    settings = uiState.settings,
                                    initialFocus = rightContentFocusRequester,
                                    leftNavFocus = leftNavFocusRequester,
                                    onAudioLangChange = onPreferredAudioLanguageChange,
                                    onToggleSubtitles = onToggleDefaultSubtitles,
                                    onSubLangChange = onPreferredSubtitleLanguageChange,
                                    onSubSizeChange = onSubtitleTextSizeChange
                                )
                            }
                            SettingsCategory.ACCOUNTS -> {
                                AccountsSettingsContent(
                                    uiState = uiState,
                                    initialFocus = rightContentFocusRequester,
                                    leftNavFocus = leftNavFocusRequester,
                                    onAddAccount = onAddAccount,
                                    onSwitchAccount = onSwitchAccount,
                                    onSetDefaultAccount = onSetDefaultAccount,
                                    onRemoveAccount = onRemoveAccount
                                )
                            }
                            SettingsCategory.DATA -> {
                                DataStorageSettingsContent(
                                    settings = uiState.settings,
                                    initialFocus = rightContentFocusRequester,
                                    leftNavFocus = leftNavFocusRequester,
                                    onToggleAutoRefresh = onToggleAutoRefreshCache,
                                    onClearImageCache = {
                                        onClearImageCache()
                                        feedbackMessage = "Poster & image cache cleared successfully"
                                    },
                                    onClearWatchHistory = {
                                        onClearWatchHistory()
                                        feedbackMessage = "Watch history cleared successfully"
                                    }
                                )
                            }
                            SettingsCategory.ABOUT -> {
                                AboutSettingsContent(
                                    uiState = uiState,
                                    initialFocus = rightContentFocusRequester,
                                    leftNavFocus = leftNavFocusRequester,
                                    onCheckUpdates = onCheckForUpdates,
                                    onDownloadUpdate = onDownloadUpdate,
                                    onDismissUpdate = onDismissUpdate,
                                    onOpenTerms = { showTermsDialog = true },
                                    onOpenPrivacy = { showPrivacyDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTermsDialog) {
            LegalInfoDialog(
                title = "Terms and Conditions",
                content = """IPXtream TV Terms and Conditions

1. Acceptance of Terms
By installing and using IPXtream TV, you agree to these terms. If you do not agree, uninstall and do not use the application.

2. Purpose and Nature of the Software
IPXtream TV is an independent client media player. It does not provide, host, sell, or index any video streams, television channels, or playlists. The application is strictly a playback tool.

3. User Responsibilities
You are solely responsible for the playlists and credentials you load. You must ensure you have the legal right or license to access your content. You must not use the application to infringe upon copyright or intellectual property rights.

4. Third-Party Services
Connections are established directly from your device to the IPTV server you specify. The developers have no affiliation with any streaming service or provider.

5. Disclaimer of Warranties
The software is provided "as is" without warranty of any kind. The developers are not liable for any damages resulting from the use of this software or content accessed through it.""",
                onDismiss = { showTermsDialog = false }
            )
        }

        if (showPrivacyDialog) {
            LegalInfoDialog(
                title = "Privacy Policy",
                content = """IPXtream TV Privacy Policy

1. Zero Data Collection
IPXtream TV does not collect, transmit, store, or sell your personal data. There are no tracking scripts, analytics SDKs, or background telemetry.

2. Local Storage
All credentials, playlists, stream caches, and download history are saved exclusively on your local device within sandboxed application storage. This information is deleted if you clear app data or uninstall the application.

3. Outgoing Connections
The application connects directly to the IPTV provider address you provide to load streams, and queries the public GitHub Releases API to check for software updates. No personal data is transmitted in these requests.

4. Contact
For questions or inquiries, visit the official repository at github.com/KyuJunior/Ipxtream-for-android-tv.""",
                onDismiss = { showPrivacyDialog = false }
            )
        }
    }
}

// =============================================================================
//  Category Pane Composables
// =============================================================================

@Composable
private fun GeneralSettingsContent(
    settings: AppSettings,
    initialFocus: FocusRequester,
    leftNavFocus: FocusRequester,
    onLanguageChange: (AppLanguage) -> Unit,
    onToggleSpotlight: (Boolean) -> Unit,
    onToggleChannelNumbers: (Boolean) -> Unit,
    onToggle24HourClock: (Boolean) -> Unit,
    onToggleWhatsNew: (Boolean) -> Unit,
    onToggleDownloads: (Boolean) -> Unit,
    onToggleLibrary: (Boolean) -> Unit
) {
    SectionHeader("General & Appearance", "Configure interface language, visual effects, and sidebar layout.")

    // App Language Selector
    SettingSelectorItem(
        title = "Application Language",
        subtitle = "Choose your preferred user interface language.",
        options = AppLanguage.values().toList(),
        selected = settings.appLanguage,
        onSelect = onLanguageChange,
        labelProvider = { it.displayName },
        modifier = Modifier.focusRequester(initialFocus).focusProperties { left = leftNavFocus }
    )

    // Dynamic Spotlight
    SettingToggleItem(
        title = "Dynamic Poster Spotlight",
        subtitle = "Renders high-resolution blurred art backdrop behind Home and Category screens.",
        isChecked = settings.showSpotlightBackdrop,
        onCheckedChange = onToggleSpotlight,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Channel Numbers
    SettingToggleItem(
        title = "Show Channel Numbers",
        subtitle = "Display index badges on Live TV channel cards.",
        isChecked = settings.showChannelNumbers,
        onCheckedChange = onToggleChannelNumbers,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Clock Format
    SettingToggleItem(
        title = "24-Hour Clock Format",
        subtitle = "Display clock in 24-hour mode instead of 12-hour AM/PM.",
        isChecked = settings.is24HourClockFormat,
        onCheckedChange = onToggle24HourClock,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    Spacer(Modifier.height(8.dp))
    Text("Side Navigation Sections", style = IpxTypography.TitleMedium, color = AccentCyan)

    SettingToggleItem(
        title = "Show \"What's New\"",
        subtitle = "Displays top rated releases section in the side menu.",
        isChecked = settings.showWhatsNewSection,
        onCheckedChange = onToggleWhatsNew,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    SettingToggleItem(
        title = "Show \"Downloads\"",
        subtitle = "Displays offline media downloads section in the side menu.",
        isChecked = settings.showDownloadsSection,
        onCheckedChange = onToggleDownloads,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    SettingToggleItem(
        title = "Show \"My Library\"",
        subtitle = "Displays bookmarks, history, and update checks in the side menu.",
        isChecked = settings.showLibrarySection,
        onCheckedChange = onToggleLibrary,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )
}

@Composable
private fun PlaybackSettingsContent(
    settings: AppSettings,
    initialFocus: FocusRequester,
    leftNavFocus: FocusRequester,
    onBufferChange: (BufferProfile) -> Unit,
    onAutoPlayNext: (Boolean) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onDecoderChange: (DecoderMode) -> Unit,
    onKeepScreenAwake: (Boolean) -> Unit
) {
    SectionHeader("Playback & Player", "Tune streaming buffers, decoding priority, and aspect ratio.")

    // Buffer Profile
    SettingSelectorItem(
        title = "Streaming Buffer Profile",
        subtitle = "Adjust buffer duration for network reliability vs startup latency.",
        options = BufferProfile.values().toList(),
        selected = settings.bufferProfile,
        onSelect = onBufferChange,
        labelProvider = { it.label },
        modifier = Modifier.focusRequester(initialFocus).focusProperties { left = leftNavFocus }
    )

    // Auto Play Next Episode
    SettingToggleItem(
        title = "Auto-play Next Episode",
        subtitle = "Automatically load the next TV episode upon finishing current playback.",
        isChecked = settings.autoPlayNextEpisode,
        onCheckedChange = onAutoPlayNext,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Aspect Ratio
    SettingSelectorItem(
        title = "Default Video Aspect Ratio",
        subtitle = "Select default framing for VOD blockbusters and TV channels.",
        options = AspectRatioMode.values().toList(),
        selected = settings.defaultAspectRatio,
        onSelect = onAspectRatioChange,
        labelProvider = { it.label },
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Decoder Mode
    SettingSelectorItem(
        title = "Hardware Acceleration",
        subtitle = "Prefer hardware decoder chips for smooth 4K/60fps TV streaming.",
        options = DecoderMode.values().toList(),
        selected = settings.decoderMode,
        onSelect = onDecoderChange,
        labelProvider = { it.label },
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Keep Screen Awake
    SettingToggleItem(
        title = "Prevent TV Sleep During Playback",
        subtitle = "Keep screen alive and block TV screen savers while video is playing.",
        isChecked = settings.keepScreenAwake,
        onCheckedChange = onKeepScreenAwake,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )
}

@Composable
private fun AudioSubsSettingsContent(
    settings: AppSettings,
    initialFocus: FocusRequester,
    leftNavFocus: FocusRequester,
    onAudioLangChange: (String) -> Unit,
    onToggleSubtitles: (Boolean) -> Unit,
    onSubLangChange: (String) -> Unit,
    onSubSizeChange: (SubtitleTextSize) -> Unit
) {
    SectionHeader("Audio & Subtitles", "Configure default tracks, subtitle languages, and font size.")

    val audioOptions = listOf(
        "default" to "Stream Default",
        "en" to "English",
        "ar" to "Arabic",
        "fr" to "French",
        "es" to "Spanish",
        "de" to "German"
    )

    // Preferred Audio Language
    SettingSelectorItem(
        title = "Preferred Audio Track Language",
        subtitle = "Automatically select matching audio tracks when streams have multiple languages.",
        options = audioOptions.map { it.first },
        selected = settings.preferredAudioLanguage,
        onSelect = onAudioLangChange,
        labelProvider = { code -> audioOptions.firstOrNull { it.first == code }?.second ?: code },
        modifier = Modifier.focusRequester(initialFocus).focusProperties { left = leftNavFocus }
    )

    // Default Subtitles Toggle
    SettingToggleItem(
        title = "Enable Subtitles by Default",
        subtitle = "Automatically turn subtitles on when matching text tracks exist.",
        isChecked = settings.defaultSubtitlesEnabled,
        onCheckedChange = onToggleSubtitles,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    val subOptions = listOf(
        "en" to "English",
        "ar" to "Arabic",
        "fr" to "French",
        "es" to "Spanish",
        "de" to "German"
    )

    // Preferred Subtitle Language
    SettingSelectorItem(
        title = "Preferred Subtitle Language",
        subtitle = "Language code used when prioritizing subtitle selection.",
        options = subOptions.map { it.first },
        selected = settings.preferredSubtitleLanguage,
        onSelect = onSubLangChange,
        labelProvider = { code -> subOptions.firstOrNull { it.first == code }?.second ?: code },
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Subtitle Text Size
    SettingSelectorItem(
        title = "Subtitle Font Size",
        subtitle = "Text scale rendered for closed captions and embedded subtitles.",
        options = SubtitleTextSize.values().toList(),
        selected = settings.subtitleTextSize,
        onSelect = onSubSizeChange,
        labelProvider = { it.label },
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountsSettingsContent(
    uiState: DashboardUiState,
    initialFocus: FocusRequester,
    leftNavFocus: FocusRequester,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String, String) -> Unit,
    onSetDefaultAccount: (String, String) -> Unit,
    onRemoveAccount: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeader("Accounts Manager", "Switch between multiple IPTV subscriptions and manage credentials.")
        Button(
            onClick = onAddAccount,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.height(34.dp).focusRequester(initialFocus).focusProperties { left = leftNavFocus }
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
            Spacer(Modifier.width(6.dp))
            Text("Add Account", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }

    if (uiState.accounts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No accounts stored.", style = IpxTypography.BodyMedium, color = TextMuted)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            uiState.accounts.forEach { account ->
                val isActive = uiState.activeAccount?.username == account.username && uiState.activeAccount?.server == account.server
                val isDefault = uiState.defaultAccount?.username == account.username && uiState.defaultAccount?.server == account.server

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) SlateCard else SlateDeep)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(account.username, style = IpxTypography.TitleMedium, color = TextPrimary)
                                Text(account.server.removePrefix("http://").removePrefix("https://").substringBefore("/"), style = IpxTypography.BodySmall, color = TextMuted)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("Active", fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (isDefault) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("Default", fontSize = 11.sp, color = AccentAmber, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!isActive) {
                                Button(
                                    onClick = { onSwitchAccount(account.server, account.username) },
                                    modifier = Modifier.height(30.dp).focusProperties { left = leftNavFocus }
                                ) {
                                    Text("Switch", fontSize = 11.sp, color = Color.Black)
                                }
                            }
                            if (!isDefault) {
                                Button(
                                    onClick = { onSetDefaultAccount(account.server, account.username) },
                                    modifier = Modifier.height(30.dp).focusProperties { left = leftNavFocus },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = SlateCard
                                    )
                                ) {
                                    Text("Set Default", fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                            Button(
                                onClick = { onRemoveAccount(account.server, account.username) },
                                modifier = Modifier.height(30.dp).focusProperties { left = leftNavFocus },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Red.copy(alpha = 0.75f),
                                    focusedContainerColor = Color.Red
                                )
                            ) {
                                Text("Remove", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataStorageSettingsContent(
    settings: AppSettings,
    initialFocus: FocusRequester,
    leftNavFocus: FocusRequester,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onClearImageCache: () -> Unit,
    onClearWatchHistory: () -> Unit
) {
    SectionHeader("Data & Storage", "Manage offline files, local poster disk cache, and continue watching records.")

    // Auto Refresh Cache
    SettingToggleItem(
        title = "Auto-Refresh Content on App Launch",
        subtitle = "Perform background Stale-While-Revalidate refresh for categories and channel listings.",
        isChecked = settings.autoRefreshCacheOnStartup,
        onCheckedChange = onToggleAutoRefresh,
        modifier = Modifier.focusRequester(initialFocus).focusProperties { left = leftNavFocus }
    )

    // Clear Poster Cache
    SettingActionItem(
        title = "Clear Poster & Image Cache",
        subtitle = "Removes cached channel thumbnails and movie posters to free local TV storage.",
        actionLabel = "Clear Cache",
        onClick = onClearImageCache,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )

    // Clear Watch History
    SettingActionItem(
        title = "Clear Watch History & Progress",
        subtitle = "Resets all resume points and clears items in Continue Watching row.",
        actionLabel = "Clear History",
        isDestructive = true,
        onClick = onClearWatchHistory,
        modifier = Modifier.focusProperties { left = leftNavFocus }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AboutSettingsContent(
    uiState: DashboardUiState,
    initialFocus: FocusRequester,
    leftNavFocus: FocusRequester,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    SectionHeader("About & Updates", "Version diagnostics, legal notices, and OTA software updates.")

    // App Information Box
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("IPXtream TV Client", style = IpxTypography.TitleMedium, color = AccentCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Version: v${BuildConfig.VERSION_NAME}", style = IpxTypography.BodySmall, color = TextPrimary)
                Text("Build Type: ${BuildConfig.BUILD_TYPE}", style = IpxTypography.BodySmall, color = TextSecondary)
                Text("Target: Android TV (Leanback)", style = IpxTypography.BodySmall, color = TextMuted)
            }
        }
    }

    // Software Update Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Software Update", style = IpxTypography.TitleMedium, color = Color.White)

            when {
                uiState.isCheckingForUpdate -> {
                    Text("Checking GitHub repository for latest release...", style = IpxTypography.BodyMedium, color = TextSecondary)
                }
                uiState.updateDownloadProgress != null -> {
                    val progress = uiState.updateDownloadProgress
                    Text("Downloading update: ${(progress * 100).toInt()}%", style = IpxTypography.BodyMedium, color = AccentCyan)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        color = AccentCyan,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
                uiState.updateRelease != null -> {
                    val release = uiState.updateRelease
                    Text("New version ${release.tagName} is available!", style = IpxTypography.BodyMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
                    if (!release.body.isNullOrBlank()) {
                        Text(release.body, style = IpxTypography.BodySmall, color = TextSecondary, maxLines = 4)
                    }
                }
                else -> {
                    Text("Running the latest version (v${BuildConfig.VERSION_NAME}).", style = IpxTypography.BodyMedium, color = TextSecondary)
                }
            }

            if (uiState.updateErrorMessage != null) {
                Text(uiState.updateErrorMessage, style = IpxTypography.BodySmall, color = Color.Red)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uiState.updateRelease != null && uiState.updateDownloadProgress == null) {
                    Button(
                        onClick = onDownloadUpdate,
                        modifier = Modifier.height(34.dp).focusRequester(initialFocus).focusProperties { left = leftNavFocus }
                    ) {
                        Text("Download Update", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onDismissUpdate,
                        modifier = Modifier.height(34.dp).focusProperties { left = leftNavFocus }
                    ) {
                        Text("Later")
                    }
                } else {
                    Button(
                        onClick = onCheckUpdates,
                        enabled = !uiState.isCheckingForUpdate && uiState.updateDownloadProgress == null,
                        modifier = Modifier.height(34.dp).focusRequester(initialFocus).focusProperties { left = leftNavFocus }
                    ) {
                        Text(if (uiState.isCheckingForUpdate) "Checking..." else "Check for Updates")
                    }
                }
            }
        }
    }

    // Legal Buttons
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onOpenTerms,
            modifier = Modifier.height(34.dp).focusProperties { left = leftNavFocus },
            colors = ButtonDefaults.colors(
                containerColor = SlateCard,
                focusedContainerColor = AccentCyan
            )
        ) {
            Text("Terms & Conditions", style = IpxTypography.BodySmall)
        }

        Button(
            onClick = onOpenPrivacy,
            modifier = Modifier.height(34.dp).focusProperties { left = leftNavFocus },
            colors = ButtonDefaults.colors(
                containerColor = SlateCard,
                focusedContainerColor = AccentCyan
            )
        ) {
            Text("Privacy Policy", style = IpxTypography.BodySmall)
        }
    }
}

// =============================================================================
//  Shared Setting Row Helpers
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(text = title, style = IpxTypography.TitleLarge, color = AccentCyan)
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, style = IpxTypography.BodySmall, color = TextMuted)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingToggleItem(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { onCheckedChange(!isChecked) },
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SlateCard,
            focusedContainerColor = SlateGlass
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(10.dp)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = IpxTypography.BodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = IpxTypography.BodySmall.copy(fontSize = 11.sp), color = TextMuted, maxLines = 1)
            }

            Spacer(Modifier.width(16.dp))

            // High Contrast Switch Pill
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(26.dp)
                    .clip(CircleShape)
                    .background(if (isChecked) AccentCyan else Color.White.copy(alpha = 0.15f))
                    .padding(3.dp),
                contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isChecked) Color.Black else Color.White.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> SettingSelectorItem(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(text = title, style = IpxTypography.BodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = IpxTypography.BodySmall.copy(fontSize = 11.sp), color = TextMuted)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isOptionSelected = option == selected
                var isOptionFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .height(34.dp)
                        .onFocusChanged { isOptionFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isOptionSelected) AccentCyan.copy(alpha = 0.2f) else SlateDeep,
                        focusedContainerColor = if (isOptionSelected) AccentCyan else Color.White.copy(alpha = 0.25f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            if (isOptionSelected) BorderStroke(1.dp, AccentCyan) else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(6.dp)
                        ),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp))
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isOptionSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isOptionFocused) Color.Black else AccentCyan
                            )
                        }
                        Text(
                            text = labelProvider(option),
                            fontSize = 12.sp,
                            fontWeight = if (isOptionSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOptionFocused && isOptionSelected) Color.Black else if (isOptionSelected) AccentCyan else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingActionItem(
    title: String,
    subtitle: String,
    actionLabel: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SlateCard,
            focusedContainerColor = if (isDestructive) Color(0xFF8B0000) else SlateGlass
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(10.dp)),
            focusedBorder = Border(BorderStroke(2.dp, if (isDestructive) Color.Red else Color.White), shape = RoundedCornerShape(10.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = IpxTypography.BodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = IpxTypography.BodySmall.copy(fontSize = 11.sp), color = TextMuted, maxLines = 1)
            }

            Spacer(Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isDestructive) Color.Red.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    color = if (isDestructive) Color.Red else AccentCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LegalInfoDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { closeFocusRequester.requestFocus() }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE608080C))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = { /* No-op */ },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.9f),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            colors = CardDefaults.colors(
                containerColor = SlatePrimary,
                focusedContainerColor = SlatePrimary
            ),
            border = CardDefaults.border(
                border = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(12.dp)),
                focusedBorder = Border(BorderStroke(1.dp, BorderSubtle), shape = RoundedCornerShape(12.dp))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = title,
                        style = IpxTypography.TitleLarge.copy(fontSize = 24.sp),
                        color = AccentCyan
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = content,
                            style = IpxTypography.BodyMedium,
                            color = TextSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(38.dp)
                            .focusRequester(closeFocusRequester),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = AccentCyan,
                            focusedContainerColor = AccentCyan
                        )
                    ) {
                        Text(
                            text = "Close",
                            style = IpxTypography.BodyMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
