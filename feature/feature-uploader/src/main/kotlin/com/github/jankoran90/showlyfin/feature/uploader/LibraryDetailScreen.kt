package com.github.jankoran90.showlyfin.feature.uploader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryDetailScreen(
    library: String,
    item: LibraryItem,
    onBack: () -> Unit,
    onRemuxClick: (library: String, folder: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(item) { viewModel.init(item) }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onBack() }

    if (uiState.openCollectionPicker) {
        CollectionPickerDialog(
            collections = uiState.collections,
            current = item.userCollection,
            onPick = { col -> viewModel.saveUserCollection(library, col); viewModel.clearCollectionPicker() },
            onClear = { viewModel.saveUserCollection(library, ""); viewModel.clearCollectionPicker() },
            onDismiss = viewModel::clearCollectionPicker,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.item?.title?.ifBlank { uiState.item?.name } ?: item.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (uiState.isApplying || uiState.isLoadingTmdb) CircularProgressIndicator(Modifier.size(20.dp))
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                // Header: poster + info
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    uiState.selectedPosterUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(80.dp, 120.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(uiState.item?.title?.ifBlank { uiState.item?.name } ?: item.title, style = MaterialTheme.typography.titleMedium)
                        uiState.item?.year?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        uiState.tmdbDetail?.let { d ->
                            Text(d.overview.take(120) + if (d.overview.length > 120) "…" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        StatusChips(uiState)
                    }
                }
            }

            // Actions row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.applyChanges(library) }, modifier = Modifier.weight(1f)) { Text("Uložit metadata") }
                    OutlinedButton(onClick = { onRemuxClick(library, uiState.item?.name ?: item.name) }, modifier = Modifier.weight(1f)) { Text("Remux") }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.toggleWatched(library) }, modifier = Modifier.weight(1f)) {
                        Text(if (uiState.item?.watched == true) "Neshlédnuto" else "Shlédnuto")
                    }
                    OutlinedButton(onClick = { viewModel.loadAndPickCollection(library) }, modifier = Modifier.weight(1f)) {
                        Text(uiState.item?.userCollection?.let { "Kolekce: $it" } ?: "Kolekce")
                    }
                }
                uiState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                    LaunchedEffect(uiState.message) { kotlinx.coroutines.delay(3_000L); viewModel.clearMessage() }
                }
                uiState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Artwork pickers
            uiState.tmdbDetail?.let { detail ->
                if (detail.posterOptions.isNotEmpty()) {
                    item {
                        Text("Postery", style = MaterialTheme.typography.labelLarge)
                        LazyRow {
                            items(detail.posterOptions.take(8)) { opt ->
                                val isSelected = opt.url == uiState.selectedPosterUrl
                                Box {
                                    AsyncImage(model = opt.thumb.ifBlank { opt.url }, contentDescription = null,
                                        modifier = Modifier.size(60.dp, 90.dp).padding(4.dp).clip(RoundedCornerShape(4.dp))
                                            .clickable { viewModel.selectArtwork("poster", opt.url) })
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (detail.backdropOptions.isNotEmpty()) {
                    item {
                        Text("Fanarty", style = MaterialTheme.typography.labelLarge)
                        LazyRow {
                            items(detail.backdropOptions.take(6)) { opt ->
                                val isSelected = opt.url == uiState.selectedBackdropUrl
                                Box {
                                    AsyncImage(model = opt.thumb.ifBlank { opt.url }, contentDescription = null,
                                        modifier = Modifier.size(120.dp, 70.dp).padding(4.dp).clip(RoundedCornerShape(4.dp))
                                            .clickable { viewModel.selectArtwork("backdrop", opt.url) })
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // TMDB re-match search
            item {
                Text("Přiřadit jinak (TMDB)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        label = { Text("Hledat") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.search(library, searchQuery) }, enabled = searchQuery.isNotBlank()) { Text("Hledat") }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (uiState.searchResults.isNotEmpty()) {
                items(uiState.searchResults) { candidate ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        candidate.posterUrl?.let {
                            AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(36.dp, 54.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${candidate.title} (${candidate.year})", style = MaterialTheme.typography.bodySmall)
                            Text("TMDB ${candidate.tmdbId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { viewModel.confirmMatch(library, candidate.tmdbId) }) { Text("Přiřadit") }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.deleteFolder(library) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isApplying,
                ) { Text("Smazat složku", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusChips(uiState: LibraryDetailUiState) {
    val chips = buildList {
        if (uiState.item?.hasNfo == true) add("NFO" to Color(0xFF4CAF50))
        if (uiState.item?.hasPoster == true) add("Poster" to Color(0xFF4CAF50))
        if (uiState.item?.hasFanart == true) add("Fanart" to Color(0xFF4CAF50))
        if (uiState.item?.complete == false) add("!" to Color(0xFFFFC107))
        if (uiState.item?.watched == true) add("✓ Shlédnuto" to Color(0xFF2196F3))
    }
    Row { chips.forEach { (text, color) -> Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(end = 4.dp)) } }
}

@Composable
private fun CollectionPickerDialog(
    collections: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kolekce") },
        text = {
            LazyColumn {
                if (!current.isNullOrBlank()) {
                    item { TextButton(onClick = onClear) { Text("Odebrat kolekci") } }
                }
                items(collections) { col ->
                    TextButton(onClick = { onPick(col) }, modifier = Modifier.fillMaxWidth()) {
                        Text(col, color = if (col == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}
