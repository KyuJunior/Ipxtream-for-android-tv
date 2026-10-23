package com.ipxtream.tv.ui.intro

import kotlin.math.sqrt

/**
 * Geometry of the IPXtream mark, in the same unit space as `الهوية البصرية/_source/build-brand.js`:
 * a wide slab X spanning x ∈ [-50, 50], y ∈ [-37, 37] (y grows downwards) whose left counter holds a
 * play triangle pointing at the crossing. Pure Kotlin so the intro can animate each part separately.
 */
object BrandMark {
    const val HALF_W = 50f
    const val HALF_H = 37f
    private const val BAR = 16.5f
    private const val GAP = 3.4f

    /** Corner radius of the play triangle; draw it with a round-joined stroke of twice this width. */
    const val TRIANGLE_RADIUS = 2.4f

    private const val SLOPE = HALF_H / HALF_W
    private val K = sqrt(1f + SLOPE * SLOPE)
    private val HV = BAR / 2f * K              // vertical half-thickness of an arm
    private val PX = (HALF_H - HV) / SLOPE     // where an arm edge meets the top/bottom edge

    /** Arm from the top-left corner to the bottom-right corner, clipped to the mark's box. */
    val armDown: List<Pair<Float, Float>> = listOf(
        -HALF_W to -HALF_H, -PX to -HALF_H, HALF_W to HALF_H - HV,
        HALF_W to HALF_H, PX to HALF_H, -HALF_W to -HALF_H + HV,
    )

    /** Arm from the bottom-left corner to the top-right corner (mirror of [armDown]). */
    val armUp: List<Pair<Float, Float>> = armDown.map { (x, y) -> x to -y }

    /** Play triangle (stroke centre line), already inset so the rounded stroke keeps [GAP] to the arms. */
    val triangle: List<Pair<Float, Float>> = run {
        val c = HV + (GAP + TRIANGLE_RADIUS) * K
        val left = -HALF_W + TRIANGLE_RADIUS
        listOf(left to SLOPE * left + c, -c / SLOPE to 0f, left to -(SLOPE * left + c))
    }

    /** Centroid of [triangle]; the intro scales the triangle around this point. */
    val triangleCentre: Pair<Float, Float> = triangle.fold(0f to 0f) { (sx, sy), (x, y) -> sx + x / 3f to sy + y / 3f }
}
