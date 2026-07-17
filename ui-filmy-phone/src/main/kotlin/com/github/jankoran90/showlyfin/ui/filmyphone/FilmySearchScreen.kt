package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.ui.phone.SearchResult
import com.github.jankoran90.showlyfin.ui.phone.SearchScope
import com.github.jankoran90.showlyfin.ui.phone.SearchViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Hledat" appky Filmy.
 * Reuse sdíleného [SearchViewModel] (ui-phone; reaktivní debounce, TMDB). Vyhledávací pole splynulé s lištou
 * (☰ + pole), pod ním chipy rozsahu Filmy/Seriály; výsledky = mřížka plakátů. Klik → stub `MediaItem`
 * (tmdbId) → sdílený DetailScreen si detail dohydratuje. Lidi/vydavatelství tu neřešíme (appka o filmech).
 */
@Composable
fun FilmySearchScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(onMenu = onMenu) {
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Hledat filmy a seriály…") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        // Rozsah (Filmy / Seriály) — druhý řádek chipů (appka o filmech: lidi/vydavatelství vynecháváme).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(SearchScope.FILMS, SearchScope.SHOWS).forEach { s ->
                FilterChip(selected = state.scope == s, onClick = { vm.onScopeChange(s) }, label = { Text(s.label) })
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.query.isBlank() -> FilmyEmpty(
                    icon = Icons.Rounded.Search,
                    title = "Co budeme hledat?",
                    text = "Napiš název filmu nebo seriálu.",
                )
                state.results.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.Search,
                    title = "Nic nenalezeno",
                    text = "Zkus jiný název nebo přepni rozsah.",
                )
                else -> SearchResultsGrid(state.results, onOpenDetail)
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(results: List<SearchResult>, onOpenDetail: (MediaItem) -> Unit) {
    val cols = rememberGridColumnPref()
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(results, key = { "${it::class.simpleName}:${it.id}" }) { r ->
            r.toMediaStub()?.let { mi -> SearchPoster(posterUrl = r.posterUrlOrNull(), title = mi.title, onClick = { onOpenDetail(mi) }) }
        }
    }
}

@Composable
private fun SearchPoster(posterUrl: String?, title: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (posterUrl != null) {
                AsyncImage(model = posterUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Movie, contentDescription = title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Filmy/seriály → stub `MediaItem` (tmdbId) pro sdílený DetailScreen (dohydratuje). Lidi/firmy = null. */
private fun SearchResult.toMediaStub(): MediaItem? = when (this) {
    is SearchResult.Movie -> MediaItem(
        traktId = 0L, tmdbId = id, imdbId = null, title = title, year = year?.toIntOrNull(),
        overview = null, rating = rating?.toFloat(), genres = null, type = MediaType.MOVIE,
    )
    is SearchResult.Show -> MediaItem(
        traktId = 0L, tmdbId = id, imdbId = null, title = title, year = year?.toIntOrNull(),
        overview = null, rating = rating?.toFloat(), genres = null, type = MediaType.SHOW,
    )
    else -> null
}

private fun SearchResult.posterUrlOrNull(): String? = when (this) {
    is SearchResult.Movie -> posterUrl
    is SearchResult.Show -> posterUrl
    else -> null
}
