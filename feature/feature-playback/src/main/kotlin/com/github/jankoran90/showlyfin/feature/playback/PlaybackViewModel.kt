package com.github.jankoran90.showlyfin.feature.playback

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
class PlaybackViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun load(itemId: String, positionMs: Long) {
        _state.value = PlaybackUiState(isLoading = true)
        viewModelScope.launch {
            val serverUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""

            if (serverUrl.isBlank() || token.isBlank()) {
                _state.update {
                    it.copy(isLoading = false, error = "Jellyfin není nastaven. Přejdi do Jellyfin záložky.")
                }
                return@launch
            }

            try {
                apiClient.update(
                    baseUrl = serverUrl,
                    accessToken = token,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )

                val title = runCatching {
                    val userUuid = UUID.fromString(userId)
                    val itemUuid = UUID.fromString(itemId)
                    apiClient.userLibraryApi.getItem(userId = userUuid, itemId = itemUuid)
                        .content.name ?: itemId
                }.getOrDefault(itemId)

                val streamUrl = "$serverUrl/Videos/$itemId/stream?static=true&api_key=$token"

                _state.update {
                    it.copy(
                        isLoading = false,
                        title = title,
                        streamUrl = streamUrl,
                        positionMs = positionMs,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba přehrávání") }
            }
        }
    }
}
