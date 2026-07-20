package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.PersonRole
import com.github.jankoran90.showlyfin.data.tmdb.model.czLabel
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** COMPASS C3 (SHW-44) — rozsah univerzálního hledání (volba hlavního parametru); výchozí = Filmy. */
enum class SearchScope(val label: String) {
    FILMS("Filmy"), SHOWS("Seriály"), PEOPLE("Lidi"), COMPANIES("Vydavatelství")
}

/**
 * COMPASS C4 (SHW-44) — kritérium řazení výsledků. `scopes` říká, u kterých rozsahů kritérium dává
 * smysl (rok/hodnocení jen u filmů/seriálů; oblíbenost i u lidí; název+relevance všude). Řadí se
 * KLIENTSKY nad už staženými výsledky → změna řazení/směru NEvolá síť (viz [SearchViewModel.applySort]).
 */
enum class SearchSort(val label: String, val scopes: Set<SearchScope>) {
    RELEVANCE("Relevance", SearchScope.entries.toSet()),
    NAME("Název", SearchScope.entries.toSet()),
    YEAR("Rok", setOf(SearchScope.FILMS, SearchScope.SHOWS)),
    RATING("Hodnocení", setOf(SearchScope.FILMS, SearchScope.SHOWS)),
    POPULARITY("Oblíbenost", setOf(SearchScope.FILMS, SearchScope.SHOWS, SearchScope.PEOPLE)),
    ;

    fun appliesTo(scope: SearchScope): Boolean = scope in scopes
}

/** Sjednocený výsledek hledání (film/seriál/osoba/vydavatelství). Numerická pole = klíče pro řazení. */
sealed interface SearchResult {
    val id: Long
    data class Movie(
        override val id: Long, val title: String, val year: String?, val posterUrl: String?,
        val rating: Double? = null, val popularity: Double? = null,
    ) : SearchResult
    data class Show(
        override val id: Long, val title: String, val year: String?, val posterUrl: String?,
        val rating: Double? = null, val popularity: Double? = null,
    ) : SearchResult
    data class Person(
        override val id: Long, val name: String, val profileUrl: String?, val department: String?,
        val popularity: Double? = null,
    ) : SearchResult
    data class Company(override val id: Long, val name: String, val logoUrl: String?) : SearchResult
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.FILMS,
    val sortBy: SearchSort = SearchSort.RELEVANCE,
    val sortDesc: Boolean = false,
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
)

/**
 * COMPASS C3+C4 (SHW-44) — ViewModel univerzálního hledání. Reaktivní hledání s debounce (350 ms),
 * přepínatelný rozsah (hlavní parametr), sjednocené výsledky + **řazení vzestup/sestup dle kritéria**
 * (Relevance/Název/Rok/Hodnocení/Oblíbenost; klientsky nad staženými výsledky → bez další sítě).
 * Hvězda přidá výsledek do [FavoritesStore] (u osoby s volbou role → naplní i kategorie Producenti/
 * Skladatelé). Tap na osobu/vydavatelství otevře jejich tvorbu (reused `PersonFilmographySheet`).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tmdb: TmdbRemoteDataSource,
    private val favoritesStore: FavoritesRepository,
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    /**
     * QUARRY (user 2026-07-20) — perzistentní přepínač mřížka/seznam sekce Hledat (appka Filmy; klíč
     * `SECTION_SEARCH`). Default GRID (výsledky jsou plakáty). Sdílený VM; hlavní telefonní SearchScreen
     * ho nepoužívá (jede vždy grid).
     */
    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { modes -> modes[ViewModeStore.SECTION_SEARCH]?.let { ViewMode.fromKey(it) } ?: ViewMode.GRID }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    fun setViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_SEARCH, mode.storeKey)

    private val queryFlow = MutableStateFlow("")
    private val scopeFlow = MutableStateFlow(SearchScope.FILMS)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Výsledky v pořadí, jak je vrátil TMDB (= relevance). Řazení se aplikuje až nad nimi → re-sort bez sítě. */
    private var rawResults: List<SearchResult> = emptyList()

    /** Reaktivní seznam oblíbených → hvězdy v hledání se hned překreslí. */
    val favorites: StateFlow<List<FavoriteItem>> = favoritesStore.items

    private val _sheet = MutableStateFlow(WorksSheetState())
    val sheet: StateFlow<WorksSheetState> = _sheet.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(350).distinctUntilChanged(),
                scopeFlow,
            ) { q, s -> q to s }.collectLatest { (q, s) -> runSearch(q, s) }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        queryFlow.value = q
    }

    fun onScopeChange(s: SearchScope) {
        if (_state.value.scope == s) return
        // Kritéria řazení se mezi rozsahy liší (rok/hodnocení jen u filmů/seriálů) → reset na Relevanci,
        // ať nezůstane viset kritérium, které nový rozsah nemá.
        _state.value = _state.value.copy(scope = s, sortBy = SearchSort.RELEVANCE, sortDesc = false)
        scopeFlow.value = s
    }

    fun onSortChange(sort: SearchSort) {
        val st = _state.value
        if (st.sortBy == sort) return
        _state.value = st.copy(sortBy = sort, results = applySort(rawResults, sort, st.sortDesc))
    }

    fun toggleSortDirection() {
        val st = _state.value
        val desc = !st.sortDesc
        _state.value = st.copy(sortDesc = desc, results = applySort(rawResults, st.sortBy, desc))
    }

    private suspend fun runSearch(q: String, s: SearchScope) {
        if (q.isBlank()) {
            rawResults = emptyList()
            _state.value = _state.value.copy(loading = false, results = emptyList())
            return
        }
        _state.value = _state.value.copy(loading = true)
        val results: List<SearchResult> = when (s) {
            SearchScope.FILMS -> tmdb.searchMovies(q).map {
                SearchResult.Movie(
                    it.id, it.title ?: it.original_title ?: "", it.release_date?.take(4), poster(it.poster_path),
                    rating = it.vote_average?.toDouble(), popularity = it.popularity?.toDouble(),
                )
            }
            SearchScope.SHOWS -> tmdb.searchShows(q).map {
                SearchResult.Show(
                    it.id, it.name ?: it.original_name ?: "", it.first_air_date?.take(4), poster(it.poster_path),
                    rating = it.vote_average?.toDouble(), popularity = it.popularity?.toDouble(),
                )
            }
            SearchScope.PEOPLE -> tmdb.searchPeople(q).map {
                SearchResult.Person(it.id, it.name ?: "", profile(it.profile_path), it.known_for_department, it.popularity?.toDouble())
            }
            SearchScope.COMPANIES -> tmdb.searchCompanies(q).map {
                SearchResult.Company(it.id, it.name ?: "", logo(it.logo_path))
            }
        }
        rawResults = results
        val st = _state.value
        _state.value = st.copy(loading = false, results = applySort(results, st.sortBy, st.sortDesc))
    }

    /** Klientské řazení nad [rawResults]. Relevance = pořadí TMDB (směr ho jen obrací); nulové klíče vždy nakonec. */
    private fun applySort(raw: List<SearchResult>, sort: SearchSort, desc: Boolean): List<SearchResult> = when (sort) {
        SearchSort.RELEVANCE -> if (desc) raw.reversed() else raw
        SearchSort.NAME -> raw.sortedWith(
            compareBy(if (desc) reverseOrder() else naturalOrder()) { it.displayName().lowercase() },
        )
        SearchSort.YEAR -> sortByNumeric(raw, desc) { it.yearKey() }
        SearchSort.RATING -> sortByNumeric(raw, desc) { it.ratingKey() }
        SearchSort.POPULARITY -> sortByNumeric(raw, desc) { it.popularityKey() }
    }

    private inline fun sortByNumeric(
        raw: List<SearchResult>,
        desc: Boolean,
        crossinline key: (SearchResult) -> Double?,
    ): List<SearchResult> = raw.sortedWith(
        compareBy(nullsLast(if (desc) reverseOrder<Double>() else naturalOrder())) { key(it) },
    )

    private fun SearchResult.displayName(): String = when (this) {
        is SearchResult.Movie -> title
        is SearchResult.Show -> title
        is SearchResult.Person -> name
        is SearchResult.Company -> name
    }

    private fun SearchResult.yearKey(): Double? = when (this) {
        is SearchResult.Movie -> year?.toIntOrNull()?.toDouble()
        is SearchResult.Show -> year?.toIntOrNull()?.toDouble()
        else -> null
    }

    private fun SearchResult.ratingKey(): Double? = when (this) {
        is SearchResult.Movie -> rating
        is SearchResult.Show -> rating
        else -> null
    }

    private fun SearchResult.popularityKey(): Double? = when (this) {
        is SearchResult.Movie -> popularity
        is SearchResult.Show -> popularity
        is SearchResult.Person -> popularity
        else -> null
    }

    fun isFavorite(kind: FavoriteKind, id: Long) = favoritesStore.isFavorite(kind, id)

    fun toggleFavorite(item: FavoriteItem) { favoritesStore.toggle(item) }

    /** Otevři tvorbu osoby (role dle [kind]; null = veškerá tvorba) nebo vydavatelství (produkce). */
    fun openWorks(kind: FavoriteKind?, id: Long, name: String) {
        if (id <= 0L) return
        val role = if (kind == null) PersonRole.GENERIC else favoriteKindToRole(kind)
        _sheet.value = WorksSheetState(open = true, name = name, loading = true, roleLabel = role.czLabel())
        viewModelScope.launch {
            val movies = runCatching {
                if (kind == FavoriteKind.COMPANY) tmdb.discoverMoviesByCompany(id)
                else tmdb.moviesByPersonRole(id, role)
            }.getOrDefault(emptyList())
            _sheet.value = _sheet.value.copy(loading = false, collection = moviesToWorksCollection(name, movies))
        }
    }

    fun closeSheet() { _sheet.value = WorksSheetState() }

    private fun poster(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    private fun profile(path: String?) = path?.let { "https://image.tmdb.org/t/p/w185$it" }
    private fun logo(path: String?) = path?.let { "https://image.tmdb.org/t/p/w300$it" }
}
