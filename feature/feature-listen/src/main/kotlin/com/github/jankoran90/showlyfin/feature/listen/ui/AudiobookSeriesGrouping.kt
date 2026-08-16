package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import java.util.Locale

/**
 * DROPSHIP série v knihovně: audioknihy se seskupí podle [Audiobook.seriesName] (víc dílů → jedna
 * karta série, otevře se sheet s díly seřazenými podle čísla). Samostatné knihy a jednodílné „série"
 * (jen 1 kniha z ní zatím v knihovně) zůstávají jako běžná karta — sjednocený abecední grid.
 */
sealed interface BookShelfItem {
    val sortKey: String
    val itemKey: String

    data class Standalone(val book: Audiobook) : BookShelfItem {
        override val sortKey get() = book.sortTitle()
        override val itemKey get() = "book_${book.id}"
    }

    data class SeriesGroup(val seriesName: String, val books: List<Audiobook>) : BookShelfItem {
        override val sortKey get() = seriesName.lowercase(Locale("cs"))
        override val itemKey get() = "series_$seriesName"
    }
}

private fun Audiobook.sortTitle() = title.lowercase(Locale("cs"))

/** ABS konvence: minifikovaný seriesName je „Název série #N" (N chybí, pokud pořadí není zadané). */
private val SERIES_SEQUENCE_SUFFIX = Regex("""^(.*) #([0-9]+(?:\.[0-9]+)?)$""")

private fun seriesBaseName(raw: String): String = SERIES_SEQUENCE_SUFFIX.find(raw)?.groupValues?.get(1) ?: raw
private fun seriesSequenceOf(raw: String): Double? = SERIES_SEQUENCE_SUFFIX.find(raw)?.groupValues?.get(2)?.toDoubleOrNull()

fun groupBooksBySeries(books: List<Audiobook>): List<BookShelfItem> {
    val bySeriesBase = books.groupBy { it.seriesName?.takeIf { s -> s.isNotBlank() }?.let(::seriesBaseName) }
    val items = buildList {
        bySeriesBase[null].orEmpty().forEach { add(BookShelfItem.Standalone(it)) }
        bySeriesBase.forEach { (base, group) ->
            if (base == null) return@forEach
            if (group.size == 1) {
                add(BookShelfItem.Standalone(group.first()))
            } else {
                val sorted = group.sortedWith(
                    compareBy(
                        { seriesSequenceOf(it.seriesName!!) ?: Double.MAX_VALUE },
                        { it.sortTitle() },
                    ),
                )
                add(BookShelfItem.SeriesGroup(base, sorted))
            }
        }
    }
    return items.sortedBy { it.sortKey }
}

@Composable
fun SeriesCard(
    group: BookShelfItem.SeriesGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = group.books.firstOrNull { it.coverUrl != null }
    com.github.jankoran90.showlyfin.core.ui.CoverCard(
        title = group.seriesName,
        subtitle = "${group.books.size} dílů",
        imageUrl = cover?.coverUrl,
        onClick = onClick,
        modifier = modifier,
        placeholder = Icons.AutoMirrored.Filled.LibraryBooks,
    )
}

/** Sheet s díly jedné série, seřazenými podle pořadí — tap na díl otevře jeho detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesVolumesSheet(
    group: BookShelfItem.SeriesGroup,
    downloadedBookIds: Set<String>,
    onOpenBook: (String) -> Unit,
    onLongClickBook: (Audiobook) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            group.seriesName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxWidth().height(420.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(group.books, key = { it.id }) { book ->
                AudiobookCard(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                    downloaded = book.id in downloadedBookIds,
                    onLongClick = { onLongClickBook(book) },
                )
            }
        }
    }
}
