package com.ipxtream.tv.ui.dashboard.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon

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

    LaunchedEffect(Unit) {
        runCatching { likeButtonFocusRequester.requestFocus() }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusProperties { exit = { FocusRequester.Cancel } }
    ) {
        // ─── Cinematic Bleed Background ───────────────────────────────────────
        AsyncImage(
            model = seriesItem.backdropPath?.firstOrNull() ?: seriesItem.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 24.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
        )

        // ─── Heavy Gradients for Readability ────────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF000000), Color(0xDD000000), Color(0x22000000))
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xDD000000), Color(0xFF000000)),
                    startY = 200f
                )
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 80.dp, end = 80.dp),
            contentPadding = PaddingValues(top = 80.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Header (Left-aligned, taking up half the screen width to avoid covering background too much)
                Column(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = seriesItem.name, 
                        style = IpxTypography.DisplayLarge.copy(fontSize = 56.sp), 
                        color = TextPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        seriesItem.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                            Text("⭐ $rating/10", style = IpxTypography.TitleLarge, color = AccentAmber, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onToggleFavorite,
                            modifier = Modifier.focusRequester(likeButtonFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.DarkGray.copy(alpha = 0.6f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.LightGray,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                                    tint = if (isFavorite) Color.Red else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isFavorite) "Liked" else "Like",
                                    style = IpxTypography.BodyMedium,
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
                        Text("Loading seasons...", style = IpxTypography.TitleLarge, color = TextPrimary)
                    }
                }
            } else if (seasons.isNotEmpty()) {
                // Seasons Row
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(seasons) { season ->
                            val isSelected = selectedSeason == season
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    selectedSeason = season 
                                    runCatching { episodesFocusRequester.requestFocus() }
                                }
                            ) {
                                Text("Season ${season.seasonNumber}")
                            }
                        }
                    }
                }

                // Episodes Grid in LazyColumn via chunking
                val currentEpisodes = selectedSeason?.episodes ?: emptyList()
                val chunkedEpisodes = currentEpisodes.chunked(3)
                
                itemsIndexed(chunkedEpisodes) { rowIndex, rowEpisodes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowEpisodes.forEachIndexed { colIndex, episode ->
                            val isFirst = rowIndex == 0 && colIndex == 0
                            EpisodeCard(
                                episode = episode,
                                onPlay = { onEpisodePlay(episode) },
                                onDownload = { onEpisodeDownload(episode) },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (isFirst) Modifier.focusRequester(episodesFocusRequester) else Modifier)
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
                        Text("No episodes found.", style = IpxTypography.TitleMedium, color = TextMuted)
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
        onLongClick = onDownload,
        modifier = modifier.fillMaxWidth().height(130.dp),
        colors = CardDefaults.colors(containerColor = com.ipxtream.tv.ui.theme.SlateCard),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${episode.episodeNum}. ${episode.title.takeIf { it.isNotBlank() } ?: "Episode ${episode.episodeNum}"}",
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
                Text("▶ OK to Play", style = IpxTypography.LabelSmall, color = AccentCyan)
                Text("Long-press to download", style = IpxTypography.LabelSmall, color = TextMuted)
            }
        }
    }
}
