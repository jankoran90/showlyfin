package com.github.jankoran90.showlyfin.feature.discover

import com.github.jankoran90.showlyfin.core.domain.MediaItem

/** Stav sekce „Na RD" (Plan QUASAR Fáze D) — uložené filmy na RealDebrid účtu. */
data class RdLibraryUiState(
    val isLoading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val unmatchedCount: Int = 0,
    val error: String? = null,
)
