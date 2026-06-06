package com.github.jankoran90.showlyfin.feature.jellyfin

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class JellyfinLibraryItemsViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(JellyfinLibraryItemsUiState())
    val state: StateFlow<JellyfinLibraryItemsUiState> = _state.asStateFlow()

    private var currentLibraryId: String = ""
    private var currentLibraryName: String = ""
    private var currentCollectionType: String? = null
    private var currentParentItemType: String? = null

    fun load(
        libraryId: String,
        libraryName: String,
        collectionType: String? = null,
        parentItemType: String? = null,
    ) {
        currentLibraryId = libraryId
        currentLibraryName = libraryName
        currentCollectionType = collectionType
        currentParentItemType = parentItemType
        val isBoxSetContext =
            parentItemType.equals("BOX_SET", ignoreCase = true) ||
                collectionType.equals("BOXSETS", ignoreCase = true)
        _state.update {
            it.copy(
                libraryName = libraryName,
                isLoading = true,
                error = null,
                isBoxSetContext = isBoxSetContext,
            )
        }
        reload()
    }

    fun selectSort(sort: JellyfinSort) {
        _state.update { it.copy(sort = sort, isLoading = true) }
        reload()
    }

    fun selectTypeFilter(filter: JellyfinTypeFilter) {
        _state.update { it.copy(typeFilter = filter, isLoading = true) }
        reload()
    }

    private fun reload() {
        if (currentLibraryId.isBlank()) return
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
                val parentUuid = UUID.fromString(currentLibraryId)
                val (sortBy, sortOrder) = when (_state.value.sort) {
                    JellyfinSort.NAME -> ItemSortBy.SORT_NAME to SortOrder.ASCENDING
                    JellyfinSort.DATE_ADDED -> ItemSortBy.DATE_CREATED to SortOrder.DESCENDING
                    JellyfinSort.YEAR_DESC -> ItemSortBy.PRODUCTION_YEAR to SortOrder.DESCENDING
                    JellyfinSort.RATING -> ItemSortBy.COMMUNITY_RATING to SortOrder.DESCENDING
                    JellyfinSort.RANDOM -> ItemSortBy.RANDOM to SortOrder.ASCENDING
                }
                val isBoxSetParent = currentParentItemType.equals("BOX_SET", ignoreCase = true)
                val isBoxSetsLibrary = currentCollectionType.equals("BOXSETS", ignoreCase = true)
                val typeKinds: List<BaseItemKind> = when {
                    isBoxSetParent -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE)
                    isBoxSetsLibrary -> listOf(BaseItemKind.BOX_SET)
                    else -> when (_state.value.typeFilter) {
                        JellyfinTypeFilter.ALL -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                        JellyfinTypeFilter.MOVIE -> listOf(BaseItemKind.MOVIE)
                        JellyfinTypeFilter.SERIES -> listOf(BaseItemKind.SERIES)
                    }
                }
                val useRecursive = !(isBoxSetParent || isBoxSetsLibrary)
                val result = apiClient.itemsApi.getItems(
                    userId = userUuid,
                    parentId = parentUuid,
                    includeItemTypes = typeKinds,
                    recursive = useRecursive,
                    sortBy = listOf(sortBy),
                    sortOrder = listOf(sortOrder),
                    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                    limit = 200,
                ).content
                val items = result.items.map { it.toJellyfinItem(serverUrl, token) }
                _state.update { it.copy(items = items, isLoading = false, error = null) }
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba načtení") }
            }
        }
    }
}

private fun BaseItemDto.toJellyfinItem(serverUrl: String, token: String) = JellyfinItem(
    id = id.toString(),
    name = name ?: "",
    imageUrl = "$serverUrl/Items/$id/Images/Primary?fillWidth=320&quality=85&api_key=$token",
    year = productionYear,
    type = type.name,
    isFolder = isFolder == true,
    progressPct = userData?.playedPercentage?.toInt(),
)
