package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentCyanGlow
import com.ipxtream.tv.ui.theme.AccentGreen
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.SlatePrimary
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

// ─── Shared card dimensions ────────────────────────────────────────────────────

private val CardWidthVod    = 180.dp
private val CardHeightVod   = 270.dp  // 2:3 poster ratio

private val CardWidthLive   = 240.dp
private val CardHeightLive  = 135.dp  // 16:9 channel card

private val CardShape = RoundedCornerShape(12.dp)
private val FocusScale = 1.15f

// =============================================================================
//  LIVE CHANNEL CARD
// =============================================================================

/**
 * Landscape 16:9 card for a Live TV channel.
 *
 * ### D-Pad Focus behaviour (provided by TV Material Card):
 * - **Scale**: smoothly animates to 110% when focused.
 * - **Border**: 2dp electric cyan glow ring appears on focus.
 * - **Elevation glow**: cyan shadow emitted below the card when focused.
 *
 * @param stream   The [StreamItem] to display.
 * @param onClick  Called when the user presses OK/Enter on the remote.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveChannelCard(
    stream:  StreamItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick  = onClick,
        modifier = modifier.size(CardWidthLive, CardHeightLive),
        shape    = CardDefaults.shape(CardShape),
        colors   = CardDefaults.colors(
            containerColor        = SlatePrimary,
            focusedContainerColor = SlateGlass
        ),
        scale  = CardDefaults.scale(focusedScale = FocusScale),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CardShape)
        ),
        glow   = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black, elevation = 24.dp)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            //  Channel logo / thumbnail
            AsyncImage(
                model             = stream.streamIcon,
                contentDescription = stream.name,
                contentScale      = ContentScale.Fit,
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )

            // "LIVE" badge
            if (stream.isLive) {
                LiveBadge(modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp))
            }

            // Gradient + channel name at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC0B1520))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text     = stream.name,
                    style    = IpxTypography.LabelSmall,
                    color    = TextPrimary,
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
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodPosterCard(
    stream:  StreamItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick  = onClick,
        modifier = modifier.size(CardWidthVod, CardHeightVod),
        shape    = CardDefaults.shape(CardShape),
        colors   = CardDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale  = CardDefaults.scale(focusedScale = FocusScale),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CardShape)
        ),
        glow   = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black, elevation = 24.dp)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model              = stream.streamIcon,
                contentDescription = stream.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            // Rating badge (sleeker top-right placement)
            stream.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                RatingBadge(
                    rating   = rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
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
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesPosterCard(
    series:  SeriesItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick  = onClick,
        modifier = modifier.size(CardWidthVod, CardHeightVod),
        shape    = CardDefaults.shape(CardShape),
        colors   = CardDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale  = CardDefaults.scale(focusedScale = FocusScale),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CardShape)
        ),
        glow   = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black, elevation = 24.dp)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model              = series.cover,
                contentDescription = series.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            series.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                RatingBadge(
                    rating   = rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

// =============================================================================
//  Shared sub-composables
// =============================================================================

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentGreen)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text  = "● LIVE",
            style = IpxTypography.LabelSmall,
            color = Color(0xFF0B1520),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun RatingBadge(
    rating:   String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xE6000000))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text  = "★ $rating",
            style = IpxTypography.LabelSmall,
            color = AccentAmber,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
