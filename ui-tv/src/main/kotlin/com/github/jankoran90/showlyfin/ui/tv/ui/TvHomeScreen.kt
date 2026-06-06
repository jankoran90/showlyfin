package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.ui.tv.TvCardSize
import com.github.jankoran90.showlyfin.ui.tv.TvHomeRow
import com.github.jankoran90.showlyfin.ui.tv.TvHomeViewModel

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    onItemClick: (itemId: String) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenLibrary: (libraryId: String, name: String, collectionType: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: TvHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstRowFocus = remember { FocusRequester() }
    var backdropUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.rows) {
        if (state.rows.isNotEmpty()) {
            runCatching { firstRowFocus.requestFocus() }
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            state.isNotConfigured -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Jellyfin není nastaven",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Zadej server URL, uživatele a heslo přímo na TV",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onOpenSetup) {
                        Text("Nastavit Jellyfin")
                    }
                }
            }
            state.error != null -> {
                Text(
                    text = state.error!!,
                    color = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.rows.isEmpty() -> {
                Text(
                    text = "Žádný obsah v knihovně",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    Crossfade(targetState = backdropUrl, label = "tv-home-backdrop") { url ->
                        if (url != null) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.55f,
                                    modifier = Modifier.fillMaxSize().blur(24.dp),
                                )
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF07071A).copy(alpha = 0.4f),
                                                Color(0xFF07071A).copy(alpha = 0.85f),
                                                Color(0xFF07071A),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
                    CompositionLocalProvider(
                        LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            itemsIndexed(state.rows) { index, row ->
                                // Uvnitř řady obnovit default spec → horizontální LazyRow nescrolluje vertikálně
                                CompositionLocalProvider(
                                    LocalBringIntoViewSpec provides defaultBringIntoViewSpec,
                                ) {
                                    TvContentRow(
                                        row = row,
                                        onItemClick = onItemClick,
                                        cardSize = state.cardSize,
                                        onItemFocused = { item -> backdropUrl = item.backdropUrl ?: item.imageUrl },
                                        onOpenLibrary = onOpenLibrary,
                                        firstItemFocusRequester = if (index == 0) firstRowFocus else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvContentRow(
    row: TvHomeRow,
    onItemClick: (String) -> Unit,
    cardSize: TvCardSize,
    onItemFocused: (com.github.jankoran90.showlyfin.ui.tv.TvJellyfinItem) -> Unit = {},
    onOpenLibrary: (libraryId: String, name: String, collectionType: String?) -> Unit = { _, _, _ -> },
    firstItemFocusRequester: FocusRequester? = null,
) {
    Column {
        Text(
            text = row.title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(row.items) { index, item ->
                val isFirst = index == 0 && firstItemFocusRequester != null
                TvItemCard(
                    item = item,
                    onClick = { onItemClick(item.id) },
                    cardSize = cardSize,
                    inLibrary = false, // Home = Jellyfin obsah → badge nemá smysl (jen u Trakt výsledků)
                    onFocused = { onItemFocused(item) },
                    modifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester!!) else Modifier,
                )
            }
            // Per-library řada → poslední karta "Do knihovny" otevře celou knihovnu
            val libId = row.libraryId
            if (libId != null) {
                item {
                    Box(
                        modifier = Modifier
                            .width(cardSize.widthDp.dp)
                            .aspectRatio(2f / 3f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(onClick = { onOpenLibrary(libId, row.libraryName ?: row.title, row.collectionType) }) {
                            Text("Do knihovny →")
                        }
                    }
                }
            }
        }
    }
}
