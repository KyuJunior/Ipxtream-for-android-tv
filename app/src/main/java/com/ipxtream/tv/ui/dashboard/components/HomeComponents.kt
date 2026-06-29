package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
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
                placeholder = "Search channels, movies, and series..."
            )
        }
        
        Spacer(Modifier.width(24.dp))
        
        // 2. Action Buttons (Middle)
        Row(
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
                icon = Icons.Rounded.ExitToApp,
                contentDescription = "Logout",
                tint = Color(0xFFE50914) // Red logout indicator
            )
        }
        
        Spacer(Modifier.weight(1f))
        
        // 3. User Profile Card (Right)
        UserProfileCard(
            activeAccount = activeAccount,
            isCheckingForUpdate = isCheckingForUpdate,
            onCheckForUpdates = onCheckForUpdates
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
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
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
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val username = activeAccount?.username ?: "Guest"
    val serverUrl = activeAccount?.server?.removePrefix("http://")?.removePrefix("https://")?.take(20) ?: "offline"
    val initials = username.take(2).uppercase()
    val currentVersion = "v" + com.ipxtream.tv.BuildConfig.VERSION_NAME
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Initials Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF9D3FE7), Color(0xFF007DFE)))),
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
        
        // Version & Updates
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = currentVersion,
                style = IpxTypography.BodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = TextSecondary
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onCheckForUpdates() }
            ) {
                if (isCheckingForUpdate) {
                    Text(
                        text = "Checking...",
                        style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                        color = AccentCyan
                    )
                } else {
                    Text(
                        text = "✓ Up to date",
                        style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                        color = Color(0xFF2ECC71)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Check updates",
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
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )
    
    val glowColor = if (isFocused) themeColor else Color.White.copy(alpha = 0.05f)
    val glowWidth = if (isFocused) 2.dp else 1.dp
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(glowWidth, glowColor, RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.03f),
            focusedContainerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Outlined Icon top right
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColor.copy(alpha = if (isFocused) 1f else 0.6f),
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.TopEnd)
            )
            
            // Text content bottom left
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = title,
                    style = IpxTypography.HeadlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = IpxTypography.BodyMedium,
                    color = TextSecondary
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
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "cwCardScale")
    
    Card(
        onClick = {
            when (item.type) {
                "live" -> {
                    val stream = StreamItem(
                        streamId = item.id.toInt(),
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
                        streamId = item.id.toInt(),
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
                        seriesId = item.id.toInt(),
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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) AccentCyan else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
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
            items(items) { item ->
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
