package com.ipxtream.tv.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxMotion
import com.ipxtream.tv.ui.theme.LocalReducedEffects
import com.ipxtream.tv.ui.theme.SlateDeep
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Start-up intro built from the brand mark: the two Ember arms sweep in and cross, the Halo play
 * triangle springs into the left counter, then the wordmark rises in, and everything fades out.
 * About 2.2 s once frames run smoothly. With reduced effects the same sequence plays without the
 * glow (the only costly part: a full-size radial gradient redrawn every frame). Any key skips it.
 *
 * Draw it on top of the first screen; [onFinished] is called once, after the fade-out.
 */
@Composable
fun BrandIntro(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedEffects.current
    val glow = remember { Animatable(0f) }
    val armDown = remember { Animatable(0f) }
    val armUp = remember { Animatable(0f) }
    val triangle = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val content = remember { Animatable(1f) }   // fades the whole intro
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val finished = remember { booleanArrayOf(false) }

    fun finish() {
        if (finished[0]) return
        finished[0] = true
        onFinished()
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        // Cold start keeps the main thread busy for a while (window fade, credential store, auto-
        // login), and a time-based animation would finish during that stall unseen. Start once
        // frames are actually arriving at a steady rate, or after 1.5 s at the latest.
        awaitSteadyFrames()
        coroutineScope {
            if (!reduced) launch { glow.animateTo(1f, tween(900, easing = IpxMotion.EaseOut)) }
            launch { armDown.animateTo(1f, tween(420, easing = IpxMotion.EaseApple)) }
            launch { delay(120); armUp.animateTo(1f, tween(420, easing = IpxMotion.EaseApple)) }
            launch {
                delay(480)
                triangle.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            }
            launch { delay(760); word.animateTo(1f, tween(450, easing = IpxMotion.EaseOut)) }
        }
        delay(550)
        content.animateTo(0f, tween(320, easing = IpxMotion.EaseStandard))
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = content.value }
            .background(SlateDeep)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && !finished[0]) {
                    scope.launch {
                        content.animateTo(0f, tween(180))
                        finish()
                    }
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp)) {
            Canvas(Modifier.size(width = 220.dp, height = 220.dp * (BrandMark.HALF_H / BrandMark.HALF_W))) {
                if (!reduced) drawGlow(glow.value)
                drawMark(armDown.value, armUp.value, triangle.value)
            }
            Image(
                painter = painterResource(R.drawable.ic_brand_wordmark),
                contentDescription = "IPXtream",
                modifier = Modifier
                    .height(44.dp)
                    .graphicsLayer {
                        alpha = word.value
                        translationY = (1f - word.value) * 18.dp.toPx()
                    }
            )
        }
    }
}

/** Suspends until [STEADY_FRAMES] consecutive frames arrive less than 40 ms apart (max 1.5 s). */
private suspend fun awaitSteadyFrames() {
    val start = withFrameNanos { it }
    var last = start
    var steady = 0
    while (steady < STEADY_FRAMES && last - start < 1_500_000_000L) {
        val now = withFrameNanos { it }
        steady = if (now - last < 40_000_000L) steady + 1 else 0
        last = now
    }
}

private const val STEADY_FRAMES = 6

private fun DrawScope.drawGlow(t: Float) {
    if (t <= 0f) return
    val r = size.width * 0.95f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Accent.copy(alpha = 0.28f * t),
            1f to Accent.copy(alpha = 0f),
            center = Offset(size.width * 0.38f, size.height / 2f),
            radius = r
        ),
        radius = r,
        center = Offset(size.width * 0.38f, size.height / 2f)
    )
}

/** Draws the mark: [down]/[up] reveal each arm along its direction, [tri] scales the triangle in. */
private fun DrawScope.drawMark(down: Float, up: Float, tri: Float) {
    val s = size.width / (2f * BrandMark.HALF_W)
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun path(points: List<Pair<Float, Float>>) = Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(cx + x * s, cy + y * s) else lineTo(cx + x * s, cy + y * s) }
        close()
    }
    // The top-left → bottom-right arm grows from the left; the other one from the right, so they cross.
    if (down > 0f) clipRect(right = size.width * down) { drawPath(path(BrandMark.armDown), Accent) }
    if (up > 0f) clipRect(left = size.width * (1f - up)) { drawPath(path(BrandMark.armUp), Accent) }
    if (tri > 0f) {
        val (tx, ty) = BrandMark.triangleCentre
        val p = path(BrandMark.triangle)
        withTransform({ scale(tri, tri, Offset(cx + tx * s, cy + ty * s)) }) {
            drawPath(p, ChipFocused.copy(alpha = tri.coerceIn(0f, 1f)))
            drawPath(p, ChipFocused.copy(alpha = tri.coerceIn(0f, 1f)), style = Stroke(width = 2f * BrandMark.TRIANGLE_RADIUS * s, join = StrokeJoin.Round))
        }
    }
}
