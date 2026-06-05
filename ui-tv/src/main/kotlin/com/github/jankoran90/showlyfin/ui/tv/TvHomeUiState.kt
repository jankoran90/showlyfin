package com.github.jankoran90.showlyfin.ui.tv

data class TvJellyfinItem(
    val id: String,
    val name: String,
    val imageUrl: String,
    val progressPct: Int?,
    val type: String,
)

data class TvHomeRow(
    val title: String,
    val items: List<TvJellyfinItem>,
)

data class PlayMessageEvent(val itemId: String, val positionMs: Long)

data class TvHomeUiState(
    val isLoading: Boolean = true,
    val rows: List<TvHomeRow> = emptyList(),
    val error: String? = null,
    val isNotConfigured: Boolean = false,
)
