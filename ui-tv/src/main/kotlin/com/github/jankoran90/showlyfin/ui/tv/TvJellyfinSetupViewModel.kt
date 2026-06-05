package com.github.jankoran90.showlyfin.ui.tv

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
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import javax.inject.Inject
import javax.inject.Named

data class TvJellyfinSetupUiState(
    val serverUrl: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class TvJellyfinSetupViewModel @Inject constructor(
    private val jellyfin: Jellyfin,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val KEY_URL = "jellyfin_server_url"
        private const val KEY_TOKEN = "jellyfin_token"
        private const val KEY_USER_ID = "jellyfin_user_id"
    }

    private val _state = MutableStateFlow(
        TvJellyfinSetupUiState(serverUrl = prefs.getString(KEY_URL, "") ?: "")
    )
    val state: StateFlow<TvJellyfinSetupUiState> = _state.asStateFlow()

    fun connect(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, serverUrl = serverUrl, username = username) }
            try {
                val api = jellyfin.createApi(baseUrl = serverUrl)
                val authResult by api.userApi.authenticateUserByName(
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
                _state.update { it.copy(isLoading = false, success = true) }
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba přihlášení") }
            }
        }
    }

    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }
}
