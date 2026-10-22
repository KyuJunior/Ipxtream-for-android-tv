package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * The one TV grid for posters and channel cards (Movies, Series, Live, What's New).
 *
 * - The grid itself never takes focus (TV scroll containers are focusable, which left focus
 *   invisible and made OK open the first item).
 * - D-Pad toward the side menu from the first column, and Up from the first row, use the
 *   grid's real column/row from layout, not a guessed column count.
 * - [firstItemFocusRequester] sits on the last item opened (else the first), so closing
 *   details or the player lands back where the user was.
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
    sideNavFocusRequester:   FocusRequester? = null,
    upFocusRequester:        FocusRequester? = null,
    onItemFocused:           (T) -> Unit = {},
    contentPadding:          PaddingValues = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
    spacingH:                Dp = 18.dp,
    spacingV:                Dp = 24.dp,
    itemContent:             @Composable (item: T, modifier: Modifier, open: () -> Unit) -> Unit
) {
    val state = rememberLazyGridState()
    var lastOpenedId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreId = lastOpenedId?.takeIf { opened -> items.any { id(it) == opened } }

    LazyVerticalGrid(
        columns               = columns,
        state                 = state,
        contentPadding        = contentPadding,
        verticalArrangement   = Arrangement.spacedBy(spacingV),
        horizontalArrangement = Arrangement.spacedBy(spacingH),
        modifier              = modifier.focusProperties { canFocus = false }
    ) {
        // Index in the key keeps duplicate portal IDs from crashing the grid.
        itemsIndexed(items, key = { index, item -> "${id(item)}#$index" }) { index, item ->
            val itemId = id(item)
            val anchor = if (restoreId != null) itemId == restoreId else index == 0
            val cardModifier = Modifier
                .onFocusChanged { if (it.isFocused) onItemFocused(item) }
                .then(if (anchor) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                .focusProperties {
                    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    if (sideNavFocusRequester != null && info?.column == 0) start = sideNavFocusRequester
                    if (upFocusRequester != null && info?.row == 0) up = upFocusRequester
                }
            itemContent(item, cardModifier) { lastOpenedId = itemId }
        }
    }
}
