package com.ipxtream.tv.ui.dashboard.components

import com.ipxtream.tv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Text
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.ui.theme.TextSecondary
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.Accent

private val ChipShape = RoundedCornerShape(50)

/**
 * Horizontal scrollable row of category filter chips.
 *
 * The first chip is always "All" (selectedCategoryId == null).
 * When a chip is selected, it fills with [Accent] and the text turns dark.
 * When focused (but not selected), the chip shows a cyan border + slight scale.
 *
 * ### D-Pad:
 * - LEFT / RIGHT scroll through chips naturally via LazyRow.
 * - DOWN moves focus into the content grid (geometrically below).
 * - UP from the grid's first row returns here (geometrically above).
 *
 * @param categories         Full category list for the current section.
 * @param selectedCategoryId Currently active filter. Null = All.
 * @param onCategorySelected Callback when the user selects a chip.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryRow(
    categories:              List<Category>,
    selectedCategoryId:      String?,
    onCategorySelected:      (String?) -> Unit,
    firstChipFocusRequester: FocusRequester? = null,
    sideNavFocusRequester:   FocusRequester? = null,
    gridFocusRequester:      FocusRequester? = null,
    searchBarFocusRequester: FocusRequester? = null,
    modifier:                Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val currentCategories by rememberUpdatedState(categories)
    val currentSelected by rememberUpdatedState(selectedCategoryId)
    // The return requester sits on the selected chip, else the first visible one: the row drops
    // chips scrolled out of view, and a requester left on "All" then makes Up from the grid do nothing.
    val anchorIndex by remember(listState) {
        derivedStateOf {
            val selected = currentSelected?.let { id -> currentCategories.indexOfFirst { it.categoryId == id } + 1 } ?: 0
            focusAnchorIndex(
                preferred = selected,
                visible   = listState.layoutInfo.visibleItemsInfo.map { it.index },
                count     = currentCategories.size + 1
            )
        }
    }
    val chipRequester: @Composable (Int) -> Modifier = { index ->
        val own = remember { FocusRequester() }
        val isAnchor by remember(index) { derivedStateOf { anchorIndex == index } }
        Modifier.focusRequester(if (isAnchor && firstChipFocusRequester != null) firstChipFocusRequester else own)
    }

    LazyRow(
        state           = listState,
        modifier        = modifier.height(58.dp).focusProperties { canFocus = false },
        contentPadding  = PaddingValues(horizontal = com.ipxtream.tv.ui.theme.IpxSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // "All" chip
        item {
            CategoryChip(
                label      = stringResource(R.string.category_all),
                isSelected = selectedCategoryId == null,
                onClick    = { onCategorySelected(null) },
                modifier   = Modifier
                    .then(chipRequester(0))
                    .focusProperties {
                        if (sideNavFocusRequester != null) start = sideNavFocusRequester
                        if (gridFocusRequester != null) down = gridFocusRequester
                        if (searchBarFocusRequester != null) up = searchBarFocusRequester
                    }
            )
        }

        // Per-category chips
        itemsIndexed(categories, key = { index, category -> "${category.categoryId}_$index" }) { index, category ->
            CategoryChip(
                label      = category.categoryName,
                isSelected = category.categoryId == selectedCategoryId,
                onClick    = { onCategorySelected(category.categoryId) },
                modifier   = chipRequester(index + 1).focusProperties {
                    if (gridFocusRequester != null) down = gridFocusRequester
                    if (searchBarFocusRequester != null) up = searchBarFocusRequester
                }
            )
        }
    }
}

/**
 * A single focusable/selectable category chip.
 *
 * Uses **TV Material `FilterChip`**, which handles:
 *  - Selected fill colour via [FilterChipDefaults.colors]
 *  - Focus border via [FilterChipDefaults.border]
 *  - Focus scale via [FilterChipDefaults.scale]
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryChip(
    label:      String,
    isSelected: Boolean,
    onClick:    () -> Unit,
    modifier:   Modifier = Modifier
) {
    // Apple: every chip is a filled capsule; the selected one is the lighter segment, no rim.
    val apple = com.ipxtream.tv.ui.theme.isAppleDesign
    FilterChip(
        selected = isSelected,
        onClick  = onClick,
        modifier = modifier,
        shape    = FilterChipDefaults.shape(shape = ChipShape),
        colors   = FilterChipDefaults.colors(
            containerColor                = if (apple) com.ipxtream.tv.ui.theme.AppleFill else Color.Transparent,
            contentColor                  = TextSecondary,
            selectedContainerColor        = if (apple) com.ipxtream.tv.ui.theme.AppleSegmentSelected else Color(0x29FFFFFF), // Frosted glass when selected
            selectedContentColor          = Color.White,
            focusedContainerColor         = ChipFocused,
            focusedContentColor           = TextOnFocus,
            focusedSelectedContainerColor = ChipFocused,
            focusedSelectedContentColor   = TextOnFocus
        ),
        border  = FilterChipDefaults.border(
            border                = Border.None,
            focusedBorder         = Border.None,
            selectedBorder        = if (apple) Border.None else Border(BorderStroke(1.dp, Color(0x33FFFFFF)), shape = ChipShape),
            focusedSelectedBorder = Border.None
        ),
        scale   = FilterChipDefaults.scale(focusedScale = 1.08f)
    ) {
        Text(
            text       = label,
            style      = com.ipxtream.tv.ui.theme.IpxTypography.BodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            modifier   = Modifier.padding(horizontal = 10.dp)
        )
    }
}
