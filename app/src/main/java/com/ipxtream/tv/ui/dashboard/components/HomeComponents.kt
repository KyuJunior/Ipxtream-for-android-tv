package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.ipxtream.tv.data.local.LibraryItem
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.model.EpisodeItem
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    activeAccount: AuthCredentials?,
    isCheckingForUpdate: Boolean,
    isCachingAll: Boolean,
    onCacheAll: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onSwitchAccount: () -> Unit,
    onLogout: () -> Unit,
    onCheckForUpdates: () -> Unit,
    updateRelease: com.ipxtream.tv.data.model.GitHubRelease? = null,
    modifier: Modifier = Modifier,
    searchModifier: Modifier = Modifier,
    actionButtonsModifier: Modifier = Modifier,
    userProfileModifier: Modifier = Modifier,
    onSearchDown: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Search Bar (Left-Center)
        Box(modifier = Modifier.width(360.dp)) {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search channels, movies, and series...",
                modifier = searchModifier,
                onDownNavigation = onSearchDown
            )
        }
        
        Spacer(Modifier.width(24.dp))
        
        // 2. Action Buttons (Middle)
        Row(
            modifier = actionButtonsModifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cache All (Cloud)
            HeaderIconButton(
                onClick = onCacheAll,
                icon = Icons.Rounded.Cloud,
                contentDescription = "Cache All",
                tint = if (isCachingAll) AccentCyan else TextPrimary
            )
            // Refresh
            HeaderIconButton(
                onClick = onRefresh,
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh Server"
            )
            // Settings
            HeaderIconButton(
                onClick = onSettings,
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings"
            )
            // Switch Account
            HeaderIconButton(
                onClick = onSwitchAccount,
                icon = Icons.Rounded.People,
                contentDescription = "Switch Account"
            )
            // Logout
            HeaderIconButton(
                onClick = onLogout,
                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                contentDescription = "Logout",
                tint = Color(0xFFE50914) // Red logout indicator
            )
        }
        
        Spacer(Modifier.weight(1f))
        
        // 3. User Profile & Update Card (Right)
        UserProfileCard(
            activeAccount = activeAccount,
            isCheckingForUpdate = isCheckingForUpdate,
            updateRelease = updateRelease,
            onCheckForUpdates = onCheckForUpdates,
            modifier = userProfileModifier
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f, label = "buttonScale")
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = CircleShape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) AccentCyan else tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UserProfileCard(
    activeAccount: AuthCredentials?,
    isCheckingForUpdate: Boolean,
    updateRelease: com.ipxtream.tv.data.model.GitHubRelease? = null,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val username = activeAccount?.username ?: "Guest"
    val serverUrl = activeAccount?.server?.removePrefix("http://")?.removePrefix("https://")?.take(22) ?: "offline"
    val initials = username.take(2).uppercase()
    val currentVersion = "v" + com.ipxtream.tv.BuildConfig.VERSION_NAME
    
    Surface(
        onClick = onCheckForUpdates,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shape = RoundedCornerShape(12.dp)),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(12.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initials Avatar: Clean, slate/cyan gradient (Zero Purple)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF005F73), Color(0xFF00A8E1)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            
            Spacer(Modifier.width(10.dp))
            
            // User & Server Details
            Column {
                Text(
                    text = username,
                    style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = serverUrl,
                    style = IpxTypography.BodySmall.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Version & Interactive Update Status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentVersion,
                    style = IpxTypography.BodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                if (isCheckingForUpdate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            color = AccentCyan,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Checking...",
                            style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                            color = AccentCyan
                        )
                    }
                } else if (updateRelease != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentAmber)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Update Available",
                            style = IpxTypography.BodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = AccentAmber
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2ECC71))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Up to date",
                            style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                            color = Color(0xFF2ECC71)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Check",
                            style = IpxTypography.BodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            ),
                            color = AccentCyan
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    themeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .zIndex(if (isFocused) 2f else 1f)
            .onFocusChanged { isFocused = it.isFocused }
            .height(180.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color.White.copy(alpha = 0.20f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(16.dp)
            ),
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Ambient accent glow top right
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                themeColor.copy(alpha = if (isFocused) 0.45f else 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Icon top right
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.White else themeColor.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(68.dp)
                    .align(Alignment.TopEnd)
            )
            
            // Text content bottom left
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(themeColor)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = title,
                    style = IpxTypography.HeadlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = IpxTypography.BodyMedium,
                    color = if (isFocused) Color.White.copy(alpha = 0.95f) else TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingCard(
    item: LibraryItem,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (SeriesItem, EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = {
            when (item.type) {
                "live" -> {
                    val stream = StreamItem(
                        streamId = item.id.toIntOrNull() ?: 0,
                        name = item.name,
                        streamType = "live",
                        streamIcon = item.iconUrl,
                        categoryId = item.categoryId,
                        added = null, num = null, rating = null, rating5based = null, containerExtension = null, epgChannelId = null, tvArchive = null, tvArchiveDuration = null, directSource = null, customSid = null
                    )
                    onStreamSelected(stream)
                }
                "movie" -> {
                    val stream = StreamItem(
                        streamId = item.id.toIntOrNull() ?: 0,
                        name = item.name,
                        streamType = "movie",
                        streamIcon = item.iconUrl,
                        categoryId = item.categoryId,
                        added = null, num = null, rating = item.rating, rating5based = null, containerExtension = item.containerExtension, epgChannelId = null, tvArchive = null, tvArchiveDuration = null, directSource = null, customSid = null
                    )
                    onStreamSelected(stream)
                }
                "series" -> {
                    val series = SeriesItem(
                        seriesId = item.id.toIntOrNull() ?: 0,
                        name = item.name,
                        cover = item.iconUrl,
                        plot = null, cast = null, director = null, genre = null, releaseDate = null, lastModified = null, rating = item.rating, rating5based = null, backdropPath = null, youtubeTrailer = null, episodeRunTime = null, categoryId = item.categoryId
                    )
                    onSeriesSelected(series)
                }
                "episode" -> {
                    val series = SeriesItem(
                        seriesId = item.parentId?.toIntOrNull() ?: 0,
                        name = item.name.substringBefore(" - "),
                        cover = item.iconUrl,
                        plot = null, cast = null, director = null, genre = null, releaseDate = null, lastModified = null, rating = null, rating5based = null, backdropPath = null, youtubeTrailer = null, episodeRunTime = null, categoryId = null
                    )
                    val episode = EpisodeItem(
                        id = item.id,
                        episodeNum = 1,
                        title = item.name.substringAfter(" - ", item.name),
                        containerExtension = item.containerExtension ?: "mp4",
                        info = null,
                        season = 1,
                        added = null, customSid = null, directSource = null
                    )
                    onEpisodePlay(series, episode)
                }
            }
        },
        modifier = modifier
            .size(180.dp, 270.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = CardDefaults.scale(focusedScale = 1.12f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(12.dp)
            ),
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            // Poster Background Image
            if (item.iconUrl != null) {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SlatePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            // Bottom Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xDD000000))
                        )
                    )
            )
            
            // Text Content Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = item.name,
                    style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(8.dp))
                
                // watched progress indicator
                if (item.durationMs > 0L) {
                    val fraction = (item.lastWatchedPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentCyan,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(
    items: List<LibraryItem>,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    onEpisodePlay: (SeriesItem, EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Text(
            text = "Continue Watching",
            style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "cw_${item.type}_${item.id}_$index" }, contentType = { _, _ -> "continue_watching" }) { _, item ->
                ContinueWatchingCard(
                    item = item,
                    onStreamSelected = onStreamSelected,
                    onSeriesSelected = onSeriesSelected,
                    onEpisodePlay = onEpisodePlay
                )
            }
        }
    }
}

@Composable
fun HomeHighlightsRow(
    title: String,
    items: List<com.ipxtream.tv.data.model.StreamItem>,
    onStreamSelected: (com.ipxtream.tv.data.model.StreamItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "hl_${item.streamId}_$index" }, contentType = { _, _ -> "live_channel" }) { _, item ->
                LiveChannelCard(stream = item, onClick = { onStreamSelected(item) })
            }
        }
    }
}

@Composable
fun HomeMoviesRow(
    title: String,
    items: List<com.ipxtream.tv.data.model.StreamItem>,
    onStreamSelected: (com.ipxtream.tv.data.model.StreamItem) -> Unit,
    modifier: Modifier = Modifier,
    watchProgress: (String) -> Float? = { null }
) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "hm_${item.streamId}_$index" }, contentType = { _, _ -> "vod_movie" }) { _, item ->
                VodPosterCard(
                    stream = item,
                    onClick = { onStreamSelected(item) },
                    watchProgress = watchProgress(item.streamId.toString())
                )
            }
        }
    }
}

@Composable
fun HomeSeriesRow(
    title: String,
    items: List<com.ipxtream.tv.data.model.SeriesItem>,
    onSeriesSelected: (com.ipxtream.tv.data.model.SeriesItem) -> Unit,
    modifier: Modifier = Modifier,
    watchProgress: (String) -> Float? = { null }
) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "hs_${item.seriesId}_$index" }, contentType = { _, _ -> "tv_series" }) { _, item ->
                SeriesPosterCard(
                    series = item,
                    onClick = { onSeriesSelected(item) },
                    watchProgress = watchProgress(item.seriesId.toString())
                )
            }
        }
    }
}
