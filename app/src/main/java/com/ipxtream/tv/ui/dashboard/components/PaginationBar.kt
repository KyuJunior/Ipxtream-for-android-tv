package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * Horizontal navigation bar with Previous / Next buttons and page indicator.
 * Only renders when [totalPages] > 1 to avoid unnecessary UI clutter.
 * Fully D-pad navigable - each button is individually focusable.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PaginationBar(
    hasPrevPage: Boolean,
    hasNextPage: Boolean,
    currentPage: Int,
    totalPages:  Int,
    onPrevPage:  () -> Unit,
    onNextPage:  () -> Unit,
    modifier:    Modifier = Modifier
) {
    if (totalPages <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // -- Previous Button --
        Button(
            onClick  = onPrevPage,
            enabled  = hasPrevPage,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
            Text("Previous")
        }

        // -- Page indicator --
        Text(
            text  = "Page ${currentPage + 1} of $totalPages",
            style = IpxTypography.BodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // -- Next Button --
        Button(
            onClick  = onNextPage,
            enabled  = hasNextPage,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text("Next")
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(start = 4.dp)
            )
        }
    }
}
