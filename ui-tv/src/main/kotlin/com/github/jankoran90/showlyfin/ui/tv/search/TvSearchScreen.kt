package com.github.jankoran90.showlyfin.ui.tv.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.ui.phone.SearchResult
import com.github.jankoran90.showlyfin.ui.phone.SearchScope
import com.github.jankoran90.showlyfin.ui.phone.SearchViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.TvPosterCard

/**
 * TENFOOT (SHW-87) F3 — nativní 10-foot Hledání. Sdílí telefonní [SearchViewModel] (reaktivní debounce
 * hledání), ale na TV se soustředí na to, co člověk u televize hledá: **film / seriál k přehrání**
 * (rozsahy Lidi/Vydavatelství = telefonní works-sheet, na TV vynechány). Výsledky = plakátová mřížka
 * ze sdílené [TvPosterCard] (stejný vzhled + fokusová záře jako domov); ťuk → nativní TV karta obsahu.
 *
 * Vstup textu: `OutlinedTextField` s auto-fokusem → na Android TV vyvolá systémovou D-pad klávesnici (IME).
 */
@Composable
fun TvSearchScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { queryFocus.requestFocus() } }

    // Na TV nabízíme jen filmy/seriály (osoby/vydavatelství = telefonní works-sheet).
    val tvScopes = remember { listOf(SearchScope.FILMS, SearchScope.SHOWS) }
    // Zahoď výsledky mimo film/seriál (kdyby VM měl zbytkový jiný rozsah) — na TV je neumíme otevřít.
    val movieShow = state.results.filter { it is SearchResult.Movie || it is SearchResult.Show }

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zpět",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .tvFocusBorder(shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Hledat film nebo seriál") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).focusRequester(queryFocus),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            tvScopes.forEach { scope ->
                FilterChip(
                    selected = state.scope == scope,
                    onClick = { viewModel.onScopeChange(scope) },
                    label = { Text(scope.label) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.query.isBlank() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Zadej název filmu nebo seriálu",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            movieShow.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Nic nenalezeno",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(movieShow, key = { it.id }) { result ->
                    TvPosterCard(
                        posterUrl = result.resultPosterUrl(),
                        title = result.displayTitle(),
                        year = result.displayYear(),
                        onClick = { onOpenDetail(result.toDetailStub()) },
                    )
                }
            }
        }
    }
}

/** Plakát výsledku (Movie/Show mají hotové TMDB URL). */
private fun SearchResult.resultPosterUrl(): String? = when (this) {
    is SearchResult.Movie -> posterUrl
    is SearchResult.Show -> posterUrl
    else -> null
}

private fun SearchResult.displayTitle(): String = when (this) {
    is SearchResult.Movie -> title
    is SearchResult.Show -> title
    else -> ""
}

private fun SearchResult.displayYear(): Int? = when (this) {
    is SearchResult.Movie -> year?.toIntOrNull()
    is SearchResult.Show -> year?.toIntOrNull()
    else -> null
}

/**
 * Stub [MediaItem] pro TV kartu obsahu — nese jen `tmdbId` + typ; zbytek (fanart, popis, ČSFD) si
 * `DetailViewModel` dohydratuje z TMDb (stejná stub-cesta jako telefonní Search → Detail).
 */
private fun SearchResult.toDetailStub(): MediaItem = MediaItem(
    traktId = 0L,
    tmdbId = id,
    imdbId = null,
    title = displayTitle(),
    year = displayYear(),
    overview = null,
    rating = null,
    genres = null,
    type = if (this is SearchResult.Show) MediaType.SHOW else MediaType.MOVIE,
    posterPath = null,
    backdropPath = null,
)
