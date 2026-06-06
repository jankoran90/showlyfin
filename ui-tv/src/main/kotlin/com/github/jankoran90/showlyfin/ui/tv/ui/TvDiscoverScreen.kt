package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDiscoverScreen(
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // DiscoverViewModel.init() už načte výchozí tab+filter — žádný explicitní load potřeba.

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        when {
            uiState.isLoading && uiState.items.isEmpty() -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.error != null -> Text(
                text = uiState.error!!,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    item {
                        Column(Modifier.padding(start = 64.dp, end = 64.dp)) {
                            Text("Discover", color = Color.White, style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Trending, populární a doporučené tituly z Trakt",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    item {
                        TvDiscoverRow(
                            title = "Trending Filmy",
                            items = uiState.items.filter { it.type.name == "MOVIE" },
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvDiscoverRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
) {
    Column {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.traktId }) { item ->
                TvDiscoverCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}
