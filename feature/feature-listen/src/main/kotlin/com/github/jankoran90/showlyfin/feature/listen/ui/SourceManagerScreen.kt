package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.listen.SourceManagerViewModel

/**
 * PRESET (SHW-65): správa sdílených zdrojů Poslechu — z postranního menu nad „Správa".
 * Nahoře hledání podle názvu (YouTube + CZ podcasty) → přidat; dole seznam zdrojů → odebrat.
 * Vše sdílené na serveru; zdroje pak splynou se sekcí Poslech → Podcasty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: SourceManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // TABULA: opuštění správy zdrojů vyčistí hledání → návrat/vstup do zdroje = čisté hledání.
    DisposableEffect(Unit) { onDispose { viewModel.onQueryChange("") } }
    val snackbar = remember { SnackbarHostState() }
    var pendingRemove by remember { mutableStateOf<PodcastSource?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 12.dp, end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Zdroje podcastů",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Najít podcast nebo YouTube kanál podle názvu") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Vymazat")
                            }
                        }
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceManagerViewModel.TypeFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.typeFilter == f,
                            onClick = { viewModel.setTypeFilter(f) },
                            label = { Text(f.label) },
                        )
                    }
                }
            }

            if (state.notConfigured) {
                item {
                    Text(
                        "Pro hledání a přidávání zdrojů se přihlas k serveru v Nastavení.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // ── Výsledky hledání ─────────────────────────────────────────────
            if (state.searching) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.results.isNotEmpty()) {
                item { SectionHeader("Výsledky") }
                items(state.results, key = { "${it.type}:${it.ref}" }) { r ->
                    SourceResultCard(result = r, added = viewModel.isAdded(r), onAdd = { viewModel.add(r) })
                }
            } else if (state.searched && state.query.trim().length >= 2) {
                item {
                    Text(
                        "Nic se nenašlo. Zkus jiný název nebo přepni typ.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // ── Přidané zdroje ───────────────────────────────────────────────
            item { SectionHeader(if (state.sources.isEmpty()) "Vaše zdroje" else "Vaše zdroje · ${state.sources.size}") }
            if (state.sources.isEmpty()) {
                item {
                    Text(
                        "Zatím žádné vlastní zdroje. Najdi podcast nebo kanál nahoře a přidej ho — objeví se v Poslechu → Podcasty u celé rodiny.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                items(state.sources, key = { it.id }) { s ->
                    SourceRow(source = s, onRemove = { pendingRemove = s })
                }
            }
        }
    }

    pendingRemove?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Odebrat zdroj?") },
            text = { Text("${s.title} se odebere u celé rodiny. Stažené ani přehrané se nemaže.") },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(s); pendingRemove = null }) { Text("Odebrat") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Zrušit") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SourceRow(source: PodcastSource, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Thumb(source.thumbnail)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(source.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (source.premium) "Prémiový zdroj rodiny" else typeLabel(source.type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // EXODUS (SHW-67): prémiový zdroj rodiny (NaVýbornou) nelze odebrat → mazání skryté.
        if (!source.premium) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Odebrat", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Thumb(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 64.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)),
    )
}

private fun typeLabel(type: String): String = when (type) {
    "youtube" -> "YouTube kanál"
    "rss" -> "Podcast"
    else -> type
}
