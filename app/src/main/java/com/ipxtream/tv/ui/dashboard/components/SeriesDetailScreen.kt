package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import com.ipxtream.tv.ui.theme.appleGlass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeasonItem
import com.ipxtream.tv.data.model.SeriesInfoResponse
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.AppleFill
import com.ipxtream.tv.ui.theme.AppleSegmentSelected
import com.ipxtream.tv.ui.theme.ipxShape
import com.ipxtream.tv.ui.theme.isAppleDesign

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailScreen(
    seriesItem: SeriesItem,
    seriesInfo: SeriesInfoResponse?,
    isLoadingSeriesInfo: Boolean,
    onEpisodePlay: (EpisodeItem) -> Unit,
    onEpisodeDownload: (EpisodeItem) -> Unit,
    onClose: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isPlayerOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedSeason by remember { mutableStateOf<SeasonItem?>(null) }
    val seasons = remember(seriesInfo) { seriesInfo?.toSeasons() ?: emptyList() }
    
    // Auto-select first season when loaded
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && selectedSeason == null) {
            selectedSeason = seasons.first()
        }
    }

    val episodesFocusRequester = remember { FocusRequester() }
    val likeButtonFocusRequester = remember { FocusRequester() }
    val seasonFocusRequester = remember { FocusRequester() }
    val playedEpisodeFocusRequester = remember { FocusRequester() }
    var playedEpisodeId by remember { mutableStateOf<String?>(null) }

    // The selected season's chip, or the first visible one when it is scrolled out of the row:
    // a requester on an item that left composition would swallow the key press.
    val seasonsRowState = rememberLazyListState()
    val seasonAnchor by remember(seasons) {
        derivedStateOf {
            val visible = seasonsRowState.layoutInfo.visibleItemsInfo.map { it.index }
            val selected = seasons.indexOf(selectedSeason)
            if (selected in visible) selected else visible.firstOrNull() ?: 0
        }
    }
    val seasonsShown = !isLoadingSeriesInfo && seasons.isNotEmpty()
    // Apple: episodes are one horizontal shelf; its return requester follows the first visible card.
    val apple = isAppleDesign
    val episodeRowState = rememberLazyListState()
    val episodeAnchor by remember {
        derivedStateOf { episodeRowState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0 }
    }

    LaunchedEffect(isPlayerOpen) {
        if (!isPlayerOpen) {
            delay(100)
            // Apple shelf: bring the played episode back into view so its requester is attached.
            if (apple) {
                val idx = selectedSeason?.episodes?.indexOfFirst { it.id == playedEpisodeId } ?: -1
                if (idx >= 0) runCatching { episodeRowState.scrollToItem(idx) }
            }
            runCatching {
                when {
                    seasons.isEmpty() -> likeButtonFocusRequester.requestFocus()
                    playedEpisodeId != null && runCatching { playedEpisodeFocusRequester.requestFocus() }.isSuccess -> Unit
                    else -> episodesFocusRequester.requestFocus()
                }
            }
        }
    }

    BackHandler(enabled = !isPlayerOpen, onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDeep)
            .focusProperties { canFocus = !isPlayerOpen }
            .onPreviewKeyEvent { keyEvent ->
                if (!isPlayerOpen && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    onClose()
                    true
                } else {
                    false
                }
            }
    ) {
        // ─── Cinematic Bleed Background ───────────────────────────────────────
        AsyncImage(
            model = seriesItem.backdropPath?.firstOrNull() ?: seriesItem.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (apple) 1f else 0.25f,
            modifier = Modifier.fillMaxSize()
        )

        // ─── Heavy Gradients for Readability ────────────────────────────────
        // Apple: bright art on the top half; the scrims darken the start side and the bottom for the shelf.
        if (apple) Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to SlateDeep.copy(alpha = 0xD9 / 255f), 0.5f to SlateDeep.copy(alpha = 0x55 / 255f), 0.85f to Color.Transparent
                )
            )
        ) else Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(SlateDeep, SlateDeep.copy(alpha = 0xDD / 255f), SlateDeep.copy(alpha = 0x22 / 255f))
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, SlateDeep.copy(alpha = 0xDD / 255f), SlateDeep),
                    startY = if (apple) 520f else 200f
                )
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 80.dp, end = 80.dp)
                .focusProperties { canFocus = false },
            contentPadding = PaddingValues(top = if (apple) 220.dp else 80.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Header (Left-aligned, taking up half the screen width to avoid covering background too much)
                Column(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = bidi(seriesItem.name), 
                        style = IpxTypography.DisplayLarge.copy(fontSize = 56.sp), 
                        color = TextPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        seriesItem.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("$rating/10", style = IpxTypography.TitleLarge, color = AccentAmber, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .focusRequester(likeButtonFocusRequester)
                                .focusProperties { if (seasonsShown) down = seasonFocusRequester },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isAppleDesign) AppleFill else Color(0x2EFFFFFF),
                                contentColor = if (isAppleDesign) Accent else Color.White,
                                focusedContainerColor = ChipFocused,
                                focusedContentColor = TextOnFocus
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                border = if (isAppleDesign) Border.None else Border(
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(50)
                                ),
                                focusedBorder = Border.None
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.favorites_remove else R.string.favorites_add),
                                    tint = if (isAppleDesign) androidx.tv.material3.LocalContentColor.current else if (isFavorite) Color.Red else Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(if (isFavorite) R.string.action_liked else R.string.action_like),
                                    style = IpxTypography.TitleMedium.copy(fontSize = 15.sp),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = onClose,
                            modifier = Modifier.focusProperties { if (seasonsShown) down = seasonFocusRequester },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isAppleDesign) AppleFill else Color(0x2EFFFFFF),
                                contentColor = if (isAppleDesign) Accent else Color.White,
                                focusedContainerColor = ChipFocused,
                                focusedContentColor = TextOnFocus
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                            scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                border = if (isAppleDesign) Border.None else Border(
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(50)
                                ),
                                focusedBorder = Border.None
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = if (isAppleDesign) androidx.tv.material3.LocalContentColor.current else Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_back),
                                    style = IpxTypography.TitleMedium.copy(fontSize = 15.sp),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    seriesItem.plot?.let { plot ->
                        Text(
                            text = plot,
                            style = IpxTypography.BodyMedium,
                            color = TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }

            // Body
            if (isLoadingSeriesInfo) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.series_loading_seasons), style = IpxTypography.TitleLarge, color = TextPrimary)
                    }
                }
            } else if (seasons.isNotEmpty()) {
                // Seasons Row
                item {
                    LazyRow(
                        state = seasonsRowState,
                        // Apple: the seasons sit in one glass track, like a segmented control.
                        modifier = Modifier
                            .then(if (apple) Modifier.appleGlass(RoundedCornerShape(50)).padding(4.dp) else Modifier)
                            .focusProperties { canFocus = false },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(seasons, key = { _, it -> it.seasonNumber }) { index, season ->
                            val isSelected = selectedSeason == season
                            
                            // Focus stays here: the new season's cards don't exist until the next frame.
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSeason = season
                                    playedEpisodeId = null
                                },
                                modifier = Modifier
                                    .then(if (index == seasonAnchor) Modifier.focusRequester(seasonFocusRequester) else Modifier)
                                    .focusProperties { down = episodesFocusRequester },
                                shape = if (isAppleDesign) FilterChipDefaults.shape(shape = RoundedCornerShape(50)) else FilterChipDefaults.shape(),
                                colors = if (isAppleDesign) FilterChipDefaults.colors(
                                    containerColor = AppleFill,
                                    contentColor = TextPrimary,
                                    selectedContainerColor = AppleSegmentSelected,
                                    selectedContentColor = TextPrimary,
                                    focusedContainerColor = ChipFocused,
                                    focusedContentColor = TextOnFocus,
                                    focusedSelectedContainerColor = ChipFocused,
                                    focusedSelectedContentColor = TextOnFocus
                                ) else FilterChipDefaults.colors(),
                                border = if (isAppleDesign) FilterChipDefaults.border(
                                    border = Border.None,
                                    selectedBorder = Border.None,
                                    focusedBorder = Border.None,
                                    focusedSelectedBorder = Border.None
                                ) else FilterChipDefaults.border()
                            ) {
                                Text(
                                    stringResource(R.string.season_n, season.seasonNumber),
                                    fontWeight = if (isAppleDesign) androidx.compose.ui.text.font.FontWeight.SemiBold else null
                                )
                            }
                        }
                    }
                }

                // Episodes Grid in LazyColumn via chunking
                val currentEpisodes = selectedSeason?.episodes ?: emptyList()
                val chunkedEpisodes = if (apple) emptyList() else currentEpisodes.chunked(3)
                

                if (apple) item(key = "episode_shelf_${selectedSeason?.seasonNumber}") {
                    LazyRow(
                        state = episodeRowState,
                        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(currentEpisodes, key = { _, e -> e.id }) { index, episode ->
                            EpisodeCard(
                                episode = episode,
                                onPlay = {
                                    playedEpisodeId = episode.id
                                    onEpisodePlay(episode)
                                },
                                onDownload = { onEpisodeDownload(episode) },
                                modifier = Modifier
                                    .width(380.dp)
                                    .height(200.dp)
                                    .then(if (index == episodeAnchor) Modifier.focusRequester(episodesFocusRequester) else Modifier)
                                    .then(if (episode.id == playedEpisodeId) Modifier.focusRequester(playedEpisodeFocusRequester) else Modifier)
                                    .focusProperties { up = seasonFocusRequester }
                            )
                        }
                    }
                }

                itemsIndexed(chunkedEpisodes, key = { index, row -> row.firstOrNull()?.id ?: index.toString() }) { rowIndex, rowEpisodes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowEpisodes.forEachIndexed { colIndex, episode ->
                            val isFirst = rowIndex == 0 && colIndex == 0
                            EpisodeCard(
                                episode = episode,
                                onPlay = {
                                    playedEpisodeId = episode.id
                                    onEpisodePlay(episode)
                                },
                                onDownload = { onEpisodeDownload(episode) },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (isFirst) Modifier.focusRequester(episodesFocusRequester) else Modifier)
                                    .then(if (episode.id == playedEpisodeId) Modifier.focusRequester(playedEpisodeFocusRequester) else Modifier)
                                    .focusProperties { if (rowIndex == 0) up = seasonFocusRequester }
                            )
                        }
                        // Fill row space if not full
                        repeat(3 - rowEpisodes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.series_no_episodes), style = IpxTypography.TitleMedium, color = TextMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onPlay,
        onLongClick = if (com.ipxtream.tv.BuildConfig.DOWNLOADS) onDownload else null,
        modifier = modifier.fillMaxWidth().height(130.dp),
        colors = CardDefaults.colors(
            containerColor = com.ipxtream.tv.ui.theme.SlateCard,
            focusedContainerColor = com.ipxtream.tv.ui.theme.SurfaceElevated2
        ),
        shape = CardDefaults.shape(shape = ipxShape(14.dp)),
        scale = CardDefaults.scale(focusedScale = 1.03f),
        border = CardDefaults.border(
            border = if (isAppleDesign) Border.None else Border(BorderStroke(1.dp, com.ipxtream.tv.ui.theme.Hairline), shape = ipxShape(14.dp)),
            focusedBorder = Border(BorderStroke(1.dp, Color(0x40FFFFFF)), shape = ipxShape(14.dp))
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${episode.episodeNum}. ${episode.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.episode_n, episode.episodeNum)}",
                    style = IpxTypography.TitleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                episode.info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Text(
                        text = plot,
                        style = IpxTypography.BodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("▶  " + stringResource(R.string.action_play), style = IpxTypography.LabelSmall, color = TextPrimary)
                if (com.ipxtream.tv.BuildConfig.DOWNLOADS) {
                    Text(stringResource(R.string.long_press_download), style = IpxTypography.LabelSmall, color = TextMuted)
                }
            }
        }
    }
}
