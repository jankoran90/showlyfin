package com.github.jankoran90.showlyfin.feature.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.WatchedBadge
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRow
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel

/**
 * Podsekce „Knihovna" v Hlavní — pohled z Traktu na Jellyfin knihovnu, řazeno do řad
 * po zdrojových knihovnách. Klik na kartu: má-li TMDB match → bohatý Trakt/TMDB detail
 * ([onItemClick] s nenulovým MediaItem), jinak proklik na Jellyfin kartu (MediaItem=null).
 */
@Composable
fun LibraryRowsScreen(
    onItemClick: (media: MediaItem?, jellyfinId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryRowsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
    ) {
        when {
            state.isLoading && state.rows.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null ->
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            state.rows.isEmpty() ->
                Text(
                    text = "Knihovna je prázdná",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.rows, key = { it.libraryId }) { row ->
                    LibraryRowSection(row = row, onItemClick = onItemClick, onOpenLibrary = onOpenLibrary)
                }
            }
        }
    }
}

@Composable
private fun LibraryRowSection(
    row: LibraryRow,
    onItemClick: (media: MediaItem?, jellyfinId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.libraryName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onOpenLibrary(row.libraryId, row.libraryName, row.collectionType) }) {
                Text("Vše", color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Otevřít knihovnu",
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(row.items, key = { it.jellyfinId }) { item ->
                LibraryRowCard(
                    item = item,
                    onClick = { onItemClick(item.mediaItem, item.jellyfinId) },
                )
            }
        }
    }
}

@Composable
private fun LibraryRowCard(
    item: LibraryRowItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.watched) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.year?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it.toString(),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item.progressPct?.takeIf { it > 0 }?.let { pct ->
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}
