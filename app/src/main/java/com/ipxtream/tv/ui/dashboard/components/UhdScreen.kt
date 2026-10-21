package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.local.VideoRes
import com.ipxtream.tv.ui.dashboard.UhdScanPhase
import com.ipxtream.tv.ui.dashboard.UhdScanState
import androidx.compose.ui.draw.clip
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

private enum class UhdKind { MOVIES, SERIES }

private enum class UhdSort(val label: String) {
    RECENT("Recently added"),
    RATING("Top rated"),
    TITLE("A–Z"),
    YEAR("Release year")
}

private val UHD_PREFIX = Regex("""(?i)^\s*(4k|uhd)\s*[-:|]\s*""")
private val YEAR_IN_NAME = Regex("""[(\[](\d{4})[)\]]""")

/** Title without a leading "4K -" style tag, for sorting and search. */
private fun displayName(raw: String) = raw.replace(UHD_PREFIX, "").trim()

private fun yearOf(name: String, releaseDate: String? = null): Int =
    YEAR_IN_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()
        ?: releaseDate?.take(4)?.toIntOrNull()
        ?: 0

/**
 * The 4K collection: every movie and series the portal tags as 4K / UHD / 2160p,
 * switchable between Movies and Series, sortable, and searchable within the set.
 */
@Composable
fun UhdScreen(
    movies:                  List<StreamItem>,
    series:                  List<SeriesItem>,
    isLoading:               Boolean,
    onStreamSelected:        (StreamItem) -> Unit,
    onSeriesSelected:        (SeriesItem) -> Unit,
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester:   FocusRequester,
    watchProgress:           (String) -> Float?,
    resolutions:             Map<String, VideoRes>,
    scan:                    UhdScanState,
    onScanLibrary:           () -> Unit,
    onStopScan:              () -> Unit,
    modifier:                Modifier = Modifier
) {
    var kindName by rememberSaveable { mutableStateOf(UhdKind.MOVIES.name) }
    var sortName by rememberSaveable { mutableStateOf(UhdSort.RECENT.name) }
    var query by rememberSaveable { mutableStateOf("") }
    val kind = UhdKind.valueOf(kindName)
    val sort = UhdSort.valueOf(sortName)

    // Open on whichever side actually has titles.
    LaunchedEffect(movies.isEmpty(), series.isEmpty()) {
        if (kind == UhdKind.MOVIES && movies.isEmpty() && series.isNotEmpty()) kindName = UhdKind.SERIES.name
    }

    val visibleMovies = remember(movies, sort, query) {
        val q = query.trim()
        movies
            .filter { q.isEmpty() || displayName(it.name).contains(q, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    // Many portals leave "added"/"rating" empty; stream IDs grow with each upload,
                    // so they make a reliable recency tie-breaker.
                    UhdSort.RECENT -> list.sortedWith(
                        compareByDescending<StreamItem> { it.added?.toLongOrNull() ?: 0L }.thenByDescending { it.streamId }
                    )
                    UhdSort.RATING -> list.sortedWith(
                        compareByDescending<StreamItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
                            .thenByDescending { yearOf(it.name) }
                            .thenBy { displayName(it.name).lowercase() }
                    )
                    UhdSort.TITLE  -> list.sortedBy { displayName(it.name).lowercase() }
                    UhdSort.YEAR   -> list.sortedWith(
                        compareByDescending<StreamItem> { yearOf(it.name) }.thenByDescending { it.streamId }
                    )
                }
            }
    }
    val visibleSeries = remember(series, sort, query) {
        val q = query.trim()
        series
            .filter { q.isEmpty() || displayName(it.name).contains(q, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    UhdSort.RECENT -> list.sortedWith(
                        compareByDescending<SeriesItem> { it.lastModified?.toLongOrNull() ?: 0L }.thenByDescending { it.seriesId }
                    )
                    UhdSort.RATING -> list.sortedWith(
                        compareByDescending<SeriesItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
                            .thenByDescending { yearOf(it.name, it.releaseDate) }
                            .thenBy { displayName(it.name).lowercase() }
                    )
                    UhdSort.TITLE  -> list.sortedBy { displayName(it.name).lowercase() }
                    UhdSort.YEAR   -> list.sortedWith(
                        compareByDescending<SeriesItem> { yearOf(it.name, it.releaseDate) }.thenByDescending { it.seriesId }
                    )
                }
            }
    }
    val count = if (kind == UhdKind.MOVIES) visibleMovies.size else visibleSeries.size
    val total = if (kind == UhdKind.MOVIES) movies.size else series.size

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(Modifier.padding(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 8.dp)) {
            Text("ULTRA HD", style = IpxTypography.Eyebrow, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("4K Collection", style = IpxTypography.DisplayLarge.copy(fontSize = 40.sp), color = Color.White)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = when {
                        isLoading -> "Loading library…"
                        query.isNotBlank() -> "$count of $total ${if (kind == UhdKind.MOVIES) "movies" else "series"}"
                        else -> "$total ${if (kind == UhdKind.MOVIES) "movies" else "series"} · ${sort.label}"
                    },
                    style = IpxTypography.BodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            ScanStatus(scan = scan, onScanLibrary = onScanLibrary, onStopScan = onStopScan)
        }

        Spacer(Modifier.height(16.dp))

        // ── Controls: kind · sort · search ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IpxSpacing.Gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillChip(
                label    = "Movies  ${movies.size}",
                selected = kind == UhdKind.MOVIES,
                onClick  = { kindName = UhdKind.MOVIES.name },
                modifier = Modifier
                    .focusRequester(firstItemFocusRequester)
                    .focusProperties { left = sideNavFocusRequester }
            )
            Spacer(Modifier.width(10.dp))
            PillChip(
                label    = "Series  ${series.size}",
                selected = kind == UhdKind.SERIES,
                onClick  = { kindName = UhdKind.SERIES.name }
            )

            Spacer(Modifier.width(20.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(GlassStroke))
            Spacer(Modifier.width(20.dp))

            UhdSort.entries.forEach { s ->
                PillChip(
                    label    = s.label,
                    selected = sort == s,
                    onClick  = { sortName = s.name },
                    quiet    = true
                )
                Spacer(Modifier.width(8.dp))
            }

            Spacer(Modifier.weight(1f))

            Box(Modifier.widthIn(min = 260.dp, max = 360.dp)) {
                SearchBar(
                    query         = query,
                    onQueryChange = { query = it },
                    placeholder   = "Search 4K titles"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Grid ─────────────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            when {
                isLoading -> {}
                count == 0 -> EmptyUhdMessage(
                    filtered = query.isNotBlank(),
                    kindLabel = if (kind == UhdKind.MOVIES) "movies" else "series",
                    scanning = scan.isRunning,
                    canScanLibrary = scan.unchecked > 0
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 20.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (kind == UhdKind.MOVIES) {
                        itemsIndexed(visibleMovies, key = { i, m -> "uhd_m_${m.streamId}_$i" }) { _, m ->
                            ResolutionBadged(resolutions["m${m.streamId}"]) {
                                VodPosterCard(
                                    stream = m,
                                    onClick = { onStreamSelected(m) },
                                    watchProgress = watchProgress(m.streamId.toString())
                                )
                            }
                        }
                    } else {
                        itemsIndexed(visibleSeries, key = { i, s -> "uhd_s_${s.seriesId}_$i" }) { _, s ->
                            ResolutionBadged(resolutions["s${s.seriesId}"]) {
                                SeriesPosterCard(
                                    series = s,
                                    onClick = { onSeriesSelected(s) },
                                    watchProgress = watchProgress(s.seriesId.toString())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyUhdMessage(filtered: Boolean, kindLabel: String, scanning: Boolean, canScanLibrary: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                filtered -> "No matches"
                scanning -> "Measuring video resolution…"
                else     -> "No verified 4K $kindLabel yet"
            },
            style = IpxTypography.HeadlineMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                filtered       -> "Try a different title."
                scanning       -> "Titles appear here as soon as their file is confirmed to be 2160p."
                canScanLibrary -> "None of the titles labelled 4K measured as real 4K. Check the entire library to find the ones that are."
                else           -> "Your whole library has been checked and no $kindLabel are encoded in 4K."
            },
            style = IpxTypography.BodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp)
        )
    }
}

/** Capsule toggle: frosted when selected, white platter when focused. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillChip(
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
    quiet:    Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick  = onClick,
        modifier = modifier.height(40.dp),
        shape    = ClickableSurfaceDefaults.shape(shape),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (selected) Color(0x33FFFFFF) else Color.Transparent,
            contentColor          = if (selected) Color.White else TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor   = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border        = if (selected || !quiet) Border(androidx.compose.foundation.BorderStroke(1.dp, GlassStroke), shape = shape) else Border.None,
            focusedBorder = Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(
                text  = label,
                style = IpxTypography.BodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            )
        }
    }
}


/** Overlays the measured quality ("4K", "4K HDR") on a poster. */
@Composable
private fun ResolutionBadged(res: VideoRes?, content: @Composable () -> Unit) {
    Box {
        content()
        if (res?.is4K == true) {
            GlassPill(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Text(
                    text = if (res.hdr) "4K HDR" else "4K",
                    style = IpxTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = Color.White
                )
            }
        }
    }
}

/** One-line status of the measurement pass, with a control for the full-library check. */
@Composable
private fun ScanStatus(scan: UhdScanState, onScanLibrary: () -> Unit, onStopScan: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Control first, so D-Pad Up from the chips below lands on it.
        when {
            scan.phase == UhdScanPhase.LIBRARY -> PillChip(label = "Pause check", selected = false, onClick = onStopScan)
            !scan.isRunning && scan.unchecked > 0 -> PillChip(label = "Check entire library", selected = false, onClick = onScanLibrary)
        }
        if (scan.phase == UhdScanPhase.LIBRARY || (!scan.isRunning && scan.unchecked > 0)) Spacer(Modifier.width(16.dp))
        val text = when (scan.phase) {
            UhdScanPhase.LABELLED -> "Verifying titles labelled 4K · ${"%,d".format(scan.checked)} of ${"%,d".format(scan.total)}"
            UhdScanPhase.LIBRARY  -> "Checking your library · ${"%,d".format(scan.checked)} of ${"%,d".format(scan.total)} · ${scan.found} in 4K"
            UhdScanPhase.IDLE     -> if (scan.unchecked > 0)
                "Measured from each file. ${"%,d".format(scan.unchecked)} titles not checked yet."
            else "Measured from each file · entire library checked"
        }
        Text(text, style = IpxTypography.BodyMedium.copy(fontFeatureSettings = "tnum"), color = TextSecondary)
        if (scan.isRunning && scan.total > 0) {
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((scan.checked.toFloat() / scan.total).coerceIn(0f, 1f))
                        .background(Color.White)
                )
            }
        }
    }
}
