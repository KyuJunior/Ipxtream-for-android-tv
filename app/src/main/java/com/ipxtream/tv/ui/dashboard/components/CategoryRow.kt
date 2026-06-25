package com.ipxtream.tv.ui.dashboard.components

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

private val ChipShape = RoundedCornerShape(50)

/**
 * Horizontal scrollable row of category filter chips.
 *
 * The first chip is always "All" (selectedCategoryId == null).
 * When a chip is selected, it fills with [AccentCyan] and the text turns dark.
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
    categories:          List<Category>,
    selectedCategoryId:  String?,
    onCategorySelected:  (String?) -> Unit,
    modifier:            Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyRow(
        state           = listState,
        modifier        = modifier.height(52.dp),
        contentPadding  = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        item {
            CategoryChip(
                label      = "All",
                isSelected = selectedCategoryId == null,
                onClick    = { onCategorySelected(null) }
            )
        }

        // Per-category chips
        items(categories, key = { it.categoryId }) { category ->
            CategoryChip(
                label      = category.categoryName,
                isSelected = category.categoryId == selectedCategoryId,
                onClick    = { onCategorySelected(category.categoryId) }
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
    FilterChip(
        selected = isSelected,
        onClick  = onClick,
        modifier = modifier,
        shape    = FilterChipDefaults.shape(shape = ChipShape),
        colors   = FilterChipDefaults.colors(
            containerColor                = Color.Transparent,
            contentColor                  = TextSecondary,
            selectedContainerColor        = Color(0x22FFFFFF), // Subtly glass when selected
            selectedContentColor          = Color.White,
            focusedContainerColor         = Color.White,
            focusedContentColor           = Color.Black,
            focusedSelectedContainerColor = Color.White,
            focusedSelectedContentColor   = Color.Black
        ),
        border  = FilterChipDefaults.border(
            border                = Border(BorderStroke(1.dp, Color.Transparent), shape = ChipShape),
            focusedBorder         = Border.None,
            selectedBorder        = Border(BorderStroke(1.dp, Color(0x44FFFFFF)), shape = ChipShape),
            focusedSelectedBorder = Border.None
        ),
        scale   = FilterChipDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text       = label,
            style      = com.ipxtream.tv.ui.theme.IpxTypography.BodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier   = Modifier.padding(horizontal = 8.dp)
        )
    }
}
