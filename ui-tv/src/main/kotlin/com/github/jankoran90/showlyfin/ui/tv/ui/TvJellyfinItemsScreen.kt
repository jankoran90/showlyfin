package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinItem
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinLibraryItemsViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinSort
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinTypeFilter

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvJellyfinItemsScreen(
    libraryId: String,
    libraryName: String,
    collectionType: String?,
    parentItemType: String?,
    onDrillIn: (JellyfinItem) -> Unit,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JellyfinLibraryItemsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(libraryId, collectionType, parentItemType) {
        viewModel.load(libraryId, libraryName, collectionType, parentItemType)
    }

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        Column(Modifier.fillMaxSize()) {
            Text(
                uiState.libraryName.ifBlank { libraryName },
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 64.dp, end = 64.dp, top = 32.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (!uiState.isBoxSetContext) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JellyfinTypeFilter.entries.forEach { f ->
                        TvChip(f.label, uiState.typeFilter == f) { viewModel.selectTypeFilter(f) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JellyfinSort.entries.forEach { s ->
                        TvChip(s.label, uiState.sort == s) { viewModel.selectSort(s) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    uiState.error != null -> Text(
                        text = uiState.error!!,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    uiState.items.isEmpty() -> Text(
                        text = "Knihovna je prázdná",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(start = 64.dp, end = 64.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            TvJellyfinItemCard(
                                item = item,
                                onClick = {
                                    if (item.isFolder || item.type.equals("BOX_SET", ignoreCase = true)) {
                                        onDrillIn(item)
                                    } else {
                                        onPlay(item.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvJellyfinItemCard(item: JellyfinItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1.0f, tween(180), label = "jf-scale")
    val border = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .border(3.dp, border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomStart)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
            Text(
                text = item.name,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp),
            )
            item.progressPct?.takeIf { it > 0 }?.let { pct ->
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}
