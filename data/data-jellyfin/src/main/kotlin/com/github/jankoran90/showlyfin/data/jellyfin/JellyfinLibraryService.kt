package com.github.jankoran90.showlyfin.data.jellyfin

import android.content.SharedPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
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

    fun invalidate() {
        cachedOwned = null
        cacheTimestamp = 0L
    }
}

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
