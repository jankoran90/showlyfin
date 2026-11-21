package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** AMPHORA F2 / KN1 — nad knihami: filtr stavu (čipy), řazení (nabídka) a mřížka / seznam. */
@Composable
internal fun LibraryToolbar(
    filter: BookFilter,
    sort: BookSort,
    listMode: Boolean,
    onFilter: (BookFilter) -> Unit,
    onSort: (BookSort) -> Unit,
    onListMode: (Boolean) -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            BookFilter.entries.forEach { f ->
                FilterChip(selected = f == filter, onClick = { onFilter(f) }, label = { Text(f.label) })
            }
        }
        Box {
            IconButton(onClick = { sortOpen = true }) {
                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Řazení: ${sort.label}")
            }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                BookSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        leadingIcon = { if (s == sort) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = { sortOpen = false; onSort(s) },
                    )
                }
            }
        }
        IconButton(onClick = { onListMode(!listMode) }) {
            Icon(
                if (listMode) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = if (listMode) "Zobrazit jako mřížku" else "Zobrazit jako seznam",
            )
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
