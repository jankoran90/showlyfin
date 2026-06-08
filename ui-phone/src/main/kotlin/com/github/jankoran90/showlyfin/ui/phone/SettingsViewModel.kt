package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.StreamFilterPrefs
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
    // Stremio / Comet filtr výsledků
    val streamFilter: StreamFilterPrefs? = null,
    val streamFilterLoading: Boolean = false,
    val streamFilterError: String? = null,
    // Živé logování (Debug)
    val liveLogging: Boolean = false,
    // Poslech / Audiobookshelf
    val absConfigured: Boolean = false,
    val absBaseUrl: String = "",
    val absLoading: Boolean = false,
    val absError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktAuthManager: TraktAuthManager,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val absRepo: AbsRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val KEY_URL = "jellyfin_server_url"
        private const val KEY_TOKEN = "jellyfin_token"
        private const val KEY_USER_ID = "jellyfin_user_id"
        const val KEY_LIVE_LOGGING = "live_logging_enabled"
    }

    private val uploaderBase get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

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
        _uiState.update { it.copy(liveLogging = prefs.getBoolean(KEY_LIVE_LOGGING, false)) }
        refreshAbsState()
        loadStreamFilter()
    }

    // ── Poslech / Audiobookshelf ──────────────────────────────────────────────

    private fun refreshAbsState() {
        _uiState.update { it.copy(absConfigured = absRepo.isConfigured, absBaseUrl = absRepo.baseUrl) }
    }

    fun absLogin(url: String, username: String, password: String) {
        _uiState.update { it.copy(absLoading = true, absError = null) }
        viewModelScope.launch {
            absRepo.login(url, username, password)
                .onSuccess {
                    _uiState.update { it.copy(absLoading = false, absConfigured = true, absBaseUrl = absRepo.baseUrl) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(absLoading = false, absError = e.message ?: "Přihlášení selhalo") }
                }
        }
    }

    fun absLogout() {
        absRepo.logout()
        refreshAbsState()
    }

    // ── Stremio / Comet filtr ─────────────────────────────────────────────────

    fun loadStreamFilter() {
        if (uploaderBase.isBlank()) return
        _uiState.update { it.copy(streamFilterLoading = true, streamFilterError = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.getStreamFilter(uploaderBase, uploaderCookie) }
                .onSuccess { sf -> _uiState.update { it.copy(streamFilter = sf, streamFilterLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(streamFilterLoading = false, streamFilterError = e.message) } }
        }
    }

    /** Lokálně updatuje + uloží na backend (merge-safe endpoint). */
    fun updateStreamFilter(transform: (StreamFilterPrefs) -> StreamFilterPrefs) {
        val current = _uiState.value.streamFilter ?: return
        val updated = transform(current)
        _uiState.update { it.copy(streamFilter = updated) }
        if (uploaderBase.isBlank()) return
        viewModelScope.launch {
            runCatching { uploaderDs.putStreamFilter(uploaderBase, uploaderCookie, updated) }
                .onFailure { e -> _uiState.update { it.copy(streamFilterError = e.message) } }
        }
    }

    /** Posun položky ve fallbackOrder (dir = -1 nahoru, +1 dolů). */
    fun moveFallback(index: Int, dir: Int) {
        val sf = _uiState.value.streamFilter ?: return
        val list = sf.fallbackOrder.toMutableList()
        val target = index + dir
        if (index !in list.indices || target !in list.indices) return
        val tmp = list[index]; list[index] = list[target]; list[target] = tmp
        updateStreamFilter { it.copy(fallbackOrder = list) }
    }

    fun toggleFallback(key: String, enabled: Boolean) {
        val sf = _uiState.value.streamFilter ?: return
        val list = sf.fallbackOrder.toMutableList()
        if (enabled) { if (key !in list) list.add(key) } else list.remove(key)
        updateStreamFilter { it.copy(fallbackOrder = list) }
    }

    fun setLiveLogging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_LOGGING, enabled).apply()
        _uiState.update { it.copy(liveLogging = enabled) }
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
