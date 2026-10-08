package com.ipxtream.tv.ui.dashboard.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.data.model.VodInfoResponse
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodDetailScreen(
    streamItem: StreamItem,
    vodInfo: VodInfoResponse? = null,
    isLoadingVodInfo: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isPlayerOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playButtonFocus = remember { FocusRequester() }

    LaunchedEffect(isPlayerOpen) {
        if (!isPlayerOpen) {
            delay(100)
            runCatching { playButtonFocus.requestFocus() }
        }
    }

    BackHandler(enabled = !isPlayerOpen, onBack = onClose)

    val info = vodInfo?.info
    val movieData = vodInfo?.movieData

    // Metadata extraction
    val title = info?.name?.takeIf { it.isNotBlank() } ?: streamItem.name
    val rating = info?.rating?.takeIf { it.isNotBlank() } ?: streamItem.rating?.takeIf { it.isNotBlank() }
    val year = info?.releaseYear
        ?: movieData?.year?.takeIf { it.isNotBlank() }
        ?: Regex("\\((\\d{4})\\)").find(title)?.groupValues?.getOrNull(1)
    val duration = info?.formattedDuration
    val format = movieData?.containerExtension?.takeIf { it.isNotBlank() }
        ?: streamItem.containerExtension?.takeIf { it.isNotBlank() }
        ?: "HD"
    val genre = info?.genre?.takeIf { it.isNotBlank() }
    val plot = info?.plotText
    val director = info?.director?.takeIf { it.isNotBlank() }
    val cast = info?.castText?.takeIf { it.isNotBlank() }
    val country = info?.country?.takeIf { it.isNotBlank() }

    val posterUrl = info?.posterUrl ?: streamItem.streamIcon
    val backdropUrl = info?.backdropUrl ?: posterUrl

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
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
        // ─── High-Res Cinematic Backdrop Fanart ───────────────────────────────
        AsyncImage(
            model              = backdropUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            alpha              = 0.35f,
            modifier           = Modifier.fillMaxSize()
        )

        // ─── Double Cinematic Gradients ──────────────────────────────────────
        // Horizontal: solid OLED black on the left, fading gently to reveal backdrop art on the right
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color(0xFF07090E),
                    0.48f to Color(0xEE07090E),
                    0.75f to Color(0x7707090E),
                    1.0f to Color(0x1807090E)
                )
            )
        )
        // Vertical: subtle dark vignette on bottom and top
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x66000000),
                    0.25f to Color.Transparent,
                    0.7f to Color(0x4407090E),
                    1.0f to Color(0xFF07090E)
                )
            )
        )

        // ─── Main 10-Foot Content Canvas ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, end = 72.dp, top = 48.dp, bottom = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ─── Left Section: Title, Badges, Actions, Synopsis, Cast ────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Metadata Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Rating Badge
                    if (!rating.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentAmber.copy(alpha = 0.18f))
                                .border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Star,
                                contentDescription = null,
                                tint               = AccentAmber,
                                modifier           = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text       = "$rating/10",
                                style      = IpxTypography.BodyMedium,
                                color      = AccentAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Release Year
                    if (!year.isNullOrBlank()) {
                        Text(
                            text       = year,
                            style      = IpxTypography.TitleMedium.copy(fontSize = 15.sp),
                            color      = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text  = "•",
                            style = IpxTypography.TitleMedium,
                            color = TextMuted
                        )
                    }

                    // Runtime / Duration
                    if (!duration.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text       = duration,
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Quality / Extension Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text          = format.uppercase(),
                            style         = IpxTypography.LabelSmall.copy(fontSize = 12.sp),
                            color         = Color.White,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }

                    // Genre
                    if (!genre.isNullOrBlank()) {
                        Text(
                            text  = "•",
                            style = IpxTypography.TitleMedium,
                            color = TextMuted
                        )
                        Text(
                            text       = genre,
                            style      = IpxTypography.BodyMedium.copy(fontSize = 14.sp),
                            color      = AccentCyan,
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Movie Title
                Text(
                    text       = title,
                    style      = IpxTypography.DisplayLarge.copy(fontSize = 44.sp, lineHeight = 52.sp),
                    color      = TextPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 3. Action Buttons Row (Sleek TV Pills)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VodActionButton(
                        onClick         = onPlay,
                        icon            = Icons.Filled.PlayArrow,
                        label           = "Play",
                        isPrimary       = true,
                        modifier        = Modifier.focusRequester(playButtonFocus)
                    )

                    VodActionButton(
                        onClick   = onDownload,
                        icon      = Icons.Filled.ArrowDownward,
                        label     = "Download",
                        isPrimary = false
                    )

                    VodActionButton(
                        onClick         = onToggleFavorite,
                        icon            = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label           = if (isFavorite) "Liked" else "Like",
                        isPrimary       = false,
                        isHeartFavorite = isFavorite
                    )

                    VodActionButton(
                        onClick   = onClose,
                        icon      = Icons.AutoMirrored.Filled.ArrowBack,
                        label     = "Back",
                        isPrimary = false
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // 4. Synopsis / Plot
                if (isLoadingVodInfo && plot == null) {
                    Text(
                        text      = "Loading movie details...",
                        style     = IpxTypography.BodyMedium.copy(fontSize = 15.sp),
                        color     = TextMuted,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Text(
                        text       = plot ?: "No synopsis available for this title.",
                        style      = IpxTypography.BodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
                        color      = Color(0xFFBAC7D5),
                        maxLines   = 4,
                        overflow   = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 5. Cast, Director & Country metadata
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!director.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text       = "Director",
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.width(72.dp)
                            )
                            Text(
                                text       = director,
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!cast.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text       = "Cast",
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.width(72.dp)
                            )
                            Text(
                                text       = cast,
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!country.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text       = "Country",
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.width(72.dp)
                            )
                            Text(
                                text       = country,
                                style      = IpxTypography.BodySmall.copy(fontSize = 13.sp),
                                color      = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(48.dp))

            // ─── Right Section: Floating Cinematic Poster Card ────────────────
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(420.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                        .background(Color(0xFF10141E))
                ) {
                    AsyncImage(
                        model              = posterUrl,
                        contentDescription = title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )

                    // Subtle format badge overlay on poster card
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text       = format.uppercase(),
                            style      = IpxTypography.LabelSmall.copy(fontSize = 11.sp),
                            color      = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VodActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    isHeartFavorite: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (isPrimary) {
            ButtonDefaults.colors(
                containerColor        = Color.White,
                contentColor          = Color(0xFF07090E),
                focusedContainerColor = AccentCyan,
                focusedContentColor   = Color(0xFF07090E)
            )
        } else {
            ButtonDefaults.colors(
                containerColor        = Color(0xFF161E2E).copy(alpha = 0.75f),
                contentColor          = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor   = Color(0xFF07090E)
            )
        },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.05f),
        border = ButtonDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, if (isPrimary) Color.Transparent else Color.White.copy(alpha = 0.18f)),
                shape  = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isPrimary) AccentCyan else Color.White),
                shape  = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                modifier           = Modifier.size(20.dp),
                tint               = if (isHeartFavorite) Color.Red else Color.Unspecified
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = label,
                style      = IpxTypography.TitleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
