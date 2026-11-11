package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ipxtream.tv.ui.theme.appleGlass
import com.ipxtream.tv.ui.theme.glassSource
import com.ipxtream.tv.ui.theme.touchShield
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.AppleFill
import com.ipxtream.tv.ui.theme.ipxShape
import com.ipxtream.tv.ui.theme.isAppleDesign

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
    resolution: com.ipxtream.tv.data.local.VideoRes? = null,
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

    val apple = isAppleDesign
    var showInfo by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val infoCloseFocus = remember { FocusRequester() }
    LaunchedEffect(showInfo) { if (showInfo) runCatching { infoCloseFocus.requestFocus() } }
    // DashboardActivity closes details on Back before Compose sees the key; it lets Back through while this is set.
    androidx.compose.runtime.SideEffect { AppleInfoSheet.open = showInfo }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { AppleInfoSheet.open = false } }
    // Gesture Back on phones: the sheet closes first (registered after the page's own handler, so it wins).
    BackHandler(enabled = showInfo && !isPlayerOpen) {
        showInfo = false
        runCatching { playButtonFocus.requestFocus() }
    }
    val badgesRow: @Composable () -> Unit = {
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
                                .clip(ipxShape(6.dp))
                                .background(AccentAmber.copy(alpha = 0.18f))
                                .border(1.dp, AccentAmber.copy(alpha = 0.5f), ipxShape(6.dp))
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
                            color      = Color(0xFFD9D0DC),
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
                                .clip(ipxShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), ipxShape(6.dp))
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

                    // Measured resolution (from the portal's probe of the actual file)
                    val measured = resolution ?: com.ipxtream.tv.data.local.VideoRes.fromPanelJson(info?.video)
                    measured?.label?.let { label ->
                        Box(
                            modifier = Modifier
                                .clip(ipxShape(6.dp))
                                .background(Color.White.copy(alpha = if (measured.is4K) 0.9f else 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), ipxShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text          = if (measured.hdr) "$label HDR" else label,
                                style         = IpxTypography.LabelSmall.copy(fontSize = 12.sp),
                                color         = if (measured.is4K) Color.Black else Color.White,
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Quality / Extension Badge
                    Box(
                        modifier = Modifier
                            .clip(ipxShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), ipxShape(6.dp))
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
                            color      = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }

    }
    val titleBlock: @Composable () -> Unit = {
                // 2. Movie Title
                Text(
                    text       = bidi(title),
                    style      = IpxTypography.DisplayLarge.copy(fontSize = 44.sp, lineHeight = 52.sp),
                    color      = TextPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )

    }
    val buttonsRow: @Composable () -> Unit = {
                // 3. Action Buttons Row (Sleek TV Pills)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VodActionButton(
                        onClick         = onPlay,
                        icon            = Icons.Filled.PlayArrow,
                        label           = stringResource(R.string.action_play),
                        isPrimary       = true,
                        modifier        = Modifier.focusRequester(playButtonFocus)
                    )

                    if (com.ipxtream.tv.BuildConfig.DOWNLOADS) {
                        VodActionButton(
                            onClick   = onDownload,
                            icon      = Icons.Filled.ArrowDownward,
                            label     = stringResource(R.string.action_download),
                            isPrimary = false
                        )
                    }

                    VodActionButton(
                        onClick         = onToggleFavorite,
                        icon            = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label           = stringResource(if (isFavorite) R.string.action_liked else R.string.action_like),
                        isPrimary       = false,
                        isHeartFavorite = isFavorite
                    )

                    if (apple) {
                        VodActionButton(
                            onClick   = { showInfo = true },
                            icon      = Icons.Filled.Info,
                            label     = stringResource(R.string.action_details),
                            isPrimary = false
                        )
                    }
                    VodActionButton(
                        onClick   = onClose,
                        icon      = Icons.AutoMirrored.Filled.ArrowBack,
                        label     = stringResource(R.string.action_back),
                        isPrimary = false
                    )
                }

    }
    val plotBlock: @Composable () -> Unit = {
                // 4. Synopsis / Plot
                if (isLoadingVodInfo && plot == null) {
                    Text(
                        text      = stringResource(R.string.vod_loading),
                        style     = IpxTypography.BodyMedium.copy(fontSize = 15.sp),
                        color     = TextMuted,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Text(
                        text       = plot ?: stringResource(R.string.no_synopsis),
                        style      = IpxTypography.BodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
                        color      = TextSecondary,
                        maxLines   = 4,
                        overflow   = TextOverflow.Ellipsis
                    )
                }

    }
    val creditsBlock: @Composable () -> Unit = {
                // 5. Cast, Director & Country metadata
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!director.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text       = stringResource(R.string.vod_director),
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
                                text       = stringResource(R.string.vod_cast),
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
                                text       = stringResource(R.string.vod_country),
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDeep)
            .focusProperties { canFocus = !isPlayerOpen }
            .onPreviewKeyEvent { keyEvent ->
                if (!isPlayerOpen && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    if (showInfo) {
                        showInfo = false
                        runCatching { playButtonFocus.requestFocus() }
                    } else onClose()
                    true
                } else {
                    false
                }
            }
    ) {
        // Apple: the page is recorded so the Info sheet's glass can bend and blur it (LiquidGlass.kt).
        val detailsBackdrop = if (apple && !com.ipxtream.tv.ui.theme.LocalReducedEffects.current) com.ipxtream.tv.ui.theme.rememberGlassBackdrop() else null
        // While the sheet is open nothing under it takes focus (D-Pad) or taps (touchShield on the sheet).
        Box(Modifier.fillMaxSize().focusProperties { if (showInfo) canFocus = false }.glassSource(detailsBackdrop)) {
        // ─── High-Res Cinematic Backdrop Fanart ───────────────────────────────
        AsyncImage(
            model              = backdropUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            alpha              = if (apple) 1f else 0.35f,
            modifier           = Modifier.fillMaxSize()
        )

        // ─── Double Cinematic Gradients ──────────────────────────────────────
        // Horizontal: solid Night canvas on the left, fading gently to reveal backdrop art on the right
        // Apple: the art stays bright; the scrims only darken the bottom-start where the copy sits.
        if (apple) Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to SlateDeep.copy(alpha = 0xD9 / 255f),
                    0.45f to SlateDeep.copy(alpha = 0x66 / 255f),
                    0.8f to Color.Transparent
                )
            )
        ) else Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to SlateDeep,
                    0.48f to SlateDeep.copy(alpha = 0xEE / 255f),
                    0.75f to SlateDeep.copy(alpha = 0x77 / 255f),
                    1.0f to SlateDeep.copy(alpha = 0x18 / 255f)
                )
            )
        )
        // Vertical: subtle dark vignette on bottom and top
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to SlateDeep.copy(alpha = 0x66 / 255f),
                    0.25f to Color.Transparent,
                    0.7f to SlateDeep.copy(alpha = 0x44 / 255f),
                    1.0f to SlateDeep
                )
            )
        )

        // ─── Main 10-Foot Content Canvas ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, end = 72.dp, top = 48.dp, bottom = if (apple) 64.dp else 48.dp),
            verticalAlignment = if (apple) Alignment.Bottom else Alignment.CenterVertically
        ) {
            // ─── Left Section: Title, Badges, Actions, Synopsis, Cast ────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = if (apple) Arrangement.Bottom else Arrangement.Center
            ) {
                if (apple) {
                    // Apple: title first, then the facts, a short synopsis and the actions, all at the bottom-start.
                    titleBlock()
                    Spacer(modifier = Modifier.height(14.dp))
                    badgesRow()
                    Spacer(modifier = Modifier.height(16.dp))
                    plotBlock()
                    Spacer(modifier = Modifier.height(28.dp))
                    buttonsRow()
                } else {
                    badgesRow()
                    Spacer(modifier = Modifier.height(16.dp))
                    titleBlock()
                    Spacer(modifier = Modifier.height(28.dp))
                    buttonsRow()
                    Spacer(modifier = Modifier.height(26.dp))
                    plotBlock()
                    Spacer(modifier = Modifier.height(20.dp))
                    creditsBlock()
                }
            }

            // Apple: full-bleed art instead of a separate poster.
            if (!apple) Spacer(modifier = Modifier.width(48.dp))

            // ─── Right Section: Floating Cinematic Poster Card ────────────────
            if (!apple) Box(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(420.dp)
                        .clip(ipxShape(18.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.22f), ipxShape(18.dp))
                        .background(SlateCard)
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
                            .clip(ipxShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), ipxShape(6.dp))
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

        // Apple: cast, director and the full synopsis in a glass sheet (Info button); Back closes it.
        if (apple && showInfo) androidx.compose.runtime.CompositionLocalProvider(com.ipxtream.tv.ui.theme.LocalGlassBackdrop provides detailsBackdrop) {
            Box(Modifier.fillMaxSize().touchShield().background(com.ipxtream.tv.ui.theme.AppleSheetDim), contentAlignment = Alignment.CenterEnd) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .width(560.dp)
                        .fillMaxHeight()
                        .appleGlass(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                        .padding(36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // The text scrolls (drag on phones) so Close always keeps its place on short screens.
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text     = bidi(title),
                            style    = IpxTypography.HeadlineMedium,
                            color    = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text  = plot ?: stringResource(R.string.no_synopsis),
                            style = IpxTypography.BodyLarge,
                            color = TextSecondary
                        )
                        creditsBlock()
                    }
                    VodActionButton(
                        onClick   = { showInfo = false; runCatching { playButtonFocus.requestFocus() } },
                        icon      = Icons.AutoMirrored.Filled.ArrowBack,
                        label     = stringResource(R.string.action_close),
                        isPrimary = false,
                        modifier  = Modifier.focusRequester(infoCloseFocus)
                    )
                }
            }
        }
    }
}

/** Whether the Apple details Info sheet is open (Back should close it, not the details page). */
internal object AppleInfoSheet { @Volatile var open = false }

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
                containerColor        = Accent,
                contentColor          = TextOnAccent,
                focusedContainerColor = ChipFocused,
                focusedContentColor   = TextOnFocus
            )
        } else {
            ButtonDefaults.colors(
                containerColor        = if (isAppleDesign) AppleFill else Color(0x2EFFFFFF),
                contentColor          = if (isAppleDesign) Accent else Color.White,
                focusedContainerColor = ChipFocused,
                focusedContentColor   = TextOnFocus
            )
        },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.08f),
        border = ButtonDefaults.border(
            border = if (isAppleDesign) Border.None else Border(
                border = BorderStroke(1.dp, if (isPrimary) Color.Transparent else Color.White.copy(alpha = 0.2f)),
                shape  = RoundedCornerShape(50)
            ),
            focusedBorder = Border.None
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                modifier           = Modifier.size(20.dp),
                tint               = if (isAppleDesign) androidx.tv.material3.LocalContentColor.current else if (isHeartFavorite) Color.Red else Color.Unspecified
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = label,
                style      = IpxTypography.TitleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
