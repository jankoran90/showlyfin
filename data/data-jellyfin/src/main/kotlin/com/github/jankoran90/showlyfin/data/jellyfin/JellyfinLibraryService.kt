package com.github.jankoran90.showlyfin.data.jellyfin

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinLibraryService @Inject constructor(
    private val apiClient: ApiClient,
) {
    private var cachedOwned: OwnedIds? = null
    private var cacheTimestamp: Long = 0L
    private val cacheValidMs = 5 * 60 * 1000L

    suspend fun getOwnedIds(userId: UUID): OwnedIds {
        val now = System.currentTimeMillis()
        val cached = cachedOwned
        if (cached != null && (now - cacheTimestamp) < cacheValidMs) {
            return cached
        }

        val imdb = mutableSetOf<String>()
        val tmdb = mutableSetOf<Long>()
        val imdbToJellyfin = mutableMapOf<String, String>()
        val tmdbToJellyfin = mutableMapOf<Long, String>()
        runCatching {
            val response = apiClient.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                fields = listOf(ItemFields.PROVIDER_IDS),
                limit = 5000,
            ).content
            for (item in response.items) {
                val ids = item.providerIds ?: continue
                val jellyfinId = item.id.toString()
                ids["Imdb"]?.takeIf { it.isNotBlank() }?.let {
                    imdb.add(it)
                    imdbToJellyfin.putIfAbsent(it, jellyfinId)
                }
                ids["Tmdb"]?.toLongOrNull()?.let {
                    tmdb.add(it)
                    tmdbToJellyfin.putIfAbsent(it, jellyfinId)
                }
            }
        }

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
        }

        val owned = OwnedIds(
            imdbIds = imdb,
            tmdbIds = tmdb,
            imdbToJellyfin = imdbToJellyfin,
            tmdbToJellyfin = tmdbToJellyfin,
            boxSets = boxSets,
            boxSetByTmdbCollection = boxSetByTmdbCollection,
            boxSetByNormalizedName = boxSetByNormalizedName,
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
)
