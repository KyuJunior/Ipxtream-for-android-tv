package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.data.model.SeriesItem
import com.ipxtream.tv.data.model.StreamItem
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeSearchResultsView(
    searchQuery: String,
    isSearching: Boolean,
    movies: List<StreamItem>,
    series: List<SeriesItem>,
    totalMoviesCount: Int,
    totalSeriesCount: Int,
    totalSearchPages: Int,
    currentPage: Int,
    hasPrevPage: Boolean,
    hasNextPage: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onStreamSelected: (StreamItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    firstCardFocusRequester: FocusRequester,
    searchBarFocusRequester: FocusRequester,
    sideNavFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    if (isSearching) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Searching \"$searchQuery\"…",
                style = IpxTypography.TitleMedium,
                color = TextSecondary
            )
        }
        return
    }

    if (movies.isEmpty() && series.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔍 No movies or TV series matched \"$searchQuery\"",
                style = IpxTypography.TitleMedium,
                color = TextSecondary
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Results title header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Search Results for \"$searchQuery\"",
                    style = IpxTypography.TitleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "$totalMoviesCount movies • $totalSeriesCount series",
                    style = IpxTypography.BodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Movies row
        if (movies.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Movies ($totalMoviesCount)",
                        style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(movies) { index, movie ->
                            VodPosterCard(
                                stream = movie,
                                onClick = { onStreamSelected(movie) },
                                modifier = Modifier
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstCardFocusRequester)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }

        // TV Series row
        if (series.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "TV Series ($totalSeriesCount)",
                        style = IpxTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(series) { index, show ->
                            SeriesPosterCard(
                                series = show,
                                onClick = { onSeriesSelected(show) },
                                modifier = Modifier
                                    .then(
                                        if (index == 0 && movies.isEmpty()) Modifier.focusRequester(firstCardFocusRequester)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Pagination Bar
        if (totalSearchPages > 1) {
            item {
                PaginationBar(
                    hasPrevPage = hasPrevPage,
                    hasNextPage = hasNextPage,
                    currentPage = currentPage,
                    totalPages  = totalSearchPages,
                    onPrevPage  = onPrevPage,
                    onNextPage  = onNextPage
                )
            }
        }
    }
}
