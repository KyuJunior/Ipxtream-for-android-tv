package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.ui.theme.SurfaceDim
import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.Hairline
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.ProgressRail
import com.ipxtream.tv.ui.theme.ProgressWatched
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SurfaceElevated
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.premiumFocus

// ─── Shared card dimensions ────────────────────────────────────────────────────

private val CardWidthVod    = 180.dp
private val CardHeightVod   = 270.dp  // 2:3 poster ratio

private val CardWidthLive   = 240.dp
private val CardHeightLive  = 135.dp  // 16:9 channel card

private val CardShape = RoundedCornerShape(12.dp)
private const val FocusScalePoster = 1.1f
private const val FocusScaleLive   = 1.08f

// =============================================================================
//  LIVE CHANNEL CARD
// =============================================================================

/**
 * Landscape 16:9 card for a Live TV channel.
 *
 * Focus uses [premiumFocus]: spring scale, lift shadow, directional lean and a
 * one-shot specular sweep — no border ring, the way tvOS presents focus.
 *
 * @param stream   The [StreamItem] to display.
 * @param onClick  Called when the user presses OK/Enter on the remote.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveChannelCard(
    stream:  StreamItem,
    onClick: () -> Unit,
    showChannelNumber: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick  = onClick,
        modifier = modifier
            .premiumFocus(isFocused, focusedScale = FocusScaleLive, shape = CardShape)
            .onFocusChanged { isFocused = it.isFocused }
            .size(CardWidthLive, CardHeightLive),
        shape    = CardDefaults.shape(CardShape),
        colors   = CardDefaults.colors(
            containerColor        = SlateCard,
            focusedContainerColor = SurfaceElevated
        ),
        scale  = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            border        = Border.None,
            focusedBorder = Border.None
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(SurfaceElevated, SurfaceDim)))
                .border(1.dp, Hairline, CardShape)
        ) {
            //  Channel logo / thumbnail
            AsyncImage(
                model             = stream.streamIcon,
                contentDescription = stream.name,
                contentScale      = ContentScale.Fit,
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp)
            )

            if (stream.isLive) {
                LiveBadge(modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp))
            }

            if (showChannelNumber && stream.num != null && stream.num > 0) {
                GlassPill(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Text(
                        text = "${stream.num}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text     = bidi(stream.name),
                    style    = IpxTypography.LabelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                    color    = if (isFocused) Color.White else TextPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =============================================================================
//  VOD POSTER CARD
// =============================================================================

/**
 * Portrait 2:3 poster card for a VOD movie.
 *
 * @param stream   The [StreamItem] to display.
 * @param onClick  Called on OK/Enter.
 */
@Composable
fun VodPosterCard(
    stream:  StreamItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchProgress: Float? = null,
    badge: String? = null
) {
    PosterCard(
        imageUrl      = stream.streamIcon,
        title         = stream.name,
        rating        = stream.rating,
        watchProgress = watchProgress,
        badge         = badge,
        onClick       = onClick,
        modifier      = modifier
    )
}

// =============================================================================
//  SERIES POSTER CARD
// =============================================================================

/**
 * Portrait 2:3 poster card for a TV Series.
 *
 * @param series  The [SeriesItem] to display.
 * @param onClick Called on OK/Enter.
 */
@Composable
fun SeriesPosterCard(
    series:  SeriesItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchProgress: Float? = null,
    badge: String? = null
) {
    PosterCard(
        imageUrl      = series.cover,
        title         = series.name,
        rating        = series.rating,
        watchProgress = watchProgress,
        badge         = badge,
        onClick       = onClick,
        modifier      = modifier
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    imageUrl:      String?,
    title:         String,
    rating:        String?,
    watchProgress: Float?,
    badge:         String? = null,
    onClick:       () -> Unit,
    modifier:      Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick  = onClick,
        modifier = modifier
            .premiumFocus(isFocused, focusedScale = FocusScalePoster, shape = CardShape)
            .onFocusChanged { isFocused = it.isFocused }
            .size(CardWidthVod, CardHeightVod),
        shape    = CardDefaults.shape(CardShape),
        colors   = CardDefaults.colors(
            containerColor        = SlateCard,
            focusedContainerColor = SlateCard
        ),
        scale  = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            border        = Border.None,
            focusedBorder = Border.None
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            PosterPlaceholder(title)

            AsyncImage(
                model              = imageUrl,
                contentDescription = title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            rating?.takeIf { it.isNotBlank() && (it.toDoubleOrNull() ?: 1.0) > 0.0 }?.let {
                RatingBadge(
                    rating   = it,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Quality label (e.g. measured "4K HDR"); drawn inside the card so it scales with focus.
            if (badge != null) {
                GlassPill(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text(
                        text = badge,
                        style = IpxTypography.LabelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            if (watchProgress != null && watchProgress in 0.02f..0.98f) {
                WatchProgressBar(watchProgress)
            }

            // Hairline edge keeps light posters from bleeding into each other.
            Box(Modifier.fillMaxSize().border(1.dp, Hairline, CardShape))
        }
    }
}

// =============================================================================
//  Shared sub-composables
// =============================================================================

/** Shown under the artwork while it loads, or when a portal has no artwork. */
@Composable
private fun PosterPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurfaceElevated, SurfaceDim))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text      = bidi(title),
            style     = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.SemiBold),
            color     = TextPrimary.copy(alpha = 0.55f),
            maxLines  = 3,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier.padding(16.dp)
        )
    }
}

/** Thin white progress line inset from the poster edges, like the TV app. */
@Composable
private fun BoxScope.WatchProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ProgressRail)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ProgressWatched)
            )
        }
    }
}

/** Dark translucent capsule used for small overlay labels. */
@Composable
internal fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    GlassPill(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AccentRed)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text  = stringResource(R.string.badge_live),
                style = IpxTypography.LabelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun RatingBadge(
    rating:   String,
    modifier: Modifier = Modifier
) {
    val formattedRating = rating.toDoubleOrNull()?.let {
        String.format(java.util.Locale.US, "%.1f", it)
    } ?: rating

    GlassPill(modifier = modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = AccentAmber,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text  = formattedRating,
                style = IpxTypography.LabelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
        }
    }
}
