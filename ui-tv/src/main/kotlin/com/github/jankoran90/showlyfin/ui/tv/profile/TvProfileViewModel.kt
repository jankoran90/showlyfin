package com.github.jankoran90.showlyfin.ui.tv.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.PinHasher
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ProfileActivator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * COUCH — přepínač Jellyfin profilu (dospělý/deti) ze sidebaru. Přepnutí = [ProfileActivator.activate]:
 * hydratuje JF creds z backendu (auto-login) PŘED aktivací, aby deti (stub bez creds) přestal hlásit
 * „JF není připojený". Reload domova (řady + per-profil layout) řeší [TvHomeViewModel] observací
 * activeProfile. Known gap: PIN prompt profilu na TV zatím neřešíme (TV = doma; číselník na D-padu = follow-up).
 */
@HiltViewModel
class TvProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileActivator: ProfileActivator,
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active: StateFlow<ProfileEntity?> = profileRepository.activeProfile

    /** Probíhá aktivace (hydratace + login) — overlay může držet spinner. */
    private val _activating = MutableStateFlow(false)
    val activating: StateFlow<Boolean> = _activating.asStateFlow()

    /** Jellyfin odmítl přihlášení profilu (null = bez chyby). */
    private val _activationError = MutableStateFlow<String?>(null)
    val activationError: StateFlow<String?> = _activationError.asStateFlow()

    /** Profil čekající na PIN (klik na profil s [ProfileEntity.loginPinHash]); null = žádný prompt. */
    private val _pendingPin = MutableStateFlow<ProfileEntity?>(null)
    val pendingPin: StateFlow<ProfileEntity?> = _pendingPin.asStateFlow()

    /** Poslední zadaný PIN byl špatný. */
    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    /** Vstupní bod z pickeru: bez PINu → rovnou přepni; s PINem → otevři PIN prompt (paritní s telefonem). */
    fun onProfileClicked(profile: ProfileEntity) {
        if (profile.loginPinHash.isNullOrBlank()) {
            switchTo(profile.id)
        } else {
            _pinError.value = false
            _pendingPin.value = profile
        }
    }

    /** Ověří PIN proti čekajícímu profilu; shoda → přepni + zavři prompt, jinak chyba. */
    fun submitPin(pin: String) {
        val pending = _pendingPin.value ?: return
        if (PinHasher.verify(pin, pending.loginPinHash)) {
            _pendingPin.value = null
            _pinError.value = false
            switchTo(pending.id)
        } else {
            _pinError.value = true
        }
    }

    fun cancelPin() {
        _pendingPin.value = null
        _pinError.value = false
    }

    fun switchTo(profileId: Long) {
        android.util.Log.i("COUCH_Profile", "switchTo($profileId)")
        val profile = profiles.value.firstOrNull { it.id == profileId } ?: return
        viewModelScope.launch {
            _activating.value = true
            _activationError.value = null
            val error = runCatching { profileActivator.activate(profile) }
                .onFailure { android.util.Log.w("COUCH_Profile", "aktivace selhala", it) }
                .getOrNull()
            _activationError.value = error
            _activating.value = false
            android.util.Log.i("COUCH_Profile", "activate($profileId) hotovo (err=$error)")
        }
    }
}
