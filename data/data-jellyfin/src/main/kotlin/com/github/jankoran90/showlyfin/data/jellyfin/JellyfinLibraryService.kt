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
        val owned = OwnedIds(imdb, tmdb, imdbToJellyfin, tmdbToJellyfin)
        cachedOwned = owned
        cacheTimestamp = now
        return owned
    }

    fun invalidate() {
        cachedOwned = null
        cacheTimestamp = 0L
    }
}

data class OwnedIds(
    val imdbIds: Set<String>,
    val tmdbIds: Set<Long>,
    val imdbToJellyfin: Map<String, String> = emptyMap(),
    val tmdbToJellyfin: Map<Long, String> = emptyMap(),
)
