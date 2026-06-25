package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.ipxtream.tv.data.download.DownloadItem
import com.ipxtream.tv.data.download.DownloadStatus
import com.ipxtream.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onClearDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clearButtonFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    val hasClearable = downloads.any { it.isFinished || it.hasFailed }

    androidx.compose.runtime.LaunchedEffect(downloads.size) {
        if (downloads.isNotEmpty() && !hasClearable) {
            runCatching { firstItemFocusRequester.requestFocus() }
        } else if (hasClearable) {
            runCatching { clearButtonFocusRequester.requestFocus() }
        }
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
            // Header row with "Downloads" Title and "Clear Done" action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads",
                    style = IpxTypography.DisplayLarge.copy(fontSize = 40.sp),
                    color = TextPrimary
                )

                if (hasClearable) {
                    Button(
                        onClick = onClearDone,
                        modifier = Modifier.focusRequester(clearButtonFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.DarkGray.copy(alpha = 0.5f),
                            focusedContainerColor = Color.LightGray,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text("Clear Done / Failed", style = IpxTypography.BodyMedium)
                    }
                }
            }

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬇", fontSize = 48.sp, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No downloads yet.",
                            style = IpxTypography.TitleMedium,
                            color = TextMuted
                        )
                        Text(
                            text = "Long-press OK on Movies or Series episodes to start downloads.",
                            style = IpxTypography.BodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        val isFirst = downloads.firstOrNull() == item
                        DownloadManagerCard(
                            item = item,
                            onPause = { onPause(item.id) },
                            onResume = { onResume(item.id) },
                            onCancel = { onCancel(item.id) },
                            onRetry = { onRetry(item.id) },
                            modifier = if (isFirst && !hasClearable) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadManagerCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressAnim by animateFloatAsState(
        targetValue = item.progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "downloadsScreenProgress_${item.id}"
    )

    Card(
        onClick = {
            // D-Pad selection triggers appropriate primary action
            when (item.status) {
                DownloadStatus.DOWNLOADING -> onPause()
                DownloadStatus.PAUSED, DownloadStatus.PENDING -> onResume()
                DownloadStatus.FAILED -> onRetry()
                else -> {}
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = IpxTypography.TitleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                ScreenStatusBadge(item.status)
            }

            // Progress bar
            if (item.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { progressAnim },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> AccentGreen
                        DownloadStatus.FAILED -> Color.Red
                        else -> AccentCyan
                    },
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            // Info & Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info text (size, speed, ETA)
                Text(
                    text = formatInfo(item),
                    style = IpxTypography.BodyMedium,
                    color = TextSecondary
                )

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            Button(onClick = onPause) {
                                Text("Pause")
                            }
                        }
                        DownloadStatus.PAUSED, DownloadStatus.PENDING -> {
                            Button(onClick = onResume) {
                                Text("Resume")
                            }
                        }
                        DownloadStatus.FAILED -> {
                            Button(onClick = onRetry) {
                                Text("Retry")
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            // Already completed
                        }
                    }

                    if (!item.isFinished) {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.DarkGray.copy(alpha = 0.3f),
                                focusedContainerColor = Color.Red,
                                focusedContentColor = Color.White
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScreenStatusBadge(status: DownloadStatus) {
    val (label, color) = when (status) {
        DownloadStatus.PENDING -> "Queued" to TextMuted
        DownloadStatus.DOWNLOADING -> "Downloading" to AccentCyan
        DownloadStatus.PAUSED -> "Paused" to AccentAmber
        DownloadStatus.COMPLETED -> "Finished" to AccentGreen
        DownloadStatus.FAILED -> "Failed" to Color.Red
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = IpxTypography.LabelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun formatInfo(item: DownloadItem): String {
    val parts = mutableListOf<String>()

    if (item.totalBytes > 0L) {
        parts += "${formatSize(item.downloadedBytes)} / ${formatSize(item.totalBytes)}"
        parts += "${item.progressPercent}%"
    } else if (item.downloadedBytes > 0L) {
        parts += formatSize(item.downloadedBytes)
    }

    if (item.isActive && item.speedBytesPerSec > 0L) {
        parts += formatSpeed(item.speedBytesPerSec)
    }

    val eta = item.etaSeconds
    if (item.isActive && eta > 0L) {
        parts += "${formatEta(eta)} left"
    }

    if (item.hasFailed && item.errorMessage != null) {
        return item.errorMessage
    }

    return parts.joinToString("  •  ").ifBlank { "—" }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "${"%.1f".format(bytes / 1_024.0)} KB"
    bytes < 1_073_741_824L -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    else -> "${"%.2f".format(bytes / 1_073_741_824.0)} GB"
}

private fun formatSpeed(bps: Long): String = when {
    bps < 1_024 -> "$bps B/s"
    bps < 1_048_576 -> "${"%.1f".format(bps / 1_024.0)} KB/s"
    else -> "${"%.1f".format(bps / 1_048_576.0)} MB/s"
}

private fun formatEta(seconds: Long): String {
    val h = seconds / 3_600
    val m = (seconds % 3_600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
