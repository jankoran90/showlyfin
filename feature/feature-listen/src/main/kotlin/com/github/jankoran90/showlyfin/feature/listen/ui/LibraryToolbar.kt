package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.BatchDownloadProgress

/**
 * AMPHORA F2 / KN1 — nad knihami JEDEN řádek (user 2026-11-21 20:58: „strašně velká hlavička… obsah začíná
 * pozdě“, „dej to do jednoho přepínače nebo listu filtru“): knihovna a stav jako rozbalovací čipy
 * („dospělý ▾“, „Vše ▾“), řazení, mřížka/seznam a ⋮ se „Stáhnout vše“. Běžící dávka = tenký pruh pod řádkem.
 */
@Composable
internal fun LibraryToolbar(
    /** Filtr jako (id, popisek); id prvního = „vše“ (nezvýrazněný). Sdílené knihami i pořady (user 2026-11-23). */
    filterOptions: List<Pair<String, String>>,
    selectedFilter: String,
    onFilter: (String) -> Unit,
    sortOptions: List<Pair<String, String>>,
    selectedSort: String,
    onSort: (String) -> Unit,
    listMode: Boolean,
    rowSize: RowSize,
    /** Mřížka (false) nebo seznam s velikostí řádku. */
    onView: (listMode: Boolean, rowSize: RowSize) -> Unit,
    libraries: List<Pair<String, String>> = emptyList(),
    selectedLibraryId: String? = null,
    onSelectLibrary: (String) -> Unit = {},
    batchProgress: BatchDownloadProgress? = null,
    onDownloadAll: (() -> Unit)? = null,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var viewOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (libraries.size > 1) {
                    DropdownChip(
                        label = libraries.firstOrNull { it.first == selectedLibraryId }?.second ?: "Knihovna",
                        options = libraries,
                        selected = selectedLibraryId,
                        onSelect = onSelectLibrary,
                    )
                }
                DropdownChip(
                    label = filterOptions.firstOrNull { it.first == selectedFilter }?.second ?: filterOptions.first().second,
                    options = filterOptions,
                    selected = selectedFilter,
                    onSelect = onFilter,
                    highlighted = selectedFilter != filterOptions.first().first,
                )
            }
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Řazení: ${sortOptions.firstOrNull { it.first == selectedSort }?.second.orEmpty()}",
                    )
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    sortOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { if (id == selectedSort) Icon(Icons.Rounded.Check, contentDescription = null) },
                            onClick = { sortOpen = false; onSort(id) },
                        )
                    }
                }
            }
            // User 2026-11-23: „list view dej možnost volit velikost řádku/náhledu“ → mřížka nebo seznam ve 3 velikostech.
            Box {
                IconButton(onClick = { viewOpen = true }) {
                    Icon(
                        if (listMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = "Zobrazení",
                    )
                }
                DropdownMenu(expanded = viewOpen, onDismissRequest = { viewOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Mřížka") },
                        leadingIcon = { if (!listMode) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = { viewOpen = false; onView(false, rowSize) },
                    )
                    RowSize.entries.forEach { size ->
                        DropdownMenuItem(
                            text = { Text("Seznam – ${size.label.lowercase()}") },
                            leadingIcon = { if (listMode && size == rowSize) Icon(Icons.Rounded.Check, contentDescription = null) },
                            onClick = { viewOpen = false; onView(true, size) },
                        )
                    }
                }
            }
            if (onDownloadAll != null || batchProgress != null) {
                Box {
                    IconButton(onClick = { moreOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Další volby")
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        if (batchProgress != null) {
                            DropdownMenuItem(
                                text = { Text("Stahuji ${batchProgress.completed}/${batchProgress.total}…") },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                enabled = false,
                                onClick = {},
                            )
                        } else if (onDownloadAll != null) {
                            DropdownMenuItem(
                                text = { Text("Stáhnout vše") },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                onClick = { moreOpen = false; onDownloadAll() },
                            )
                        }
                    }
                }
            }
        }
        if (batchProgress != null && batchProgress.total > 0) {
            LinearProgressIndicator(
                progress = { batchProgress.completed.toFloat() / batchProgress.total },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            )
        }
    }
}

/** Čip s rozbalovací nabídkou („dospělý ▾“) — jeden přepínač místo řady čipů. */
@Composable
private fun DropdownChip(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    highlighted: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = highlighted,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { if (id == selected) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { open = false; onSelect(id) },
                )
            }
        }
    }
}

/** AMPHORA F2 / KN1 + AK1 — knihy jako seznam (jednotný řádek [SlovoMediaRow]) s ⋮ na každém řádku. */
@Composable
internal fun BookShelfList(
    items: List<BookShelfItem>,
    playingId: String?,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (BookShelfItem.SeriesGroup) -> Unit,
    onBookActions: (Audiobook) -> Unit,
    rowSize: RowSize = RowSize.MEDIUM,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.miniPlayerReserve)) {
        items(items, key = { it.itemKey }) { item ->
            when (item) {
                is BookShelfItem.Standalone -> {
                    val b = item.book
                    SlovoMediaRow(
                        model = bookModel(b, playing = b.id == playingId),
                        onClick = { onOpenBook(b.id) },
                        onLongClick = { onBookActions(b) },
                        coverSize = rowSize.cover,
                        trailing = {
                            IconButton(onClick = { onBookActions(b) }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Akce") }
                        },
                    )
                }
                is BookShelfItem.SeriesGroup -> SlovoMediaRow(
                    model = SlovoMediaModel(
                        title = item.seriesName,
                        subtitle = "série · ${item.books.size} " + if (item.books.size in 2..4) "díly" else "dílů",
                        coverUrl = item.books.firstOrNull { it.coverUrl != null }?.coverUrl,
                        placeholder = Icons.AutoMirrored.Rounded.LibraryBooks,
                    ),
                    onClick = { onOpenSeries(item) },
                    coverSize = rowSize.cover,
                )
            }
        }
    }
}

/** Kniha → jednotná karta (průběh, zbývá, dohráno). Sdíleno s řadami Domů. */
internal fun bookModel(b: Audiobook, playing: Boolean = false, badge: String? = null): SlovoMediaModel {
    val started = b.progress > 0.0 || b.currentTimeSec > 0.0
    val durMs = (b.durationSec * 1000).toLong()
    return SlovoMediaModel(
        title = b.title,
        subtitle = b.author,
        coverUrl = b.coverUrl,
        progress = b.progress.toFloat().takeIf { started && !b.isFinished },
        meta = if (started) remainingLabel((b.currentTimeSec * 1000).toLong(), durMs) else durMs.takeIf { it > 0 }?.let { durationLabel(it) },
        finished = b.isFinished,
        playing = playing,
        badge = badge,
    )
}
