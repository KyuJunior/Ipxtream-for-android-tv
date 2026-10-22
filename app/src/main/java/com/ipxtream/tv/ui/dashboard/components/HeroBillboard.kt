package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.ui.theme.bidi
import com.ipxtream.tv.ui.theme.directionalKeys
import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.GlassFill
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxMotion
import com.ipxtream.tv.ui.theme.IpxSpacing
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

// =============================================================================
//  Model
// =============================================================================

/** One slide of the Home billboard, built from a movie or a series. */
data class HeroFeature(
    val key:         String,
    val title:       String,
    val kind:        String,
    val posterUrl:   String?,
    val backdropUrl: String?,
    val rating:      Double?,
    val year:        String?,
    val genre:       String?,
    val plot:        String?,
    val stream:      StreamItem? = null,
    val series:      SeriesItem? = null
)

private val YEAR_SUFFIX = Regex("""\s*[(\[](\d{4})[)\]]\s*$""")
private val LANG_PREFIX = Regex("""^(\|[^|]{1,8}\|\s*|[A-Z]{2,3}\s?[-:|]\s+)""")

/** Strips portal noise ("EN - ", "|AR| ", trailing "(2024)") for display. */
private fun cleanTitle(raw: String): Pair<String, String?> {
    val year = YEAR_SUFFIX.find(raw)?.groupValues?.get(1)
    val title = raw.replace(YEAR_SUFFIX, "").replace(LANG_PREFIX, "").trim()
    return (title.ifBlank { raw.trim() }) to year
}

/**
 * Interleaves the best movies and series into at most [max] billboard slides.
 * Items without artwork are skipped — an empty billboard looks broken.
 */
fun buildHeroFeatures(
    movies: List<StreamItem>,
    series: List<SeriesItem>,
    max:    Int = 8
): List<HeroFeature> {
    val movieFeatures = movies
        .filter { !it.streamIcon.isNullOrBlank() }
        .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
        .take(max)
        .map { m ->
            val (title, year) = cleanTitle(m.name)
            HeroFeature(
                key = "m_${m.streamId}", title = title, kind = "Movie",
                posterUrl = m.streamIcon, backdropUrl = null,
                rating = m.rating?.toDoubleOrNull()?.takeIf { it > 0 },
                year = year, genre = null, plot = null, stream = m
            )
        }
    val seriesFeatures = series
        .filter { !it.cover.isNullOrBlank() }
        .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
        .take(max)
        .map { s ->
            val (title, year) = cleanTitle(s.name)
            HeroFeature(
                key = "s_${s.seriesId}", title = title, kind = "Series",
                posterUrl = s.cover,
                backdropUrl = s.backdropPath?.firstOrNull { it.isNotBlank() },
                rating = s.rating?.toDoubleOrNull()?.takeIf { it > 0 },
                year = year ?: s.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) },
                genre = s.genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(", "),
                plot = s.plot?.trim()?.takeIf { it.length > 20 },
                series = s
            )
        }
    val out = ArrayList<HeroFeature>(max)
    var i = 0
    while (out.size < max && (i < movieFeatures.size || i < seriesFeatures.size)) {
        movieFeatures.getOrNull(i)?.let { out += it }
        if (out.size < max) seriesFeatures.getOrNull(i)?.let { out += it }
        i++
    }
    return out
}

// =============================================================================
//  Backdrop (full-bleed, sits behind the header and the first rows)
// =============================================================================

/**
 * Cinematic backdrop for the current [feature]: slow Ken Burns drift, a long
 * cross-dissolve between slides, and parallax + fade as the page scrolls.
 *
 * Landscape backdrops are shown edge to edge. Portrait-only titles get an
 * ambient, heavily blurred wash of their poster plus the crisp poster on the right.
 *
 * @param scrollProgress 0 = page at top, 1 = billboard scrolled away.
 */
@Composable
fun HeroBackdrop(
    feature:        HeroFeature?,
    scrollProgress: () -> Float,
    animate:        Boolean = true,
    modifier:       Modifier = Modifier
) {
    // The drift only runs while the billboard is actually on screen; under the player or a
    // dialog it would keep re-rendering a blurred layer every frame for nothing.
    val reduced = com.ipxtream.tv.ui.theme.LocalReducedEffects.current
    val kenBurnsState: State<Float> = if (animate && !reduced) {
        rememberInfiniteTransition(label = "kenBurns").animateFloat(
            initialValue  = 1.0f,
            targetValue   = 1.07f,
            animationSpec = infiniteRepeatable(tween(18_000, easing = IpxMotion.EaseStandard), RepeatMode.Reverse),
            label         = "kenBurnsScale"
        )
    } else {
        remember { mutableFloatStateOf(1.035f) }
    }

    Box(
        // Clip so the Ken Burns zoom never spills past the scrims.
        modifier = modifier.clipToBounds().graphicsLayer {
            val p = scrollProgress()
            translationY = -p * size.height * 0.18f
            alpha = 1f - 0.7f * p
        }
    ) {
        Crossfade(
            targetState   = feature,
            animationSpec = tween(if (reduced) 250 else IpxMotion.Hero, easing = IpxMotion.EaseStandard),
            label         = "heroBackdrop"
        ) { f ->
            Box(Modifier.fillMaxSize()) {
                if (f != null) {
                    if (f.backdropUrl != null) {
                        AsyncImage(
                            model              = f.backdropUrl,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            alignment          = Alignment.TopCenter,
                            modifier           = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = kenBurnsState.value; scaleY = kenBurnsState.value }
                        )
                    } else {
                        AmbientArtwork(
                            url = f.posterUrl,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = kenBurnsState.value; scaleY = kenBurnsState.value }
                        )
                        AsyncImage(
                            model              = f.posterUrl,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 96.dp, end = 72.dp)
                                .size(width = 220.dp, height = 330.dp)
                                .shadow(32.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }

        // Legibility scrims: left for the copy, top for the header, bottom to melt into the rows.
        // Each covers only the band where it isn't transparent: on a weak TV GPU every
        // blended full-screen layer costs several milliseconds per frame.
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(
                Brush.horizontalGradient(
                    0f to Color(0xF2000000),
                    0.54f to Color(0xA6000000),
                    1f to Color.Transparent
                )
            )
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.22f).background(
                Brush.verticalGradient(0f to Color(0x99000000), 1f to Color.Transparent)
            )
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.45f).background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.67f to Color(0xCC000000),
                    1f to Color.Black
                )
            )
        )
    }
}

/**
 * A soft colour wash derived from the artwork. Real blur on Android 12+;
 * on older boxes a tiny decode is up-scaled, which smooths out almost as well
 * and costs nothing.
 */
@Composable
private fun AmbientArtwork(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Real blur is a full-screen GPU pass; Smooth mode uses the cheap up-scaled decode instead.
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !com.ipxtream.tv.ui.theme.LocalReducedEffects.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(if (canBlur) 160 else 24, if (canBlur) 240 else 36)
        .build()
    AsyncImage(
        model              = request,
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        alpha              = 0.6f,
        modifier           = modifier.then(if (canBlur) Modifier.blur(48.dp) else Modifier)
    )
}

// =============================================================================
//  Billboard copy + actions (first item of the Home list)
// =============================================================================

/**
 * Title block, metadata, actions and page indicator for the current slide.
 * Copy animates per slide; the buttons stay mounted so D-Pad focus never jumps.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroInfo(
    features:        List<HeroFeature>,
    index:           Int,
    isFavorite:      Boolean,
    onPrimary:       (HeroFeature) -> Unit,
    onDetails:       (HeroFeature) -> Unit,
    onToggleFavorite:(HeroFeature) -> Unit,
    onIndexChange:   (Int) -> Unit,
    primaryModifier: Modifier = Modifier,
    height:          Dp = 420.dp,
    modifier:        Modifier = Modifier
) {
    if (features.isEmpty()) return
    val feature = features[index.coerceIn(features.indices)]
    val next = { onIndexChange((index + 1) % features.size) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(start = IpxSpacing.Gutter, end = IpxSpacing.Gutter, bottom = 8.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        AnimatedContent(
            targetState = feature,
            transitionSpec = {
                (fadeIn(tween(IpxMotion.Slow, delayMillis = 120, easing = IpxMotion.EaseOut)) +
                    slideInVertically(tween(IpxMotion.Slow, delayMillis = 120, easing = IpxMotion.EaseApple)) { it / 10 })
                    .togetherWith(fadeOut(tween(IpxMotion.Fast)))
            },
            contentKey = { it.key },
            label = "heroCopy"
        ) { f ->
            Column(Modifier.widthIn(max = 640.dp)) {
                Text(
                    text  = stringResource(if (f.kind == "Series") R.string.hero_featured_series else R.string.hero_featured_movie),
                    style = IpxTypography.Eyebrow,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text     = bidi(f.title),
                    style    = IpxTypography.HeroTitle,
                    color    = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                MetaLine(f)
                if (f.plot != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text     = f.plot,
                        style    = IpxTypography.BodyLarge,
                        color    = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            PremiumButton(
                label    = stringResource(if (feature.kind == "Series") R.string.action_episodes else R.string.action_play),
                icon     = if (feature.kind == "Series") Icons.Rounded.VideoLibrary else Icons.Rounded.PlayArrow,
                onClick  = { onPrimary(feature) },
                modifier = primaryModifier
            )
            if (feature.kind == "Movie") {
                PremiumButton(
                    label   = stringResource(R.string.action_details),
                    icon    = Icons.Rounded.Info,
                    onClick = { onDetails(feature) }
                )
            }
            val keys = directionalKeys()
            GlassIconButton(
                icon     = if (isFavorite) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = stringResource(if (isFavorite) R.string.library_remove else R.string.library_add),
                onClick  = { onToggleFavorite(feature) },
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == keys.end && features.size > 1) {
                        next(); true
                    } else false
                }
            )
        }

        Spacer(Modifier.height(22.dp))

        if (features.size > 1) {
            PageDots(count = features.size, selected = index)
        }
    }
}

@Composable
private fun MetaLine(f: HeroFeature) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val parts = buildList {
            f.year?.let { add(it) }
            f.genre?.let { add(it) }
            add(f.kind)
        }
        if (f.rating != null) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = AccentAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text  = String.format(java.util.Locale.US, "%.1f", f.rating),
                style = IpxTypography.BodyLarge,
                color = TextPrimary
            )
            Text("  ·  ", style = IpxTypography.BodyLarge, color = TextSecondary)
        }
        Text(
            text  = parts.joinToString("  ·  "),
            style = IpxTypography.BodyLarge,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == selected
            val w by animateDpAsState(
                targetValue   = if (active) 22.dp else 6.dp,
                animationSpec = tween(IpxMotion.Standard, easing = IpxMotion.EaseApple),
                label         = "dotWidth"
            )
            Box(
                Modifier
                    .height(6.dp)
                    .width(w)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) Color.White else Color.White.copy(alpha = 0.3f))
            )
        }
    }
}

// =============================================================================
//  Buttons — glass at rest, solid white when focused (tvOS platter style)
// =============================================================================

/** Capsule action button: frosted at rest, white with black label on focus. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PremiumButton(
    label:    String,
    icon:     ImageVector?,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick  = onClick,
        modifier = modifier.height(48.dp),
        shape    = ClickableSurfaceDefaults.shape(shape),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = GlassFill,
            contentColor          = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor   = Color.Black,
            pressedContainerColor = Color(0xFFD1D1D6),
            pressedContentColor   = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border        = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, GlassStroke), shape = shape),
            focusedBorder = androidx.tv.material3.Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        glow  = ClickableSurfaceDefaults.glow()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = label, style = IpxTypography.TitleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        }
    }
}

/** Circular glass button for secondary icon-only actions. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GlassIconButton(
    icon:               ImageVector,
    contentDescription: String,
    onClick:            () -> Unit,
    modifier:           Modifier = Modifier,
    size:               Dp = 48.dp
) {
    Surface(
        onClick  = onClick,
        modifier = modifier.size(size),
        shape    = ClickableSurfaceDefaults.shape(CircleShape),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = GlassFill,
            contentColor          = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor   = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border        = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, GlassStroke), shape = CircleShape),
            focusedBorder = androidx.tv.material3.Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.46f))
        }
    }
}
