package com.github.jankoran90.showlyfin.feature.jellyfin.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import androidx.compose.foundation.shape.RoundedCornerShape
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.WatchedBadge
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRow
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel

/**
 * Podsekce „Knihovna" — pohled z Traktu na Jellyfin knihovnu, řazeno do řad po zdrojových knihovnách.
 *
 * PANORAMA (SHW-78): řady zůstávají, ale styl karet (Poster / Na šířku / Na šířku + popis) a rozvržení
 * (Řady / Mřížka) se konfigurují z Nastavení → „Zobrazení knihovny" (klíče v `trakt_prefs`); navíc
 * per-řada override přes long-press nadpisu kategorie. Klik na kartu: má-li TMDB match → bohatý Trakt
 * detail, jinak Jellyfin karta.
 */
@Composable
fun LibraryRowsScreen(
    onItemClick: (media: MediaItem?, jellyfinId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryRowsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE) }
    // Čteme každou rekompozici → změna v Nastavení se projeví po návratu na obrazovku.
    val gridLayout = (prefs.getString("library_layout", LIBRARY_LAYOUT_ROWS) ?: LIBRARY_LAYOUT_ROWS) == LIBRARY_LAYOUT_GRID
    val globalStyle = ViewMode.fromKey(prefs.getString("library_style", ViewMode.GRID.storeKey))
    // Per-řada override (live po long-pressu); iniciálně z prefs.
    val rowOverrides = remember { mutableStateMapOf<String, ViewMode>() }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading && state.rows.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null ->
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            state.rows.isEmpty() ->
                Text(
                    text = "Knihovna je prázdná",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.rows, key = { it.libraryId }) { row ->
                    val style = rowOverrides[row.libraryId]
                        ?: prefs.getString(libraryRowStyleKey(row.libraryId), null)?.let { ViewMode.fromKey(it) }
                        ?: globalStyle
                    LibraryRowSection(
                        row = row,
                        style = style,
                        gridLayout = gridLayout,
                        onItemClick = onItemClick,
                        onOpenLibrary = onOpenLibrary,
                        onPickStyle = { picked ->
                            rowOverrides[row.libraryId] = picked
                            prefs.edit().putString(libraryRowStyleKey(row.libraryId), picked.storeKey).apply()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryRowSection(
    row: LibraryRow,
    style: ViewMode,
    gridLayout: Boolean,
    onItemClick: (media: MediaItem?, jellyfinId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    onPickStyle: (ViewMode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Text(
                    text = row.libraryName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.combinedClickable(
                        onClick = { menuOpen = true },
                        onLongClick = { menuOpen = true },
                    ),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Text(
                        "Styl karet této řady",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    listOf(ViewMode.GRID, ViewMode.LANDSCAPE, ViewMode.LANDSCAPE_DETAIL).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == ViewMode.GRID) "Plakát" else m.label) },
                            onClick = { onPickStyle(m); menuOpen = false },
                            trailingIcon = if (m == style) { { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } } else null,
                        )
                    }
                }
            }
            TextButton(
                onClick = { onOpenLibrary(row.libraryId, row.libraryName, row.collectionType) },
                modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50)),
            ) {
                Text("Vše", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Otevřít knihovnu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // „Na šířku + popis" je vždy svislý seznam (karty jsou široké); jinak dle rozvržení.
        when {
            style == ViewMode.LANDSCAPE_DETAIL -> {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.items.forEach { item ->
                        LibraryDetailCard(item, Modifier.fillMaxWidth()) { onItemClick(item.mediaItem, item.jellyfinId) }
                    }
                }
            }
            gridLayout -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.items.forEach { item ->
                        if (style == ViewMode.LANDSCAPE) {
                            LibraryLandscapeCard(item, Modifier.width(200.dp)) { onItemClick(item.mediaItem, item.jellyfinId) }
                        } else {
                            LibraryPosterCard(item, Modifier.width(110.dp)) { onItemClick(item.mediaItem, item.jellyfinId) }
                        }
                    }
                }
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(row.items, key = { it.jellyfinId }) { item ->
                        if (style == ViewMode.LANDSCAPE) {
                            LibraryLandscapeCard(item, Modifier.width(220.dp)) { onItemClick(item.mediaItem, item.jellyfinId) }
                        } else {
                            LibraryPosterCard(item, Modifier.width(120.dp)) { onItemClick(item.mediaItem, item.jellyfinId) }
                        }
                    }
                }
            }
        }
    }
}

/** Plakátová karta (2:3). Název na 1 řádek (PANORAMA — dvouřádkové názvy se userovi nelíbily). */
@Composable
private fun LibraryPosterCard(item: LibraryRowItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .tvFocusable(shape = RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            BottomScrim(Modifier.align(Alignment.BottomStart))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.year?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it.toString(), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                }
            }
            ProgressBar(item.progressPct, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** „Netflix" karta na šířku (16:9). Backdrop/thumb → fallback poster; scrim [název · rok]. */
@Composable
private fun LibraryLandscapeCard(item: LibraryRowItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .tvFocusable(shape = RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.landscapeUrl ?: item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (item.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            BottomScrim(Modifier.align(Alignment.BottomStart))
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                )
                item.year?.let {
                    Spacer(Modifier.width(4.dp))
                    Text(it.toString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
            ProgressBar(item.progressPct, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Široká karta s popisem: vlevo backdrop (16:9), vpravo název + rok + popis (Jellyfin overview). */
@Composable
private fun LibraryDetailCard(item: LibraryRowItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocusable(shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(150.dp).aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = item.landscapeUrl ?: item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (item.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
                ProgressBar(item.progressPct, Modifier.align(Alignment.BottomStart))
            }
            Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.year?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                if (!item.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.overview!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
    )
}

@Composable
private fun ProgressBar(pct: Int?, modifier: Modifier = Modifier) {
    pct?.takeIf { it > 0 }?.let { p ->
        LinearProgressIndicator(
            progress = { p / 100f },
            modifier = modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
    }
}

// PANORAMA (SHW-78): klíče konfigurace zobrazení knihovny v `trakt_prefs` (sdílené s Nastavením).
private const val LIBRARY_LAYOUT_ROWS = "rows"
private const val LIBRARY_LAYOUT_GRID = "grid"
internal fun libraryRowStyleKey(libraryId: String) = "libstyle_$libraryId"
