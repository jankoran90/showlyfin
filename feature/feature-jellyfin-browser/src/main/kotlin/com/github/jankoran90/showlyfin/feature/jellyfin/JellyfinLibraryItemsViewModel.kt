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

    fun load(libraryId: String, libraryName: String) {
        _state.update { it.copy(libraryName = libraryName, isLoading = true, error = null) }
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
                val parentUuid = UUID.fromString(libraryId)
                val result = apiClient.itemsApi.getItems(
                    userId = userUuid,
                    parentId = parentUuid,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                    limit = 200,
                ).content
                val items = result.items.map { it.toJellyfinItem(serverUrl, token) }
                _state.update { it.copy(items = items, isLoading = false) }
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
