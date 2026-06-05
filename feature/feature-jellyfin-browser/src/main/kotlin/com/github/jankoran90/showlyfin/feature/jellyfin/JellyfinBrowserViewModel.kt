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
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class JellyfinBrowserViewModel @Inject constructor(
    private val jellyfinApiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val KEY_URL = "jellyfin_server_url"
        private const val KEY_TOKEN = "jellyfin_token"
        private const val KEY_USER_ID = "jellyfin_user_id"
    }

    private val _uiState = MutableStateFlow(JellyfinBrowserUiState())
    val uiState: StateFlow<JellyfinBrowserUiState> = _uiState.asStateFlow()

    init {
        val savedUrl = prefs.getString(KEY_URL, "") ?: ""
        val savedToken = prefs.getString(KEY_TOKEN, "") ?: ""
        val savedUserId = prefs.getString(KEY_USER_ID, "") ?: ""
        _uiState.update { it.copy(serverUrl = savedUrl) }
        if (savedUrl.isNotBlank() && savedToken.isNotBlank() && savedUserId.isNotBlank()) {
            loadLibraries(savedUrl, savedToken, savedUserId)
        }
    }

    fun connect(serverUrl: String, token: String, userId: String) {
        prefs.edit()
            .putString(KEY_URL, serverUrl)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .apply()
        loadLibraries(serverUrl, token, userId)
    }

    private fun loadLibraries(serverUrl: String, token: String, userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, serverUrl = serverUrl) }
            try {
                jellyfinApiClient.update(
                    baseUrl = serverUrl,
                    accessToken = token,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )
                val userUuid = UUID.fromString(userId)
                val response = jellyfinApiClient.userViewsApi.getUserViews(userId = userUuid)
                val libraries = response.content.items?.map { view ->
                    JellyfinLibrary(
                        id = view.id.toString(),
                        name = view.name ?: "",
                        itemCount = view.childCount,
                        imageTag = view.imageTags?.get("Primary"),
                    )
                } ?: emptyList()
                _uiState.update { it.copy(libraries = libraries, isLoading = false, isConnected = true) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba připojení") }
            }
        }
    }
}
