package com.github.jankoran90.showlyfin.feature.discover

enum class DiscoverSort(val displayName: String) {
    DEFAULT("Výchozí"),
    RATING_DESC("Hodnocení"),
    YEAR_DESC("Nejnovější"),
    YEAR_ASC("Nejstarší"),
    ALPHABETICAL("Abecedně"),
}

data class DiscoverFilters(
    val selectedGenres: Set<String> = emptySet(),
    val yearMin: Int = 1950,
    val yearMax: Int = 2030,
    val minRating: Float = 0f,
    val hideInJellyfin: Boolean = false,
    val hideInWatchlist: Boolean = false,
    val hideWatched: Boolean = false,
    val sortBy: DiscoverSort = DiscoverSort.DEFAULT,
) {
    val isActive: Boolean
        get() = selectedGenres.isNotEmpty() ||
            yearMin != 1950 ||
            yearMax != 2030 ||
            minRating > 0f ||
            hideInJellyfin ||
            hideInWatchlist ||
            hideWatched ||
            sortBy != DiscoverSort.DEFAULT
}
