package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileGateState(
    val isLoading: Boolean = true,
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfile: ProfileEntity? = null,
    val isAddingProfile: Boolean = false,
    /** Viditelné sekce aktivního profilu (Plan PROFILES 1E). Prázdné = vše (admin/legacy). */
    val visibleSections: Set<String> = emptySet(),
    /** „Hlavní" sekce aktivního profilu (Plan PROFILES Fáze 4). null = výchozí. */
    val defaultSection: String? = null,
    /** Plan WARDEN W1: profil čekající na zadání PINu (klik na profil s [ProfileEntity.loginPinHash]). */
    val pendingPinProfile: ProfileEntity? = null,
    /** Plan WARDEN W1: poslední zadaný PIN byl špatný. */
    val pinError: Boolean = false,
)

@HiltViewModel
class ProfileGateViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileGateState())
    val state: StateFlow<ProfileGateState> = _state.asStateFlow()

    init {
        profileRepository.observeAll()
            .combine(profileRepository.activeProfile) { profiles, active -> profiles to active }
            .onEach { (profiles, active) ->
                _state.value = _state.value.copy(
                    profiles = profiles,
                    activeProfile = active,
                    isLoading = false,
                )
            }
            .launchIn(viewModelScope)
        profileRepository.activeConfig
            .onEach { cfg ->
                _state.value = _state.value.copy(
                    visibleSections = cfg.visibleSections,
                    defaultSection = cfg.defaultSection,
                )
            }
            .launchIn(viewModelScope)
    }

    fun selectProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileRepository.setActive(profile.id) }
    }

    /**
     * Plan WARDEN W1 — klik na avatar v úvodní bráně. Bez PINu → rovnou aktivuje; s PINem
     * ([ProfileEntity.loginPinHash]) → otevře PIN prompt (ověří se v [submitPin]).
     */
    fun onProfileClicked(profile: ProfileEntity) {
        if (profile.loginPinHash.isNullOrBlank()) {
            selectProfile(profile)
        } else {
            _state.value = _state.value.copy(pendingPinProfile = profile, pinError = false)
        }
    }

    /** Ověří zadaný PIN proti čekajícímu profilu; shoda → aktivuje a zavře prompt, jinak chyba. */
    fun submitPin(pin: String) {
        val pending = _state.value.pendingPinProfile ?: return
        if (PinHasher.verify(pin, pending.loginPinHash)) {
            _state.value = _state.value.copy(pendingPinProfile = null, pinError = false)
            selectProfile(pending)
        } else {
            _state.value = _state.value.copy(pinError = true)
        }
    }

    fun cancelPin() {
        _state.value = _state.value.copy(pendingPinProfile = null, pinError = false)
    }

    fun startAddProfile() {
        _state.value = _state.value.copy(isAddingProfile = true)
    }

    fun cancelAddProfile() {
        _state.value = _state.value.copy(isAddingProfile = false)
    }
}
