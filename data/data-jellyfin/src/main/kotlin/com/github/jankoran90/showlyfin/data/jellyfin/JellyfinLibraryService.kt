package com.github.jankoran90.showlyfin.data.jellyfin

import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class JellyfinLibraryService @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private var cachedOwned: OwnedIds? = null
    private var cacheTimestamp: Long = 0L
    private val cacheValidMs = 5 * 60 * 1000L

    private fun ensureApiConfigured(): Boolean {
        val serverUrl = prefs.getString("jellyfin_server_url", "")?.takeIf { it.isNotBlank() }
        val token = prefs.getString("jellyfin_token", "")?.takeIf { it.isNotBlank() }
        if (serverUrl == null || token == null) {
            Timber.d("ensureApiConfigured: missing serverUrl=$serverUrl token=${token?.take(8)}…")
            return false
        }
        runCatching {
            apiClient.update(
                baseUrl = serverUrl,
                accessToken = token,
                clientInfo = clientInfo,
                deviceInfo = deviceInfo,
            )
        }.onFailure { Timber.w(it, "apiClient.update failed") }
        return true
    }

    /**
     * TENFOOT KOLO2 (N): reverzní lookup metadat jednoho Jellyfin titulu podle jeho id — providerIds
     * (Tmdb/Imdb) + typ + název/rok/popis. Slouží k sestavení [MediaItem] pro nativní immersive TV
     * detail (místo telefonního JellyfinDetailScreen) i u vstupů, kde známe jen jellyfinId (home owned
     * řada, browser bez tmdb). Vrací null, když Jellyfin není nastaven, id je nevalidní, nebo dotaz selže.
     */
    suspend fun getItemMeta(jellyfinId: String): JfItemMeta? {
        if (!ensureApiConfigured()) return null
        val userId = prefs.getString("jellyfin_user_id", "")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val userUuid = UUID.fromString(userId)
            val itemUuid = UUID.fromString(jellyfinId)
            val r = apiClient.userLibraryApi.getItem(userId = userUuid, itemId = itemUuid).content
            val providerIds = r.providerIds ?: emptyMap()
            JfItemMeta(
                jellyfinId = r.id.toString(),
                tmdbId = providerIds["Tmdb"]?.toLongOrNull(),
                imdbId = providerIds["Imdb"]?.takeIf { it.isNotBlank() },
                isSeries = r.type == BaseItemKind.SERIES,
                name = r.name ?: "",
                year = r.productionYear,
                overview = r.overview,
            )
        }.onFailure { Timber.w(it, "[Jellyfin] getItemMeta failed for $jellyfinId") }.getOrNull()
    }

    suspend fun getOwnedIds(userId: UUID): OwnedIds {
        val now = System.currentTimeMillis()
        val cached = cachedOwned
        if (cached != null && (now - cacheTimestamp) < cacheValidMs) {
            Timber.d("getOwnedIds cache hit: imdb=${cached.imdbIds.size} tmdb=${cached.tmdbIds.size} boxSets=${cached.boxSets.size}")
            return cached
        }
        if (!ensureApiConfigured()) {
            Timber.w("getOwnedIds: ApiClient not configured (Jellyfin nepřihlášen)")
            return OwnedIds(emptySet(), emptySet())
        }
        Timber.d("getOwnedIds fetching for userId=$userId")

        val imdb = mutableSetOf<String>()
        val tmdb = mutableSetOf<Long>()
        val imdbToJellyfin = mutableMapOf<String, String>()
        val tmdbToJellyfin = mutableMapOf<Long, String>()
        val watchedImdb = mutableSetOf<String>()
        val watchedTmdb = mutableSetOf<Long>()
        val watchedJellyfin = mutableSetOf<String>()
        val boxSetIds = linkedSetOf<UUID>()
        runCatching {
            val response = apiClient.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PATH),
                limit = 5000,
            ).content
            Timber.d("getOwnedIds Movies+Series response: ${response.items.size}")
            var skippedByPath = 0
            for (item in response.items) {
                val name = item.name ?: "?"
                val path = item.path
                if (isExcludedPath(path)) {
                    skippedByPath++
                    continue
                }
                // Kolekce (BoxSet) se sem připletou i při includeItemTypes=MOVIE,SERIES a nesou
                // collection-id v Tmdb → přeskoč, posbírej id a děti rozbalíme níže.
                if (item.type == BaseItemKind.BOX_SET) {
                    boxSetIds.add(item.id)
                    continue
                }
                val ids = item.providerIds
                val jellyfinId = item.id.toString()
                val hasImdb = ids?.get("Imdb")?.takeIf { it.isNotBlank() } != null
                val hasTmdb = ids?.get("Tmdb")?.toLongOrNull() != null
                if (ids == null || (!hasImdb && !hasTmdb)) {
                    Timber.w("[Jellyfin] item bez providerIds: '$name' (id=$jellyfinId)")
                    continue
                }
                val isPlayed = item.userData?.played == true
                if (isPlayed) watchedJellyfin.add(jellyfinId)
                ids["Imdb"]?.takeIf { it.isNotBlank() }?.let {
                    imdb.add(it)
                    imdbToJellyfin.putIfAbsent(it, jellyfinId)
                    if (isPlayed) watchedImdb.add(it)
                }
                ids["Tmdb"]?.toLongOrNull()?.let {
                    tmdb.add(it)
                    tmdbToJellyfin.putIfAbsent(it, jellyfinId)
                    if (isPlayed) watchedTmdb.add(it)
                }
            }
            if (skippedByPath > 0) Timber.i("[Jellyfin] skipped $skippedByPath items by excluded path (RealDebrid)")
            Timber.i("[Jellyfin] watched: jellyfin=${watchedJellyfin.size} imdb=${watchedImdb.size} tmdb=${watchedTmdb.size}")
        }.onFailure { Timber.w(it, "getItems(MOVIE,SERIES) failed") }

        val boxSets = mutableListOf<BoxSetInfo>()
        val boxSetByTmdbCollection = mutableMapOf<Long, String>()
        val boxSetByNormalizedName = mutableMapOf<String, String>()
        runCatching {
            val response = apiClient.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                recursive = true,
                fields = listOf(ItemFields.PROVIDER_IDS),
                limit = 1000,
            ).content
            for (item in response.items) {
                val jellyfinId = item.id.toString()
                boxSetIds.add(item.id)
                val name = item.name ?: continue
                val tmdbCollectionId = item.providerIds?.get("TmdbCollection")?.toLongOrNull()
                    ?: item.providerIds?.get("Tmdb")?.toLongOrNull()
                boxSets.add(BoxSetInfo(jellyfinId, name, tmdbCollectionId))
                tmdbCollectionId?.let { boxSetByTmdbCollection.putIfAbsent(it, jellyfinId) }
                val normalized = normalizeBoxSetName(name)
                if (normalized.isNotBlank()) {
                    boxSetByNormalizedName.putIfAbsent(normalized, jellyfinId)
                }
            }
        }.onFailure { Timber.w(it, "getItems(BOX_SET) failed") }

        // Rozbal kolekce na děti — filmy/seriály uvnitř nesou reálná movie/show TMDB id.
        // Bez toho položky v kolekci nemají „v knihovně" příznak. boxSetIds sbíráme z hlavní
        // smyčky (kde se kolekce připletou) i z BOX_SET dotazu → pokrývá i případ boxSets=0.
        if (boxSetIds.isNotEmpty()) {
            runCatching {
                val childItems = coroutineScope {
                    boxSetIds.map { bsId ->
                        async {
                            runCatching {
                                apiClient.itemsApi.getItems(
                                    userId = userId,
                                    parentId = bsId,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    fields = listOf(ItemFields.PROVIDER_IDS),
                                ).content.items
                            }.getOrElse { emptyList() }
                        }
                    }.awaitAll().flatten()
                }
                for (child in childItems) {
                    val cids = child.providerIds ?: continue
                    val childJfId = child.id.toString()
                    val isPlayed = child.userData?.played == true
                    if (isPlayed) watchedJellyfin.add(childJfId)
                    cids["Imdb"]?.takeIf { it.isNotBlank() }?.let {
                        imdb.add(it)
                        imdbToJellyfin.putIfAbsent(it, childJfId)
                        if (isPlayed) watchedImdb.add(it)
                    }
                    cids["Tmdb"]?.toLongOrNull()?.let {
                        tmdb.add(it)
                        tmdbToJellyfin.putIfAbsent(it, childJfId)
                        if (isPlayed) watchedTmdb.add(it)
                    }
                }
                Timber.i("[Jellyfin] boxset children harvested: boxsets=${boxSetIds.size} children=${childItems.size} → tmdb total=${tmdb.size}")
            }.onFailure { Timber.w(it, "boxset child harvest failed") }
        }
        Timber.i("getOwnedIds DONE: imdb=${imdb.size} tmdb=${tmdb.size} boxSets=${boxSets.size}")

        val owned = OwnedIds(
            imdbIds = imdb,
            tmdbIds = tmdb,
            imdbToJellyfin = imdbToJellyfin,
            tmdbToJellyfin = tmdbToJellyfin,
            boxSets = boxSets,
            boxSetByTmdbCollection = boxSetByTmdbCollection,
            boxSetByNormalizedName = boxSetByNormalizedName,
            watchedImdbIds = watchedImdb,
            watchedTmdbIds = watchedTmdb,
            watchedJellyfinIds = watchedJellyfin,
        )
        cachedOwned = owned
        cacheTimestamp = now
        return owned
    }

    /**
     * COUCH — zhlédnuté položky daného JF uživatele pro jednorázový import do Trakt `sync/history`.
     * Rozlišuje FILMY vs celé SERIÁLY (`item.type`), aby šly do správného pole Trakt payloadu. Epizodová
     * granularita = mimo rozsah (follow-up). Čte `userData.played` (jako watched badges), BoxSet přeskočí
     * (filmy z kolekcí přijdou jako samostatné MOVIE díky `recursive`).
     */
    suspend fun getWatchedForTraktSync(userId: UUID): WatchedForSync {
        if (!ensureApiConfigured()) {
            Timber.w("getWatchedForTraktSync: ApiClient not configured (Jellyfin nepřihlášen)")
            return WatchedForSync()
        }
        val movies = mutableListOf<WatchedIds>()
        val shows = mutableListOf<WatchedIds>()
        runCatching {
            val resp = apiClient.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PATH),
                limit = 5000,
            ).content
            for (item in resp.items) {
                if (item.userData?.played != true) continue
                if (isExcludedPath(item.path)) continue
                val ids = item.providerIds ?: continue
                val tmdb = ids["Tmdb"]?.toLongOrNull()
                val imdb = ids["Imdb"]?.takeIf { it.isNotBlank() }
                if (tmdb == null && imdb == null) continue
                when (item.type) {
                    BaseItemKind.SERIES -> shows.add(WatchedIds(tmdb, imdb))
                    BaseItemKind.MOVIE -> movies.add(WatchedIds(tmdb, imdb))
                    else -> Unit // BoxSet ap. — přeskoč
                }
            }
            Timber.i("[Jellyfin] getWatchedForTraktSync: movies=${movies.size} shows=${shows.size}")
        }.onFailure { Timber.w(it, "getWatchedForTraktSync failed") }
        return WatchedForSync(movies, shows)
    }

    /**
     * Najde Jellyfin BoxSet, který obsahuje danou položku, a vrátí jeho děti
     * jako kolekci seřazenou od nejstarší po nejnovější. Null pokud item v žádném BoxSetu není.
     */
    suspend fun findBoxSetCollectionForItem(userId: UUID, jellyfinItemId: String): JfCollection? {
        if (!ensureApiConfigured()) return null
        val serverUrl = prefs.getString("jellyfin_server_url", "")?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString("jellyfin_token", "")?.takeIf { it.isNotBlank() } ?: return null
        val itemUuid = runCatching { UUID.fromString(jellyfinItemId) }.getOrNull() ?: return null

        val boxSets = runCatching {
            apiClient.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                recursive = true,
            ).content.items.orEmpty()
        }.getOrNull() ?: return null

        for (boxSet in boxSets) {
            val children = runCatching {
                apiClient.itemsApi.getItems(
                    userId = userId,
                    parentId = boxSet.id,
                    fields = listOf(ItemFields.PROVIDER_IDS),
                ).content.items.orEmpty()
            }.getOrNull() ?: continue

            if (children.any { it.id == itemUuid }) {
                val parts = children.map { child ->
                    JfCollectionPart(
                        jellyfinId = child.id.toString(),
                        tmdbId = child.providerIds?.get("Tmdb")?.toLongOrNull(),
                        title = child.name ?: "",
                        posterUrl = "$serverUrl/Items/${child.id}/Images/Primary?quality=85&api_key=$token",
                        year = child.productionYear,
                        watched = child.userData?.played == true,
                    )
                }.sortedBy { it.year ?: Int.MAX_VALUE }
                return JfCollection(name = boxSet.name ?: "Kolekce", parts = parts)
            }
        }
        return null
    }

    /**
     * TV DETAIL REDESIGN (OTA 299): per-epizoda stav zhlédnutí seriálu z Jellyfinu — pro horizontální řadu
     * epizod v detailu + auto-scroll na první nezhlédnutou. Klíč = (season, episode). `nextUp` z Jellyfin
     * getNextUp (přesné „další na řadě"); prázdný stav když Jellyfin nepřihlášen / seriál není v knihovně.
     */
    suspend fun getSeriesEpisodeStatus(seriesJellyfinId: String): SeriesEpisodeStatus {
        if (!ensureApiConfigured()) return SeriesEpisodeStatus()
        val userId = prefs.getString("jellyfin_user_id", "")?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return SeriesEpisodeStatus()
        val seriesUuid = runCatching { UUID.fromString(seriesJellyfinId) }.getOrNull() ?: return SeriesEpisodeStatus()
        return runCatching {
            val nextUpEp = runCatching {
                apiClient.tvShowsApi.getNextUp(userId = userId, seriesId = seriesUuid).content.items.firstOrNull()
            }.getOrNull()
            val nextUp = nextUpEp?.let { ep ->
                val s = ep.parentIndexNumber
                val e = ep.indexNumber
                if (s != null && e != null) s to e else null
            }
            val episodes = apiClient.tvShowsApi.getEpisodes(seriesId = seriesUuid, userId = userId).content.items
            val watched = mutableSetOf<Pair<Int, Int>>()
            val progress = mutableMapOf<Pair<Int, Int>, Int>()
            // KOLO2 (G): (season,episode) → jellyfin episode id pro přímé přehrání z knihovny.
            val episodeIds = mutableMapOf<Pair<Int, Int>, String>()
            for (ep in episodes) {
                val s = ep.parentIndexNumber ?: continue
                val e = ep.indexNumber ?: continue
                episodeIds[s to e] = ep.id.toString()
                if (ep.userData?.played == true) watched.add(s to e)
                ep.userData?.playedPercentage?.toInt()?.takeIf { it in 1..99 }?.let { progress[s to e] = it }
            }
            SeriesEpisodeStatus(watched = watched, progress = progress, nextUp = nextUp, episodeIds = episodeIds)
        }.getOrElse {
            Timber.w(it, "getSeriesEpisodeStatus failed for $seriesJellyfinId")
            SeriesEpisodeStatus()
        }
    }

    /**
     * TENFOOT KOLO2 (H/J): nahlásí `played` stav titulu/epizody ZPĚT do Jellyfin UserData (dosud showlyfin
     * playback stav vůbec nereportoval — statický `/Videos/{id}/stream` server neoznačí zhlédnuto). Použito
     * long-pressem „označit zhlédnuto" (J) i po dokoukání (H). Invaliduje owned/watched cache. Vrací úspěch.
     */
    suspend fun markPlayed(jellyfinId: String, played: Boolean): Boolean {
        if (!ensureApiConfigured()) return false
        val userId = prefs.getString("jellyfin_user_id", "")?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        val itemUuid = runCatching { UUID.fromString(jellyfinId) }.getOrNull() ?: return false
        return runCatching {
            if (played) {
                apiClient.playStateApi.markPlayedItem(userId = userId, itemId = itemUuid)
            } else {
                apiClient.playStateApi.markUnplayedItem(userId = userId, itemId = itemUuid)
            }
            invalidate() // owned/watched cache je teď zastaralá → přenačti při dalším dotazu
            true
        }.onFailure { Timber.w(it, "[Jellyfin] markPlayed($played) failed for $jellyfinId") }.getOrDefault(false)
    }

    fun invalidate() {
        cachedOwned = null
        cacheTimestamp = 0L
    }
}

/** TENFOOT KOLO2 (N): metadata Jellyfin titulu pro sestavení immersive detailu z pouhého jellyfinId. */
data class JfItemMeta(
    val jellyfinId: String,
    val tmdbId: Long?,
    val imdbId: String?,
    val isSeries: Boolean,
    val name: String,
    val year: Int?,
    val overview: String?,
)

/** TV DETAIL REDESIGN (OTA 299): stav epizod seriálu z Jellyfinu, klíčováno (season, episode). */
data class SeriesEpisodeStatus(
    val watched: Set<Pair<Int, Int>> = emptySet(),
    val progress: Map<Pair<Int, Int>, Int> = emptyMap(),
    val nextUp: Pair<Int, Int>? = null,
    // KOLO2 (G): (season,episode) → jellyfin episode id pro přímé přehrání epizody z knihovny.
    val episodeIds: Map<Pair<Int, Int>, String> = emptyMap(),
)

data class JfCollectionPart(
    val jellyfinId: String,
    val tmdbId: Long?,
    val title: String,
    val posterUrl: String,
    val year: Int?,
    val watched: Boolean,
)

data class JfCollection(
    val name: String,
    val parts: List<JfCollectionPart>,
)

fun normalizeBoxSetName(name: String): String =
    name.lowercase().filter { it.isLetterOrDigit() }

private val EXCLUDED_PATH_SUBSTRINGS = listOf("realdebrid", "/rd/", "real-debrid")

private fun isExcludedPath(path: String?): Boolean {
    val lower = path?.lowercase() ?: return false
    return EXCLUDED_PATH_SUBSTRINGS.any { lower.contains(it) }
}

data class BoxSetInfo(
    val jellyfinId: String,
    val name: String,
    val tmdbCollectionId: Long?,
)

/** COUCH — jedna zhlédnutá položka (tmdb/imdb id) pro Trakt sync/history. */
data class WatchedIds(val tmdb: Long?, val imdb: String?)

/** COUCH — zhlédnuté položky JF profilu rozdělené na filmy vs celé seriály (Trakt payload je odděluje). */
data class WatchedForSync(
    val movies: List<WatchedIds> = emptyList(),
    val shows: List<WatchedIds> = emptyList(),
)

data class OwnedIds(
    val imdbIds: Set<String>,
    val tmdbIds: Set<Long>,
    val imdbToJellyfin: Map<String, String> = emptyMap(),
    val tmdbToJellyfin: Map<Long, String> = emptyMap(),
    val boxSets: List<BoxSetInfo> = emptyList(),
    val boxSetByTmdbCollection: Map<Long, String> = emptyMap(),
    val boxSetByNormalizedName: Map<String, String> = emptyMap(),
    val watchedImdbIds: Set<String> = emptySet(),
    val watchedTmdbIds: Set<Long> = emptySet(),
    val watchedJellyfinIds: Set<String> = emptySet(),
)
