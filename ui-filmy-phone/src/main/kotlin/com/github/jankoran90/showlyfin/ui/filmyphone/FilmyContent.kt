package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem

/**
 * CELLULOID (SHW-98) M2.5 — sdílené stavební bloky telefonních sekcí appky „Filmy".
 *
 * Grid plakátů ([MediaCard]) i seznam bohatých řádků ([MediaRow]: cover+název+režie+rok·žánry+popis) —
 * jednotný vzhled napříč Pro tebe / Klenoty / Knihovna / Filmotéka. Přepínač zobrazení ([FilmyViewToggle])
 * do lišty sekce (princip usera 2026-07-17: ovladače v liště). Výchozí SEZNAM (přání usera).
 */

/** Řada obsahu (společný tvar pro Klenoty/Filmotéku — nese `HomeRowItem` s `mediaItem: MediaItem?`). */
data class FilmyRailData(val id: String, val title: String, val items: List<HomeRowItem>)

/** Ikona přepínače zobrazení mřížka⇄seznam — do `trailing` [FilmySectionBar]. */
@Composable
fun FilmyViewToggle(viewMode: ViewMode, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (viewMode == ViewMode.GRID) {
            Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = "Zobrazit jako seznam")
        } else {
            Icon(Icons.Rounded.GridView, contentDescription = "Zobrazit jako mřížku")
        }
    }
}

/** Plochý seznam `MediaItem` jako mřížka plakátů (Pro tebe). */
@Composable
fun FilmyMediaGrid(items: List<MediaItem>, onOpenDetail: (MediaItem) -> Unit) {
    val cols = rememberGridColumnPref()
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(items, key = { it.stableKey() }) { mi ->
            MediaCard(item = mi, onClick = { onOpenDetail(mi) })
        }
    }
}

/** Plochý seznam `MediaItem` jako bohaté řádky (Pro tebe). */
@Composable
fun FilmyMediaList(items: List<MediaItem>, onOpenDetail: (MediaItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        listItems(items, key = { it.stableKey() }) { mi ->
            MediaRow(
                item = mi,
                onClick = { onOpenDetail(mi) },
                genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                showDirector = true,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        }
    }
}

/**
 * Řady (`HomeRowItem`) jako mřížka plakátů se sekcemi (Klenoty, Knihovna). Položka s `mediaItem` → bohatá
 * karta (klik [onOpenDetail]); JF-only (bez mediaItem) → jednoduchý plakát (klik [onJfClick], JF detail = gap).
 */
@Composable
fun FilmyRailsGrid(
    rails: List<FilmyRailData>,
    onOpenDetail: (MediaItem) -> Unit,
    onJfClick: (HomeRowItem) -> Unit = {},
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
                item(key = "hdr_${rail.id}", span = { GridItemSpan(maxLineSpan) }) { FilmySectionHeader(rail.title) }
            }
            gridItems(rail.items, key = { it.key }) { row ->
                val mi = row.mediaItem
                if (mi != null) MediaCard(item = mi, onClick = { onOpenDetail(mi) })
                else FilmyJfPoster(item = row, onClick = { onJfClick(row) })
            }
        }
    }
}

/**
 * Řady (`HomeRowItem`) jako bohaté řádky se sekcemi (Klenoty, Knihovna). Položka s `mediaItem` → [MediaRow];
 * JF-only → jednoduchý řádek [FilmyJfRow] (klik [onJfClick]).
 */
@Composable
fun FilmyRailsList(
    rails: List<FilmyRailData>,
    onOpenDetail: (MediaItem) -> Unit,
    onJfClick: (HomeRowItem) -> Unit = {},
) {
    val showHeaders = rails.size > 1
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}") { FilmySectionHeader(rail.title) }
            }
            listItems(rail.items, key = { it.key }) { row ->
                val mi = row.mediaItem
                if (mi != null) {
                    MediaRow(
                        item = mi,
                        onClick = { onOpenDetail(mi) },
                        watched = row.watched,
                        genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                        showDirector = true,
                    )
                } else {
                    FilmyJfRow(item = row, onClick = { onJfClick(row) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

/** LibraryRowItem (JF knihovna) → HomeRowItem pro jednotný render. Sdílené domovem i Knihovnou. */
internal fun LibraryRowItem.toHomeRowItem(): HomeRowItem = HomeRowItem(
    key = jellyfinId,
    title = name,
    year = year,
    posterUrl = imageUrl,
    landscapeUrl = landscapeUrl,
    progressPct = progressPct,
    watched = watched,
    mediaItem = mediaItem,
    jellyfinId = jellyfinId,
)

/** JF-only řádek (bez bohatého `mediaItem`) — cover + název + rok. Sdílené domovem, Klenoty, Knihovnou. */
@Composable
internal fun FilmyJfRow(item: HomeRowItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .height(120.dp)
                .width(80.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.year?.let {
                Spacer(Modifier.height(2.dp))
                Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** JF-only plakát pro mřížku (bez `mediaItem`) — cover, fallback ikona. */
@Composable
internal fun FilmyJfPoster(item: HomeRowItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (item.posterUrl != null) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Movie, contentDescription = item.title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FilmySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** Prázdný stav sekce — ikona + nadpis + vysvětlení. */
@Composable
fun FilmyEmpty(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Stabilní klíč pro `MediaItem` v LazyList (tmdb/trakt/imdb, fallback titul+rok). */
private fun MediaItem.stableKey(): String =
    tmdbId?.let { "t$it" } ?: traktId.takeIf { it != 0L }?.let { "k$it" } ?: imdbId ?: "$title:$year"
