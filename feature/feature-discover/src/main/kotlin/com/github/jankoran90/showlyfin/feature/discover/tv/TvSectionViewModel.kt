package com.github.jankoran90.showlyfin.feature.discover.tv

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.TvSectionSort
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * FOYER (SHW-107, user 2026-07-26) — JAK SE ZOBRAZUJE TV SEKCE. Jeden malý VM pro všechny ploché TV
 * sekce (Filmotéka „Vše", Pro tebe, Chci vidět, Oblíbené, Vzácné klenoty, mřížka řady z domova):
 *
 * - **výchozí = MŘÍŽKA + ABECEDNĚ** (přání usera; platí JEN na TV — telefon má vlastní klíče),
 * - přepnutí uživatele se ULOŽÍ a od té chvíle platí (volba „4b"),
 * - persistence sdílí [ViewModeStore] (vlastní prefs soubor, přežije odhlášení Traktu), ale pod
 *   TV-prefixovanými klíči ([ViewModeStore.tvViewModeKey] / [ViewModeStore.tvSortKey]).
 */
@HiltViewModel
class TvSectionViewModel @Inject constructor(
    private val store: ViewModeStore,
) : ViewModel() {

    /** Syrová mapa klíč→hodnota; UI si z ní přes [viewModeOf]/[sortOf] přečte svou sekci. */
    val modes: StateFlow<Map<String, String>> = store.modes

    /** Zobrazení sekce na TV — nic uloženého = MŘÍŽKA. */
    fun viewModeOf(modes: Map<String, String>, sectionKey: String): ViewMode =
        modes[ViewModeStore.tvViewModeKey(sectionKey)]?.let { ViewMode.fromKey(it) } ?: ViewMode.GRID

    /** Řazení sekce na TV — nic uloženého = ABECEDNĚ. */
    fun sortOf(modes: Map<String, String>, sectionKey: String): TvSectionSort =
        TvSectionSort.fromKey(modes[ViewModeStore.tvSortKey(sectionKey)])

    fun setViewMode(sectionKey: String, mode: ViewMode) =
        store.set(ViewModeStore.tvViewModeKey(sectionKey), mode.storeKey)

    fun setSort(sectionKey: String, sort: TvSectionSort) =
        store.set(ViewModeStore.tvSortKey(sectionKey), sort.storeKey)
}

/** FOYER — seřaď položky sekce dle TV volby. Chybějící hodnota (rok/hodnocení/datum) jde na konec. */
fun List<HomeRowItem>.sortedBy(sort: TvSectionSort): List<HomeRowItem> = when (sort) {
    TvSectionSort.ABECEDNE -> sortedBy { it.title.lowercase() }
    TvSectionSort.NEDAVNO -> sortedByDescending { it.mediaItem?.addedAtMs ?: 0L }
    TvSectionSort.ROK -> sortedByDescending { it.year ?: 0 }
    TvSectionSort.HODNOCENI -> sortedByDescending { it.mediaItem?.rating ?: -1f }
    TvSectionSort.STOPAZ -> sortedBy { it.mediaItem?.runtimeMinutes ?: Int.MAX_VALUE }
    TvSectionSort.VYCHOZI -> this
}

/** FOYER — totéž pro sekce, které pracují rovnou s [MediaItem] (Pro tebe, Chci vidět, Oblíbené). */
@JvmName("sortedMediaItemsBy")
fun List<MediaItem>.sortedBy(sort: TvSectionSort): List<MediaItem> = when (sort) {
    TvSectionSort.ABECEDNE -> sortedBy { it.displayTitle.lowercase() }
    TvSectionSort.NEDAVNO -> sortedByDescending { it.addedAtMs ?: 0L }
    TvSectionSort.ROK -> sortedByDescending { it.year ?: 0 }
    TvSectionSort.HODNOCENI -> sortedByDescending { it.rating ?: -1f }
    TvSectionSort.STOPAZ -> sortedBy { it.runtimeMinutes ?: Int.MAX_VALUE }
    TvSectionSort.VYCHOZI -> this
}
