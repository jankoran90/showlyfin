package com.github.jankoran90.showlyfin.feature.listen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.github.jankoran90.showlyfin.core.ui.tokens.CoverSize
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import com.github.jankoran90.showlyfin.feature.listen.PodcastSearchViewModel
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AtCoverCard
import com.github.jankoran90.showlyfin.feature.listen.ui.SlovoMediaModel
import com.github.jankoran90.showlyfin.feature.listen.ui.SlovoMediaRow
import com.github.jankoran90.showlyfin.feature.listen.ui.bookModel

/** AMPHORA F3 — stavební kousky výsledků jednotného hledání ([UnifiedSearchScreen]). */

@Composable
internal fun GroupHeader(title: String, count: Int?, loading: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        if (count != null) {
            Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) CircularProgressIndicator(Modifier.size(Spacing.l), strokeWidth = Spacing.xxs)
    }
}

@Composable
internal fun BookResultRow(book: Audiobook, onClick: () -> Unit) {
    val base = bookModel(book)
    SlovoMediaRow(
        model = base.copy(
            subtitle = listOfNotNull(book.author, book.seriesName).joinToString(" · ").ifBlank { null },
            placeholder = Icons.AutoMirrored.Rounded.LibraryBooks,
        ),
        onClick = onClick,
    )
}

@Composable
internal fun SourceResultRow(source: PodcastSource, onClick: () -> Unit) {
    SlovoMediaRow(
        model = SlovoMediaModel(
            title = source.title,
            subtitle = sourceTypeLabel(source.type),
            coverUrl = source.thumbnail,
            placeholder = Icons.Rounded.Podcasts,
        ),
        onClick = onClick,
    )
}

@Composable
internal fun NewShowRow(result: SourceSearchResult, onOpen: () -> Unit, onFollow: () -> Unit) {
    SlovoMediaRow(
        model = SlovoMediaModel(
            title = result.title,
            subtitle = result.subtitle?.takeIf { it.isNotBlank() } ?: sourceTypeLabel(result.type),
            coverUrl = result.thumbnail,
            meta = listOfNotNull(
                result.category,
                result.episodeCount?.takeIf { it > 0 }?.let { "$it dílů" },
            ).joinToString(" · ").ifBlank { null },
            placeholder = Icons.Rounded.Podcasts,
        ),
        onClick = onOpen,
        trailing = {
            FilledTonalButton(onClick = onFollow, contentPadding = PaddingValues(horizontal = Spacing.m)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(Spacing.l))
                Text("Sledovat", Modifier.padding(start = Spacing.xs))
            }
        },
    )
}

@Composable
internal fun AudiotekaResultRow(books: List<AtBook>, onOpen: (AtBook) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(books, key = { (if (it.isSeries) "s" else "b") + it.id }) { b ->
            AtCoverCard(b, onOpen, Modifier.width(CoverSize.card))
        }
    }
}

@Composable
internal fun EpisodeSortChips(selected: PodcastSearchViewModel.SortMode, onSelect: (PodcastSearchViewModel.SortMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        PodcastSearchViewModel.SortMode.entries.forEach { mode ->
            FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(mode.label) })
        }
    }
}

@Composable
internal fun ShowAllButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = Spacing.s)) { Text(label) }
}

/** Prázdné pole: nedávná hledání (ťuk = hledat znovu, × = odebrat) a nápověda. */
@Composable
internal fun RecentSearches(
    recent: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    hint: String,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "hint") {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
        }
        if (recent.isNotEmpty()) {
            item(key = "recent-h") {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.s, top = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Nedávná hledání",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClear) { Text("Vymazat") }
                }
            }
            items(recent, key = { "r$it" }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = Spacing.l, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                        Text(q, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onRemove(q) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Odebrat z nedávných")
                    }
                }
            }
        }
    }
}

private fun sourceTypeLabel(type: String): String = when (type) {
    "youtube" -> "YouTube"
    "ctv" -> "Česká televize"
    else -> "Podcast"
}
