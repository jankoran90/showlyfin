package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.feature.detail.ui.PersonFilmographySheet

private val CATEGORIES = listOf(
    FavoriteKind.MOVIE to "Filmy",
    FavoriteKind.ACTOR to "Herci",
    FavoriteKind.DIRECTOR to "Režiséři",
    FavoriteKind.WRITER to "Scénáristé",
    FavoriteKind.PRODUCER to "Producenti",
    FavoriteKind.COMPOSER to "Skladatelé",
    FavoriteKind.COMPANY to "Vydavatelství",
)

/**
 * COMPASS C2 (SHW-44) — sekce Oblíbení: kategorie (filmy/herci/režiséři/producenti/skladatelé/
 * vydavatelství) + mřížka oblíbených dané kategorie. Tap na film → detail; tap na osobu/vydavatelství
 * → jejich tvorba (sheet). Dlouhý stisk → odebrat.
 */
@Composable
fun OblibeniScreen(
    onOpenDetail: (tmdbId: Long, title: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: OblibeniViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(FavoriteKind.MOVIE) }
    var pendingRemove by remember { mutableStateOf<FavoriteItem?>(null) }

    val shown = remember(items, selected) {
        items.filter { it.kind == selected }.sortedByDescending { it.addedAtMs }
    }

    Column(modifier.fillMaxSize()) {
        Text(
            "Oblíbení",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CATEGORIES) { (kind, label) ->
                val count = items.count { it.kind == kind }
                FilterChip(
                    selected = selected == kind,
                    onClick = { selected = kind },
                    label = { Text(if (count > 0) "$label ($count)" else label) },
                )
            }
        }

        if (shown.isEmpty()) {
            EmptyState(selected, Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { "${it.kind}_${it.id}" }) { item ->
                    FavoriteTile(
                        item = item,
                        onClick = {
                            if (item.kind == FavoriteKind.MOVIE) onOpenDetail(item.id, item.name)
                            else vm.openWorks(item)
                        },
                        onLongClick = { pendingRemove = item },
                    )
                }
            }
        }
    }

    pendingRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Odebrat z oblíbených?") },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(onClick = { vm.remove(target); pendingRemove = null }) { Text("Odebrat") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Zrušit") }
            },
        )
    }

    if (sheet.open) {
        PersonFilmographySheet(
            name = sheet.name,
            loading = sheet.loading,
            collection = sheet.collection,
            roleLabel = sheet.roleLabel,
            onPartClick = { part ->
                part.tmdbId?.let { onOpenDetail(it, part.title) }
                vm.closeSheet()
            },
            onDismiss = { vm.closeSheet() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(
    item: FavoriteItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isMovie = item.kind == FavoriteKind.MOVIE
    val isCompany = item.kind == FavoriteKind.COMPANY
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            isMovie -> PosterImage(item.imageUrl, item.name)
            isCompany -> LogoBox(item.imageUrl, item.name)
            else -> CircleImage(item.imageUrl, item.name)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (isMovie && item.year != null) {
            Text(
                item.year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun PosterImage(url: String?, name: String) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium),
        )
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun CircleImage(url: String?, name: String) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(CircleShape),
        )
    } else {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogoBox(url: String?, name: String) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.4f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Fit)
        } else {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyState(kind: FavoriteKind, modifier: Modifier = Modifier) {
    val hint = when (kind) {
        FavoriteKind.MOVIE -> "Filmy přidáš hvězdičkou ⭐ na detailu filmu."
        FavoriteKind.ACTOR -> "Herce přidáš u filmu — ťukni na herce v sekci Tvůrci a dej ⭐."
        FavoriteKind.DIRECTOR -> "Režiséry přidáš u filmu — ťukni na režii v sekci Tvůrci a dej ⭐."
        FavoriteKind.WRITER -> "Scénáristy přidáš u filmu — ťukni na scénář v sekci Tvůrci a dej ⭐, nebo přes hledání."
        FavoriteKind.PRODUCER -> "Producenty přidáš přes vyhledávání (hvězda → role)."
        FavoriteKind.COMPOSER -> "Skladatele přidáš přes vyhledávání (hvězda → role)."
        FavoriteKind.COMPANY -> "Vydavatelství přidáš přes vyhledávání (hvězda)."
    }
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
