package com.ipxtream.tv.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

// Liquid glass without a library: the page behind a floating control is recorded once into a GraphicsLayer
// (a RenderNode), and each glass element draws that layer again under itself through a RenderEffect:
// blur on Android 12+, plus an AGSL lens on Android 13+ that bends the backdrop near the rim, splits it
// slightly into colours and lifts saturation, as in Apple's iOS & iPadOS 27 kit ("Liquid Glass", regular).
// Nothing is copied pixel by pixel on the CPU; the RenderThread does the work, only under the glass.

/** A page that glass can see through. Make one with [rememberGlassBackdrop], mark the page with [glassSource]. */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Records this node's drawing for [backdrop] and draws it as usual. Glass must not sit inside this node. */
fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) this
    else this
        .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }

/** True when glass here can show the real page behind it (Android 12+, a backdrop is provided). */
internal fun canSeeThrough(backdrop: GlassBackdrop?): Boolean =
    backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** True when the lens shader runs (Android 13+). */
internal val hasLens: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Draws [backdrop] under this node through blur (+ lens on Android 13+), then a [tint].
 * The caller clips to [shape] first.
 */
@Composable
internal fun Modifier.glassBackdrop(backdrop: GlassBackdrop, shape: Shape, tint: Color): Modifier {
    val glass = rememberGraphicsLayer()
    val position = remember { PositionHolder() }
    return this
        .onGloballyPositioned { position.value = it.positionInRoot() }
        .drawWithCache {
            val radius = when (val o = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
                is Outline.Rectangle -> 0f
                is Outline.Generic -> minOf(size.width, size.height) / 2f
            }
            glass.renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lensEffect(size.width, size.height, radius, edge = minOf(22.dp.toPx(), size.minDimension / 2f), blur = 6.dp.toPx())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RenderEffect.createBlurEffect(16.dp.toPx(), 16.dp.toPx(), Shader.TileMode.CLAMP).asComposeRenderEffect()
            } else null
            onDrawBehind {
                val shift = backdrop.origin - position.value
                glass.record {
                    translate(shift.x, shift.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(glass)
                drawRect(tint)
            }
        }
}

private class PositionHolder {
    var value by mutableStateOf(Offset.Zero)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(w: Float, h: Float, radius: Float, edge: Float, blur: Float): androidx.compose.ui.graphics.RenderEffect {
    val shader = RuntimeShader(LENS_AGSL).apply {
        setFloatUniform("size", w, h)
        setFloatUniform("radius", radius)
        setFloatUniform("edge", edge)
        setFloatUniform("depth", edge * 0.55f)
        setFloatUniform("sat", 1.6f)
    }
    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val soft = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
    // The chain blurs first (inner), then bends the blurred page (outer).
    return RenderEffect.createChainEffect(lens, soft).asComposeRenderEffect()
}

// Rounded-rect lens: inside a band of `edge` px along the rim, samples are pulled inward along the rim's
// normal (stronger towards the rim), with red/blue offset a little apart; the page is also saturated.
internal const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float edge;
uniform float depth;
uniform float sat;

float sdf(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 c) {
    float2 hb = size * 0.5;
    float2 p = c - hb;
    float d = -sdf(p, hb, radius);
    float t = clamp(1.0 - d / edge, 0.0, 1.0);
    t = t * t;
    float2 e = float2(0.5, 0.0);
    float2 n = float2(sdf(p + e.xy, hb, radius) - sdf(p - e.xy, hb, radius),
                      sdf(p + e.yx, hb, radius) - sdf(p - e.yx, hb, radius));
    n = n / max(length(n), 0.0001);
    float2 o = -n * t * depth;
    half4 base = content.eval(c + o);
    half r = content.eval(c + o * 1.08).r;
    half b = content.eval(c + o * 0.92).b;
    half3 col = half3(r, base.g, b);
    half l = dot(col, half3(0.2126, 0.7152, 0.0722));
    col = mix(half3(l), col, half(sat));
    return half4(clamp(col, 0.0, 1.0), base.a);
}
"""
