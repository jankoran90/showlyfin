package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel

/**
 * Poslechová sekce — knihovny audioknih (chips) + grid obálek s progressem.
 * Vše v Material 3 Expressive tématu (ListenExpressiveTheme). ABS login je v Nastavení.
 */
@Composable
fun ListenScreen(
    onOpenBook: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ListenExpressiveTheme {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                !state.isConfigured -> CenteredMessage(
                    "Poslech zatím není nastaven.\nPřihlas se k Audiobookshelf serveru v Nastavení → Poslech (Audiobookshelf).",
                )

                state.isLoading && state.books.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.books.isEmpty() ->
                    CenteredMessage(state.error!!)

                else -> Column(Modifier.fillMaxSize()) {
                    if (state.libraries.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.libraries.forEach { lib ->
                                FilterChip(
                                    selected = lib.id == state.selectedLibraryId,
                                    onClick = { viewModel.selectLibrary(lib.id) },
                                    label = { Text(lib.name) },
                                )
                            }
                        }
                    }

                    if (state.books.isEmpty() && !state.isLoading) {
                        CenteredMessage("V této knihovně zatím nejsou žádné audioknihy.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.books, key = { it.id }) { book ->
                                AudiobookCard(book = book, onClick = { onOpenBook(book.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Vystředěná zpráva — funguje v Column i Box (vlastní fillMaxSize Box). */
@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
