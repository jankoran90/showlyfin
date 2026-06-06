package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class SettingsUiState(
    val traktLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val jellyfinServerUrl: String = "",
    val jellyfinConnected: Boolean = false,
    val jellyfinUserName: String = "",
    val parentalAgeRating: AgeRating = AgeRating.UNRESTRICTED,
    val parentalLocked: Boolean = false,
    val maxParentalRating: Int? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfileId: Long? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktAuthManager: TraktAuthManager,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val KEY_URL = "jellyfin_server_url"
        private const val KEY_TOKEN = "jellyfin_token"
        private const val KEY_USER_ID = "jellyfin_user_id"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshJellyfinState()
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
        parentalControlsRepository.profile
            .onEach { profile ->
                _uiState.update {
                    it.copy(
                        jellyfinUserName = profile.userInfo?.userName ?: "",
                        parentalAgeRating = profile.effectiveAgeRating,
                        parentalLocked = profile.isLocked,
                        maxParentalRating = profile.userInfo?.maxParentalRating,
                    )
                }
            }
            .launchIn(viewModelScope)
        profileRepository.observeAll()
            .onEach { list -> _uiState.update { it.copy(profiles = list) } }
            .launchIn(viewModelScope)
        profileRepository.activeProfile
            .onEach { active -> _uiState.update { it.copy(activeProfileId = active?.id) } }
            .launchIn(viewModelScope)
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setActive(profileId) }
    }

    fun setDefaultProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setDefault(profileId) }
    }

    fun setTvDefaultProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setTvDefault(profileId) }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileRepository.delete(profile) }
    }

    fun updateProfileAgeRating(profileId: Long, rating: AgeRating?) {
        viewModelScope.launch {
            profileRepository.updateMaxAgeRating(profileId, rating?.name)
        }
    }

    private fun refreshJellyfinState() {
        val url = prefs.getString(KEY_URL, "") ?: ""
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        _uiState.update {
            it.copy(
                jellyfinServerUrl = url,
                jellyfinConnected = url.isNotBlank() && token.isNotBlank() && userId.isNotBlank(),
            )
        }
    }

    fun logout() {
        traktAuthManager.logout()
        _uiState.update { it.copy(traktLoggedIn = false) }
    }

    fun disconnectJellyfin() {
        prefs.edit()
            .remove(KEY_URL)
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .apply()
        parentalControlsRepository.clear()
        refreshJellyfinState()
    }
}
