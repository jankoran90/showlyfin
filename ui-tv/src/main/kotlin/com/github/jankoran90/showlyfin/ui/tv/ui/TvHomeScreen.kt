package com.github.jankoran90.showlyfin.ui.tv.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.ui.tv.TvHomeRow
import com.github.jankoran90.showlyfin.ui.tv.TvHomeViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    onItemClick: (itemId: String) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstRowFocus = remember { FocusRequester() }

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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    itemsIndexed(state.rows) { index, row ->
                        TvContentRow(
                            row = row,
                            onItemClick = onItemClick,
                            firstItemFocusRequester = if (index == 0) firstRowFocus else null,
                        )
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
                    modifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester!!) else Modifier,
                )
            }
        }
    }
}
