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
    // COUCH T3 (user 2026-07-13): v Objevovat NECHCI co už mám na Traktu → watchlist / zhlédnuté /
    // hodnocené skryté DEFAULTNĚ. (hideInJellyfin ponechán vypnutý — vlastněné v JF si můžu chtít pustit.)
    val hideInWatchlist: Boolean = true,
    val hideWatched: Boolean = true,
    val hideRated: Boolean = true,
    val sortBy: DiscoverSort = DiscoverSort.DEFAULT,
) {
    /** Aktivní = liší se od výchozích filtrů (robustní vůči změně defaultů). */
    val isActive: Boolean get() = this != DEFAULT

    companion object {
        val DEFAULT = DiscoverFilters()
    }
}
