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
import com.ipxtream.tv.ui.theme.SlateDeep

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
        animationSpec = tween(com.ipxtream.tv.ui.theme.IpxMotion.Slow, easing = com.ipxtream.tv.ui.theme.IpxMotion.EaseStandard),
        label = "spotlightFade"
    ) { (currentUrl, currentTitle) ->
        Box(modifier = modifier.fillMaxSize().background(SlateDeep)) {
            if (currentUrl.isNotEmpty()) {
                // Background dimmed image without GPU-choking blur
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.4f,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback empty solid background
                Box(modifier = Modifier.fillMaxSize().background(SlateDeep))
            }

            // Dark gradients to blend seamlessly into OLED pitch black
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SlateDeep.copy(alpha = 0x55 / 255f), // Semi-transparent top
                                SlateDeep.copy(alpha = 0xCC / 255f), // Darker mid
                                SlateDeep  // Pure OLED black bottom
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
                                SlateDeep.copy(alpha = 0xEE / 255f),
                                SlateDeep.copy(alpha = 0x88 / 255f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Backdrop only: the grid's header and chips sit on top of this area, so a
            // title here would collide with them.
        }
    }
}
