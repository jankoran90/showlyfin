package com.github.jankoran90.showlyfin.feature.discover

import com.github.jankoran90.showlyfin.core.domain.MediaItem

/** Řazení sekce „Na RD". */
enum class RdSort(val label: String) {
    ADDED("Naposledy přidané"),
    TITLE("Název A–Z"),
    YEAR("Rok ↓"),
}

/** Stav sekce „Na RD" (Plan QUASAR Fáze D) — uložené filmy na RealDebrid účtu. */
data class RdLibraryUiState(
    val isLoading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val unmatchedCount: Int = 0,
    val error: String? = null,
    // VANTAGE/SWEEP: parita s ostatními sekcemi — hledání + řazení (grid/list drží ViewModeStore).
    val searchQuery: String = "",
    val sortBy: RdSort = RdSort.ADDED,
) {
    /** Filmy po aplikaci hledání + řazení (to, co se reálně vykreslí). */
    val displayedItems: List<MediaItem>
        get() {
            val q = searchQuery.trim()
            val filtered = if (q.isBlank()) items
            else items.filter { it.title.contains(q, ignoreCase = true) }
            return when (sortBy) {
                RdSort.ADDED -> filtered // pořadí z backendu = naposledy přidané
                RdSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
                RdSort.YEAR -> filtered.sortedByDescending { it.year ?: 0 }
            }
        }
}
