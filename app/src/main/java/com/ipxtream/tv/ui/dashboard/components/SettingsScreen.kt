package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    onSwitchAccount: (server: String, username: String) -> Unit,
    onSetDefaultAccount: (server: String, username: String) -> Unit,
    onRemoveAccount: (server: String, username: String) -> Unit,
    onAddAccount: () -> Unit,
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
            .padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            Text(
                text = "Settings",
                style = IpxTypography.DisplayLarge.copy(fontSize = 36.sp),
                color = TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Left Column: App Info
                Card(
                    onClick = { /* No-op */ },
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = SlatePrimary,
                        focusedContainerColor = SlatePrimary
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.Transparent), shape = RoundedCornerShape(12.dp))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Application Info",
                            style = IpxTypography.TitleLarge,
                            color = AccentCyan
                        )

                        Spacer(Modifier.height(4.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Name: IPXtream TV", style = IpxTypography.BodyMedium, color = TextPrimary)
                            Text("Version: v${BuildConfig.VERSION_NAME}", style = IpxTypography.BodyMedium, color = TextSecondary)
                            Text("Build Type: ${BuildConfig.BUILD_TYPE}", style = IpxTypography.BodyMedium, color = TextMuted)
                            Text("Target Device: Android TV", style = IpxTypography.BodyMedium, color = TextMuted)
                        }
                    }
                }

                // 2. Middle Column: Accounts Manager
                Card(
                    onClick = { /* No-op */ },
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.colors(
                        containerColor = SlatePrimary,
                        focusedContainerColor = SlatePrimary
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.Transparent), shape = RoundedCornerShape(12.dp))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Accounts Manager",
                                    style = IpxTypography.TitleLarge,
                                    color = AccentCyan
                                )
                                Button(
                                    onClick = onAddAccount,
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Account",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.Black
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add", fontSize = 12.sp, color = Color.Black)
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Scrollable list of accounts
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.accounts.isEmpty()) {
                                    Text(
                                        text = "No accounts saved.",
                                        style = IpxTypography.BodyMedium,
                                        color = TextMuted,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    uiState.accounts.forEach { account ->
                                        val isActive = uiState.activeAccount?.username == account.username && 
                                                uiState.activeAccount?.server == account.server
                                        val isDefault = uiState.defaultAccount?.username == account.username && 
                                                uiState.defaultAccount?.server == account.server

                                        // Individual Account item layout
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActive) SlateCard else SlateDeep)
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column {
                                                    Text(
                                                        text = account.username,
                                                        style = IpxTypography.BodyMedium,
                                                        color = TextPrimary
                                                    )
                                                    Text(
                                                        text = account.server.removePrefix("http://").removePrefix("https://").substringBefore("/"),
                                                        style = IpxTypography.BodySmall,
                                                        color = TextMuted
                                                    )
                                                }

                                                // Status badges
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isActive) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(AccentCyan.copy(alpha = 0.2f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("Active", fontSize = 10.sp, color = AccentCyan)
                                                        }
                                                    }
                                                    if (isDefault) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(AccentAmber.copy(alpha = 0.2f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("Default", fontSize = 10.sp, color = AccentAmber)
                                                        }
                                                    }
                                                }

                                                // Action Row
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    if (!isActive) {
                                                        Button(
                                                            onClick = { onSwitchAccount(account.server, account.username) },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("Switch", fontSize = 10.sp, color = Color.Black)
                                                        }
                                                    }
                                                    if (!isDefault) {
                                                        Button(
                                                            onClick = { onSetDefaultAccount(account.server, account.username) },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            colors = ButtonDefaults.colors(
                                                                containerColor = Color.Transparent,
                                                                focusedContainerColor = SlateCard
                                                            )
                                                        ) {
                                                            Text("Set Default", fontSize = 10.sp, color = TextPrimary)
                                                        }
                                                    }
                                                    Button(
                                                        onClick = { onRemoveAccount(account.server, account.username) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        colors = ButtonDefaults.colors(
                                                            containerColor = Color.Red.copy(alpha = 0.8f),
                                                            focusedContainerColor = Color.Red
                                                        )
                                                    ) {
                                                        Text("Remove", fontSize = 10.sp, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Right Column: Software Update
                Card(
                    onClick = {
                        if (uiState.updateRelease != null && uiState.updateDownloadProgress == null) {
                            onDownloadUpdate()
                        } else if (uiState.updateRelease == null && !uiState.isCheckingForUpdate) {
                            onCheckForUpdates()
                        }
                    },
                    modifier = Modifier
                        .weight(0.28f)
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
                        modifier = Modifier.padding(20.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Software Update",
                                style = IpxTypography.TitleLarge,
                                color = AccentCyan
                            )

                            Spacer(Modifier.height(4.dp))

                            val currentVersion = BuildConfig.VERSION_NAME
                            when {
                                uiState.isCheckingForUpdate -> {
                                    Text("Checking repository for latest release...", style = IpxTypography.BodyMedium, color = TextSecondary)
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
                                            maxLines = 4
                                        )
                                    }
                                }
                                else -> {
                                    Text("Running the latest version.", style = IpxTypography.BodyMedium, color = TextPrimary)
                                    Text("Current: v$currentVersion", style = IpxTypography.BodySmall, color = TextSecondary)
                                }
                            }

                            if (uiState.updateErrorMessage != null) {
                                Text(
                                    text = uiState.updateErrorMessage,
                                    style = IpxTypography.BodySmall,
                                    color = Color.Red,
                                    modifier = Modifier.padding(top = 4.dp)
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
                                    Text("Download Now")
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
                                    Text("Check Updates")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
