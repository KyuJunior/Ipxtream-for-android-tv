package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.ui.theme.directionalKeys
import androidx.annotation.StringRes
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.BorderFocus
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.Accent

enum class SettingsCategory(@StringRes val titleRes: Int, val icon: ImageVector) {
    GENERAL(R.string.settings_cat_general, Icons.Rounded.Palette),
    PLAYBACK(R.string.settings_cat_playback, Icons.Rounded.PlayCircle),
    AUDIO_SUBS(R.string.settings_cat_audio, Icons.Rounded.Subtitles),
    ACCOUNTS(R.string.settings_cat_accounts, Icons.Rounded.ManageAccounts),
    DATA(R.string.settings_cat_data, Icons.Rounded.Storage),
    ABOUT(R.string.settings_cat_about, Icons.Rounded.Info)
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
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }
    val keys = directionalKeys()
    val leftNavFocusRequester = remember { FocusRequester() }
    val rightContentFocusRequester = remember { FocusRequester() }

    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val imageCacheClearedMsg = stringResource(R.string.feedback_image_cache_cleared)
    val historyClearedMsg = stringResource(R.string.feedback_history_cleared)

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
                            .background(Accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
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
                // Up from the top of either pane must not slide diagonally into the side menu
                // (the nearest thing above-left); Left/Back still reach the menu on purpose.
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusProperties {
                        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                        exit = { direction ->
                            if (direction == androidx.compose.ui.focus.FocusDirection.Up) FocusRequester.Cancel
                            else FocusRequester.Default
                        }
                    }
                    .focusGroup(),
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
                                        if (event.type == KeyEventType.KeyDown && event.key == keys.end) {
                                            runCatching { rightContentFocusRequester.requestFocus() }
                                            true
                                        } else false
                                    },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Accent.copy(alpha = 0.15f) else Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.2f)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(
                                        if (isSelected) BorderStroke(1.dp, Accent.copy(alpha = 0.4f)) else BorderStroke(0.dp, Color.Transparent),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                    focusedBorder = Border(BorderStroke(2.dp, BorderFocus), shape = RoundedCornerShape(8.dp))
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
                                        contentDescription = stringResource(category.titleRes),
                                        tint = if (isSelected || isFocused) Accent else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(category.titleRes),
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
                                    onPerformanceModeChange = onPerformanceModeChange,
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
                                        feedbackMessage = imageCacheClearedMsg
                                    },
                                    onClearWatchHistory = {
                                        onClearWatchHistory()
                                        feedbackMessage = historyClearedMsg
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
                title = stringResource(R.string.legal_terms_title),
                content = stringResource(R.string.legal_terms_body),
                onDismiss = { showTermsDialog = false }
            )
        }

        if (showPrivacyDialog) {
            LegalInfoDialog(
                title = stringResource(R.string.legal_privacy_title),
                content = stringResource(R.string.legal_privacy_body),
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
    onPerformanceModeChange: (com.ipxtream.tv.data.local.PerformanceMode) -> Unit,
    onToggleChannelNumbers: (Boolean) -> Unit,
    onToggle24HourClock: (Boolean) -> Unit,
    onToggleWhatsNew: (Boolean) -> Unit,
    onToggleDownloads: (Boolean) -> Unit,
    onToggleLibrary: (Boolean) -> Unit
) {
    SectionHeader(stringResource(R.string.settings_general_title), stringResource(R.string.settings_general_sub))

    // App Language Selector
    SettingSelectorItem(
        title = stringResource(R.string.settings_language),
        subtitle = stringResource(R.string.settings_language_sub),
        options = AppLanguage.values().toList(),
        selected = settings.appLanguage,
        onSelect = onLanguageChange,
        labelProvider = { it.displayName },
        modifier = Modifier.focusRequester(initialFocus).focusProperties { start = leftNavFocus }
    )

    // Performance mode
    val context = androidx.compose.ui.platform.LocalContext.current
    val autoIsReduced = androidx.compose.runtime.remember {
        com.ipxtream.tv.ui.theme.reducedEffectsFor(context, com.ipxtream.tv.data.local.PerformanceMode.AUTO)
    }
    SettingSelectorItem(
        title = stringResource(R.string.settings_performance),
        subtitle = when (settings.performanceMode) {
            com.ipxtream.tv.data.local.PerformanceMode.AUTO ->
                stringResource(R.string.settings_performance_auto_sub, stringResource(if (autoIsReduced) R.string.performance_current_smooth else R.string.performance_current_full))
            com.ipxtream.tv.data.local.PerformanceMode.FULL ->
                stringResource(R.string.settings_performance_full_sub)
            com.ipxtream.tv.data.local.PerformanceMode.SMOOTH ->
                stringResource(R.string.settings_performance_smooth_sub)
        },
        options = com.ipxtream.tv.data.local.PerformanceMode.values().toList(),
        selected = settings.performanceMode,
        onSelect = onPerformanceModeChange,
        labelProvider = { stringResource(it.labelRes) },
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Dynamic Spotlight
    SettingToggleItem(
        title = stringResource(R.string.settings_spotlight),
        subtitle = stringResource(R.string.settings_spotlight_sub),
        isChecked = settings.showSpotlightBackdrop,
        onCheckedChange = onToggleSpotlight,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Channel Numbers
    SettingToggleItem(
        title = stringResource(R.string.settings_channel_numbers),
        subtitle = stringResource(R.string.settings_channel_numbers_sub),
        isChecked = settings.showChannelNumbers,
        onCheckedChange = onToggleChannelNumbers,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Clock Format
    SettingToggleItem(
        title = stringResource(R.string.settings_clock_24h),
        subtitle = stringResource(R.string.settings_clock_24h_sub),
        isChecked = settings.is24HourClockFormat,
        onCheckedChange = onToggle24HourClock,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.settings_side_nav_sections), style = IpxTypography.TitleMedium, color = Accent)

    SettingToggleItem(
        title = stringResource(R.string.settings_show_whats_new),
        subtitle = stringResource(R.string.settings_show_whats_new_sub),
        isChecked = settings.showWhatsNewSection,
        onCheckedChange = onToggleWhatsNew,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    SettingToggleItem(
        title = stringResource(R.string.settings_show_downloads),
        subtitle = stringResource(R.string.settings_show_downloads_sub),
        isChecked = settings.showDownloadsSection,
        onCheckedChange = onToggleDownloads,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    SettingToggleItem(
        title = stringResource(R.string.settings_show_library),
        subtitle = stringResource(R.string.settings_show_library_sub),
        isChecked = settings.showLibrarySection,
        onCheckedChange = onToggleLibrary,
        modifier = Modifier.focusProperties { start = leftNavFocus }
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
    SectionHeader(stringResource(R.string.settings_playback_title), stringResource(R.string.settings_playback_sub))

    // Buffer Profile
    SettingSelectorItem(
        title = stringResource(R.string.settings_buffer),
        subtitle = stringResource(R.string.settings_buffer_sub),
        options = BufferProfile.values().toList(),
        selected = settings.bufferProfile,
        onSelect = onBufferChange,
        labelProvider = { stringResource(it.labelRes) },
        modifier = Modifier.focusRequester(initialFocus).focusProperties { start = leftNavFocus }
    )

    // Auto Play Next Episode
    SettingToggleItem(
        title = stringResource(R.string.settings_autoplay_next),
        subtitle = stringResource(R.string.settings_autoplay_next_sub),
        isChecked = settings.autoPlayNextEpisode,
        onCheckedChange = onAutoPlayNext,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Aspect Ratio
    SettingSelectorItem(
        title = stringResource(R.string.settings_aspect),
        subtitle = stringResource(R.string.settings_aspect_sub),
        options = AspectRatioMode.values().toList(),
        selected = settings.defaultAspectRatio,
        onSelect = onAspectRatioChange,
        labelProvider = { stringResource(it.labelRes) },
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Decoder Mode
    SettingSelectorItem(
        title = stringResource(R.string.settings_hw_accel),
        subtitle = stringResource(R.string.settings_hw_accel_sub),
        options = DecoderMode.values().toList(),
        selected = settings.decoderMode,
        onSelect = onDecoderChange,
        labelProvider = { stringResource(it.labelRes) },
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Keep Screen Awake
    SettingToggleItem(
        title = stringResource(R.string.settings_keep_awake),
        subtitle = stringResource(R.string.settings_keep_awake_sub),
        isChecked = settings.keepScreenAwake,
        onCheckedChange = onKeepScreenAwake,
        modifier = Modifier.focusProperties { start = leftNavFocus }
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
    SectionHeader(stringResource(R.string.settings_audio_title), stringResource(R.string.settings_audio_sub))

    val audioOptions = listOf(
        "default" to stringResource(R.string.lang_stream_default),
        "en" to stringResource(R.string.lang_english),
        "ar" to stringResource(R.string.lang_arabic),
        "fr" to stringResource(R.string.lang_french),
        "es" to stringResource(R.string.lang_spanish),
        "de" to stringResource(R.string.lang_german)
    )

    // Preferred Audio Language
    SettingSelectorItem(
        title = stringResource(R.string.settings_audio_lang),
        subtitle = stringResource(R.string.settings_audio_lang_sub),
        options = audioOptions.map { it.first },
        selected = settings.preferredAudioLanguage,
        onSelect = onAudioLangChange,
        labelProvider = { code -> audioOptions.firstOrNull { it.first == code }?.second ?: code },
        modifier = Modifier.focusRequester(initialFocus).focusProperties { start = leftNavFocus }
    )

    // Default Subtitles Toggle
    SettingToggleItem(
        title = stringResource(R.string.settings_subs_default),
        subtitle = stringResource(R.string.settings_subs_default_sub),
        isChecked = settings.defaultSubtitlesEnabled,
        onCheckedChange = onToggleSubtitles,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    val subOptions = listOf(
        "en" to stringResource(R.string.lang_english),
        "ar" to stringResource(R.string.lang_arabic),
        "fr" to stringResource(R.string.lang_french),
        "es" to stringResource(R.string.lang_spanish),
        "de" to stringResource(R.string.lang_german)
    )

    // Preferred Subtitle Language
    SettingSelectorItem(
        title = stringResource(R.string.settings_sub_lang),
        subtitle = stringResource(R.string.settings_sub_lang_sub),
        options = subOptions.map { it.first },
        selected = settings.preferredSubtitleLanguage,
        onSelect = onSubLangChange,
        labelProvider = { code -> subOptions.firstOrNull { it.first == code }?.second ?: code },
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Subtitle Text Size
    SettingSelectorItem(
        title = stringResource(R.string.settings_sub_size),
        subtitle = stringResource(R.string.settings_sub_size_sub),
        options = SubtitleTextSize.values().toList(),
        selected = settings.subtitleTextSize,
        onSelect = onSubSizeChange,
        labelProvider = { stringResource(it.labelRes) },
        modifier = Modifier.focusProperties { start = leftNavFocus }
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
        SectionHeader(stringResource(R.string.settings_accounts_title), stringResource(R.string.settings_accounts_sub))
        Button(
            onClick = onAddAccount,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.height(34.dp).focusRequester(initialFocus).focusProperties { start = leftNavFocus }
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextOnFocus)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.accounts_add), fontSize = 12.sp, color = TextOnFocus, fontWeight = FontWeight.Bold)
        }
    }

    if (uiState.accounts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.accounts_none), style = IpxTypography.BodyMedium, color = TextMuted)
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
                                Text(bidi(account.username), style = IpxTypography.TitleMedium, color = TextPrimary)
                                Text(bidi(account.server.removePrefix("http://").removePrefix("https://").substringBefore("/")), style = IpxTypography.BodySmall, color = TextMuted)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Accent.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(stringResource(R.string.accounts_active), fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (isDefault) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(stringResource(R.string.accounts_default), fontSize = 11.sp, color = AccentAmber, fontWeight = FontWeight.Bold)
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
                                    modifier = Modifier.height(30.dp).focusProperties { start = leftNavFocus }
                                ) {
                                    Text(stringResource(R.string.accounts_switch), fontSize = 11.sp, color = TextOnFocus)
                                }
                            }
                            if (!isDefault) {
                                Button(
                                    onClick = { onSetDefaultAccount(account.server, account.username) },
                                    modifier = Modifier.height(30.dp).focusProperties { start = leftNavFocus },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = SlateCard
                                    )
                                ) {
                                    Text(stringResource(R.string.accounts_set_default), fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                            Button(
                                onClick = { onRemoveAccount(account.server, account.username) },
                                modifier = Modifier.height(30.dp).focusProperties { start = leftNavFocus },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Red.copy(alpha = 0.75f),
                                    focusedContainerColor = Color.Red
                                )
                            ) {
                                Text(stringResource(R.string.accounts_remove), fontSize = 11.sp, color = Color.White)
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
    SectionHeader(stringResource(R.string.settings_data_title), stringResource(R.string.settings_data_sub))

    // Auto Refresh Cache
    SettingToggleItem(
        title = stringResource(R.string.settings_auto_refresh),
        subtitle = stringResource(R.string.settings_auto_refresh_sub),
        isChecked = settings.autoRefreshCacheOnStartup,
        onCheckedChange = onToggleAutoRefresh,
        modifier = Modifier.focusRequester(initialFocus).focusProperties { start = leftNavFocus }
    )

    // Clear Poster Cache
    SettingActionItem(
        title = stringResource(R.string.settings_clear_images),
        subtitle = stringResource(R.string.settings_clear_images_sub),
        actionLabel = stringResource(R.string.settings_clear_cache),
        onClick = onClearImageCache,
        modifier = Modifier.focusProperties { start = leftNavFocus }
    )

    // Clear Watch History
    SettingActionItem(
        title = stringResource(R.string.settings_clear_history),
        subtitle = stringResource(R.string.settings_clear_history_sub),
        actionLabel = stringResource(R.string.settings_clear_history_action),
        isDestructive = true,
        onClick = onClearWatchHistory,
        modifier = Modifier.focusProperties { start = leftNavFocus }
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
    SectionHeader(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_sub))

    // App Information Box
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.about_client), style = IpxTypography.TitleMedium, color = Accent)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = IpxTypography.BodySmall, color = TextPrimary)
                Text(stringResource(R.string.about_build_type, BuildConfig.BUILD_TYPE), style = IpxTypography.BodySmall, color = TextSecondary)
                Text(stringResource(R.string.about_target), style = IpxTypography.BodySmall, color = TextMuted)
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
            Text(stringResource(R.string.about_software_update), style = IpxTypography.TitleMedium, color = Color.White)

            when {
                uiState.isCheckingForUpdate -> {
                    Text(stringResource(R.string.about_checking_github), style = IpxTypography.BodyMedium, color = TextSecondary)
                }
                uiState.updateDownloadProgress != null -> {
                    val progress = uiState.updateDownloadProgress
                    // The Download button disappears while downloading; park focus on the progress
                    // text so it doesn't fall to the search field (which opens the keyboard).
                    val progressFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { progressFocus.requestFocus() } }
                    Text(
                        stringResource(R.string.update_downloading_pct, (progress * 100).toInt()),
                        style = IpxTypography.BodyMedium,
                        color = Accent,
                        modifier = Modifier.focusRequester(progressFocus).focusable()
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
                uiState.updateRelease != null -> {
                    val release = uiState.updateRelease
                    Text(stringResource(R.string.update_new_version, release.tagName), style = IpxTypography.BodyMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
                }
                else -> {
                    Text(stringResource(R.string.update_running_latest, BuildConfig.VERSION_NAME), style = IpxTypography.BodyMedium, color = TextSecondary)
                }
            }

            if (uiState.updateErrorMessage != null) {
                Text(uiState.updateErrorMessage, style = IpxTypography.BodySmall, color = Color.Red)
            }

            // When a download ends (installer opened, or an error), give focus back to the buttons.
            val downloading = uiState.updateDownloadProgress != null
            var wasDownloading by remember { mutableStateOf(false) }
            LaunchedEffect(downloading) {
                if (downloading) {
                    wasDownloading = true
                } else if (wasDownloading) {
                    wasDownloading = false
                    kotlinx.coroutines.delay(50)
                    runCatching { initialFocus.requestFocus() }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uiState.updateRelease != null && uiState.updateDownloadProgress == null) {
                    Button(
                        onClick = onDownloadUpdate,
                        modifier = Modifier.height(34.dp).focusRequester(initialFocus).focusProperties { start = leftNavFocus }
                    ) {
                        Text(stringResource(R.string.update_download), color = TextOnFocus, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onDismissUpdate,
                        modifier = Modifier.height(34.dp).focusProperties { start = leftNavFocus }
                    ) {
                        Text(stringResource(R.string.action_later))
                    }
                } else {
                    Button(
                        onClick = onCheckUpdates,
                        enabled = !uiState.isCheckingForUpdate && uiState.updateDownloadProgress == null,
                        modifier = Modifier.height(34.dp).focusRequester(initialFocus).focusProperties { start = leftNavFocus }
                    ) {
                        Text(if (uiState.isCheckingForUpdate) stringResource(R.string.update_checking) else stringResource(R.string.update_check))
                    }
                }
            }
        }
    }

    // Most recent crash, kept locally by IpxApplication (nothing is uploaded).
    val crashContext = androidx.compose.ui.platform.LocalContext.current
    var lastCrash by remember { mutableStateOf(com.ipxtream.tv.data.local.CrashLog.latest(crashContext)) }
    lastCrash?.let { crash ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlateCard)
                .padding(16.dp)
        ) {
            val time = remember(crash.timeMs) {
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(crash.timeMs))
            }
            Text(stringResource(R.string.about_last_crash, time), style = IpxTypography.TitleMedium, color = Color.White)
            Text(stringResource(R.string.about_crash_hint), style = IpxTypography.BodySmall, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                text = crash.text.lineSequence().take(8).joinToString("\n"),
                style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                color = TextSecondary,
                maxLines = 8
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { com.ipxtream.tv.data.local.CrashLog.clear(crashContext); lastCrash = null },
                modifier = Modifier.height(34.dp).focusProperties { start = leftNavFocus }
            ) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }

    // Legal Buttons
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onOpenTerms,
            modifier = Modifier.height(34.dp).focusProperties { start = leftNavFocus },
            colors = ButtonDefaults.colors(
                containerColor = SlateCard,
                focusedContainerColor = Accent
            )
        ) {
            Text(stringResource(R.string.legal_terms_short), style = IpxTypography.BodySmall)
        }

        Button(
            onClick = onOpenPrivacy,
            modifier = Modifier.height(34.dp).focusProperties { start = leftNavFocus },
            colors = ButtonDefaults.colors(
                containerColor = SlateCard,
                focusedContainerColor = Accent
            )
        ) {
            Text(stringResource(R.string.legal_privacy_title), style = IpxTypography.BodySmall)
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
        Text(text = title, style = IpxTypography.TitleLarge, color = Accent)
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
            focusedBorder = Border(BorderStroke(2.dp, BorderFocus), shape = RoundedCornerShape(10.dp))
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
                    .background(if (isChecked) Accent else Color.White.copy(alpha = 0.15f))
                    .padding(3.dp),
                contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isChecked) TextOnFocus else Color.White.copy(alpha = 0.6f))
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
    labelProvider: @Composable (T) -> String,
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
                        containerColor = if (isOptionSelected) Accent.copy(alpha = 0.2f) else SlateDeep,
                        focusedContainerColor = if (isOptionSelected) Accent else Color.White.copy(alpha = 0.25f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            if (isOptionSelected) BorderStroke(1.dp, Accent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(6.dp)
                        ),
                        focusedBorder = Border(BorderStroke(2.dp, BorderFocus), shape = RoundedCornerShape(6.dp))
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
                                tint = if (isOptionFocused) TextOnAccent else Accent
                            )
                        }
                        Text(
                            text = labelProvider(option),
                            fontSize = 12.sp,
                            fontWeight = if (isOptionSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOptionFocused && isOptionSelected) TextOnAccent else if (isOptionSelected) Accent else TextPrimary
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
            focusedBorder = Border(BorderStroke(2.dp, if (isDestructive) AccentRed else BorderFocus), shape = RoundedCornerShape(10.dp))
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
                    .background(if (isDestructive) Color.Red.copy(alpha = 0.2f) else Accent.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    color = if (isDestructive) Color.Red else Accent,
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
            .background(SlateDeep.copy(alpha = 0xE6 / 255f))
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
                        color = Accent
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
                            containerColor = Accent,
                            focusedContainerColor = Accent
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            style = IpxTypography.BodyMedium,
                            color = TextOnAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
