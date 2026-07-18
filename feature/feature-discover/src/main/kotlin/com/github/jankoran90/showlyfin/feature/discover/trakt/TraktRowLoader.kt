package com.github.jankoran90.showlyfin.feature.discover.trakt

import android.util.Log
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.CustomList
import com.github.jankoran90.showlyfin.data.trakt.model.SyncItem
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import com.github.jankoran90.showlyfin.feature.discover.mapper.toMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.ln
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COUCH (SHW-88) — sdílený loader Trakt řad: watchlist / historie (watched) / konkrétní seznam /
 * couchmonkey doporučení. Sjednocuje data + TMDB obohacení pro DOMOV ([TvHomeViewModel]) i sekci
 * TRAKT ([com.github.jankoran90.showlyfin.ui.tv.trakt.TvTraktScreen]). Vše OAuth; nepřihlášený / chyba
 * → prázdný list (řada/mřížka se pak nezobrazí). Vrací obohacené [MediaItem] (poster/backdrop + CZ titulek).
 */
@Singleton
class TraktRowLoader @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val tmdb: TmdbRemoteDataSource,
    private val enricher: MediaEnricher,
    private val parental: ParentalControlsRepository,
) {
    /** Aktivní věkový strop profilu (null = bez omezení). Řídí i to, zda enrich tahá certifikace. */
    private fun capAge(): Int? = parental.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parental.profile.value.hideUnratedForAge
    /** Watchlist, NEJNOVĚJI PŘIDANÉ PRVNÍ (listed_at desc — user 2026-07-13). [kind] = movies|shows|all. */
    suspend fun watchlist(kind: String = "all"): List<MediaItem> {
        // FIX (2026-07-15): filmy a seriály NEZÁVISLE — pád jednoho sub-volání (429/timeout v návalu paralelních
        // Trakt requestů sekce Trakt) NESMÍ vynulovat druhý. Dřív jeden `runCatching` kolem obou → jeden timeout
        // smazal CELÝ watchlist (i filmy) → řada zmizela ze sekce (v Home lazy load prošla).
        suspend fun movies() = runCatching { authorizedTraktApi.fetchSyncMoviesWatchlist() }.getOrElse { emptyList() }
        suspend fun shows() = runCatching { authorizedTraktApi.fetchSyncShowsWatchlist() }.getOrElse { emptyList() }
        val raw = when (kind) {
            "movies" -> movies()
            "shows" -> shows()
            else -> movies() + shows()
        }
        Log.i(TAG, "watchlist($kind): ${raw.size} položek")
        // CATALOGUE — nes `listed_at` na položku (addedAtMs) pro stabilní řazení „Nedávno přidané" ve Filmotéce.
        return enrich(raw.sortedByDescending { it.lastListedMillis() }) { it.lastListedMillis() }
    }

    /** Historie sledování (watched), nejnověji sledované první. [kind] jako u [watchlist]. */
    suspend fun history(kind: String = "all"): List<MediaItem> {
        val raw = runCatching {
            when (kind) {
                "movies" -> authorizedTraktApi.fetchSyncWatchedMovies()
                "shows" -> authorizedTraktApi.fetchSyncWatchedShows()
                else -> authorizedTraktApi.fetchSyncWatchedMovies() + authorizedTraktApi.fetchSyncWatchedShows()
            }
        }.getOrElse { emptyList() }
        return enrich(raw.sortedByDescending { it.lastWatchedMillis() })
    }

    /** Položky konkrétního Trakt seznamu (trakt id listu). */
    suspend fun list(listId: Long): List<MediaItem> {
        val raw = runCatching { authorizedTraktApi.fetchSyncListItems(listId, withMovies = true) }
            .onFailure { Log.w(TAG, "list($listId) selhal", it) }
            .getOrElse { emptyList() }
        Log.i(TAG, "list($listId): ${raw.size} položek")
        return enrich(raw)
    }

    /** Všechny userovy Trakt seznamy (users/me/lists), v pořadí z API. */
    suspend fun myLists(): List<CustomList> =
        runCatching { authorizedTraktApi.fetchSyncLists() }
            .onFailure { Log.w(TAG, "myLists() selhal", it) }
            .getOrElse { emptyList() }
            .also { Log.i(TAG, "myLists(): ${it.size} seznamů: ${it.joinToString { l -> l.name }}") }

    /**
     * COUCH T2 — sloučená „Doporučeno": všechny userovy couchmonkey listy (název obsahuje „couchmonkey"),
     * jejich položky UNION + DEDUP (dle trakt id), mínus vše, co už mám (viděné ∪ hodnocené ∪ watchlist).
     */
    suspend fun couchmonkeyRecommendations(): List<MediaItem> {
        val lists = myLists().filter { it.name.contains("couchmonkey", ignoreCase = true) }
        Log.i(TAG, "couchmonkey listy: ${lists.size} (${lists.joinToString { it.name }})")
        if (lists.isEmpty()) return emptyList()
        val merged = LinkedHashMap<Long, SyncItem>()
        for (l in lists) {
            val items = runCatching { authorizedTraktApi.fetchSyncListItems(l.ids.trakt, withMovies = true) }.getOrElse { emptyList() }
            for (si in items) { val id = si.getTraktId() ?: continue; merged.putIfAbsent(id, si) }
        }
        val exclude = ownedTraktIds()
        return enrich(merged.values.filter { (it.getTraktId() ?: return@filter false) !in exclude })
    }

    /**
     * COUCH (SHW-88) — play-count vážená doporučení „na míru dle sledování". Z nejvíc přehrávaných
     * filmů (Trakt `plays`) vezme TMDB recommendations, každý kandidát dostane skóre = Σ přes seedy
     * `ln(1+plays) / (pozice+1)` (log tlumí megahity, pozice zvýhodní bližší doporučení). Odečte co už
     * mám (tmdb) a projde věkovým gate. Prázdné = málo historie / nepřihlášen.
     */
    suspend fun weightedRecommendations(limit: Int): List<MediaItem> = coroutineScope {
        val watched = runCatching { authorizedTraktApi.fetchSyncWatchedMovies() }.getOrElse { emptyList() }
        val seeds = watched.filter { it.getTmdbId() != null }
            .sortedByDescending { it.plays ?: 1 }
            .take(SEED_COUNT)
        if (seeds.isEmpty()) { Log.i(TAG, "weighted: žádné seedy (prázdná historie)"); return@coroutineScope emptyList() }
        val ownedTmdb = ownedTmdbIds()
        val recLists = seeds.map { seed ->
            async {
                val weight = ln(1.0 + (seed.plays ?: 1).toDouble())
                val recs = runCatching { tmdb.movieRecommendations(seed.getTmdbId()!!) }.getOrElse { emptyList() }
                recs.take(REC_PER_SEED).mapIndexed { idx, item -> item to weight / (idx + 1.0) }
            }
        }.awaitAll().flatten()
        val scores = LinkedHashMap<Long, Double>()
        val byId = HashMap<Long, TmdbSearchMovieItem>()
        for ((item, w) in recLists) {
            if (item.id in ownedTmdb) continue
            byId.putIfAbsent(item.id, item)
            scores[item.id] = (scores[item.id] ?: 0.0) + w
        }
        val ranked = scores.entries.sortedByDescending { it.value }
            .mapNotNull { byId[it.key] }
            .take(limit.coerceIn(1, 60))
            .map { it.toMovieMediaItem() }
        Log.i(TAG, "weighted: ${seeds.size} seedů → ${ranked.size} kandidátů")
        ContentAgeGate.filter(capAge(), enricher.enrich(ranked, withCertification = capAge() != null), hideUnrated())
    }

    private fun TmdbSearchMovieItem.toMovieMediaItem() = MediaItem(
        traktId = 0L,
        tmdbId = id,
        imdbId = null,
        title = title ?: original_title ?: "",
        year = release_date?.take(4)?.toIntOrNull(),
        overview = overview,
        rating = vote_average,
        genres = null,
        type = MediaType.MOVIE,
        posterPath = poster_path,
        backdropPath = backdrop_path,
    )

    /** Sjednocené tmdb id všeho, co už mám: watched ∪ watchlist (pro dedup TMDB doporučení). */
    private suspend fun ownedTmdbIds(): Set<Long> = coroutineScope {
        val watched = async { runCatching { authorizedTraktApi.fetchSyncWatchedMovies() + authorizedTraktApi.fetchSyncWatchedShows() }.getOrElse { emptyList() } }
        val watchlist = async { runCatching { authorizedTraktApi.fetchSyncMoviesWatchlist() + authorizedTraktApi.fetchSyncShowsWatchlist() }.getOrElse { emptyList() } }
        val set = mutableSetOf<Long>()
        (watched.await() + watchlist.await()).forEach { it.getTmdbId()?.let(set::add) }
        set
    }

    /** Sjednocené trakt id všeho, co už mám: watched ∪ hodnocené ∪ watchlist. */
    suspend fun ownedTraktIds(): Set<Long> = coroutineScope {
        val watched = async { runCatching { authorizedTraktApi.fetchSyncWatchedMovies() + authorizedTraktApi.fetchSyncWatchedShows() }.getOrElse { emptyList() } }
        val watchlist = async { runCatching { authorizedTraktApi.fetchSyncMoviesWatchlist() + authorizedTraktApi.fetchSyncShowsWatchlist() }.getOrElse { emptyList() } }
        val ratedShows = async { runCatching { authorizedTraktApi.fetchShowsRatings() }.getOrElse { emptyList() } }
        val ratedMovies = async { runCatching { authorizedTraktApi.fetchMoviesRatings() }.getOrElse { emptyList() } }
        val set = mutableSetOf<Long>()
        (watched.await() + watchlist.await()).forEach { it.getTraktId()?.let(set::add) }
        ratedShows.await().forEach { it.show.ids.trakt?.let(set::add) }
        ratedMovies.await().forEach { it.movie.ids.trakt?.let(set::add) }
        set
    }

    /**
     * SyncItem → obohacené MediaItem (poster/backdrop + CZ titulek + žánry + certifikace, paralelně),
     * pak **věkový gate** dětského profilu ([ContentAgeGate]). Filtr běží tady → pokrývá Trakt řady na
     * DOMOVĚ i v sekci TRAKT najednou; pro dospělý profil (cap=null) je no-op.
     */
    private suspend fun enrich(
        items: List<SyncItem>,
        addedAtOf: (SyncItem) -> Long? = { null },
    ): List<MediaItem> {
        val base = items.mapNotNull { si ->
            (si.movie?.toMediaItem() ?: si.show?.toMediaItem())?.copy(addedAtMs = addedAtOf(si))
        }
        val enriched = enricher.enrich(base, withCertification = capAge() != null)
        return ContentAgeGate.filter(capAge(), enriched, hideUnrated())
    }

    private companion object {
        const val TAG = "COUCH_Trakt"
        /** Kolik nejhranějších titulů použít jako seed (omezuje počet TMDB volání). */
        const val SEED_COUNT = 15
        /** Kolik doporučení vzít z jednoho seedu. */
        const val REC_PER_SEED = 20
    }
}
