package com.github.jankoran90.showlyfin.feature.discover.trakt

import android.util.Log
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.CustomList
import com.github.jankoran90.showlyfin.data.trakt.model.SyncItem
import com.github.jankoran90.showlyfin.feature.discover.mapper.toMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
) {
    /** Watchlist, NEJNOVĚJI PŘIDANÉ PRVNÍ (listed_at desc — user 2026-07-13). [kind] = movies|shows|all. */
    suspend fun watchlist(kind: String = "all"): List<MediaItem> {
        val raw = runCatching {
            when (kind) {
                "movies" -> authorizedTraktApi.fetchSyncMoviesWatchlist()
                "shows" -> authorizedTraktApi.fetchSyncShowsWatchlist()
                else -> authorizedTraktApi.fetchSyncMoviesWatchlist() + authorizedTraktApi.fetchSyncShowsWatchlist()
            }
        }.getOrElse { emptyList() }
        Log.i(TAG, "watchlist($kind): ${raw.size} položek")
        return enrich(raw.sortedByDescending { it.lastListedMillis() })
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

    /** Sjednocené trakt id všeho, co už mám: watched ∪ hodnocené ∪ watchlist. */
    private suspend fun ownedTraktIds(): Set<Long> = coroutineScope {
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

    /** SyncItem → obohacené MediaItem (per-item TMDB poster/backdrop + CZ titulek), paralelně. */
    private suspend fun enrich(items: List<SyncItem>): List<MediaItem> = coroutineScope {
        items.mapNotNull { si -> si.movie?.toMediaItem() ?: si.show?.toMediaItem() }
            .map { base -> async { enrichOne(base) } }
            .awaitAll()
    }

    private companion object {
        const val TAG = "COUCH_Trakt"
    }

    private suspend fun enrichOne(item: MediaItem): MediaItem {
        val tmdbId = item.tmdbId ?: return item
        return if (item.type == MediaType.SHOW) {
            val details = runCatching { tmdb.fetchShowDetails(tmdbId) }.getOrNull()
            val tr = runCatching { tmdb.fetchShowTranslation(tmdbId, "cs") }.getOrNull()
            item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path, titleCz = tr?.name?.takeIf { it.isNotBlank() })
        } else {
            val details = runCatching { tmdb.fetchMovieDetails(tmdbId) }.getOrNull()
            val tr = runCatching { tmdb.fetchMovieTranslation(tmdbId, "cs") }.getOrNull()
            item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path, titleCz = tr?.title?.takeIf { it.isNotBlank() })
        }
    }
}
