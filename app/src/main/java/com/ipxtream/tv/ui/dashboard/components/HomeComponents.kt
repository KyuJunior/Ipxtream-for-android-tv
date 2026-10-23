package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
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
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.ProgressWatched

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
    onSearchDown: (() -> Unit)? = null,
    onSearchLeft: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IpxSpacing.Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Search Bar (Left-Center)
        Box(modifier = Modifier.width(360.dp)) {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.search_home_hint),
                modifier = searchModifier,
                onDownNavigation = onSearchDown,
                onLeftNavigation = onSearchLeft
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
                contentDescription = stringResource(R.string.action_cache_all),
                tint = if (isCachingAll) Accent else TextPrimary
            )
            // Refresh
            HeaderIconButton(
                onClick = onRefresh,
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.action_refresh_server)
            )
            // Settings
            HeaderIconButton(
                onClick = onSettings,
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.section_settings)
            )
            // Switch Account
            HeaderIconButton(
                onClick = onSwitchAccount,
                icon = Icons.Rounded.People,
                contentDescription = stringResource(R.string.action_switch_account)
            )
            // Logout
            HeaderIconButton(
                onClick = onLogout,
                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                contentDescription = stringResource(R.string.action_logout)
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
    // Frosted circle at rest, Halo platter with a dark glyph on focus.
    Surface(
        onClick = onClick,
        modifier = modifier.size(42.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = tint,
            focusedContainerColor = ChipFocused,
            focusedContentColor = TextOnFocus
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = CircleShape),
            focusedBorder = Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
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
    val username = activeAccount?.username ?: stringResource(R.string.account_guest)
    val serverUrl = activeAccount?.server?.removePrefix("http://")?.removePrefix("https://")?.take(22) ?: "offline"
    val initials = username.take(2).uppercase()
    val currentVersion = "v" + com.ipxtream.tv.BuildConfig.VERSION_NAME
    
    Surface(
        onClick = onCheckForUpdates,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.22f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = RoundedCornerShape(14.dp)),
            focusedBorder = Border(BorderStroke(1.dp, GlassStroke), shape = RoundedCornerShape(14.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monogram avatar in the neutral grey gradient tvOS uses for profiles
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(AccentOrchid, AccentOrchidDeep))),
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
                    text = bidi(username),
                    style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = bidi(serverUrl),
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
                if (!com.ipxtream.tv.BuildConfig.SELF_UPDATE) {
                    // Play build: Google Play handles updates, so there is no status to show here.
                } else if (isCheckingForUpdate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            color = Accent,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.update_checking),
                            style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                            color = Accent
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
                            text = stringResource(R.string.update_available),
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
                                .background(AccentGreen)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.update_up_to_date),
                            style = IpxTypography.BodySmall.copy(fontSize = 10.sp),
                            color = AccentGreen
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.update_check_short),
                            style = IpxTypography.BodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            ),
                            color = Accent
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
    val shape = RoundedCornerShape(16.dp)
    val glow by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(IpxMotion.Standard, easing = IpxMotion.EaseApple),
        label = "tileGlow"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .premiumFocus(isFocused, focusedScale = 1.05f, shape = shape)
            .onFocusChanged { isFocused = it.isFocused }
            .height(150.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceElevated,
            focusedContainerColor = SurfaceElevated
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, GlassStroke), shape = shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            themeColor.copy(alpha = 0.28f + 0.22f * glow),
                            themeColor.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f + 0.45f * glow),
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopEnd)
            )

            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = title,
                    style = IpxTypography.HeadlineMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = IpxTypography.BodyMedium,
                    color = TextSecondary.copy(alpha = 0.85f + 0.15f * glow)
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
            .premiumFocus(isFocused, focusedScale = 1.1f, shape = RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .size(180.dp, 270.dp),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = SlateCard,
            focusedContainerColor = SlateCard
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border.None,
            border = Border(
                border = BorderStroke(1.dp, Hairline),
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
                    text = bidi(item.name),
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
                        color = ProgressWatched,
                        trackColor = ProgressRail
                    )
                }
            }
        }
    }
}

// =============================================================================
//  Shelves
// =============================================================================

@Composable
private fun ShelfTitle(title: String) {
    Text(
        text = title,
        style = IpxTypography.RowTitle,
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = IpxSpacing.Gutter)
    )
}

/**
 * Wraps a poster so its title fades in underneath while focused — posters stay
 * clean artwork at rest, the way the TV app presents them.
 */
@Composable
private fun CaptionedPoster(
    title: String,
    content: @Composable () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = tween(IpxMotion.Standard, easing = IpxMotion.EaseApple),
        label = "captionReveal"
    )
    Column(
        modifier = Modifier
            .zIndex(if (hasFocus) 1f else 0f)
            .width(180.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
    ) {
        content()
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = IpxTypography.BodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * -6.dp.toPx()
            }
        )
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
    Column(modifier = modifier.padding(top = IpxSpacing.RowGap)) {
        ShelfTitle(stringResource(R.string.continue_watching))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = IpxSpacing.Gutter, vertical = 18.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "cw_${item.type}_${item.id}_$index" }, contentType = { _, _ -> "continue_watching" }) { index, item ->
                FocusMemoryItem("cw_${item.type}_${item.id}_$index") {
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
}

@Composable
fun HomeHighlightsRow(
    title: String,
    items: List<com.ipxtream.tv.data.model.StreamItem>,
    onStreamSelected: (com.ipxtream.tv.data.model.StreamItem) -> Unit,
    showChannelNumber: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = IpxSpacing.RowGap)) {
        ShelfTitle(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = IpxSpacing.Gutter, vertical = 16.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "hl_${item.streamId}_$index" }, contentType = { _, _ -> "live_channel" }) { index, item ->
                FocusMemoryItem("hl_${item.streamId}_$index") {
                    LiveChannelCard(stream = item, onClick = { onStreamSelected(item) }, showChannelNumber = showChannelNumber)
                }
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
    Column(modifier = modifier.padding(top = IpxSpacing.RowGap)) {
        ShelfTitle(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 18.dp, bottom = 4.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "hm_${item.streamId}_$index" }, contentType = { _, _ -> "vod_movie" }) { index, item ->
                FocusMemoryItem("hm_${item.streamId}_$index") {
                    CaptionedPoster(title = item.name) {
                        VodPosterCard(
                            stream = item,
                            onClick = { onStreamSelected(item) },
                            watchProgress = watchProgress(item.streamId.toString())
                        )
                    }
                }
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
    Column(modifier = modifier.padding(top = IpxSpacing.RowGap)) {
        ShelfTitle(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, top = 18.dp, bottom = 4.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "hs_${item.seriesId}_$index" }, contentType = { _, _ -> "tv_series" }) { index, item ->
                FocusMemoryItem("hs_${item.seriesId}_$index") {
                    CaptionedPoster(title = item.name) {
                        SeriesPosterCard(
                            series = item,
                            onClick = { onSeriesSelected(item) },
                            watchProgress = watchProgress(item.seriesId.toString())
                        )
                    }
                }
            }
        }
    }
}
