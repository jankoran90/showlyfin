package com.github.jankoran90.showlyfin.feature.discover.curator

import android.content.SharedPreferences
import android.util.Log
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AUTEUR (SHW-91) — kurátorská řada „Pro tebe". Sestaví MODEL VKUSU z appky (Trakt watched+ratings+
 * watchlist ∪ showlyfin Favorites), pošle na backend `POST /curator/recommend` (mozek → resolve na
 * TMDB) a vrátí obohacené [MediaItem] (poster/CZ + věkový gate) — stejný výstupní tvar jako
 * [com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader].
 *
 * ZDROJ VKUSU (user 2026-07-14): dospělé profily = **jen Trakt + Favorites, BEZ Jellyfinu** (na JF
 * filmy/seriály nekoukají). JF watched jako signál vkusu je vyhrazen dětským profilům (fáze C4).
 */
@Singleton
class CuratorLoader @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val favoritesStore: FavoritesStore,
    private val enricher: MediaEnricher,
    private val parental: ParentalControlsRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val gson: Gson,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private fun capAge(): Int? = parental.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parental.profile.value.hideUnratedForAge
    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty()
    private fun serverBase(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun serverCookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    /**
     * Kurátorská doporučení „Pro tebe". Prázdné = backend/mozek nedostupný nebo studený vkus →
     * volající (TvHomeViewModel) může fallbacknout na `weightedRecommendations`.
     */
    suspend fun forYou(limit: Int): List<MediaItem> {
        val base = serverBase(); val cookie = serverCookie(); val key = profileKey()
        if (base.isBlank() || key.isBlank()) {
            Log.i(TAG, "forYou: chybí backend/profil (base=${base.isNotBlank()}, key=${key.isNotBlank()})")
            return emptyList()
        }
        val taste = buildTaste()
        if (taste.isEmpty()) { Log.i(TAG, "forYou: studený vkus → fallback"); return emptyList() }

        val request = RecommendRequest(
            profileKey = key,
            kind = "both",
            count = limit.coerceIn(1, 60),
            wait = true,
            ageCap = capAge(),
            taste = taste,
        )
        val raw = runCatching { uploaderDs.curatorRecommend(base, cookie, gson.toJson(request)) }
            .onFailure { Log.w(TAG, "forYou: volání backendu selhalo", it) }
            .getOrNull() ?: return emptyList()
        val resp = runCatching { gson.fromJson(raw, RecommendResponse::class.java) }
            .onFailure { Log.w(TAG, "forYou: parse odpovědi selhal", it) }
            .getOrNull() ?: return emptyList()
        val base0 = resp.items.mapNotNull { it.toMediaItem() }
        if (base0.isEmpty()) { Log.i(TAG, "forYou: source=${resp.source}, 0 položek"); return emptyList() }
        val enriched = enricher.enrich(base0, withCertification = capAge() != null)
        return ContentAgeGate.filter(capAge(), enriched, hideUnrated())
            .also { Log.i(TAG, "forYou: source=${resp.source} → ${it.size} položek") }
    }

    /** Sestaví taste payload z Traktu (watched+plays, ratings, watchlist) + Favorites. */
    private suspend fun buildTaste(): TastePayload = coroutineScope {
        val watchedMoviesD = async { runCatching { authorizedTraktApi.fetchSyncWatchedMovies() }.getOrElse { emptyList() } }
        val watchedShowsD = async { runCatching { authorizedTraktApi.fetchSyncWatchedShows() }.getOrElse { emptyList() } }
        val movieRatingsD = async { runCatching { authorizedTraktApi.fetchMoviesRatings() }.getOrElse { emptyList() } }
        val showRatingsD = async { runCatching { authorizedTraktApi.fetchShowsRatings() }.getOrElse { emptyList() } }
        val watchlistD = async {
            runCatching { authorizedTraktApi.fetchSyncMoviesWatchlist() + authorizedTraktApi.fetchSyncShowsWatchlist() }
                .getOrElse { emptyList() }
        }

        val watched = watchedMoviesD.await() + watchedShowsD.await()
        // tmdbId → label (pro dohledání názvu u ratingů, které nesou jen ids)
        val labelByTmdb = HashMap<Long, TasteEntry>()
        for (si in watched) {
            val tmdb = si.getTmdbId() ?: continue
            si.toEntry()?.let { labelByTmdb.putIfAbsent(tmdb, it) }
        }

        val top = watched.filter { it.toEntry() != null }
            .sortedByDescending { it.plays ?: 1 }
            .mapNotNull { it.toEntry(withPlays = true) }
            .take(TOP_CAP)
        val recent = watched.sortedByDescending { it.lastWatchedMillis() }
            .mapNotNull { it.toEntry() }
            .take(RECENT_CAP)

        // ratings: vysoké → loved, nízké → avoid; název dohledán přes watched mapu (RatingResultValue nemá title)
        val lovedFromRatings = mutableListOf<TasteEntry>()
        val avoid = mutableListOf<String>()
        movieRatingsD.await().forEach { r ->
            val tmdb = r.movie.ids.tmdb ?: return@forEach
            val e = labelByTmdb[tmdb] ?: return@forEach
            if (r.rating >= LOVE_MIN) lovedFromRatings += e
            if (r.rating <= AVOID_MAX) avoid += e.title
        }
        showRatingsD.await().forEach { r ->
            val tmdb = r.show.ids.tmdb ?: return@forEach
            val e = labelByTmdb[tmdb] ?: return@forEach
            if (r.rating >= LOVE_MIN) lovedFromRatings += e
            if (r.rating <= AVOID_MAX) avoid += e.title
        }

        val lovedFromFavorites = favoritesStore.items.value
            .filter { it.kind == FavoriteKind.MOVIE && it.name.isNotBlank() }
            .map { TasteEntry(title = it.name, year = it.year) }

        val watchlist = watchlistD.await().mapNotNull { it.toEntry() }.take(WATCHLIST_CAP)

        TastePayload(
            loved = (lovedFromFavorites + lovedFromRatings).dedupByTitle().take(LOVED_CAP),
            top = top,
            recent = recent,
            watchlist = watchlist,
            avoid = avoid.distinct().take(AVOID_CAP),
        )
    }

    private fun SyncItem.toEntry(withPlays: Boolean = false): TasteEntry? {
        val title = (movie?.title ?: show?.title)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val year = movie?.year ?: show?.year
        return TasteEntry(title = title, year = year, plays = if (withPlays) plays else null)
    }

    private fun List<TasteEntry>.dedupByTitle(): List<TasteEntry> {
        val seen = HashSet<String>()
        return filter { seen.add(it.title.lowercase()) }
    }

    private fun RecItem.toMediaItem(): MediaItem? {
        val id = tmdbId ?: return null
        return MediaItem(
            traktId = 0L,
            tmdbId = id,
            imdbId = null,
            title = title.orEmpty(),
            year = year,
            overview = null,
            rating = null,
            genres = null,
            type = if (type == "show") MediaType.SHOW else MediaType.MOVIE,
            posterPath = posterPath,
            backdropPath = null,
        )
    }

    // ── DTO pro backend /curator/recommend ────────────────────────────────────
    private data class TasteEntry(val title: String, val year: Int?, val plays: Int? = null)
    private data class TastePayload(
        val loved: List<TasteEntry>,
        val top: List<TasteEntry>,
        val recent: List<TasteEntry>,
        val watchlist: List<TasteEntry>,
        val avoid: List<String>,
    ) {
        fun isEmpty(): Boolean = top.isEmpty() && recent.isEmpty() && loved.isEmpty()
    }
    private data class RecommendRequest(
        val profileKey: String,
        val kind: String,
        val count: Int,
        val wait: Boolean,
        val ageCap: Int?,
        val taste: TastePayload,
    )
    private data class RecItem(val tmdbId: Long?, val type: String?, val title: String?, val year: Int?, val posterPath: String?)
    private data class RecommendResponse(val items: List<RecItem> = emptyList(), val source: String? = null)

    private companion object {
        const val TAG = "AUTEUR_Curator"
        const val TOP_CAP = 30
        const val RECENT_CAP = 14
        const val LOVED_CAP = 20
        const val WATCHLIST_CAP = 20
        const val AVOID_CAP = 30
        const val LOVE_MIN = 8      // Trakt rating (1-10) ≥ 8 = „miluje"
        const val AVOID_MAX = 4     // ≤ 4 = palec dolů → veto
    }
}
