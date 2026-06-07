package com.github.jankoran90.showlyfin.feature.jellyfin

enum class JellyfinSort(val label: String) {
    NAME("Název A–Z"),
    DATE_ADDED("Nedávno přidané"),
    YEAR_DESC("Nejnovější"),
    RATING("Hodnocení"),
    RANDOM("Náhodně"),
}

enum class JellyfinTypeFilter(val label: String) {
    ALL("Vše"),
    MOVIE("Filmy"),
    SERIES("Seriály"),
}

data class JellyfinItem(
    val id: String,
    val name: String,
    val imageUrl: String,
    val year: Int?,
    val type: String,
    val isFolder: Boolean,
    val progressPct: Int?,
    val watched: Boolean = false,
    val tmdbId: Long? = null,
    val imdbId: String? = null,
)

data class JellyfinLibraryItemsUiState(
    val libraryName: String = "",
    val items: List<JellyfinItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sort: JellyfinSort = JellyfinSort.NAME,
    val typeFilter: JellyfinTypeFilter = JellyfinTypeFilter.ALL,
    val isBoxSetContext: Boolean = false,
    val detailRich: Boolean = true,
)
