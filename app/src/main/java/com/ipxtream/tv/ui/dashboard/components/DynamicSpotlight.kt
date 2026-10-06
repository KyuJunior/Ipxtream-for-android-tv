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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        animationSpec = tween(250),
        label = "spotlightFade"
    ) { (currentUrl, currentTitle) ->
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            if (currentUrl.isNotEmpty()) {
                // Background dimmed image without GPU-choking blur
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback empty solid background
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }

            // Dark gradients to blend seamlessly into OLED pitch black
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x55000000), // Semi-transparent top
                                Color(0xCC000000), // Darker mid
                                Color(0xFF000000)  // Pure OLED black bottom
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Horizontal gradient to ensure text readability on the left
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEE000000),
                                Color(0x88000000),
                                Color.Transparent
                            )
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("$rating", color = AccentAmber, style = IpxTypography.TitleMedium)
                            }
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
