package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.data.download.DownloadItem
import com.ipxtream.tv.data.download.DownloadStatus
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateNav
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * Persistent bottom tray showing all active, paused, completed, and failed
 * downloads.
 *
 * ## Collapsed vs Expanded
 * - **Collapsed** (default): a single 48dp bar showing active count + aggregate
 *   progress. The user presses DPAD_UP to expand it.
 * - **Expanded**: a [LazyColumn] of per-item rows, each with its own progress
 *   bar, speed, ETA, and Pause/Resume/Cancel buttons.
 *
 * ## D-Pad integration
 * The tray is placed at the bottom of [DashboardScreen]'s main `Column`.
 * DPAD_DOWN from the grid does NOT focus the tray by default — the user
 * must explicitly navigate to it. TV Material `Button` handles focus
 * within the tray.
 *
 * @param downloads      List of [DownloadItem] from [DashboardViewModel.downloadItems].
 * @param onPause        Called with the download ID to pause.
 * @param onResume       Called with the download ID to resume.
 * @param onCancel       Called with the download ID to cancel + delete.
 * @param onRetry        Called with the download ID to retry a failed download.
 * @param onClearDone    Called when the user presses "Clear completed".
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadTray(
    downloads:   List<DownloadItem>,
    onPause:     (String) -> Unit,
    onResume:    (String) -> Unit,
    onCancel:    (String) -> Unit,
    onRetry:     (String) -> Unit,
    onClearDone: () -> Unit,
    modifier:    Modifier = Modifier
) {
    if (downloads.isEmpty()) return

    var isExpanded by remember { mutableStateOf(true) }

    val activeCount    = downloads.count { it.isActive }
    val totalProgress  = downloads
        .filter { it.totalBytes > 0L }
        .map    { it.progressFraction }
        .average().toFloat().takeIf  { it.isFinite() } ?: 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(SlateNav)
    ) {

        // ── Header / summary row ──────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text      = "⬇ Downloads",
                style     = IpxTypography.TitleMedium,
                color     = AccentCyan,
                fontWeight = FontWeight.SemiBold
            )

            if (activeCount > 0) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text     = "$activeCount active",
                        fontSize = 10.sp,
                        color    = Color(0xFF0B1520),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Aggregate progress bar (collapsed and expanded)
                LinearProgressIndicator(
                    progress  = { totalProgress },
                    modifier  = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color      = AccentCyan,
                    trackColor = Color(0x33FFFFFF),
                    strokeCap  = StrokeCap.Round,
                    gapSize    = 0.dp,
                    drawStopIndicator = {}
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.width(12.dp))

            // Clear finished button
            if (downloads.any { it.isFinished || it.hasFailed }) {
                TrayIconButton(label = "Clear done", onClick = onClearDone)
                Spacer(Modifier.width(8.dp))
            }

            // Collapse / Expand toggle
            TrayIconButton(
                label   = if (isExpanded) "▼ Collapse" else "▲ Expand",
                onClick = { isExpanded = !isExpanded }
            )
        }

        // ── Expanded item list ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    DownloadRow(
                        item     = item,
                        onPause  = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onCancel = { onCancel(item.id) },
                        onRetry  = { onRetry(item.id) }
                    )
                }
            }
        }
    }
}

// =============================================================================
//  Per-item row
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadRow(
    item:     DownloadItem,
    onPause:  () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry:  () -> Unit
) {
    val progressAnim by animateFloatAsState(
        targetValue   = item.progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label         = "downloadProgress_${item.id}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateCard)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // ── Title + status badge ──────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = item.title,
                style    = IpxTypography.BodyMedium,
                color    = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(item.status)
        }

        Spacer(Modifier.height(6.dp))

        // ── Progress bar ──────────────────────────────────────────────────────
        if (item.totalBytes > 0L) {
            LinearProgressIndicator(
                progress  = { progressAnim },
                modifier  = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color      = when (item.status) {
                    DownloadStatus.COMPLETED  -> AccentGreen
                    DownloadStatus.FAILED     -> AccentAmber
                    else                      -> AccentCyan
                },
                trackColor = Color(0x22FFFFFF),
                strokeCap  = StrokeCap.Round,
                gapSize    = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Info row: size, speed, ETA, controls ──────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress text
            Text(
                text  = buildInfoText(item),
                style = IpxTypography.LabelSmall,
                color = TextSecondary
            )

            Spacer(Modifier.weight(1f))

            // Control buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING ->
                        TrayIconButton("⏸", onPause)

                    DownloadStatus.PAUSED, DownloadStatus.PENDING ->
                        TrayIconButton("▶", onResume)

                    DownloadStatus.FAILED ->
                        TrayIconButton("↺ Retry", onRetry)

                    DownloadStatus.COMPLETED -> { /* no action needed */ }
                }

                if (!item.isFinished) {
                    TrayIconButton("✕", onCancel)
                }
            }
        }
    }
}

// =============================================================================
//  Supporting composables
// =============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusBadge(status: DownloadStatus) {
    val (label, color) = when (status) {
        DownloadStatus.PENDING     -> "Queued"     to TextMuted
        DownloadStatus.DOWNLOADING -> "●  Active"  to AccentCyan
        DownloadStatus.PAUSED      -> "⏸ Paused"  to AccentAmber
        DownloadStatus.COMPLETED   -> "✓ Done"     to AccentGreen
        DownloadStatus.FAILED      -> "⚠ Failed"  to AccentAmber
    }
    Text(label, style = IpxTypography.LabelSmall, color = color, fontSize = 10.sp)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrayIconButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors  = ButtonDefaults.colors(
            containerColor        = Color(0x22FFFFFF),
            focusedContainerColor = AccentCyan
        ),
        scale   = ButtonDefaults.scale(focusedScale = 1.06f),
        modifier = Modifier.height(28.dp)
    ) {
        Text(label, style = IpxTypography.LabelSmall, color = TextPrimary, fontSize = 11.sp)
    }
}

// =============================================================================
//  Formatting helpers
// =============================================================================

private fun buildInfoText(item: DownloadItem): String {
    val parts = mutableListOf<String>()

    // Downloaded / total
    if (item.totalBytes > 0L) {
        parts += "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
        parts += "${item.progressPercent}%"
    } else if (item.downloadedBytes > 0L) {
        parts += formatBytes(item.downloadedBytes)
    }

    // Speed (only while downloading)
    if (item.isActive && item.speedBytesPerSec > 0L) {
        parts += formatSpeed(item.speedBytesPerSec)
    }

    // ETA
    val eta = item.etaSeconds
    if (item.isActive && eta > 0L) {
        parts += "${formatEta(eta)} left"
    }

    if (item.hasFailed && item.errorMessage != null) {
        return item.errorMessage
    }

    return parts.joinToString("  ·  ").ifBlank { "—" }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024                -> "$bytes B"
    bytes < 1_048_576            -> "${"%.1f".format(bytes / 1_024.0)} KB"
    bytes < 1_073_741_824L       -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    else                         -> "${"%.2f".format(bytes / 1_073_741_824.0)} GB"
}

private fun formatSpeed(bps: Long): String = when {
    bps < 1_024      -> "$bps B/s"
    bps < 1_048_576  -> "${"%.1f".format(bps / 1_024.0)} KB/s"
    else             -> "${"%.1f".format(bps / 1_048_576.0)} MB/s"
}

private fun formatEta(seconds: Long): String {
    val h = seconds / 3_600
    val m = (seconds % 3_600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else  -> "${s}s"
    }
}
