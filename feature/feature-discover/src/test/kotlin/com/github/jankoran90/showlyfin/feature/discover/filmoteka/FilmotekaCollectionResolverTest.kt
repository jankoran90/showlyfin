package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ATRIUM (SHW-118) — pravidla sdružování kolekcí proti KONKRÉTNÍM případům z provozu uživatele.
 * Resolver je čistá logika bez Androidu, takže jde ověřit tady a ne až na zařízení.
 *
 * Případy pojmenované po tom, co je nahlásil:
 *  - Zootropolis: 1. díl v Jellyfinu, 2. přes sdilej.cz → MUSÍ se sejít pod jednou kartou.
 *  - Medvídek Pú: ruční kolekce v Jellyfinu BEZ TMDB id → musí fungovat čistě přes členství v JF.
 *  - Auta: běžný BoxSet se všemi díly v knihovně.
 */
class FilmotekaCollectionResolverTest {

    // ── Testovací dvojníci ──────────────────────────────────────────────────────

    /** TMDB, který zná `belongs_to_collection` jen pro id, která mu předhodíme. */
    private class FakeTmdb(private val collections: Map<Long, TmdbBelongsToCollection>) : TmdbRemoteDataSource {
        var movieDetailCalls = 0
            private set

        override suspend fun fetchMovieDetails(tmdbId: Long, language: String?): TmdbMovieDetails? {
            movieDetailCalls++
            return TmdbMovieDetails(
                id = tmdbId,
                title = "film $tmdbId",
                poster_path = null,
                backdrop_path = null,
                overview = null,
                vote_average = null,
                release_date = null,
                runtime = null,
                genres = null,
                tagline = null,
                status = null,
                belongs_to_collection = collections[tmdbId],
            )
        }

        // Zbytek TMDB rozhraní test nepotřebuje — sdružování se ptá výhradně na `belongs_to_collection`.
    override suspend fun fetchShowDetails(tmdbId: Long, language: String?): TmdbShowDetails? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchShowImdbId(tmdbId: Long): String? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchMovieCertificationAge(tmdbId: Long): Int? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchShowCertificationAge(tmdbId: Long): Int? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun movieRecommendations(tmdbId: Long): List<TmdbSearchMovieItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchShowImages(tmdbId: Long): TmdbImages =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchSeason(tmdbId: Long, seasonNumber: Int): TmdbSeasonDetails? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchEpisodeImage(showTmdbId: Long?, season: Int?, episode: Int?): TmdbImage? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchMovieImages(tmdbId: Long): TmdbImages =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchMoviePeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchShowPeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchShowWatchProviders(tmdbId: Long, countryCode: String): TmdbStreamingCountry? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchMovieWatchProviders(tmdbId: Long, countryCode: String): TmdbStreamingCountry? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchPersonDetails(id: Long): TmdbPerson =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchPersonTranslations(id: Long): Map<String, TmdbTranslation.Data> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchPersonImages(tmdbId: Long): TmdbImages =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchMovieTranslation(tmdbId: Long, language: String): TmdbTranslation.Data? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchShowTranslation(tmdbId: Long, language: String): TmdbTranslation.Data? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun fetchCollection(collectionId: Long): TmdbCollection? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun searchMovies(query: String, language: String): List<TmdbSearchMovieItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun searchShows(query: String, language: String): List<TmdbSearchShowItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun findTmdbIdByImdb(imdbId: String, isShow: Boolean): Long? =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun searchPeople(query: String, language: String): List<TmdbSearchPersonItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun searchCompanies(query: String): List<TmdbSearchCompanyItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun discoverMoviesByPerson(personId: Long, language: String): List<TmdbSearchMovieItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun discoverMoviesByCompany(companyId: Long, language: String): List<TmdbSearchMovieItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    override suspend fun moviesByPersonRole(personId: Long, role: PersonRole, language: String): List<TmdbSearchMovieItem> =
        throw NotImplementedError("test tuhle cestu nepoužívá")
    }

    private fun movie(tmdbId: Long, title: String, year: Int? = null, addedAtMs: Long? = null) = MediaItem(
        traktId = 0L,
        tmdbId = tmdbId,
        imdbId = null,
        title = title,
        year = year,
        overview = null,
        rating = null,
        genres = null,
        type = MediaType.MOVIE,
        addedAtMs = addedAtMs,
    )

    private fun tmdbCollection(id: Long, name: String) =
        TmdbBelongsToCollection(id = id, name = name, poster_path = null, backdrop_path = null)

    private fun boxSet(
        id: String,
        name: String,
        memberTmdbIds: List<Long>,
        tmdbCollectionId: Long? = null,
    ) = FilmotekaCollection(
        jellyfinId = id,
        name = name,
        posterUrl = "poster:$id",
        tmdbCollectionId = tmdbCollectionId,
        memberKeys = memberTmdbIds.map { "tmdb:$it" }.toSet(),
    )

    // ── Případy ─────────────────────────────────────────────────────────────────

    @Test
    fun `BoxSet se vsemi dily v knihovne se sdruzi pod jednu kartu`() = runTest {
        val base = listOf(
            movie(920, "Auta", year = 2006),
            movie(49013, "Auta 2", year = 2011),
            movie(260514, "Auta 3", year = 2017),
            movie(12345, "Nesouvisejici film"),
        )
        val resolver = FilmotekaCollectionResolver(FakeTmdb(emptyMap()))

        val groups = resolver.resolve(base, listOf(boxSet("bs1", "Auta (kolekce)", listOf(920, 49013, 260514))))

        assertEquals(1, groups.size)
        val auta = groups.single()
        assertEquals("Auta (kolekce)", auta.name)
        assertEquals("jf:bs1", auta.id)
        assertEquals(listOf("Auta", "Auta 2", "Auta 3"), auta.members.map { it.displayTitle })
        assertEquals(2006, auta.year, "rok kolekce = nejstarší díl")
    }

    @Test
    fun `Zootropolis - BoxSet pohlti dil, ktery v Jellyfinu vubec neni`() = runTest {
        // 1. díl je v Jellyfin BoxSetu, 2. má jen uložený zdroj (sdilej.cz) — Jellyfin o něm neví.
        val base = listOf(
            movie(269149, "Zootropolis", year = 2016),
            movie(1022789, "Zootropolis 2", year = 2025),
        )
        val tmdb = FakeTmdb(
            mapOf(
                269149L to tmdbCollection(726871, "Zootropolis Collection"),
                1022789L to tmdbCollection(726871, "Zootropolis Collection"),
            )
        )
        val resolver = FilmotekaCollectionResolver(tmdb)

        val groups = resolver.resolve(
            base,
            listOf(boxSet("bs-zoo", "Zootropolis (kolekce)", listOf(269149), tmdbCollectionId = 726871)),
        )

        assertEquals(1, groups.size, "oba díly patří pod JEDNU kartu, ne každý zvlášť")
        val zoo = groups.single()
        assertEquals("jf:bs-zoo", zoo.id, "identitu drží Jellyfin BoxSet (je nadřazený TMDB)")
        assertEquals(
            listOf("Zootropolis", "Zootropolis 2"),
            zoo.members.map { it.displayTitle },
            "díl mimo Jellyfin musí být pod stejnou střechou",
        )
    }

    @Test
    fun `Medvidek Pu - rucni JF kolekce bez TMDB id se sdruzi pres clenstvi`() = runTest {
        val base = listOf(
            movie(11430, "Medvídek Pú"),
            movie(53912, "Medvídek Pú a Den pro Ijáčka"),
        )
        // Vlastní kolekce vytvořená v Jellyfinu — TMDB o ní neví a filmy nemají společnou TMDB kolekci.
        val resolver = FilmotekaCollectionResolver(FakeTmdb(emptyMap()))

        val groups = resolver.resolve(
            base,
            listOf(boxSet("bs-pu", "Medvídek Pú (kolekce)", listOf(11430, 53912), tmdbCollectionId = null)),
        )

        assertEquals(1, groups.size)
        assertEquals("Medvídek Pú (kolekce)", groups.single().name)
        assertEquals(2, groups.single().members.size)
    }

    @Test
    fun `kolekce s jedinym dostupnym dilem se nesdruzuje`() = runTest {
        val base = listOf(movie(920, "Auta"))
        val resolver = FilmotekaCollectionResolver(FakeTmdb(emptyMap()))

        // BoxSet zná tři díly, ale v bázi (po věkovém gate / bez zdroje) je jen jeden.
        val groups = resolver.resolve(base, listOf(boxSet("bs1", "Auta (kolekce)", listOf(920, 49013, 260514))))

        assertTrue(groups.isEmpty(), "jeden díl = žádná karta kolekce, film zůstane sám za sebe")
    }

    @Test
    fun `dva dily mimo Jellyfin se sdruzi ciste pres TMDB kolekci`() = runTest {
        val base = listOf(
            movie(671, "Harry Potter a Kámen mudrců", year = 2001),
            movie(672, "Harry Potter a Tajemná komnata", year = 2002),
        )
        val tmdb = FakeTmdb(
            mapOf(
                671L to tmdbCollection(1241, "Harry Potter Collection"),
                672L to tmdbCollection(1241, "Harry Potter Collection"),
            )
        )
        val resolver = FilmotekaCollectionResolver(tmdb)

        val groups = resolver.resolve(base, jellyfinCollections = emptyList())

        assertEquals(1, groups.size)
        assertEquals("tmdb:1241", groups.single().id)
        assertNull(groups.single().jellyfinId, "kolekce mimo Jellyfin nemá co otevřít v knihovně")
        assertEquals("Harry Potter Collection", groups.single().name)
    }

    @Test
    fun `clenove kolekce se v seznamu uz neopakuji`() = runTest {
        val base = listOf(
            movie(920, "Auta", year = 2006),
            movie(49013, "Auta 2", year = 2011),
            movie(12345, "Nesouvisejici film"),
        )
        val resolver = FilmotekaCollectionResolver(FakeTmdb(emptyMap()))
        val groups = resolver.resolve(base, listOf(boxSet("bs1", "Auta (kolekce)", listOf(920, 49013))))

        val result = FilmotekaGrouping.build(
            all = base,
            axis = com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis.ALL,
            allSort = com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort.ALPHABETICAL,
            genreFilter = emptySet(),
            countryFilter = emptySet(),
            enabledRegions = emptySet(),
            hybridGenres = false,
            collectionGroups = groups,
        )

        val titles = result.rails.single().items.map { it.title }
        assertEquals(2, titles.size, "karta kolekce + nesouvisející film")
        assertTrue(titles.any { it.contains("Auta") }, "kolekce v seznamu je")
        assertTrue(
            result.rails.single().items.none { it.mediaItem?.displayTitle == "Auta 2" },
            "jednotlivé díly se vedle karty kolekce už neukazují",
        )
        val card = result.rails.single().items.single { it.collectionKey != null }
        assertEquals("jf:bs1", card.collectionKey)
        assertEquals("2 díly", card.subtitle)
    }

    @Test
    fun `kolekce je i v ose Zanr, pod zanrem svych dilu`() = runTest {
        val animovane = listOf("Animovaný", "Rodinný")
        val base = listOf(
            movie(920, "Auta", year = 2006).copy(genres = animovane),
            movie(49013, "Auta 2", year = 2011).copy(genres = animovane),
            movie(12345, "Drsny thriller").copy(genres = listOf("Thriller")),
        )
        val resolver = FilmotekaCollectionResolver(FakeTmdb(emptyMap()))
        val groups = resolver.resolve(base, listOf(boxSet("bs1", "Auta (kolekce)", listOf(920, 49013))))

        val result = FilmotekaGrouping.build(
            all = base,
            axis = com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis.GENRE,
            allSort = com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort.ALPHABETICAL,
            genreFilter = emptySet(),
            countryFilter = emptySet(),
            enabledRegions = emptySet(),
            hybridGenres = false,
            collectionGroups = groups,
        )

        // Karta kolekce musí být v NĚJAKÉ žánrové řadě (dědí žánr svých dílů), a to právě jednou.
        val cards = result.rails.flatMap { rail -> rail.items.filter { it.collectionKey != null } }
        assertEquals(1, cards.size, "kolekce patří do jedné žánrové sekce, ne do všech")
        assertTrue(
            result.rails.none { rail -> rail.items.any { it.mediaItem?.displayTitle == "Auta" } },
            "díly se ani v ose Žánr neukazují vedle karty kolekce",
        )
    }

    @Test
    fun `vypnuty prepinac nechava dily jednotlive`() {
        val base = listOf(movie(920, "Auta"), movie(49013, "Auta 2"))

        // Vypnutý přepínač = prázdné skupiny (řeší ViewModel) → grouping se nesmí nijak zaplést.
        val result = FilmotekaGrouping.build(
            all = base,
            axis = com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis.ALL,
            allSort = com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort.ALPHABETICAL,
            genreFilter = emptySet(),
            countryFilter = emptySet(),
            enabledRegions = emptySet(),
            hybridGenres = false,
            collectionGroups = emptyList(),
        )

        assertEquals(2, result.rails.single().items.size)
        assertTrue(result.rails.single().items.all { it.collectionKey == null })
    }
}
