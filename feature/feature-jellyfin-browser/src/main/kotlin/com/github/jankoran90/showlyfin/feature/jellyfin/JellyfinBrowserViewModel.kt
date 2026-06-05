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
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class JellyfinBrowserViewModel @Inject constructor(
    private val jellyfin: Jellyfin,
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
            viewModelScope.launch {
                loadLibraries(savedUrl, savedToken, savedUserId)
            }
        }
    }

    fun connect(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, serverUrl = serverUrl) }
            try {
                val tempApi = jellyfin.createApi(baseUrl = serverUrl)
                val authResult by tempApi.userApi.authenticateUserByName(
                    username = username,
                    password = password,
                )
                val accessToken = authResult.accessToken
                    ?: throw IllegalStateException("Server nevrátil přístupový token")
                val userId = authResult.user?.id?.toString()
                    ?: throw IllegalStateException("Server nevrátil ID uživatele")

                prefs.edit()
                    .putString(KEY_URL, serverUrl)
                    .putString(KEY_TOKEN, accessToken)
                    .putString(KEY_USER_ID, userId)
                    .apply()

                loadLibraries(serverUrl, accessToken, userId)
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba přihlášení") }
            }
        }
    }

    private suspend fun loadLibraries(serverUrl: String, token: String, userId: String) {
        _uiState.update { it.copy(isLoading = true, error = null, serverUrl = serverUrl) }
        try {
            jellyfinApiClient.update(
                baseUrl = serverUrl,
                accessToken = token,
                clientInfo = clientInfo,
                deviceInfo = deviceInfo,
            )
            val userUuid = UUID.fromString(userId)
            val views = jellyfinApiClient.userViewsApi.getUserViews(userId = userUuid).content
            val libraries = views.items.map { view ->
                val viewId = view.id.toString()
                JellyfinLibrary(
                    id = viewId,
                    name = view.name ?: "Knihovna",
                    collectionType = view.collectionType?.name,
                    imageUrl = "$serverUrl/Items/$viewId/Images/Primary?fillWidth=400&quality=85&api_key=$token",
                )
            }
            _uiState.update { it.copy(libraries = libraries, isLoading = false, isConnected = true) }
        } catch (e: Throwable) {
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba připojení") }
        }
    }
}
