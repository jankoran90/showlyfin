package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.PersonRole
import com.github.jankoran90.showlyfin.data.tmdb.model.czLabel
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/** COMPASS C3 (SHW-44) — rozsah univerzálního hledání; výchozí = Filmy. */
enum class SearchScope(val label: String) {
    FILMS("Filmy"), SHOWS("Seriály"), PEOPLE("Lidi"), COMPANIES("Vydavatelství")
}

/** Sjednocený výsledek hledání (film/seriál/osoba/vydavatelství). */
sealed interface SearchResult {
    val id: Long
    data class Movie(override val id: Long, val title: String, val year: String?, val posterUrl: String?) : SearchResult
    data class Show(override val id: Long, val title: String, val year: String?, val posterUrl: String?) : SearchResult
    data class Person(override val id: Long, val name: String, val profileUrl: String?, val department: String?) : SearchResult
    data class Company(override val id: Long, val name: String, val logoUrl: String?) : SearchResult
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.FILMS,
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
)

/**
 * COMPASS C3 (SHW-44) — ViewModel univerzálního hledání. Reaktivní hledání s debounce (350 ms),
 * přepínatelný rozsah, sjednocené výsledky. Hvězda přidá výsledek do [FavoritesStore] (u osoby s volbou
 * role → naplní i kategorie Producenti/Skladatelé). Tap na osobu/vydavatelství otevře jejich tvorbu
 * (reused `PersonFilmographySheet`, sdílený stav [WorksSheetState] s Oblíbenými).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tmdb: TmdbRemoteDataSource,
    private val favoritesStore: FavoritesStore,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val scopeFlow = MutableStateFlow(SearchScope.FILMS)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

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
        _state.value = _state.value.copy(scope = s)
        scopeFlow.value = s
    }

    private suspend fun runSearch(q: String, s: SearchScope) {
        if (q.isBlank()) {
            _state.value = _state.value.copy(loading = false, results = emptyList())
            return
        }
        _state.value = _state.value.copy(loading = true)
        val results: List<SearchResult> = when (s) {
            SearchScope.FILMS -> tmdb.searchMovies(q).map {
                SearchResult.Movie(it.id, it.title ?: it.original_title ?: "", it.release_date?.take(4), poster(it.poster_path))
            }
            SearchScope.SHOWS -> tmdb.searchShows(q).map {
                SearchResult.Show(it.id, it.name ?: it.original_name ?: "", it.first_air_date?.take(4), poster(it.poster_path))
            }
            SearchScope.PEOPLE -> tmdb.searchPeople(q).map {
                SearchResult.Person(it.id, it.name ?: "", profile(it.profile_path), it.known_for_department)
            }
            SearchScope.COMPANIES -> tmdb.searchCompanies(q).map {
                SearchResult.Company(it.id, it.name ?: "", logo(it.logo_path))
            }
        }
        _state.value = _state.value.copy(loading = false, results = results)
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
