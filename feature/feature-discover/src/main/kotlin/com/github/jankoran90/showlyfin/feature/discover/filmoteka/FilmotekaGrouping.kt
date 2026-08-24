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

    /**
     * Hlavní žánr titulu. `hybrid=true` (RUBRIC SHW-104) → unifikovaný hybridní žánr dle kaskády priorit
     * ([GenreNormalizer]); `hybrid=false` → první žánr v pořadí, sjednocený na český název. Kanonizace
     * vždy srovná Trakt EN slugy s TMDB českými názvy do jedné řady.
     */
    fun mainGenreOf(item: MediaItem, hybrid: Boolean): String? =
        GenreNormalizer.mainGenre(item.genres, hybrid)

    /** Hlavní region titulu = první region dle váhy (analogie [mainGenreOf]); prázdné/neznámé → OSTATNI. */
    fun mainRegionOf(item: MediaItem): CinematographyRegion =
        regionsOf(item.originCountries).firstOrNull() ?: CinematographyRegion.OSTATNI

    /** Hlavní žánry v bázi dle četnosti sestupně (tie-break český Collator) — nabídka pickeru. */
    fun availableGenresOf(items: List<MediaItem>, hybrid: Boolean): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (item in items) { val g = mainGenreOf(item, hybrid) ?: continue; counts[g] = (counts[g] ?: 0) + 1 }
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
        hybridGenres: Boolean,
        collectionGroups: List<FilmotekaCollectionGroup> = emptyList(),
    ): FilmotekaGroupingResult {
        val available = availableGenresOf(all, hybridGenres)
        val availableC = availableCountriesOf(all, enabledRegions)
        // ATRIUM (SHW-118): sdružení PŘED filtrem i grupováním → karta kolekce se chová jako každý
        // jiný titul (filtruje se, řadí se, je ve VŠECH osách), jen zastupuje své díly.
        val entries = withCollections(all, collectionGroups)
        val filtered = entries
            .let { if (genreFilter.isEmpty()) it else it.filter { e -> mainGenreOf(e.item, hybridGenres) in genreFilter } }
            .let { if (countryFilter.isEmpty()) it else it.filter { e -> mainRegionOf(e.item) in countryFilter } }
        val rails = when (axis) {
            FilmotekaAxis.ALL -> groupAll(filtered, allSort)
            FilmotekaAxis.GENRE -> groupByGenre(filtered, hybridGenres)
            FilmotekaAxis.COUNTRY -> groupByCountry(filtered, enabledRegions)
        }
        return FilmotekaGroupingResult(rails, entries.size, available, availableC)
    }

    /**
     * Nahraď členy kolekce JEDNOU zastupující položkou (user 2026-08-24: „slouč její děti do jedné
     * mateřské karty kolekce"). Titul, který v žádné sdružené kolekci není, projde beze změny.
     */
    private fun withCollections(
        all: List<MediaItem>,
        groups: List<FilmotekaCollectionGroup>,
    ): List<Entry> {
        if (groups.isEmpty()) return all.map { Entry(it) }
        val claimed = groups.flatMapTo(HashSet()) { it.memberKeys }
        val singles = all.filter { filmotekaDedupKey(it) !in claimed }.map { Entry(it) }
        return singles + groups.map { Entry(it.asMediaItem(), it) }
    }

    /**
     * Kolekce jako běžná položka pro řazení/filtrování: žánry, země a rok si bere od svých dílů, takže
     * spadne do stejné sekce, kam by spadl její hlavní díl. Datum „přidáno" = nejmladší díl.
     */
    private fun FilmotekaCollectionGroup.asMediaItem() = MediaItem(
        traktId = 0L,
        tmdbId = null,
        imdbId = null,
        title = collectionCardTitle(name),
        year = year,
        // Místo popisu výčet dílů — seznamový řádek má stejný prostor jako u filmu a musí něco říkat.
        overview = members.joinToString(" · ") { it.displayTitle },
        overviewCz = members.joinToString(" · ") { it.displayTitle },
        rating = null,
        genres = members.firstNotNullOfOrNull { it.genres?.takeIf(List<String>::isNotEmpty) },
        type = com.github.jankoran90.showlyfin.core.domain.MediaType.MOVIE,
        originCountries = members.firstNotNullOfOrNull { it.originCountries?.takeIf(List<String>::isNotEmpty) },
        fallbackPosterUrl = posterUrl,
        addedAtMs = addedAtMs,
    )

    /** Položka seznamu: buď samotný titul, nebo kolekce zastupující své díly ([group] neprázdné). */
    private data class Entry(val item: MediaItem, val group: FilmotekaCollectionGroup? = null)

    /** Osa „Vše": jedna plochá řada, řazení Nedávno (addedAtMs) / Abecedně (český Collator). */
    private fun groupAll(items: List<Entry>, allSort: FilmotekaAllSort): List<FilmotekaRail> {
        if (items.isEmpty()) return emptyList()
        val sorted = when (allSort) {
            FilmotekaAllSort.RECENT -> items.sortedByDescending { it.item.addedAtMs ?: Long.MIN_VALUE }
            FilmotekaAllSort.ALPHABETICAL -> {
                val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
                items.sortedWith(Comparator { a, b -> coll.compare(a.item.displayTitle, b.item.displayTitle) })
            }
            // MERIDIAN (SHW-119): od nejkratší. Neznámá délka = Int.MAX_VALUE → na konec, ne na začátek
            // (jinak by se hromada titulů bez metadat tvářila jako „nejkratší filmy").
            FilmotekaAllSort.RUNTIME -> {
                val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
                items.sortedWith(
                    compareBy<Entry> { it.item.runtimeMinutes ?: Int.MAX_VALUE }
                        .thenComparator { a, b -> coll.compare(a.item.displayTitle, b.item.displayTitle) }
                )
            }
        }
        val title = when (allSort) {
            FilmotekaAllSort.RECENT -> "Nedávno přidané"
            FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
            FilmotekaAllSort.RUNTIME -> "Od nejkratšího"
        }
        return listOf(FilmotekaRail(id = "filmo_all", title = title, items = sorted.map { it.toHomeRowItem("all") }))
    }

    /** Řady dle HLAVNÍHO žánru, sestupně dle četnosti. Film v jedné sekci. */
    private fun groupByGenre(items: List<Entry>, hybrid: Boolean): List<FilmotekaRail> {
        val byGenre = LinkedHashMap<String, MutableList<Entry>>()
        for (item in items) {
            val g = mainGenreOf(item.item, hybrid)
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
    private fun groupByCountry(items: List<Entry>, enabled: Set<CinematographyRegion>): List<FilmotekaRail> {
        val byRegion = LinkedHashMap<CinematographyRegion, MutableList<Entry>>()
        for (item in items) {
            val region = mainRegionOf(item.item)
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

    private fun Entry.toHomeRowItem(axisValue: String): HomeRowItem {
        val g = group ?: return HomeRowItem(
            key = "filmo_${axisValue}_${item.tmdbId ?: item.imdbId ?: item.traktId}",
            title = item.displayTitle,
            year = item.year,
            posterUrl = item.posterUrl("w342"),
            landscapeUrl = item.backdropUrl("w780"),
            mediaItem = item,
        )
        // ATRIUM (SHW-118): karta kolekce — klik otevře její OBSAH, ne detail s hledáním zdroje.
        // `mediaItem` = syntetická položka kolekce (název, rok, žánry, plakát, výčet dílů), aby ji
        // seznam uměl vykreslit TÝMŽ řádkem jako film. Do cesty přehrání se dostat nemůže: každý
        // konzument testuje `collectionKey` PŘED `mediaItem`, a položka nemá tmdb/imdb identitu.
        return HomeRowItem(
            key = "filmo_${axisValue}_${g.id}",
            title = g.name,
            subtitle = dilyLabel(g.members.size),
            year = g.year,
            posterUrl = g.posterUrl,
            landscapeUrl = g.backdropUrl,
            mediaItem = item,
            jellyfinId = g.jellyfinId,
            collection = true,
            collectionKey = g.id,
        )
    }
}
