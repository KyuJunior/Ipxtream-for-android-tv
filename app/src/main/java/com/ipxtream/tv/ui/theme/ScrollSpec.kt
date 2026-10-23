package com.ipxtream.tv.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * How lists and grids scroll when D-Pad focus moves inside them.
 *
 * On TV devices Compose's default keeps the focused item pinned at 30% of the row, so every
 * Left/Right press slides the whole row. This scrolls only when the focused item would end
 * up within [edgePx] of an edge (or off screen), and then just far enough, so the row stays
 * still while focus walks across visible posters.
 */
@OptIn(ExperimentalFoundationApi::class)
class EdgeBringIntoViewSpec(private val edgePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Keep a margin for the focus scale and a peek of the next item, unless the item can't fit.
        val margin = edgePx.coerceAtMost(((containerSize - size) / 2f).coerceAtLeast(0f))
        val start = margin
        val end = containerSize - margin
        val trailing = offset + size
        return when {
            offset >= start && trailing <= end -> 0f   // already comfortably visible: don't move
            offset < start                     -> offset - start
            else                               -> trailing - end
        }
    }
}
