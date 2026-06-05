package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val traktLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktAuthManager: TraktAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(traktLoggedIn = traktAuthManager.isLoggedIn()) }
        viewModelScope.launch {
            traktAuthManager.authCodeFlow.collect { code ->
                _uiState.update { it.copy(isLoading = true, error = null) }
                try {
                    traktAuthManager.authorize(code)
                    _uiState.update { it.copy(traktLoggedIn = true, isLoading = false) }
                } catch (e: Throwable) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba autorizace") }
                }
            }
        }
    }

    fun logout() {
        traktAuthManager.logout()
        _uiState.update { it.copy(traktLoggedIn = false) }
    }
}
