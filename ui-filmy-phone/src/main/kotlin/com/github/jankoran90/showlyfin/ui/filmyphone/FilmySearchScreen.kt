package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.feature.detail.ui.PersonFilmographySheet
import com.github.jankoran90.showlyfin.ui.phone.SearchResult
import com.github.jankoran90.showlyfin.ui.phone.SearchScope
import com.github.jankoran90.showlyfin.ui.phone.SearchSort
import com.github.jankoran90.showlyfin.ui.phone.SearchViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Hledat" appky Filmy.
 *
 * Reuse sdíleného [SearchViewModel] (ui-phone; reaktivní debounce, TMDB, řazení, filmografie osob). Vyhledávací
 * pole splynulé s lištou (☰ + pole), pod ním chipy rozsahu (Filmy / Seriály / **Lidi**) + **řádek řazení**
 * (user 2026-07-20: Relevance / Oblíbenost / Hodnocení / Rok / Název + ▲▼ — jiné než Filmotéka, dává smysl pro
 * hledání). Film/seriál → sdílený DetailScreen; **osoba (režisér/herec) → [PersonFilmographySheet]** = jeho filmy.
 */
@Composable
fun FilmySearchScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    // RELEVANCE (user 2026-07-20) — u výchozího řazení strč výsledky BEZ plakátu na konec: obskurní tituly bez
    // plakátu (často i bez IMDB → zdroje se pro ně stejně nenajdou) nemají zahlcovat vršek u krátkého dotazu.
    // U explicitního řazení (Rok/Hodnocení/…) respektuj pořadí z VM. Klientsky, bez sítě.
    val displayResults = remember(state.results, state.sortBy) {
        if (state.sortBy == SearchSort.RELEVANCE) {
            val (withPoster, without) = state.results.partition { it.hasArt() }
            withPoster + without
        } else {
            state.results
        }
    }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(onMenu = onMenu) {
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Hledat filmy, seriály, režiséry…") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        // Rozsah (Filmy / Seriály / Lidi) + počítadlo + přepínač zobrazení vpravo (parita s Filmotékou/Pro tebe).
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(SearchScope.FILMS, SearchScope.SHOWS, SearchScope.PEOPLE).forEach { s ->
                    FilterChip(selected = state.scope == s, onClick = { vm.onScopeChange(s) }, label = { Text(s.label) })
                }
            }
            if (state.results.isNotEmpty()) {
                Text(
                    text = "${state.results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                // Přepínač zobrazení dává smysl jen u filmů/seriálů (lidi jsou vždy portréty v mřížce).
                if (state.scope != SearchScope.PEOPLE) {
                    FilmyViewToggle(viewMode) {
                        vm.setViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                    }
                }
            }
        }
        // ŘAZENÍ (user 2026-07-20) — kritéria vhodná pro hledání, filtrovaná dle rozsahu (rok/hodnocení jen u
        // filmů/seriálů). Směr ▲/▼. Vše klientsky nad staženými výsledky (bez další sítě). Jen když jsou výsledky.
        if (state.results.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchSort.entries.filter { it.appliesTo(state.scope) }.forEach { sort ->
                        FilterChip(
                            selected = state.sortBy == sort,
                            onClick = { vm.onSortChange(sort) },
                            label = { Text(sort.label) },
                        )
                    }
                }
                IconButton(onClick = { vm.toggleSortDirection() }) {
                    Text(
                        text = if (state.sortDesc) "▼" else "▲",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.query.isBlank() -> FilmyEmpty(
                    icon = Icons.Rounded.Search,
                    title = "Co budeme hledat?",
                    text = "Napiš název filmu, seriálu nebo jméno režiséra či herce.",
                )
                state.results.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.Search,
                    title = "Nic nenalezeno",
                    text = "Zkus jiný název nebo přepni rozsah.",
                )
                viewMode == ViewMode.LIST && state.scope != SearchScope.PEOPLE -> SearchResultsList(
                    results = displayResults,
                    onOpenDetail = onOpenDetail,
                )
                else -> SearchResultsGrid(
                    results = displayResults,
                    onOpenDetail = onOpenDetail,
                    onOpenPerson = { p -> vm.openWorks(departmentToKind(p.department), p.id, p.name) },
                )
            }
        }
    }

    if (sheet.open) {
        PersonFilmographySheet(
            name = sheet.name,
            loading = sheet.loading,
            collection = sheet.collection,
            roleLabel = sheet.roleLabel,
            onPartClick = { part ->
                part.tmdbId?.let { tmdb ->
                    onOpenDetail(
                        MediaItem(
                            traktId = 0L, tmdbId = tmdb, imdbId = null, title = part.title, year = null,
                            overview = null, rating = null, genres = null, type = MediaType.MOVIE,
                        ),
                    )
                }
                vm.closeSheet()
            },
            onDismiss = { vm.closeSheet() },
        )
    }
}

@Composable
private fun SearchResultsGrid(
    results: List<SearchResult>,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenPerson: (SearchResult.Person) -> Unit,
) {
    val cols = rememberGridColumnPref()
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(results, key = { "${it::class.simpleName}:${it.id}" }) { r ->
            when (r) {
                is SearchResult.Person -> PersonResult(r, onClick = { onOpenPerson(r) })
                else -> r.toMediaStub()?.let { mi ->
                    SearchPoster(posterUrl = r.posterUrlOrNull(), title = mi.title, onClick = { onOpenDetail(mi) })
                }
            }
        }
    }
}

/** Seznam bohatých řádků (plakát + název + režie + rok · žánry) — parita s Filmotékou. Jen filmy/seriály. */
@Composable
private fun SearchResultsList(results: List<SearchResult>, onOpenDetail: (MediaItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        listItems(results, key = { "${it::class.simpleName}:${it.id}" }) { r ->
            r.toMediaStub()?.let { mi ->
                MediaRow(item = mi, onClick = { onOpenDetail(mi) }, showDirector = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

/** Má výsledek vizuál (plakát/portrét/logo)? RELEVANCE strká bez-vizuálu (obskurní) na konec. */
private fun SearchResult.hasArt(): Boolean = when (this) {
    is SearchResult.Movie -> posterUrl != null
    is SearchResult.Show -> posterUrl != null
    is SearchResult.Person -> profileUrl != null
    is SearchResult.Company -> logoUrl != null
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

/** Osoba (režisér/herec) — kulatý portrét + jméno + role (Režie/Herec…). Klik → filmografie ([PersonFilmographySheet]). */
@Composable
private fun PersonResult(person: SearchResult.Person, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            contentAlignment = Alignment.Center,
        ) {
            if (person.profileUrl != null) {
                AsyncImage(
                    model = person.profileUrl, contentDescription = person.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
            } else {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = person.name, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                }
            }
        }
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        val dep = departmentCz(person.department)
        if (dep.isNotBlank()) {
            Text(dep, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
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

/** known_for_department → výchozí kategorie tvorby při tapu (null = veškerá tvorba). Zrcadlí ui-phone SearchScreen. */
private fun departmentToKind(dep: String?): FavoriteKind? = when (dep?.lowercase()) {
    "acting" -> FavoriteKind.ACTOR
    "directing" -> FavoriteKind.DIRECTOR
    "writing" -> FavoriteKind.WRITER
    "production" -> FavoriteKind.PRODUCER
    "sound" -> FavoriteKind.COMPOSER
    else -> null
}

private fun departmentCz(dep: String?): String = when (dep?.lowercase()) {
    "acting" -> "Herec"
    "directing" -> "Režie"
    "production" -> "Produkce"
    "sound" -> "Hudba"
    "writing" -> "Scénář"
    "camera" -> "Kamera"
    "editing" -> "Střih"
    "art" -> "Výprava"
    "crew" -> "Štáb"
    else -> dep ?: ""
}
