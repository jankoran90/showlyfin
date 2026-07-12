package com.github.jankoran90.showlyfin.feature.jellyfin

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class JellyfinDetailViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val tmdbApi: TmdbRemoteDataSource,
    private val jellyfinLibraryService: JellyfinLibraryService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(JellyfinDetailUiState())
    val state: StateFlow<JellyfinDetailUiState> = _state.asStateFlow()

    fun load(itemId: String) {
        _state.update { it.copy(isLoading = true, error = null, collection = null) }
        viewModelScope.launch {
            val serverUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""
            if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) {
                _state.update { it.copy(isLoading = false, error = "Jellyfin není nastaven") }
                return@launch
            }
            try {
                apiClient.update(
                    baseUrl = serverUrl,
                    accessToken = token,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )
                val userUuid = UUID.fromString(userId)
                val itemUuid = UUID.fromString(itemId)
                val response = apiClient.userLibraryApi.getItem(userId = userUuid, itemId = itemUuid).content
                val runtimeMin = response.runTimeTicks?.let { (it / 600_000_000L).toInt() }
                val providerIds = response.providerIds ?: emptyMap()
                val tmdbId = providerIds["Tmdb"]?.toLongOrNull()
                val imdbId = providerIds["Imdb"]
                val detail = JellyfinDetail(
                    id = response.id.toString(),
                    name = response.name ?: "",
                    overview = response.overview,
                    backdropUrl = response.backdropImageTags?.firstOrNull()?.let { tag ->
                        "$serverUrl/Items/${response.id}/Images/Backdrop?tag=$tag&quality=85"
                    } ?: "$serverUrl/Items/${response.id}/Images/Backdrop?quality=85&api_key=$token",
                    posterUrl = "$serverUrl/Items/${response.id}/Images/Primary?quality=85&api_key=$token",
                    year = response.productionYear,
                    runtimeMinutes = runtimeMin,
                    rating = response.communityRating,
                    officialRating = response.officialRating,
                    genres = response.genres.orEmpty(),
                    type = response.type.name,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                )
                _state.update { it.copy(isLoading = false, detail = detail) }
                if (response.type.name.equals("MOVIE", ignoreCase = true)) {
                    loadCollectionWithPriority(itemUuid, userUuid, serverUrl, token, tmdbId)
                } else if (response.type == BaseItemKind.SERIES) {
                    loadNextUp(itemUuid, userUuid, serverUrl, token)
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba načtení") }
            }
        }
    }

    /**
     * TENFOOT — „další na řadě" epizoda pro kartu seriálu (yellyfin-like). Jellyfin NextUp = první nezhlédnutá
     * napříč sezónami (respektuje watched stav ze serveru); fallback na první epizodu (nový seriál). Jen náhled
     * (1 položka) — plný výběr řeší EpisodePicker.
     */
    private fun loadNextUp(seriesUuid: UUID, userUuid: UUID, serverUrl: String, token: String) {
        viewModelScope.launch {
            val ep = runCatching {
                apiClient.tvShowsApi.getNextUp(
                    userId = userUuid,
                    seriesId = seriesUuid,
                    fields = listOf(ItemFields.OVERVIEW),
                ).content.items.firstOrNull()
                    ?: apiClient.tvShowsApi.getEpisodes(
                        seriesId = seriesUuid,
                        userId = userUuid,
                        limit = 1,
                        fields = listOf(ItemFields.OVERVIEW),
                    ).content.items.firstOrNull()
            }.getOrNull() ?: return@launch
            val epId = ep.id.toString()
            _state.update {
                it.copy(
                    nextUp = EpisodeRow(
                        id = epId,
                        seasonNumber = ep.parentIndexNumber,
                        episodeNumber = ep.indexNumber,
                        name = ep.name ?: "Epizoda",
                        imageUrl = "$serverUrl/Items/$epId/Images/Primary?fillWidth=480&quality=85&api_key=$token",
                        overview = ep.overview,
                        watched = ep.userData?.played == true,
                        progressPct = ep.userData?.playedPercentage?.toInt(),
                        isNextUp = true,
                    ),
                )
            }
        }
    }

    private fun loadCollectionWithPriority(
        itemUuid: UUID,
        userUuid: UUID,
        serverUrl: String,
        token: String,
        tmdbId: Long?,
    ) {
        viewModelScope.launch {
            val jellyfinCollection = runCatching {
                findJellyfinBoxSet(itemUuid, userUuid, serverUrl, token)
            }.getOrNull()

            if (jellyfinCollection != null) {
                val tmdbCollectionResolved = if (tmdbId != null && tmdbId > 0) {
                    runCatching { loadTmdbCollection(tmdbId, userUuid) }.getOrNull()
                } else null
                val merged = if (tmdbCollectionResolved != null) {
                    mergeBoxSetWithTmdb(jellyfinCollection, tmdbCollectionResolved)
                } else jellyfinCollection
                _state.update { it.copy(collection = merged) }
                return@launch
            }
            if (tmdbId != null && tmdbId > 0) {
                val tmdbCollection = loadTmdbCollection(tmdbId, userUuid)
                if (tmdbCollection != null) {
                    _state.update { it.copy(collection = tmdbCollection) }
                }
            }
        }
    }

    private fun mergeBoxSetWithTmdb(jellyfin: MediaCollection, tmdb: MediaCollection): MediaCollection {
        val jellyfinTmdbIds = jellyfin.parts.mapNotNull { it.tmdbId }.toSet()
        val missing = tmdb.parts.filter { it.tmdbId != null && it.tmdbId !in jellyfinTmdbIds }
        return MediaCollection(
            name = jellyfin.name,
            parts = (jellyfin.parts + missing).sortedBy { it.year?.toIntOrNull() ?: Int.MAX_VALUE },
        )
    }

    private suspend fun findJellyfinBoxSet(
        itemUuid: UUID,
        userUuid: UUID,
        serverUrl: String,
        token: String,
    ): MediaCollection? {
        val boxSets = apiClient.itemsApi.getItems(
            userId = userUuid,
            includeItemTypes = listOf(BaseItemKind.BOX_SET),
            recursive = true,
        ).content.items.orEmpty()

        for (boxSet in boxSets) {
            val children = runCatching {
                apiClient.itemsApi.getItems(
                    userId = userUuid,
                    parentId = boxSet.id,
                ).content.items.orEmpty()
            }.getOrNull() ?: continue

            if (children.any { it.id == itemUuid }) {
                val parts = children.map { child ->
                    CollectionPart(
                        key = "jellyfin_${child.id}",
                        tmdbId = child.providerIds?.get("Tmdb")?.toLongOrNull(),
                        jellyfinId = child.id.toString(),
                        title = child.name ?: "",
                        posterUrl = "$serverUrl/Items/${child.id}/Images/Primary?quality=85&api_key=$token",
                        year = child.productionYear?.toString(),
                        watched = child.userData?.played == true,
                    )
                }.sortedBy { it.year?.toIntOrNull() ?: Int.MAX_VALUE }
                return MediaCollection(
                    name = boxSet.name ?: "Kolekce",
                    parts = parts,
                )
            }
        }
        return null
    }

    private suspend fun loadTmdbCollection(tmdbId: Long, userUuid: UUID): MediaCollection? {
        val collectionId = runCatching { tmdbApi.fetchMovieDetails(tmdbId)?.belongs_to_collection?.id }.getOrNull()
        if (collectionId == null || collectionId <= 0) return null
        val collection = runCatching { tmdbApi.fetchCollection(collectionId) }.getOrNull() ?: return null
        val owned = runCatching { jellyfinLibraryService.getOwnedIds(userUuid) }.getOrNull()
        val ownedTmdbToJellyfin = owned?.tmdbToJellyfin.orEmpty()
        val watchedTmdb = owned?.watchedTmdbIds.orEmpty()
        val parts = collection.parts.orEmpty().map { part ->
            CollectionPart(
                key = "tmdb_${part.id}",
                tmdbId = part.id,
                jellyfinId = ownedTmdbToJellyfin[part.id],
                title = part.title ?: "",
                posterUrl = part.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                year = part.release_date?.take(4),
                watched = watchedTmdb.contains(part.id),
            )
        }
        return MediaCollection(
            name = collection.name ?: "Kolekce",
            parts = parts.sortedBy { it.year?.toIntOrNull() ?: Int.MAX_VALUE },
        )
    }
}
