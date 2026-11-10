package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.ui.theme.AppleLens
import com.ipxtream.tv.ui.theme.ChipFocused
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextOnFocus
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.appleGlass
import com.ipxtream.tv.ui.theme.bidi

/**
 * Apple design: the categories of Live / Movies / Series as a list on the start side of the grid
 * (in place of the chip row). OK picks a category; End goes to the grid; Up from the top goes to the tab bar.
 * [anchorRequester] stays on the selected row while it is composed, else the first visible one, so
 * "back to the categories" never names a row that scrolled out of composition.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleCategoryList(
    categories:         List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    anchorRequester:    FocusRequester,
    /** The grid's return requester, or null while the grid has nothing to focus. */
    gridFocusRequester: FocusRequester?,
    upFocusRequester:   FocusRequester,
    modifier:           Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val currentCategories by rememberUpdatedState(categories)
    val currentSelected by rememberUpdatedState(selectedCategoryId)
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
    val rows = remember(categories) { listOf<Category?>(null) + categories }
    val allLabel = stringResource(R.string.category_all)

    Box(modifier.padding(start = 24.dp, top = 8.dp, bottom = 24.dp).appleGlass(RoundedCornerShape(28.dp))) {
        LazyColumn(
            state = listState,
            modifier = Modifier.focusProperties { canFocus = false },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(rows, key = { index, c -> "${c?.categoryId ?: "all"}_$index" }) { index, category ->
                val own = remember { FocusRequester() }
                val isAnchor = anchorIndex == index
                val selected = category?.categoryId == selectedCategoryId
                Surface(
                    onClick  = { onCategorySelected(category?.categoryId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(if (isAnchor) anchorRequester else own)
                        .focusProperties {
                            if (gridFocusRequester != null) end = gridFocusRequester
                            if (index == 0) up = upFocusRequester
                        },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = if (selected) AppleLens else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor          = if (selected) TextPrimary else TextSecondary,
                        focusedContainerColor = ChipFocused,
                        focusedContentColor   = TextOnFocus
                    ),
                    border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.03f)
                ) {
                    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text     = category?.categoryName?.let { bidi(it) } ?: allLabel,
                            style    = IpxTypography.BodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
