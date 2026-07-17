package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.czLabel
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
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
    private val favorites: FavoritesRepository,
    private val tmdb: TmdbRemoteDataSource,
) : ViewModel() {

    val items: StateFlow<List<FavoriteItem>> = favorites.items

    init {
        // DINGO — při otevření obrazovky dotáhni oblíbené aktuálního profilu ze serveru (per-profil sync).
        favorites.refresh()
    }

    private val _sheet = MutableStateFlow(WorksSheetState())
    val sheet: StateFlow<WorksSheetState> = _sheet.asStateFlow()

    fun remove(item: FavoriteItem) = favorites.remove(item.kind, item.id)

    /** Otevři tvorbu osoby (filmografie) nebo vydavatelství (produkce). */
    fun openWorks(item: FavoriteItem) {
        if (item.id <= 0L) return
        // VANTAGE (SHW-48): role z kategorie Oblíbeného → rolově konkrétní tvorba + rolový titulek
        // (režisér → režíroval, herec → hrál, skladatel → hudba …). Vydavatelství = produkce studia.
        // Mapování sdíleno s hledáním (COMPASS C3) přes WorksMapping.kt.
        val role = favoriteKindToRole(item.kind)
        _sheet.value = WorksSheetState(open = true, name = item.name, loading = true, roleLabel = role.czLabel())
        viewModelScope.launch {
            val movies = runCatching {
                if (item.kind == FavoriteKind.COMPANY) tmdb.discoverMoviesByCompany(item.id)
                else tmdb.moviesByPersonRole(item.id, role)
            }.getOrDefault(emptyList())
            _sheet.value = _sheet.value.copy(loading = false, collection = moviesToWorksCollection(item.name, movies))
        }
    }

    fun closeSheet() { _sheet.value = WorksSheetState() }
}

/** Stav spodního listu „tvorba osoby / vydavatelství". */
data class WorksSheetState(
    val open: Boolean = false,
    val name: String? = null,
    val loading: Boolean = false,
    val collection: MediaCollection? = null,
    // VANTAGE (SHW-48): rolový titulek listu (Herecká tvorba / Režie / Hudba …).
    val roleLabel: String? = null,
)
