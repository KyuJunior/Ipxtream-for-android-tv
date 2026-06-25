package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.ipxtream.tv.BuildConfig
import com.ipxtream.tv.ui.dashboard.DashboardUiState
import com.ipxtream.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: DashboardUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checkUpdatesFocusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { checkUpdatesFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDeep)
            .padding(48.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Screen Header
            Text(
                text = "Settings",
                style = IpxTypography.DisplayLarge.copy(fontSize = 40.sp),
                color = TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Left Column: App Info / Info Card
                Card(
                    onClick = { /* Detail about app info if needed */ },
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = SlatePrimary,
                        focusedContainerColor = SlateCard
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Application Info",
                            style = IpxTypography.TitleLarge,
                            color = AccentCyan
                        )

                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Name: IPXtream TV", style = IpxTypography.BodyMedium, color = TextPrimary)
                            Text("Version: v${BuildConfig.VERSION_NAME}", style = IpxTypography.BodyMedium, color = TextSecondary)
                            Text("Build Type: ${BuildConfig.BUILD_TYPE}", style = IpxTypography.BodyMedium, color = TextMuted)
                            Text("Target Device: Android TV", style = IpxTypography.BodyMedium, color = TextMuted)
                        }
                    }
                }

                // Right Column: System Updates & Configuration
                Card(
                    onClick = {
                        if (uiState.updateRelease != null && uiState.updateDownloadProgress == null) {
                            onDownloadUpdate()
                        } else if (uiState.updateRelease == null && !uiState.isCheckingForUpdate) {
                            onCheckForUpdates()
                        }
                    },
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .focusRequester(checkUpdatesFocusRequester),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = SlatePrimary,
                        focusedContainerColor = SlateCard
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Software Update",
                                style = IpxTypography.TitleLarge,
                                color = AccentCyan
                            )

                            Spacer(Modifier.height(8.dp))

                            val currentVersion = BuildConfig.VERSION_NAME
                            when {
                                uiState.isCheckingForUpdate -> {
                                    Text("Checking GitHub repository for latest release...", style = IpxTypography.BodyMedium, color = TextSecondary)
                                    Text("Please wait...", style = IpxTypography.BodySmall, color = TextMuted)
                                }
                                uiState.updateDownloadProgress != null -> {
                                    val progress = uiState.updateDownloadProgress
                                    Text("Downloading update: ${(progress * 100).toInt()}%", style = IpxTypography.BodyMedium, color = AccentCyan)
                                    Spacer(Modifier.height(8.dp))
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { progress },
                                        color = AccentCyan,
                                        trackColor = Color.White.copy(alpha = 0.1f),
                                        strokeCap = StrokeCap.Round,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }
                                uiState.updateRelease != null -> {
                                    val release = uiState.updateRelease
                                    Text("New version ${release.tagName} is available!", style = IpxTypography.BodyMedium, color = AccentGreen)
                                    if (release.body != null) {
                                        Text(
                                            text = release.body,
                                            style = IpxTypography.BodySmall,
                                            color = TextSecondary,
                                            maxLines = 5
                                        )
                                    }
                                }
                                else -> {
                                    Text("You are currently running the latest version.", style = IpxTypography.BodyMedium, color = TextPrimary)
                                    Text("Current: v$currentVersion", style = IpxTypography.BodySmall, color = TextSecondary)
                                }
                            }

                            if (uiState.updateErrorMessage != null) {
                                Text(
                                    text = uiState.updateErrorMessage,
                                    style = IpxTypography.BodySmall,
                                    color = Color.Red,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        // Footer Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isCheckingForUpdate) {
                                Button(
                                    onClick = {},
                                    enabled = false
                                ) {
                                    Text("Checking...")
                                }
                            } else if (uiState.updateDownloadProgress != null) {
                                Button(
                                    onClick = {},
                                    enabled = false
                                ) {
                                    Text("Downloading...")
                                }
                            } else if (uiState.updateRelease != null) {
                                Button(
                                    onClick = onDownloadUpdate
                                ) {
                                    Text("Download & Install Now")
                                }
                                Button(
                                    onClick = onDismissUpdate
                                ) {
                                    Text("Later")
                                }
                            } else {
                                Button(
                                    onClick = onCheckForUpdates
                                ) {
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
