package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ipxtream.tv.R
import com.ipxtream.tv.data.match.Competition
import com.ipxtream.tv.data.match.FollowedCompetitions
import com.ipxtream.tv.data.match.Match
import com.ipxtream.tv.data.match.MatchState
import com.ipxtream.tv.data.match.MatchTeam
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.dashboard.MatchListing
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.Hairline
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.SurfaceElevated
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.ui.theme.premiumFocus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Days offered in Matches, relative to today. */
private val MATCH_DAYS = -1..5

/**
 * Matches: the followed competitions' fixtures for one day, grouped by competition (today's live
 * matches pinned on top). OK on a live match plays its best channel; long-press OK (or OK on a
 * match that isn't live) opens the channel picker, where a choice can be remembered.
 */
@Composable
fun MatchesScreen(
    matches:                 List<MatchListing>,
    dayOffset:               Int,
    isLoading:               Boolean,
    updatedAt:               Long?,
    offline:                 Boolean,
    use24Hour:               Boolean,
    allChannels:             List<StreamItem>,
    onDaySelected:           (Int) -> Unit,
    onPlay:                  (MatchListing, StreamItem) -> Unit,
    onPick:                  (MatchListing, StreamItem, Boolean) -> Unit,
    firstItemFocusRequester: FocusRequester,
    sideNavFocusRequester:   FocusRequester,
    modifier:                Modifier = Modifier
) {
    val timeFormat = remember(use24Hour) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    var pickerFor by remember { mutableStateOf<MatchListing?>(null) }
    val groups = remember(matches, dayOffset) { groupMatches(matches, pinLive = dayOffset == 0) }

    val listState = rememberLazyListState()
    val firstRow = remember { FocusRequester() }
    val focusMemory = LocalFocusMemory.current
    val scope = rememberCoroutineScope()
    val firstMatchId = groups.firstOrNull()?.listings?.firstOrNull()?.match?.id

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(Modifier.padding(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 8.dp)) {
            Text(stringResource(R.string.matches_eyebrow), style = IpxTypography.Eyebrow, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                // No negative tracking: Arabic glyphs then measure narrower than they draw and the last letter is cut.
                Text(
                    stringResource(R.string.matches_title),
                    style    = IpxTypography.DisplayLarge.copy(fontSize = 40.sp, letterSpacing = 0.sp),
                    color    = Color.White,
                    maxLines = 1
                )
                Spacer(Modifier.width(16.dp))
                val updated = updatedAt?.let { timeFormat.format(Date(it)) }
                Text(
                    text = when {
                        offline && updated != null -> stringResource(R.string.matches_offline, updated)
                        updated != null            -> stringResource(R.string.matches_updated, updated)
                        else                       -> stringResource(R.string.matches_source)
                    },
                    style    = IpxTypography.BodyLarge,
                    color    = if (offline) Accent else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(bottom = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Day chips ────────────────────────────────────────────────────────
        LazyRow(
            modifier              = Modifier.fillMaxWidth().focusProperties { canFocus = false },
            contentPadding        = PaddingValues(horizontal = IpxSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(MATCH_DAYS.toList(), key = { "day_$it" }) { offset ->
                PillChip(
                    label    = dayLabel(offset),
                    selected = offset == dayOffset,
                    onClick  = { onDaySelected(offset) },
                    modifier = Modifier
                        .then(if (offset == dayOffset) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .then(if (offset == MATCH_DAYS.first) Modifier.focusProperties { start = sideNavFocusRequester } else Modifier)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── List ─────────────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize()) {
            when {
                isLoading && matches.isEmpty() -> CenterNote(stringResource(R.string.matches_loading), null)
                matches.isEmpty() && offline   -> CenterNote(
                    stringResource(R.string.matches_unavailable), stringResource(R.string.matches_unavailable_hint)
                )
                matches.isEmpty()              -> CenterNote(
                    stringResource(R.string.matches_empty), stringResource(R.string.matches_empty_hint)
                )
                else -> LazyColumn(
                    state               = listState,
                    // canFocus = false alone doesn't stop this list (its first item is a header) from
                    // taking focus on D-Pad Down from the chips, with nothing lit. If it does get focus,
                    // hand it straight to the last focused match, else the first one.
                    modifier            = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                        .onFocusChanged { state ->
                            if (!state.isFocused) return@onFocusChanged
                            if (focusMemory?.restore() == true) return@onFocusChanged
                            if (runCatching { firstRow.requestFocus() }.isFailure) {
                                scope.launch {
                                    listState.scrollToItem(0)
                                    runCatching { firstRow.requestFocus() }
                                }
                            }
                        },
                    contentPadding      = PaddingValues(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 12.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEach { group ->
                        item(key = "h_${group.key}") { GroupHeader(group) }
                        items(group.listings, key = { "${group.key}_${it.match.id}" }) { listing ->
                            FocusMemoryItem(key = "match_${listing.match.id}") {
                                MatchRow(
                                    listing    = listing,
                                    timeFormat = timeFormat,
                                    onClick    = {
                                        val best = listing.channels.firstOrNull()
                                        if (listing.match.state == MatchState.LIVE && best != null) onPlay(listing, best)
                                        else pickerFor = listing
                                    },
                                    onLongClick = { pickerFor = listing },
                                    modifier    = Modifier
                                        .then(if (listing.match.id == firstMatchId) Modifier.focusRequester(firstRow) else Modifier)
                                        .focusProperties {
                                            start = sideNavFocusRequester
                                            // The first match goes Up to the selected day, not into the list.
                                            if (listing.match.id == firstMatchId) up = firstItemFocusRequester
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pickerFor?.let { listing ->
        ChannelPicker(
            listing     = listing,
            allChannels = allChannels,
            onDismiss   = { pickerFor = null },
            onPick      = { stream, forCompetition ->
                pickerFor = null
                onPick(listing, stream, forCompetition)
            }
        )
    }
}

// ─── Grouping ───────────────────────────────────────────────────────────────

private data class MatchGroup(val key: String, val competition: Competition?, val listings: List<MatchListing>)

/** Competition groups in the followed order; on today, live matches first in their own group. */
private fun groupMatches(matches: List<MatchListing>, pinLive: Boolean): List<MatchGroup> {
    val live = if (pinLive) matches.filter { it.match.state == MatchState.LIVE } else emptyList()
    val rest = matches - live.toSet()
    val order = FollowedCompetitions.DEFAULT
    val byCompetition = rest.groupBy { it.match.competition.slug }
        .toList()
        .sortedBy { (slug, _) -> order.indexOf(slug).let { if (it < 0) Int.MAX_VALUE else it } }
        .map { (slug, list) -> MatchGroup(slug, list.first().match.competition, list.sortedBy { it.match.kickoffMs }) }
    return (if (live.isNotEmpty()) listOf(MatchGroup("live", null, live)) else emptyList()) + byCompetition
}

@Composable
private fun competitionName(c: Competition): String = when (c.slug) {
    "uefa.champions" -> stringResource(R.string.competition_uefa_champions)
    "eng.1"          -> stringResource(R.string.competition_eng_1)
    "esp.1"          -> stringResource(R.string.competition_esp_1)
    else             -> bidi(c.name)
}

@Composable
private fun dayLabel(offset: Int): String = when (offset) {
    -1   -> stringResource(R.string.matches_day_yesterday)
    0    -> stringResource(R.string.matches_day_today)
    1    -> stringResource(R.string.matches_day_tomorrow)
    else -> remember(offset) {
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, offset) }
        SimpleDateFormat("EEE d", Locale.getDefault()).format(day.time)
    }
}

// ─── Pieces ─────────────────────────────────────────────────────────────────

@Composable
private fun GroupHeader(group: MatchGroup) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val competition = group.competition
        if (competition == null) {
            LivePill(clock = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.matches_live_now), style = IpxTypography.RowTitle, color = TextPrimary)
        } else {
            Crest(url = competition.crestUrl, fallback = competition.name, size = 26)
            Spacer(Modifier.width(10.dp))
            Text(competitionName(competition), style = IpxTypography.RowTitle, color = TextPrimary)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MatchRow(
    listing:     MatchListing,
    timeFormat:  SimpleDateFormat,
    onClick:     () -> Unit,
    onLongClick: () -> Unit,
    modifier:    Modifier = Modifier
) {
    val match = listing.match
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val finished = match.state == MatchState.FINISHED || match.state == MatchState.POSTPONED
    Surface(
        onClick     = onClick,
        onLongClick = onLongClick,
        modifier    = modifier
            .fillMaxWidth()
            .height(84.dp)
            .premiumFocus(focused, focusedScale = 1.02f, shape = shape, lean = false)
            .onFocusChanged { focused = it.isFocused },
        shape  = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor        = SlateCard,
            contentColor          = TextPrimary,
            focusedContainerColor = SurfaceElevated,
            focusedContentColor   = TextPrimary
        ),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border        = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, ChipFocused), shape = shape)
        )
    ) {
        // Beside the Live split player the row is about half as wide: drop the channel column.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 760.dp
            Row(
                modifier          = Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home team on the start side, crest at the outer edge.
                Crest(url = match.home.crestUrl, fallback = match.home.name, size = if (compact) 32 else 40)
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                TeamName(match.home, TextAlign.Start, dim = finished, compact = compact, modifier = Modifier.weight(1f))
                CentreBlock(match, timeFormat, modifier = Modifier.width(if (compact) 104.dp else 150.dp))
                TeamName(match.away, TextAlign.End, dim = finished, compact = compact, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                Crest(url = match.away.crestUrl, fallback = match.away.name, size = if (compact) 32 else 40)

                if (!compact) {
                    Spacer(Modifier.width(24.dp))
                    Box(Modifier.width(1.dp).height(40.dp).background(Hairline))
                    Spacer(Modifier.width(18.dp))
                    ChannelHint(listing, modifier = Modifier.width(240.dp))
                }
            }
        }
    }
}

@Composable
private fun TeamName(team: MatchTeam, align: TextAlign, dim: Boolean, compact: Boolean, modifier: Modifier) {
    Text(
        text      = bidi(if (compact) team.shortName else team.name),
        style     = IpxTypography.TitleLarge.copy(fontSize = if (compact) 17.sp else 20.sp),
        color     = if (dim) TextSecondary else TextPrimary,
        textAlign = align,
        maxLines  = 1,
        overflow  = TextOverflow.Ellipsis,
        modifier  = modifier
    )
}

/** Score (live / finished) or kick-off time, with the match state under it. */
@Composable
private fun CentreBlock(match: Match, timeFormat: SimpleDateFormat, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (match.state) {
            MatchState.LIVE, MatchState.FINISHED -> {
                // Separate texts, so each score sits beside its team in both layout directions.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreText(match.home.score)
                    Text("–", style = IpxTypography.TitleLarge, color = TextSecondary, modifier = Modifier.padding(horizontal = 10.dp))
                    ScoreText(match.away.score)
                }
                Spacer(Modifier.height(2.dp))
                if (match.state == MatchState.LIVE) LivePill(clock = match.clock)
                else Text(stringResource(R.string.matches_ft), style = IpxTypography.LabelSmall, color = TextMuted)
            }
            MatchState.UPCOMING -> {
                Text(
                    timeFormat.format(Date(match.kickoffMs)),
                    style = IpxTypography.TitleLarge.copy(fontSize = 24.sp, fontFeatureSettings = "tnum"),
                    color = TextPrimary
                )
                val minutes = ((match.kickoffMs - System.currentTimeMillis()) / 60_000L).toInt()
                if (minutes in 1..59) {
                    Text(stringResource(R.string.matches_starts_in, minutes), style = IpxTypography.LabelSmall, color = Accent)
                }
            }
            MatchState.POSTPONED -> {
                Text(stringResource(R.string.matches_postponed), style = IpxTypography.TitleMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ScoreText(score: String?) {
    Text(
        text  = score ?: "-",
        style = IpxTypography.HeadlineMedium.copy(fontFeatureSettings = "tnum"),
        color = TextPrimary
    )
}

@Composable
private fun LivePill(clock: String?) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Accent)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.matches_live), style = IpxTypography.LabelSmall.copy(fontWeight = FontWeight.Bold), color = TextOnAccent)
        if (!clock.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                if (clock == "HT") stringResource(R.string.matches_halftime) else clock,
                style = IpxTypography.LabelSmall.copy(fontFeatureSettings = "tnum"),
                color = TextOnAccent
            )
        }
    }
}

@Composable
private fun ChannelHint(listing: MatchListing, modifier: Modifier) {
    val best = listing.channels.firstOrNull()
    Column(modifier = modifier) {
        Text(
            text     = when {
                best == null        -> stringResource(R.string.matches_no_channel)
                listing.remembered  -> stringResource(R.string.matches_channel_saved)
                else                -> stringResource(R.string.matches_channel_suggested)
            },
            style    = IpxTypography.LabelSmall,
            color    = TextMuted,
            maxLines = 1
        )
        if (best != null) {
            Text(
                text     = bidi(best.name),
                style    = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.Medium),
                color    = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Team or competition crest; the name's first letter when there is no image. */
@Composable
private fun Crest(url: String?, fallback: String, size: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(fallback.take(1).uppercase(), style = IpxTypography.TitleMedium, color = TextSecondary)
            }
        } else {
            AsyncImage(
                model              = url,
                contentDescription = fallback,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CenterNote(title: String, hint: String?) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = IpxTypography.HeadlineMedium, color = TextPrimary)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                hint,
                style     = IpxTypography.BodyLarge,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 560.dp)
            )
        }
    }
}

// ─── Channel picker ─────────────────────────────────────────────────────────

/**
 * The match's suggested channels, then every live channel, with a search. Picking one plays it
 * and remembers it for this match (and, with the toggle on, for the whole competition).
 */
@Composable
private fun ChannelPicker(
    listing:     MatchListing,
    allChannels: List<StreamItem>,
    onDismiss:   () -> Unit,
    onPick:      (StreamItem, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var forCompetition by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val suggestedIds = remember(listing) { listing.channels.map { it.streamId }.toSet() }
    val shown = remember(query, allChannels, listing) {
        val q = query.trim()
        if (q.isEmpty()) listing.channels + allChannels.filter { it.streamId !in suggestedIds }
        else allChannels.filter { it.name.contains(q, ignoreCase = true) }.take(300)
    }
    val competition = competitionName(listing.match.competition)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(SlateDeep.copy(alpha = 0xE6 / 255f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SlateCard)
                    .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
                    .padding(28.dp)
            ) {
                Text(stringResource(R.string.picker_title), style = IpxTypography.Eyebrow, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    bidi("${listing.match.home.name}  –  ${listing.match.away.name}"),
                    style = IpxTypography.HeadlineMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillChip(
                        label    = stringResource(R.string.picker_remember_competition, competition),
                        selected = forCompetition,
                        onClick  = { forCompetition = !forCompetition }
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) {
                        SearchBar(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.picker_search_hint))
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (shown.isEmpty()) {
                    CenterNote(stringResource(R.string.picker_none), null)
                } else {
                    LazyColumn(
                        modifier            = Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = false }
                            .onFocusChanged { if (it.isFocused) runCatching { firstFocus.requestFocus() } },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding      = PaddingValues(vertical = 4.dp)
                    ) {
                        items(shown.size, key = { i -> "pick_${shown[i].streamId}_$i" }) { i ->
                            val stream = shown[i]
                            if (i == 0 && query.isBlank() && suggestedIds.isNotEmpty()) PickerHeader(stringResource(R.string.picker_suggested))
                            if (query.isBlank() && i == listing.channels.size) PickerHeader(stringResource(R.string.picker_all))
                            PickerRow(
                                stream   = stream,
                                onClick  = { onPick(stream, forCompetition) },
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun PickerHeader(text: String) {
    Text(text, style = IpxTypography.Eyebrow, color = TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PickerRow(stream: StreamItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape    = ClickableSurfaceDefaults.shape(shape),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            contentColor          = TextPrimary,
            focusedContainerColor = ChipFocused,
            focusedContentColor   = TextOnFocus
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model              = stream.streamIcon,
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(width = 56.dp, height = 32.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(bidi(stream.name), style = IpxTypography.BodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
