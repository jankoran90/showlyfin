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
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class JellyfinDetailViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(JellyfinDetailUiState())
    val state: StateFlow<JellyfinDetailUiState> = _state.asStateFlow()

    fun load(itemId: String) {
        _state.update { it.copy(isLoading = true, error = null) }
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
                )
                _state.update { it.copy(isLoading = false, detail = detail) }
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba načtení") }
            }
        }
    }
}
