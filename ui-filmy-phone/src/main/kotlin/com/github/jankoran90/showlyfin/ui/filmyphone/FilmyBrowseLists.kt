package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.PosterCard
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.collectionCardTitle
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/**
 * Mřížka a seznam Filmotéky (telefon). Vytaženo z [FilmyBrowseSection] 2026-08-24 (ATRIUM SHW-118) —
 * sekce byla na 615 řádcích (tvrdý strop 600) a karty sdružených kolekcí do ní potřebovaly přibýt.
 * Obě zobrazení kreslí tytéž řady, jen jinou formou; kartu kolekce od filmu pozná podle
 * [HomeRowItem.collectionKey].
 */

/** Mřížka plakátů se sekcemi. Víc řad (žánr/země) → full-width nadpis mezi sekcemi; jedna řada (Vše) → bez nadpisu. */
@Composable
internal fun FilmotekaGrid(
    rails: List<FilmotekaRail>,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenCollection: (String) -> Unit,
    scrollerLabel: (HomeRowItem) -> String?,
    nextUp: List<HomeRowItem> = emptyList(),
    onOpenNextUp: (HomeRowItem) -> Unit = {},
) {
    val cols = rememberGridColumnPref()
    val showHeaders = rails.size > 1
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val flat = remember(rails) { rails.flatMap { it.items } }
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (nextUp.isNotEmpty()) {
            item(key = "nextup_row", span = { GridItemSpan(maxLineSpan) }) {
                FilmyNextUpRow(items = nextUp, onClick = onOpenNextUp)
            }
        }
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(rail.title)
                }
            }
            gridItems(rail.items, key = { it.key }) { row ->
                val collectionKey = row.collectionKey
                if (collectionKey != null) {
                    // Kolekce není film: karta nemá ČSFD ani odznak zdroje, klik otevře její obsah.
                    PosterCard(
                        posterUrl = row.posterUrl,
                        title = collectionCardTitle(row.title),
                        year = row.year?.toString(),
                        onClick = { onOpenCollection(collectionKey) },
                        enableCsfd = false,
                    )
                } else {
                    row.mediaItem?.let { mi -> MediaCard(item = mi, onClick = { onOpenDetail(mi) }) }
                }
            }
        }
    }
    // MERIDIAN (SHW-119): posuvník počítá v POLOŽKÁCH, ne v řádcích mřížky — uživatel táhne k titulu,
    // ne k řádku. Přepočet na řádek si udělá `scrollToItem` sám (LazyVerticalGrid indexuje položkami).
    FilmyFastScroller(
        itemCount = flat.size,
        progress = if (flat.size <= 1) 0f else gridState.firstVisibleItemIndex.toFloat() / (flat.size - 1),
        label = { i -> flat.getOrNull(i)?.let(scrollerLabel) },
        onScrollTo = { i -> scope.launch { gridState.scrollToItem(i) } },
        modifier = Modifier.align(Alignment.TopEnd).padding(vertical = 8.dp),
    )
    }
}

/** Seznam bohatých řádků (cover + název + režie + rok · žánry + popis) — stejný řádek jako domov. */
@Composable
internal fun FilmotekaList(
    rails: List<FilmotekaRail>,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenCollection: (String) -> Unit,
    scrollerLabel: (HomeRowItem) -> String?,
    nextUp: List<HomeRowItem> = emptyList(),
    onOpenNextUp: (HomeRowItem) -> Unit = {},
) {
    val showHeaders = rails.size > 1
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val flat = remember(rails) { rails.flatMap { it.items } }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (nextUp.isNotEmpty()) {
            item(key = "nextup_row") { FilmyNextUpRow(items = nextUp, onClick = onOpenNextUp) }
        }
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}") { SectionHeader(rail.title) }
            }
            listItems(rail.items, key = { it.key }) { row ->
                row.mediaItem?.let { mi ->
                    val collectionKey = row.collectionKey
                    // 🔒 2026-08-24 (user: „kolekce v zobrazení seznam je zobrazeno špatně a jinak") —
                    // kolekce kreslí TENTÝŽ [MediaRow] jako film, ne vlastní chudší řádek. Liší se jen
                    // tím, co se pro ni nedá dohledat: režie (kolekce ji nemá) a ČSFD (nemá tmdb/imdb).
                    // Klik řídí `collectionKey` — ten se testuje PŘED cestou na detail.
                    MediaRow(
                        item = mi,
                        onClick = {
                            if (collectionKey != null) onOpenCollection(collectionKey) else onOpenDetail(mi)
                        },
                        watched = row.watched,
                        genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                        showDirector = collectionKey == null,
                        enableCsfd = collectionKey == null,
                        progressText = row.subtitle.takeIf { collectionKey != null },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
    FilmyFastScroller(
        itemCount = flat.size,
        progress = if (flat.size <= 1) 0f else listState.firstVisibleItemIndex.toFloat() / (flat.size - 1),
        label = { i -> flat.getOrNull(i)?.let(scrollerLabel) },
        onScrollTo = { i -> scope.launch { listState.scrollToItem(i) } },
        modifier = Modifier.align(Alignment.TopEnd).padding(vertical = 8.dp),
    )
    }
}
