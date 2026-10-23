package com.ipxtream.tv

import com.ipxtream.tv.ui.dashboard.components.focusAnchorIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which poster holds the grid's "return focus here" requester (see PosterGrid). */
class PosterGridAnchorTest {

    @Test fun lastFocusedWinsWhileComposed() {
        assertEquals(13, focusAnchorIndex(preferred = 13, visible = (6..14).toList(), count = 15))
    }

    // The original bug: the requester stayed on item 0 after the grid scrolled it away, so
    // jumping back from the side menu or the category chips focused nothing.
    @Test fun scrolledAwayFallsBackToFirstVisible() {
        assertEquals(6, focusAnchorIndex(preferred = -1, visible = (6..14).toList(), count = 15))
        assertEquals(6, focusAnchorIndex(preferred = 0, visible = (6..14).toList(), count = 15))
    }

    @Test fun beforeFirstLayoutUsesPreferredOrFirst() {
        assertEquals(4, focusAnchorIndex(preferred = 4, visible = emptyList(), count = 15))
        assertEquals(0, focusAnchorIndex(preferred = -1, visible = emptyList(), count = 15))
    }

    @Test fun listShrankUnderTheAnchor() {
        // Category switch: old layout info still lists indices past the new end.
        assertEquals(2, focusAnchorIndex(preferred = -1, visible = listOf(20, 2, 3), count = 5))
        assertEquals(0, focusAnchorIndex(preferred = 9, visible = listOf(20, 21), count = 5))
    }

    @Test fun emptyGridHasNoAnchor() {
        assertEquals(-1, focusAnchorIndex(preferred = 3, visible = listOf(0, 1), count = 0))
    }
}
