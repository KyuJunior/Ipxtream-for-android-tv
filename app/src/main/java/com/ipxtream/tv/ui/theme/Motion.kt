package com.ipxtream.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Motion tokens shared by every screen so movement feels like one system.
 *
 * Curves and durations follow the tvOS feel: focus accelerates briskly, then
 * decelerates into place with a soft settle instead of a linear stop.
 */
object IpxMotion {
    /** Signature deceleration curve — fast out, long gentle settle. */
    val EaseApple    = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    /** General-purpose ease-in-out. */
    val EaseStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** Decelerate only — for things arriving on screen. */
    val EaseOut      = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    const val Fast     = 200
    const val Standard = 350
    const val Slow     = 600
    const val Hero     = 900

    /** Focus grow: quick with a barely perceptible settle. */
    fun <T> focusSpring(): SpringSpec<T> = spring(dampingRatio = 0.78f, stiffness = 380f)
    /** Focus release: critically damped so items never wobble on the way out. */
    fun <T> releaseSpring(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 520f)
    /** Lean/tilt recovery after a D-Pad move — slightly springy, like inertia. */
    fun <T> leanSpring(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 170f)
}

/** Layout rhythm shared across screens. */
object IpxSpacing {
    /** Left/right safe margin for page content, headers and rows. */
    val Gutter = 32.dp
    /** Vertical gap between content shelves. */
    val RowGap = 20.dp
}
