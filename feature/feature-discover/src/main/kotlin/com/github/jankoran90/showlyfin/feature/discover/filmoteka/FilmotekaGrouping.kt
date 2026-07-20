package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.regionsOf
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/**
 * MIRROR (user 2026-07-20) — SDÍLENÝ grouper/filtr Filmotéky, aby stejné nástroje (osy Vše/Žánr/Země,
 * filtr žánru + země dle HLAVNÍ hodnoty, počítadlo, řady) šly použít 1:1 i v sekci „Pro tebe".
 * Čistá logika bez závislostí (osy/řazení/regiony jdou parametrem) → [TvFilmotekaViewModel] i
 * [com.github.jankoran90.showlyfin.feature.discover.foryou.ForYouViewModel] volají tentýž `build`,
 * takže obě sekce grupují/filtrují identicky (žádný drift).
 *
 * Filtr i grupování berou „hlavní hodnotu s největší vahou" — u žánru první žánr ([mainGenreOf]),
 * u země první region ([mainRegionOf]) → titul je v JEDNÉ sekci, ne duplikát napříč všemi svými hodnotami.
 */
data class FilmotekaGroupingResult(
    val rails: List<FilmotekaRail>,
    val total: Int,
    val availableGenres: List<String>,
    val availableCountries: List<CinematographyRegion>,
)

object FilmotekaGrouping {

    /** Hlavní žánr titulu = první neprázdný (nejvyšší váha dle TMDB řazení relevance). */
    fun mainGenreOf(item: MediaItem): String? =
        item.genres.orEmpty().firstOrNull { it.isNotBlank() }?.trim()

    /** Hlavní region titulu = první region dle váhy (analogie [mainGenreOf]); prázdné/neznámé → OSTATNI. */
    fun mainRegionOf(item: MediaItem): CinematographyRegion =
        regionsOf(item.originCountries).firstOrNull() ?: CinematographyRegion.OSTATNI

    /** Hlavní žánry v bázi dle četnosti sestupně (tie-break český Collator) — nabídka pickeru. */
    fun availableGenresOf(items: List<MediaItem>): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (item in items) { val g = mainGenreOf(item) ?: continue; counts[g] = (counts[g] ?: 0) + 1 }
        val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenComparator { a, b -> coll.compare(a.key, b.key) })
            .map { it.key }
    }

    /** Hlavní regiony v bázi dle četnosti sestupně; respektuje zapnuté regiony, OSTATNI vždy — nabídka pickeru. */
    fun availableCountriesOf(items: List<MediaItem>, enabled: Set<CinematographyRegion>): List<CinematographyRegion> {
        val counts = LinkedHashMap<CinematographyRegion, Int>()
        for (item in items) {
            val r = mainRegionOf(item)
            if (r != CinematographyRegion.OSTATNI && r !in enabled) continue
            counts[r] = (counts[r] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * Sestav řady dané osy z už dedup+gate báze [all]. Filtry (žánr/země) se aplikují PŘED grupováním (uniformně
     * na všechny osy, skládají se AND). `total` = velikost báze PŘED filtrem. Nabídky = z plné báze.
     */
    fun build(
        all: List<MediaItem>,
        axis: FilmotekaAxis,
        allSort: FilmotekaAllSort,
        genreFilter: Set<String>,
        countryFilter: Set<CinematographyRegion>,
        enabledRegions: Set<CinematographyRegion>,
    ): FilmotekaGroupingResult {
        val available = availableGenresOf(all)
        val availableC = availableCountriesOf(all, enabledRegions)
        val filtered = all
            .let { if (genreFilter.isEmpty()) it else it.filter { m -> mainGenreOf(m) in genreFilter } }
            .let { if (countryFilter.isEmpty()) it else it.filter { m -> mainRegionOf(m) in countryFilter } }
        val rails = when (axis) {
            FilmotekaAxis.ALL -> groupAll(filtered, allSort)
            FilmotekaAxis.GENRE -> groupByGenre(filtered)
            FilmotekaAxis.COUNTRY -> groupByCountry(filtered, enabledRegions)
        }
        return FilmotekaGroupingResult(rails, all.size, available, availableC)
    }

    /** Osa „Vše": jedna plochá řada, řazení Nedávno (addedAtMs) / Abecedně (český Collator). */
    private fun groupAll(items: List<MediaItem>, allSort: FilmotekaAllSort): List<FilmotekaRail> {
        if (items.isEmpty()) return emptyList()
        val sorted = when (allSort) {
            FilmotekaAllSort.RECENT -> items.sortedByDescending { it.addedAtMs ?: Long.MIN_VALUE }
            FilmotekaAllSort.ALPHABETICAL -> {
                val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
                items.sortedWith(Comparator { a, b -> coll.compare(a.displayTitle, b.displayTitle) })
            }
        }
        val title = when (allSort) {
            FilmotekaAllSort.RECENT -> "Nedávno přidané"
            FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
        }
        return listOf(FilmotekaRail(id = "filmo_all", title = title, items = sorted.map { it.toHomeRowItem("all") }))
    }

    /** Řady dle HLAVNÍHO žánru, sestupně dle četnosti. Film v jedné sekci. */
    private fun groupByGenre(items: List<MediaItem>): List<FilmotekaRail> {
        val byGenre = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in items) {
            val g = mainGenreOf(item)
            if (!g.isNullOrBlank()) byGenre.getOrPut(g) { mutableListOf() }.add(item)
        }
        return byGenre.entries
            .sortedByDescending { it.value.size }
            .map { (genre, list) ->
                FilmotekaRail(id = "filmo_genre_$genre", title = genre, items = list.map { it.toHomeRowItem(genre) })
            }
            .filter { it.items.isNotEmpty() }
    }

    /** Řady dle HLAVNÍHO regionu (film v jedné sekci); vypnuté regiony skryj, OSTATNI vždy. Řazení = pořadí enumu. */
    private fun groupByCountry(items: List<MediaItem>, enabled: Set<CinematographyRegion>): List<FilmotekaRail> {
        val byRegion = LinkedHashMap<CinematographyRegion, MutableList<MediaItem>>()
        for (item in items) {
            val region = mainRegionOf(item)
            if (region != CinematographyRegion.OSTATNI && region !in enabled) continue
            byRegion.getOrPut(region) { mutableListOf() }.add(item)
        }
        return CinematographyRegion.entries.mapNotNull { region ->
            val list = byRegion[region] ?: return@mapNotNull null
            FilmotekaRail(
                id = "filmo_country_${region.name}",
                title = region.label,
                items = list.map { it.toHomeRowItem("country_${region.name}") },
            )
        }.filter { it.items.isNotEmpty() }
    }

    private fun MediaItem.toHomeRowItem(axisValue: String) = HomeRowItem(
        key = "filmo_${axisValue}_${tmdbId ?: imdbId ?: traktId}",
        title = displayTitle,
        year = year,
        posterUrl = posterUrl("w342"),
        landscapeUrl = backdropUrl("w780"),
        mediaItem = this,
    )
}
