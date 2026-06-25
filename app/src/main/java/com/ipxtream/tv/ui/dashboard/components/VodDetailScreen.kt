package com.ipxtream.tv.ui.dashboard.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodDetailScreen(
    streamItem: StreamItem,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playButtonFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { playButtonFocus.requestFocus() }
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
            model              = streamItem.streamIcon,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
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
                    colors = listOf(Color.Transparent, Color(0xAA000000), Color(0xFF000000)),
                    startY = 300f
                )
            )
        )

        // ─── Foreground Content ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 80.dp, bottom = 80.dp, end = 80.dp, top = 80.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text  = streamItem.name,
                style = IpxTypography.DisplayLarge.copy(fontSize = 56.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                streamItem.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                    Text("⭐ $rating/10", style = IpxTypography.TitleLarge, color = com.ipxtream.tv.ui.theme.AccentAmber, fontWeight = FontWeight.Bold)
                }
                if (streamItem.containerExtension != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(streamItem.containerExtension.uppercase(), style = IpxTypography.LabelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ─── Actions ──────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playButtonFocus),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = com.ipxtream.tv.ui.theme.AccentCyan,
                        focusedContentColor = Color.White
                    ),
                    shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "▶  Play",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = IpxTypography.TitleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDownload,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Color.DarkGray.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.LightGray,
                        focusedContentColor = Color.Black
                    ),
                    shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "⬇  Download",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = IpxTypography.TitleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onToggleFavorite,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Color.DarkGray.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.LightGray,
                        focusedContentColor = Color.Black
                    ),
                    shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                            tint = if (isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isFavorite) "Liked" else "Like",
                            style = IpxTypography.TitleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Text("Press Back to return to grid", style = IpxTypography.LabelSmall, color = TextMuted)
        }
    }
}
