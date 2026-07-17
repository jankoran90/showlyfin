package com.github.jankoran90.showlyfin.ui.tv.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * TENFOOT — TV „Oblíbené". BUG fix: telefonní „Oblíbené" čte per-profil [FavoritesStore] (sync přes
 * backend), ne Trakt watchlist. TV dřív ukazovala Trakt watchlist (= telefonní „Chci vidět") → prázdno.
 * Teď TV čte TÝŽ zdroj = uživatelovy oblíbené filmy jeho profilu. Sdílí [FavoritesStore] singleton.
 */
@HiltViewModel
class TvFavoritesViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
) : ViewModel() {

    init {
        // DINGO — dotáhni oblíbené aktivního profilu ze serveru (per-profil sync), stejně jako telefon.
        favorites.refresh()
    }

    /** Oblíbené FILMY, nejnověji přidané první. */
    val movies: StateFlow<List<FavoriteItem>> = favorites.items
        .map { list -> list.filter { it.kind == FavoriteKind.MOVIE }.sortedByDescending { it.addedAtMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
