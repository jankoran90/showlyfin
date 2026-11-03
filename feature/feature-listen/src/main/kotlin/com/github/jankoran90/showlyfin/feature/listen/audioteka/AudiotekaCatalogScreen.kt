package com.github.jankoran90.showlyfin.feature.listen.audioteka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenExpressiveTheme

/**
 * Sekce „Katalog" — hledání v celé Audiotéce, výsledky smíchané s naší knihovnou (odznak „V knihovně").
 * Série jsou výsledky vlastního typu (otevřou seznam dílů). Tuk na knihu, kterou máme, otevře naši knihu.
 */
@Composable
fun AudiotekaCatalogScreen(
    onOpen: (AtBook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiotekaSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    ListenExpressiveTheme {
        Column(modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Hledat v Audiotéce — název, autor, interpret") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Smazat")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            when (val r = result) {
                AtLoad.Idle -> Hint("Napiš aspoň dvě písmena. Uvidíš knihy i série z Audioteky a co z nich už máš v knihovně.")
                AtLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                AtLoad.Empty -> Hint("Audioteka nic nenašla.")
                AtLoad.Failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Katalog se nepodařilo načíst.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::retry) { Text("Zkusit znovu") }
                }
                is AtLoad.Ready -> ResultsGrid(r.data, onOpen)
            }
        }
    }
}

@Composable
private fun ResultsGrid(items: List<AtBook>, onOpen: (AtBook) -> Unit) {
    val series = items.filter { it.isSeries }
    val books = items.filterNot { it.isSeries }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (series.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Série") }
            items(series, key = { "s" + it.id }) { AtCoverCard(it, onOpen) }
        }
        if (books.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GroupLabel("Knihy · máš ${books.count { it.inLibrary != null }} z ${books.size}")
            }
            items(books, key = { "b" + it.id }) { AtCoverCard(it, onOpen) }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
