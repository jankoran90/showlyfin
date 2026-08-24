package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
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
) {
    val cols = rememberGridColumnPref()
    val showHeaders = rails.size > 1
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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
}

/** Seznam bohatých řádků (cover + název + režie + rok · žánry + popis) — stejný řádek jako domov. */
@Composable
internal fun FilmotekaList(
    rails: List<FilmotekaRail>,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val showHeaders = rails.size > 1
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}") { SectionHeader(rail.title) }
            }
            listItems(rail.items, key = { it.key }) { row ->
                val collectionKey = row.collectionKey
                if (collectionKey != null) {
                    FilmotekaCollectionRow(row = row, onClick = { onOpenCollection(collectionKey) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                } else {
                    row.mediaItem?.let { mi ->
                        MediaRow(
                            item = mi,
                            onClick = { onOpenDetail(mi) },
                            watched = row.watched,
                            genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                            showDirector = true,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    }
                }
            }
        }
    }
}


/**
 * ATRIUM (SHW-118) — řádek sdružené kolekce v seznamovém režimu. Záměrně chudší než [MediaRow]:
 * kolekce nemá režii, popis ani hodnocení — jen plakát, název a počet dílů, aby bylo na první pohled
 * jasné, že klik nevede na film, ale na obsah.
 */
@Composable
internal fun FilmotekaCollectionRow(row: HomeRowItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        AsyncImage(
            model = row.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = collectionCardTitle(row.title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(row.year?.toString(), row.subtitle).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
