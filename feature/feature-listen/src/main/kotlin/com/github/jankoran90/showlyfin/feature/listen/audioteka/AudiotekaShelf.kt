package com.github.jankoran90.showlyfin.feature.listen.audioteka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtSection

/** Podtitulek karty: autor · cena (u série jen „Série"). */
internal fun AtBook.cardSubtitle(): String = when {
    isSeries -> "Série"
    inLibrary != null -> authors.firstOrNull() ?: "V knihovně"
    else -> listOfNotNull(authors.firstOrNull(), price ?: if (free) "Zdarma" else null).joinToString(" · ")
}

/**
 * Díl série na kartě (user 2026-11-03: „popisky nejsou celé, díl není vidět"): název bez opakovaného
 * jména série („SPRÁVNÁ PĚTKA na útěku" → „na útěku"), podtitulek „3. díl · cena".
 */
internal fun AtBook.seriesTitle(seriesName: String): String {
    val rest = if (title.startsWith(seriesName, ignoreCase = true)) title.substring(seriesName.length) else title
    return rest.trimStart(' ', ':', '-', '–', ',', '.').ifBlank { title }
}

internal fun numberedSeries(seriesName: String, items: List<AtBook>): Boolean =
    seriesName.isNotBlank() && items.all { it.title.startsWith(seriesName, ignoreCase = true) }

internal fun AtBook.seriesSubtitle(order: Int): String =
    listOfNotNull("$order. díl", if (inLibrary != null) null else price ?: if (free) "Zdarma" else null).joinToString(" · ")

/** Odznak přes obálku: „Tato kniha" / „V knihovně" (máme) / „Klub" (v předplatném Audioteky). */
@Composable
internal fun BoxScope.AtBadge(book: AtBook, current: Boolean = false) {
    val text = when {
        current -> "Tato kniha"
        book.inLibrary != null -> "V knihovně"
        book.inClub -> "Klub"
        else -> return
    }
    val owned = current || book.inLibrary != null
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (owned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .background(
                if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun AtCoverCard(
    book: AtBook,
    onOpen: (AtBook) -> Unit,
    modifier: Modifier = Modifier,
    title: String = book.title,
    subtitle: String = book.cardSubtitle(),
    current: Boolean = false,
) {
    CoverCard(
        title = title,
        subtitle = subtitle,
        imageUrl = book.imageUrl,
        onClick = { onOpen(book) },
        modifier = modifier,
        placeholder = if (book.isSeries) Icons.Rounded.LibraryBooks else Icons.Rounded.MenuBook,
        overlay = { AtBadge(book, current) },
    )
}

/** Vodorovný pruh knih s nadpisem a počtem („máš 4 z 12" u série). */
@Composable
fun AtShelf(
    section: AtSection,
    onOpen: (AtBook) -> Unit,
    modifier: Modifier = Modifier,
    hideOwned: Boolean = false,
    /** Právě otevřená kniha (id v Audiotéce nebo v naší knihovně) → odznak „Tato kniha". */
    currentId: String? = null,
) {
    val isGroup = section.kind == "series" || section.kind == "collection"
    val items = if (hideOwned && !isGroup) section.items.filter { it.inLibrary == null } else section.items
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isGroup) {
                val owned = items.count { it.inLibrary != null }
                val label = if (section.kind == "series") "Série" else "Kolekce"
                Text(
                    "  $label · máš $owned z ${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Číslovat jen opravdovou sérii: všechny díly začínají jejím jménem (kolekce autora ne).
        val isSeries = section.kind == "series" && numberedSeries(section.title, items)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(end = 8.dp)) {
            itemsIndexed(items, key = { _, b -> b.id }) { i, b ->
                val cur = currentId != null && (b.id == currentId || b.inLibrary == currentId)
                if (isSeries) AtCoverCard(
                    b, onOpen, Modifier.width(136.dp),
                    title = b.seriesTitle(section.title),
                    subtitle = b.seriesSubtitle(i + 1),
                    current = cur,
                ) else AtCoverCard(b, onOpen, Modifier.width(136.dp), current = cur)
            }
        }
    }
}

/**
 * Návrhy z Audioteky v detailu audioknihy z NAŠÍ knihovny (user 2026-11-03: „návrhy u každé knihy pro
 * rozšíření série nebo kolekce nebo podobných"). Nenalezená kniha / chyba = nic se nevykreslí.
 */
@Composable
fun AudiotekaSuggestions(
    title: String,
    author: String?,
    currentItemId: String?,
    hideOwned: Boolean,
    onOpen: (AtBook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiotekaSuggestionsViewModel = hiltViewModel(key = "at-sugg-$title|$author"),
) {
    LaunchedEffect(title, author) { viewModel.load(title, author) }
    val st by viewModel.state.collectAsStateWithLifecycle()
    val ready = st as? AtLoad.Ready ?: return
    Column(modifier.fillMaxWidth()) {
        Text(
            "Z Audioteky",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        ready.data.sections.forEach { AtShelf(it, onOpen, hideOwned = hideOwned, currentId = currentItemId) }
    }
}
