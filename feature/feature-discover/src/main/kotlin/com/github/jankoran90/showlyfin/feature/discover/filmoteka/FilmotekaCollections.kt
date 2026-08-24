package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ATRIUM (SHW-118, user 2026-08-24: „slouč její děti do jedné mateřské karty kolekce … JF kolekce,
 * i sdilej.cz zdroj pod jednou střechou") — SDRUŽOVÁNÍ dílů kolekce pod JEDNU kartu, PARITA s webem
 * (`static/filmy/flatgrid.js`), který tohle dělá od 2026-08-17 a je pro appku předlohou.
 *
 * Společný jmenovatel napříč zdroji je **TMDB collection id**: Jellyfin BoxSet ho nese ve svých
 * `ProviderIds.Tmdb`, u working-source titulu (sdilej.cz/RD) se dotáhne z TMDB detailu
 * (`belongs_to_collection`). Díky tomu se pozná, že „Zootropolis" z Jellyfinu a „Zootropolis 2"
 * z uloženého zdroje patří k sobě, i když o sobě navzájem nic nevědí.
 *
 * Pravidla (převzatá z webu, ověřená provozem):
 *  - **JF kolekce je nadřazená TMDB** (user 2026-08-17) — má-li Jellyfin pro danou TMDB kolekci
 *    vlastní BoxSet, použije se ON (jeho název, plakát, možnost otevřít obsah přímo v JF), a žádná
 *    syntetická TMDB skupina se vedle něj netvoří.
 *  - **Sdružuje se od 2 členů** — kolekce s jediným dostupným dílem by byla zbytečná mezivrstva
 *    (klik navíc k jednomu filmu), takže takový díl zůstane v seznamu sám za sebe.
 */

/** Jedna Jellyfin KOLEKCE (BoxSet) tak, jak ji vidí Filmotéka: identita + grafika + členové. */
data class FilmotekaCollection(
    val jellyfinId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    /** TMDB `collection id` z `ProviderIds.Tmdb` BoxSetu — pojítko na working-source díly. */
    val tmdbCollectionId: Long? = null,
    /** Dedup klíče členů (`tmdb:…`/`imdb:…`), jak je vrátil Jellyfin. */
    val memberKeys: Set<String> = emptySet(),
)

/**
 * Sdružená kolekce k ZOBRAZENÍ — nahrazuje své členy jednou kartou. [members] jsou hotové položky
 * báze Filmotéky (už obohacené a prohnané věkovým gate), takže obsah kolekce je jen zobrazí.
 */
data class FilmotekaCollectionGroup(
    /** Stabilní klíč: `jf:<boxSetId>` nebo `tmdb:<collectionId>`. */
    val id: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    /** Neprázdné jen u kolekce, kterou zná Jellyfin (umí otevřít její obsah přímo v knihovně). */
    val jellyfinId: String?,
    val members: List<MediaItem>,
    /** Nejnovější „přidáno" z členů — kolekce se v „Nedávno přidané" řadí podle svého nejmladšího dílu. */
    val addedAtMs: Long?,
    /** Rok nejstaršího dílu — na kartě funguje jako rok kolekce (stejně jako na webu). */
    val year: Int?,
) {
    /** Klíče členů pro rychlé vyřazení z plochého seznamu. */
    val memberKeys: Set<String> get() = members.mapNotNull { filmotekaDedupKey(it) }.toSet()
}

@Singleton
class FilmotekaCollectionResolver @Inject constructor(
    private val tmdb: TmdbRemoteDataSource,
) {
    // Stejný strop jako [MediaEnricher] — Filmotéka má stovky titulů a TMDB má rate-limit.
    private val semaphore = Semaphore(6)

    /**
     * Sestav sdružené kolekce nad bází Filmotéky.
     *
     * @param base hotová báze (po enrichi i věkovém gate) — členy kolekcí bereme JEN z ní, takže se
     *   do kolekce nikdy nedostane titul, který by uživatel jinak vidět neměl.
     * @param jellyfinCollections BoxSety z Jellyfinu (viz [FilmotekaBaseLoader.lastJellyfinCollections]).
     */
    suspend fun resolve(
        base: List<MediaItem>,
        jellyfinCollections: List<FilmotekaCollection>,
    ): List<FilmotekaCollectionGroup> {
        if (base.isEmpty()) return emptyList()
        val byKey = base.mapNotNull { item -> filmotekaDedupKey(item)?.let { it to item } }.toMap()

        // ── 1) Jellyfin BoxSety (mají přednost) ─────────────────────────────────
        val jfGroups = jellyfinCollections.mapNotNull { coll ->
            val members = coll.memberKeys.mapNotNull { byKey[it] }
            if (members.isEmpty()) return@mapNotNull null
            coll to members
        }
        // TMDB kolekce, které už Jellyfin pokrývá vlastním BoxSetem → syntetickou nestav.
        val coveredTmdbCollections = jfGroups.mapNotNull { (coll, _) -> coll.tmdbCollectionId }.toSet()
        // Klíče, které si BoxSet nárokuje → do syntetických skupin už nesmí (žádná duplicita).
        val claimedKeys = jfGroups.flatMap { (_, members) -> members.mapNotNull { filmotekaDedupKey(it) } }.toSet()

        // ── 2) TMDB kolekce pro zbytek (working-source díly bez JF protějšku) ───
        val rest = base.filter { it.type == MediaType.MOVIE && filmotekaDedupKey(it) !in claimedKeys }
        val tmdbGroups = groupByTmdbCollection(rest, coveredTmdbCollections)

        val out = jfGroups.map { (coll, members) ->
            FilmotekaCollectionGroup(
                id = "jf:${coll.jellyfinId}",
                name = coll.name.ifBlank { members.first().displayTitle },
                posterUrl = coll.posterUrl ?: members.firstNotNullOfOrNull { it.posterUrl("w342") },
                backdropUrl = coll.backdropUrl ?: members.firstNotNullOfOrNull { it.backdropUrl("w780") },
                jellyfinId = coll.jellyfinId,
                members = members.sortedByYearThenTitle(),
                addedAtMs = members.mapNotNull { it.addedAtMs }.maxOrNull(),
                year = members.mapNotNull { it.year }.minOrNull(),
            )
        } + tmdbGroups
        // Sdružuje se od 2 členů (viz pravidla v hlavičce souboru).
        return out.filter { it.members.size >= 2 }
    }

    /**
     * Skupiny podle TMDB `belongs_to_collection`. Dotaz jde jen na FILMY, které ještě nemá pokryté
     * Jellyfin BoxSet — u uživatele jsou to desítky uložených zdrojů, ne stovky titulů knihovny.
     * [CachedTmdbRemoteDataSource] navíc drží odpovědi v paměti, takže přeskládání os nic nedotahuje.
     */
    private suspend fun groupByTmdbCollection(
        items: List<MediaItem>,
        skipCollectionIds: Set<Long>,
    ): List<FilmotekaCollectionGroup> = coroutineScope {
        val resolved = items
            .filter { it.tmdbId != null && !it.isCtvTitle }
            .map { item ->
                async {
                    semaphore.withPermit {
                        val details = runCatching { tmdb.fetchMovieDetails(item.tmdbId!!) }.getOrNull()
                        details?.belongs_to_collection?.let { coll -> Triple(item, coll.id, coll) }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .filter { (_, collectionId, _) -> collectionId !in skipCollectionIds }

        resolved.groupBy { (_, collectionId, _) -> collectionId }
            .map { (collectionId, triples) ->
                val members = triples.map { it.first }
                val coll = triples.first().third
                FilmotekaCollectionGroup(
                    id = "tmdb:$collectionId",
                    name = coll.name?.takeIf { it.isNotBlank() } ?: members.first().displayTitle,
                    posterUrl = coll.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" }
                        ?: members.firstNotNullOfOrNull { it.posterUrl("w342") },
                    backdropUrl = coll.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" }
                        ?: members.firstNotNullOfOrNull { it.backdropUrl("w780") },
                    jellyfinId = null,
                    members = members.sortedByYearThenTitle(),
                    addedAtMs = members.mapNotNull { it.addedAtMs }.maxOrNull(),
                    year = members.mapNotNull { it.year }.minOrNull(),
                )
            }
    }

    /** Uvnitř kolekce se díly řadí chronologicky (Auta → Auta 2 → Auta 3), bez roku podle názvu. */
    private fun List<MediaItem>.sortedByYearThenTitle(): List<MediaItem> {
        val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
        return sortedWith(
            compareBy<MediaItem> { it.year ?: Int.MAX_VALUE }
                .thenComparator { a, b -> coll.compare(a.displayTitle, b.displayTitle) }
        )
    }
}

/** „1 díl / 2 díly / 5 dílů" — česká shoda čísla a podstatného jména (popisek karty kolekce). */
fun dilyLabel(count: Int): String = when {
    count == 1 -> "1 díl"
    count in 2..4 -> "$count díly"
    else -> "$count dílů"
}

/**
 * Popisek karty kolekce. Jellyfin BoxSety mívají „(kolekce)" rovnou v názvu (tak si je uživatel
 * pojmenoval), takže se nedopisuje dvakrát.
 */
fun collectionCardTitle(name: String): String =
    if (name.contains("kolekce", ignoreCase = true)) name else "$name (kolekce)"
