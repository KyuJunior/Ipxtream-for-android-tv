package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one TV grid for posters and channel cards (Movies, Series, Live, What's New, 4K).
 *
 * - The grid itself never takes focus (TV scroll containers are focusable, which left focus
 *   invisible and made OK open the first item).
 * - D-Pad toward the side menu from the first column, and Up from the first row, use the
 *   grid's real column/row from layout, not a guessed column count.
 * - [firstItemFocusRequester] sits on the last item focused (else the first), so coming back
 *   from the side menu, the category chips, details or the player lands where the user was.
 *   It only ever sits on a card that is composed: a lazy grid drops cards scrolled out of
 *   view, and a requester left on one of those makes every jump into the grid do nothing.
 *
 * [itemContent] gets the modifier to apply to its card and `open`, to call before opening the item.
 */
@Composable
fun <T> PosterGrid(
    items:                   List<T>,
    id:                      (T) -> String,
    columns:                 GridCells,
    firstItemFocusRequester: FocusRequester,
    modifier:                Modifier = Modifier,
    state:                   LazyGridState = rememberLazyGridState(),
    // Index in the default key keeps duplicate portal IDs from crashing the grid.
    key:                     (index: Int, item: T) -> Any = { index, item -> "${id(item)}#$index" },
    sideNavFocusRequester:   FocusRequester? = null,
    upFocusRequester:        FocusRequester? = null,
    onItemFocused:           (T) -> Unit = {},
    contentPadding:          PaddingValues = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
    spacingH:                Dp = 18.dp,
    spacingV:                Dp = 24.dp,
    itemContent:             @Composable (item: T, modifier: Modifier, open: () -> Unit) -> Unit
) {
    var lastFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentItems by rememberUpdatedState(items)
    val currentId by rememberUpdatedState(id)
    // Recomputed on scroll, but only changes (and recomposes two cards) when the anchor moves.
    val anchorIndex by remember(state) {
        derivedStateOf {
            val preferred = lastFocusedId?.let { focused -> currentItems.indexOfFirst { currentId(it) == focused } } ?: -1
            focusAnchorIndex(
                preferred = preferred,
                visible   = state.layoutInfo.visibleItemsInfo.map { it.index },
                count     = currentItems.size
            )
        }
    }

    LazyVerticalGrid(
        columns               = columns,
        state                 = state,
        contentPadding        = contentPadding,
        verticalArrangement   = Arrangement.spacedBy(spacingV),
        horizontalArrangement = Arrangement.spacedBy(spacingH),
        modifier              = modifier.focusProperties { canFocus = false }
    ) {
        itemsIndexed(items, key = key) { index, item ->
            val itemId = id(item)
            val isAnchor by remember(index) { derivedStateOf { anchorIndex == index } }
            // Same modifier shape on every card, so moving the anchor onto the focused card
            // only swaps the requester instead of rebuilding its focus node.
            val ownRequester = remember { FocusRequester() }
            val cardModifier = Modifier
                .onFocusChanged {
                    if (it.isFocused) {
                        lastFocusedId = itemId
                        onItemFocused(item)
                    }
                }
                .focusRequester(if (isAnchor) firstItemFocusRequester else ownRequester)
                .focusProperties {
                    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    if (sideNavFocusRequester != null && info?.column == 0) start = sideNavFocusRequester
                    if (upFocusRequester != null && info?.row == 0) up = upFocusRequester
                }
            itemContent(item, cardModifier) { lastFocusedId = itemId }
        }
    }
}

/**
 * Which card holds a grid's "return focus here" requester: the [preferred] one (last focused)
 * while it is composed, else the first [visible] card, else the first card. -1 for an empty grid.
 */
internal fun focusAnchorIndex(preferred: Int, visible: List<Int>, count: Int): Int = when {
    count <= 0                            -> -1
    preferred in visible                  -> preferred
    visible.any { it in 0 until count }   -> visible.first { it in 0 until count }
    preferred in 0 until count            -> preferred
    else                                  -> 0
}
