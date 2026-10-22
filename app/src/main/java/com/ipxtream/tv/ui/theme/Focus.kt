package com.ipxtream.tv.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * Remembers the direction of the last D-Pad press so the next focused item
 * can "lean" in from that direction — a TV take on tvOS focus inertia.
 * Plain fields (not snapshot state) so key presses never trigger recomposition.
 */
object FocusTravel {
    @Volatile var dx: Float = 0f
    @Volatile var dy: Float = 0f
}

/** Attach once near the root of a screen to feed [FocusTravel]. */
fun Modifier.trackFocusTravel(): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionLeft  -> { FocusTravel.dx = -1f; FocusTravel.dy = 0f }
            Key.DirectionRight -> { FocusTravel.dx = 1f;  FocusTravel.dy = 0f }
            Key.DirectionUp    -> { FocusTravel.dx = 0f;  FocusTravel.dy = -1f }
            Key.DirectionDown  -> { FocusTravel.dx = 0f;  FocusTravel.dy = 1f }
            else -> Unit
        }
    }
    false
}

/**
 * Apple TV-style focus treatment for cards and tiles.
 *
 * - Springs up to [focusedScale] and lifts on a soft shadow.
 * - Leans a few degrees toward the D-Pad travel direction, then settles.
 * - A specular highlight sweeps across the surface once, plus a faint top
 *   light that stays while focused, so the tile reads as glass, not a flat box.
 *
 * Pair with a component whose own focus scale is 1f and that draws no focus border.
 */
@Composable
fun Modifier.premiumFocus(
    focused: Boolean,
    focusedScale: Float = 1.08f,
    shape: Shape = RoundedCornerShape(12.dp),
    lift: Dp = 24.dp,
    lean: Boolean = true,
    gloss: Boolean = true
): Modifier {
    val scale     = remember { Animatable(1f) }
    val leanY     = remember { Animatable(0f) }
    val leanX     = remember { Animatable(0f) }
    val sweep     = remember { Animatable(-1f) }
    val topLight  = remember { Animatable(0f) }
    val density   = LocalDensity.current
    val reduced   = LocalReducedEffects.current

    LaunchedEffect(focused, reduced) {
        if (reduced) {
            // Smooth mode: one short eased scale; no shadow, lean or shine to render.
            scale.animateTo(if (focused) focusedScale else 1f, tween(140, easing = IpxMotion.EaseOut))
            return@LaunchedEffect
        }
        if (focused) {
            launch { scale.animateTo(focusedScale, IpxMotion.focusSpring()) }
            launch { topLight.animateTo(1f, tween(IpxMotion.Standard, easing = IpxMotion.EaseOut)) }
            if (lean) {
                launch {
                    leanY.snapTo(FocusTravel.dx * 5f)
                    leanY.animateTo(0f, IpxMotion.leanSpring())
                }
                launch {
                    leanX.snapTo(-FocusTravel.dy * 4f)
                    leanX.animateTo(0f, IpxMotion.leanSpring())
                }
            }
            if (gloss) {
                launch {
                    sweep.snapTo(0f)
                    sweep.animateTo(1f, tween(1100, easing = IpxMotion.EaseApple))
                    sweep.snapTo(-1f)
                }
            }
        } else {
            launch { scale.animateTo(1f, IpxMotion.releaseSpring()) }
            launch { topLight.animateTo(0f, tween(IpxMotion.Fast)) }
            leanY.snapTo(0f)
            leanX.snapTo(0f)
            sweep.snapTo(-1f)
        }
    }

    val liftPx = with(density) { lift.toPx() }
    val range  = (focusedScale - 1f).coerceAtLeast(0.001f)

    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationY = leanY.value
            rotationX = leanX.value
            cameraDistance = 14f * density.density
            val t = ((scale.value - 1f) / range).coerceIn(0f, 1f)
            shadowElevation = if (reduced) 0f else liftPx * t
            this.shape = shape
            clip = false
        }
        .drawWithContent {
            drawContent()
            val light = topLight.value
            val s = sweep.value
            if (light <= 0.01f && s < 0f) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            clipPath(path) {
                if (light > 0.01f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.10f * light),
                            0.45f to Color.Transparent
                        )
                    )
                }
                if (s >= 0f) {
                    // Diagonal specular band travelling top-left → bottom-right.
                    val diag = size.width + size.height
                    val center = -0.3f * diag + s * 1.6f * diag
                    val band = diag * 0.22f
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.22f * (1f - s * 0.6f)),
                            1f to Color.Transparent,
                            start = Offset(center - band, 0f),
                            end = Offset(center + band, band * 1.2f)
                        )
                    )
                }
            }
        }
}
