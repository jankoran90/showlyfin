package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
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

        // Kdo patří do kterého BoxSetu (dedup klíč → BoxSet). Jellyfin je pro členství autorita.
        val jfOf = HashMap<String, FilmotekaCollection>()
        for (coll in jellyfinCollections) for (key in coll.memberKeys) jfOf.putIfAbsent(key, coll)
        // BoxSet podle TMDB kolekce — most, kterým BoxSet POHLTÍ i díly ležící mimo Jellyfin.
        val jfByTmdbCollection = jellyfinCollections
            .mapNotNull { coll -> coll.tmdbCollectionId?.let { it to coll } }
            .toMap()

        val tmdbCollectionOf = tmdbCollectionsOf(base)

        // Jeden průchod bází: každý titul dostane klíč své kolekce, nebo žádný.
        val groups = LinkedHashMap<String, MutableList<MediaItem>>()
        val jfOfGroup = HashMap<String, FilmotekaCollection>()
        val tmdbOfGroup = HashMap<String, TmdbCollectionInfo>()
        for (item in base) {
            val key = filmotekaDedupKey(item) ?: continue
            val tmdbColl = tmdbCollectionOf[key]
            // 🔴 POŘADÍ JE PODSTATA VĚCI (user 2026-08-24: „jf kolekce jsou nadřazené tmdb kolekcím"):
            // (1) je členem BoxSetu → patří pod NĚJ; (2) není, ale jeho TMDB kolekce nějaký BoxSet má →
            // pohltí ho TEN BoxSet (přesně Zootropolis: 1. díl v Jellyfinu, 2. přes sdilej.cz — dřív se
            // nesešly, protože BoxSet o tom druhém neví a syntetická skupina se vedle BoxSetu nestavěla);
            // (3) jinak čistě TMDB kolekce (oba díly mimo Jellyfin).
            val jfColl = jfOf[key] ?: tmdbColl?.let { jfByTmdbCollection[it.id] }
            val groupId = when {
                jfColl != null -> "jf:${jfColl.jellyfinId}"
                tmdbColl != null -> "tmdb:${tmdbColl.id}"
                else -> continue
            }
            groups.getOrPut(groupId) { mutableListOf() }.add(item)
            jfColl?.let { jfOfGroup.putIfAbsent(groupId, it) }
            tmdbColl?.let { tmdbOfGroup.putIfAbsent(groupId, it) }
        }

        return groups.mapNotNull { (groupId, members) ->
            // Sdružuje se od 2 členů (viz pravidla v hlavičce souboru).
            if (members.size < 2) return@mapNotNull null
            val jf = jfOfGroup[groupId]
            val tmdb = tmdbOfGroup[groupId]
            val sorted = members.sortedByYearThenTitle()
            FilmotekaCollectionGroup(
                id = groupId,
                name = jf?.name?.takeIf { it.isNotBlank() }
                    ?: tmdb?.name?.takeIf { it.isNotBlank() }
                    ?: sorted.first().displayTitle,
                posterUrl = jf?.posterUrl ?: tmdb?.posterUrl ?: sorted.firstNotNullOfOrNull { it.posterUrl("w342") },
                backdropUrl = jf?.backdropUrl ?: tmdb?.backdropUrl ?: sorted.firstNotNullOfOrNull { it.backdropUrl("w780") },
                jellyfinId = jf?.jellyfinId,
                members = sorted,
                addedAtMs = sorted.mapNotNull { it.addedAtMs }.maxOrNull(),
                year = sorted.mapNotNull { it.year }.minOrNull(),
            )
        }
    }

    /** TMDB kolekce titulů báze (dedup klíč → kolekce). Jen filmy; seriál do filmové kolekce nepatří. */
    private suspend fun tmdbCollectionsOf(base: List<MediaItem>): Map<String, TmdbCollectionInfo> = coroutineScope {
        base.filter { it.type == MediaType.MOVIE && it.tmdbId != null && !it.isCtvTitle }
            .map { item ->
                async {
                    semaphore.withPermit {
                        // 🔴 JAZYK MUSÍ SEDĚT S [MediaEnricher.LANG]: cache TMDB je keyed `(id, jazyk)`,
                        // takže dotaz bez jazyka by MINUL už teplou cache z enrichu a Filmotéka by při
                        // každém studeném startu vystřelila stovky zbytečných dotazů navíc. Se shodným
                        // jazykem je resolver prakticky zadarmo — a název kolekce přijde rovnou česky.
                        val details = runCatching {
                            tmdb.fetchMovieDetails(item.tmdbId!!, MediaEnricher.LANG)
                        }.getOrNull()
                        val coll = details?.belongs_to_collection ?: return@withPermit null
                        val key = filmotekaDedupKey(item) ?: return@withPermit null
                        key to TmdbCollectionInfo(
                            id = coll.id,
                            name = coll.name,
                            posterUrl = coll.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" },
                            backdropUrl = coll.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" },
                        )
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    /** TMDB kolekce filmu — identita + grafika, když ji Jellyfin nedodá. */
    private data class TmdbCollectionInfo(
        val id: Long,
        val name: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
    )

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
