package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * COMPASS C2 (SHW-44) — ViewModel sekce Oblíbení. Drží reaktivní seznam z [FavoritesStore] a po kliknutí
 * na osobu/vydavatelství dotáhne jejich tvorbu (`discoverMoviesByPerson/Company`) jako kolekci karet
 * (znovupoužitý `PersonFilmographySheet` z ENSEMBLE).
 */
@HiltViewModel
class OblibeniViewModel @Inject constructor(
    private val favorites: FavoritesStore,
    private val tmdb: TmdbRemoteDataSource,
) : ViewModel() {

    val items: StateFlow<List<FavoriteItem>> = favorites.items

    private val _sheet = MutableStateFlow(WorksSheetState())
    val sheet: StateFlow<WorksSheetState> = _sheet.asStateFlow()

    fun remove(item: FavoriteItem) = favorites.remove(item.kind, item.id)

    /** Otevři tvorbu osoby (filmografie) nebo vydavatelství (produkce). */
    fun openWorks(item: FavoriteItem) {
        if (item.id <= 0L) return
        _sheet.value = WorksSheetState(open = true, name = item.name, loading = true)
        viewModelScope.launch {
            val movies = runCatching {
                if (item.kind == FavoriteKind.COMPANY) tmdb.discoverMoviesByCompany(item.id)
                else tmdb.discoverMoviesByPerson(item.id)
            }.getOrDefault(emptyList())
            _sheet.value = _sheet.value.copy(loading = false, collection = toCollection(item.name, movies))
        }
    }

    fun closeSheet() { _sheet.value = WorksSheetState() }

    private fun toCollection(name: String, movies: List<TmdbSearchMovieItem>): MediaCollection? {
        val parts = movies
            .filter { !it.poster_path.isNullOrBlank() }
            .take(30)
            .map { m ->
                CollectionPart(
                    key = "tmdb_${m.id}",
                    tmdbId = m.id,
                    jellyfinId = null,
                    title = m.title ?: "",
                    posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                    year = m.release_date?.take(4),
                )
            }
        return if (parts.isEmpty()) null else MediaCollection(name = name, parts = parts)
    }
}

/** Stav spodního listu „tvorba osoby / vydavatelství". */
data class WorksSheetState(
    val open: Boolean = false,
    val name: String? = null,
    val loading: Boolean = false,
    val collection: MediaCollection? = null,
)
