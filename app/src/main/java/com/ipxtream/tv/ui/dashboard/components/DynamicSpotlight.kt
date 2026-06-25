package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlatePrimary
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * A cinematic hero banner that displays the dynamically focused item in the dashboard.
 * Designed to dramatically elevate the visual aesthetics of the application.
 */
@Composable
fun DynamicSpotlight(
    streamItem: StreamItem?,
    seriesItem: SeriesItem?,
    modifier:   Modifier = Modifier
) {
    // Both can't be active at the same time, we check whichever is non-null
    val title     = streamItem?.name ?: seriesItem?.name ?: ""
    val imageUrl  = streamItem?.streamIcon ?: seriesItem?.cover ?: ""
    val rating    = streamItem?.rating?.toDoubleOrNull() ?: seriesItem?.rating?.toDoubleOrNull() ?: 0.0
    val isLive    = streamItem?.streamType == "live"

    Crossfade(
        targetState = imageUrl to title,
        animationSpec = tween(700),
        label = "spotlightFade"
    ) { (currentUrl, currentTitle) ->
        Box(modifier = modifier.fillMaxSize()) {
            if (currentUrl.isNotEmpty()) {
                // Background massive blurred image
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .blur(radius = 64.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            } else {
                // Fallback empty solid background
                Box(modifier = Modifier.fillMaxSize().background(SlatePrimary))
            }

            // Dark gradients to blend it seamlessly into the application layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x66000000), // Semi-transparent top
                                Color(0xDD000000), // Darker mid
                                Color(0xFF0F0F0F)  // Pure deep charcoal bottom to blend into Grid
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Content Overlay (Title, Info) aligned to Top-Left
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.65f) // Don't take whole width so it doesn't overlap cards too much
                    .padding(start = 32.dp, top = 48.dp, end = 32.dp)
            ) {
                if (currentTitle.isNotEmpty()) {
                    Text(
                        text = currentTitle,
                        style = IpxTypography.DisplayLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(com.ipxtream.tv.ui.theme.AccentGreen)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE", color = Color.Black, style = IpxTypography.LabelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                        } else if (rating > 0.0) {
                            Text("★ $rating", color = AccentAmber, style = IpxTypography.TitleMedium)
                            Spacer(Modifier.width(12.dp))
                        }
                        
                        Text(
                            text = if (seriesItem != null) "Series" else "Movie",
                            color = TextMuted,
                            style = IpxTypography.TitleMedium
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Press OK to view details or play.",
                        style = IpxTypography.BodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
