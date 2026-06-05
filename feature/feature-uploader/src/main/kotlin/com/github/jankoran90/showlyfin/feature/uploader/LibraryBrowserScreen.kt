package com.github.jankoran90.showlyfin.feature.uploader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBrowserScreen(
    onBack: () -> Unit,
    onItemClick: (library: String, item: LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadLibraries() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.selectedLibrary ?: "Knihovny") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedLibrary != null) viewModel.selectLibrary("") else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.selectedLibrary != null -> LibraryItemList(
                library = uiState.selectedLibrary!!,
                items = uiState.items,
                onItemClick = { item -> onItemClick(uiState.selectedLibrary!!, item) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LibraryList(
                libraries = uiState.libraries,
                onSelect = viewModel::selectLibrary,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        uiState.error?.let {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.BottomCenter) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun LibraryList(libraries: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        items(libraries) { lib ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(lib) }) {
                Text(lib, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun LibraryItemList(library: String, items: List<LibraryItem>, onItemClick: (LibraryItem) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(horizontal = 8.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        items(items) { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onItemClick(item) }) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.artworkPosterUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(40.dp, 60.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.title.ifBlank { item.name }, style = MaterialTheme.typography.bodyMedium)
                        item.year?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row {
                            if (item.hasNfo) Badge("NFO")
                            if (item.hasPoster) Badge("Poster")
                            if (!item.complete) Badge("!", color = Color(0xFFFFC107))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Badge(text: String, color: Color = Color(0xFF4CAF50)) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.padding(end = 4.dp),
    )
}
