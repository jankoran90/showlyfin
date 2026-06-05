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
    private var cachedImdbIds: Set<String>? = null
    private var cachedTmdbIds: Set<Long>? = null
    private var cacheTimestamp: Long = 0L
    private val cacheValidMs = 5 * 60 * 1000L

    suspend fun getOwnedIds(userId: UUID): OwnedIds {
        val now = System.currentTimeMillis()
        val cachedImdb = cachedImdbIds
        val cachedTmdb = cachedTmdbIds
        if (cachedImdb != null && cachedTmdb != null && (now - cacheTimestamp) < cacheValidMs) {
            return OwnedIds(cachedImdb, cachedTmdb)
        }

        val imdb = mutableSetOf<String>()
        val tmdb = mutableSetOf<Long>()
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
                ids["Imdb"]?.takeIf { it.isNotBlank() }?.let { imdb.add(it) }
                ids["Tmdb"]?.toLongOrNull()?.let { tmdb.add(it) }
            }
        }
        cachedImdbIds = imdb
        cachedTmdbIds = tmdb
        cacheTimestamp = now
        return OwnedIds(imdb, tmdb)
    }

    fun invalidate() {
        cachedImdbIds = null
        cachedTmdbIds = null
        cacheTimestamp = 0L
    }
}

data class OwnedIds(val imdbIds: Set<String>, val tmdbIds: Set<Long>)
