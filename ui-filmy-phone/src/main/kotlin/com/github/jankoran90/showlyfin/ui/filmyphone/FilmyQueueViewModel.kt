package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.db.repository.PlayQueueRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Řazení fronty „K přehrání" (user 2026-08-28: *„urcite dej razeni"*). */
enum class QueueSort(val chipLabel: String) {
    ADDED("Nedávno přidané"),
    ALPHABETICAL("Abecedně"),
    YEAR("Podle roku"),
}

/**
 * RAMPA (SHW-121) — mozek stránky „K přehrání" (telefon).
 *
 * Data jsou fronta z [PlayQueueRepository] (per-profil, sdílená s TV i webem). Položky se balí do
 * JEDNÉ [FilmotekaRail], takže stránka může použít **tytéž** `FilmotekaGrid`/`FilmotekaList` jako
 * Filmotéka — mřížka, seznam i rychloposuvník se nepsaly znovu.
 */
@HiltViewModel
class FilmyQueueViewModel @Inject constructor(
    private val queue: PlayQueueRepository,
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    private val _sort = MutableStateFlow(QueueSort.ADDED)
    val sort: StateFlow<QueueSort> = _sort.asStateFlow()

    /** Přepínač mřížka⇄seznam si drží hodnotu napříč spuštěními (týž mechanismus jako Filmotéka). */
    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { modes -> if (modes[SECTION_KEY] == ViewModeStore.LIST) ViewMode.LIST else ViewMode.GRID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readViewMode())

    /** Fronta jako jedna řada — hotové komponenty Filmotéky ji vykreslí beze změny. */
    val rails: StateFlow<List<FilmotekaRail>> = queue.observe()
        .combine(_sort) { items, sort -> listOf(FilmotekaRail(id = RAIL_ID, title = "", items = items.sortedBy(sort).map { it.toRow() })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Kolik titulů fronta má — prázdná se nikde nezobrazuje (zadání usera). */
    val total: StateFlow<Int> = queue.observe()
        .combine(_sort) { items, _ -> items.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setSort(value: QueueSort) { _sort.value = value }

    fun toggleViewMode() {
        val next = if (readViewMode() == ViewMode.GRID) ViewModeStore.LIST else ViewModeStore.GRID
        viewModeStore.set(SECTION_KEY, next)
    }

    fun remove(item: MediaItem) {
        val tmdb = item.tmdbId ?: return
        queue.remove(tmdb, item.type != MediaType.MOVIE)
    }

    private fun readViewMode(): ViewMode =
        if (viewModeStore.modes.value[SECTION_KEY] == ViewModeStore.LIST) ViewMode.LIST else ViewMode.GRID

    private fun List<FavoriteItem>.sortedBy(sort: QueueSort): List<FavoriteItem> = when (sort) {
        QueueSort.ADDED -> sortedByDescending { it.addedAtMs }
        QueueSort.ALPHABETICAL -> sortedWith { a, b -> czCollator.compare(a.name, b.name) }
        QueueSort.YEAR -> sortedByDescending { it.year ?: 0 }
    }

    private fun FavoriteItem.toRow(): HomeRowItem {
        val isShow = kind == FavoriteKind.QUEUE_SHOW
        return HomeRowItem(
            key = "queue_${kind.name}_$id",
            title = name,
            year = year,
            posterUrl = imageUrl,
            mediaItem = MediaItem(
                traktId = 0L,
                tmdbId = id,
                imdbId = null,
                title = name,
                year = year,
                overview = null,
                rating = null,
                genres = null,
                type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
                // `imageUrl` je HOTOVÁ URL, ne TMDB cesta — do `posterPath` nepatří (skládala by se
                // podruhé a vznikla by nesmyslná adresa). Přesně na tohle je `fallbackPosterUrl`.
                fallbackPosterUrl = imageUrl,
            ),
        )
    }

    private companion object {
        const val RAIL_ID = "queue"
        const val SECTION_KEY = "queue"
        val czCollator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
    }
}
