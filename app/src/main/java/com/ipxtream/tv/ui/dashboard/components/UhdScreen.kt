package com.ipxtream.tv.ui.dashboard.components

import androidx.annotation.StringRes
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.ProgressWatched

private enum class UhdKind { MOVIES, SERIES }

private enum class UhdSort(@StringRes val labelRes: Int) {
    RECENT(R.string.uhd_sort_recent),
    RATING(R.string.uhd_sort_rating),
    TITLE(R.string.uhd_sort_title),
    YEAR(R.string.uhd_sort_year)
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
    // The page's "return focus here" requester sits on the Movies chip until a poster has been
    // focused, then on the grid's last focused poster (PosterGrid keeps it on a composed one),
    // so the side menu, details and the player all come back to where the user was.
    val gridFocus = remember { FocusRequester() }
    var gridVisited by remember { mutableStateOf(false) }
    val gridOwnsReturn = gridVisited && count > 0 && !isLoading
    val gridRequester = if (gridOwnsReturn) firstItemFocusRequester else gridFocus
    // D-Pad Down from any control goes straight to the grid.
    val toGrid = Modifier.focusProperties { if (count > 0 && !isLoading) down = gridRequester }
    val total = if (kind == UhdKind.MOVIES) movies.size else series.size

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(Modifier.padding(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 8.dp)) {
            Text(stringResource(R.string.uhd_eyebrow), style = IpxTypography.Eyebrow, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.uhd_title), style = IpxTypography.DisplayLarge.copy(fontSize = 40.sp), color = Color.White)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = when {
                        isLoading -> stringResource(R.string.uhd_loading)
                        query.isNotBlank() -> stringResource(R.string.uhd_count_filtered, count, total, stringResource(if (kind == UhdKind.MOVIES) R.string.uhd_kind_movies else R.string.uhd_kind_series))
                        else -> stringResource(R.string.uhd_count_sorted, total, stringResource(if (kind == UhdKind.MOVIES) R.string.uhd_kind_movies else R.string.uhd_kind_series), stringResource(sort.labelRes))
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
                label    = stringResource(R.string.uhd_tab_movies, movies.size),
                selected = kind == UhdKind.MOVIES,
                onClick  = { kindName = UhdKind.MOVIES.name },
                modifier = Modifier
                    .then(if (!gridOwnsReturn) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                    .focusProperties { start = sideNavFocusRequester }
                    .then(toGrid)
            )
            Spacer(Modifier.width(10.dp))
            PillChip(
                label    = stringResource(R.string.uhd_tab_series, series.size),
                selected = kind == UhdKind.SERIES,
                onClick  = { kindName = UhdKind.SERIES.name },
                modifier = toGrid
            )

            Spacer(Modifier.width(20.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(GlassStroke))
            Spacer(Modifier.width(20.dp))

            UhdSort.entries.forEach { s ->
                PillChip(
                    label    = stringResource(s.labelRes),
                    selected = sort == s,
                    onClick  = { sortName = s.name },
                    quiet    = true,
                    modifier = toGrid
                )
                Spacer(Modifier.width(8.dp))
            }

            Spacer(Modifier.weight(1f))

            Box(Modifier.widthIn(min = 260.dp, max = 360.dp).then(toGrid)) {
                SearchBar(
                    query         = query,
                    onQueryChange = { query = it },
                    placeholder   = stringResource(R.string.uhd_search_hint)
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
                    kindLabel = stringResource(if (kind == UhdKind.MOVIES) R.string.uhd_kind_movies else R.string.uhd_kind_series),
                    scanning = scan.isRunning,
                    canScanLibrary = scan.unchecked > 0
                )
                kind == UhdKind.MOVIES -> PosterGrid(
                    items                   = visibleMovies,
                    id                      = { "m${it.streamId}" },
                    // Stable keys: the list updates while titles are being measured, and
                    // index-based keys would recreate (and unfocus) the poster you're on.
                    key                     = { _, m -> "uhd_m_${m.streamId}" },
                    columns                 = UHD_COLUMNS,
                    firstItemFocusRequester = gridRequester,
                    sideNavFocusRequester   = sideNavFocusRequester,
                    onItemFocused           = { gridVisited = true },
                    contentPadding          = UHD_GRID_PADDING,
                    spacingH                = 20.dp,
                    spacingV                = 28.dp,
                    modifier                = Modifier.fillMaxSize()
                ) { m, mod, open ->
                    VodPosterCard(
                        modifier = mod,
                        stream = m,
                        onClick = { open(); onStreamSelected(m) },
                        watchProgress = watchProgress(m.streamId.toString()),
                        badge = uhdBadge(resolutions["m${m.streamId}"])
                    )
                }
                else -> PosterGrid(
                    items                   = visibleSeries,
                    id                      = { "s${it.seriesId}" },
                    key                     = { _, s -> "uhd_s_${s.seriesId}" },
                    columns                 = UHD_COLUMNS,
                    firstItemFocusRequester = gridRequester,
                    sideNavFocusRequester   = sideNavFocusRequester,
                    onItemFocused           = { gridVisited = true },
                    contentPadding          = UHD_GRID_PADDING,
                    spacingH                = 20.dp,
                    spacingV                = 28.dp,
                    modifier                = Modifier.fillMaxSize()
                ) { s, mod, open ->
                    SeriesPosterCard(
                        modifier = mod,
                        series = s,
                        onClick = { open(); onSeriesSelected(s) },
                        watchProgress = watchProgress(s.seriesId.toString()),
                        badge = uhdBadge(resolutions["s${s.seriesId}"])
                    )
                }
            }
        }
    }
}

private val UHD_COLUMNS = GridCells.Adaptive(minSize = 180.dp)
private val UHD_GRID_PADDING = PaddingValues(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 20.dp, bottom = 48.dp)

@Composable
private fun EmptyUhdMessage(filtered: Boolean, kindLabel: String, scanning: Boolean, canScanLibrary: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                filtered -> stringResource(R.string.uhd_no_matches)
                scanning -> stringResource(R.string.uhd_measuring)
                else     -> stringResource(R.string.uhd_none_verified, kindLabel)
            },
            style = IpxTypography.HeadlineMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                filtered       -> stringResource(R.string.uhd_try_different)
                scanning       -> stringResource(R.string.uhd_appear_hint)
                canScanLibrary -> stringResource(R.string.uhd_none_labelled)
                else           -> stringResource(R.string.uhd_library_checked_none, kindLabel)
            },
            style = IpxTypography.BodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp)
        )
    }
}

/** Capsule toggle: frosted when selected, Halo platter when focused. */
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
            focusedContainerColor = ChipFocused,
            focusedContentColor   = TextOnFocus
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


private fun uhdBadge(res: VideoRes?): String? =
    if (res?.is4K == true) (if (res.hdr) "4K HDR" else "4K") else null

/** One-line status of the measurement pass, with a control for the full-library check. */
@Composable
private fun ScanStatus(scan: UhdScanState, onScanLibrary: () -> Unit, onStopScan: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Control first, so D-Pad Up from the chips below lands on it.
        when {
            scan.phase == UhdScanPhase.LIBRARY -> PillChip(label = stringResource(R.string.uhd_pause_check), selected = false, onClick = onStopScan)
            !scan.isRunning && scan.unchecked > 0 -> PillChip(label = stringResource(R.string.uhd_check_library), selected = false, onClick = onScanLibrary)
        }
        if (scan.phase == UhdScanPhase.LIBRARY || (!scan.isRunning && scan.unchecked > 0)) Spacer(Modifier.width(16.dp))
        val text = when (scan.phase) {
            UhdScanPhase.LABELLED -> stringResource(R.string.uhd_scan_labelled, scan.checked, scan.total)
            UhdScanPhase.LIBRARY  -> stringResource(R.string.uhd_scan_library, scan.checked, scan.total, scan.found)
            UhdScanPhase.IDLE     -> if (scan.unchecked > 0)
                stringResource(R.string.uhd_scan_idle, scan.unchecked)
            else stringResource(R.string.uhd_scan_done)
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
                        .background(ProgressWatched)
                )
            }
        }
    }
}
