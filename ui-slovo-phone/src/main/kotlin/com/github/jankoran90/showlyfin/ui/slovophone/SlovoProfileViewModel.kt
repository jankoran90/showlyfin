package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.PinHasher
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SlovoProfileUiState(
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfileId: Long? = null,
    /** Admin authoring (2026-08-15): všechny ABS podcasty + jestli jsou vidět dětskému profilu. */
    val podcasts: List<Podcast> = emptyList(),
    val hiddenForKids: Set<String> = emptySet(),
    val podcastsLoading: Boolean = false,
    /** null = žádná chyba (buď se ještě nenačítalo, nebo úspěch — i s prázdným seznamem). */
    val podcastsError: String? = null,
)

/**
 * Profily (2026-08-15) — VM sekce „Profil" appky Slovo: přepínání Dospělý/Děti + PIN (vzor
 * showlyfin/Filmy `SettingsViewModel`, zúžené jen na profily — Slovo nemá Trakt/šablony/roster).
 * Navíc admin curation: Dospělý v seznamu podcastů odškrtává, které se zobrazí Dětskému profilu
 * ([com.github.jankoran90.showlyfin.core.domain.ProfileConfig.hiddenPodcastIds] na profilu Děti,
 * NE na aktivním — proto se zapisuje přímo přes [ProfileRepository.updateConfig] s kids ID).
 */
@HiltViewModel
class SlovoProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val absRepo: AbsRepository,
) : ViewModel() {

    private val _podcasts = MutableStateFlow<List<Podcast>>(emptyList())
    private val _podcastsLoading = MutableStateFlow(false)
    private val _podcastsError = MutableStateFlow<String?>(null)
    private val _kidsHidden = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<SlovoProfileUiState> =
        combine(
            profileRepository.observeAll(),
            profileRepository.activeProfile,
            _podcasts,
            _kidsHidden,
            _podcastsLoading,
        ) { profiles, active, podcasts, hidden, loading ->
            SlovoProfileUiState(profiles, active?.id, podcasts, hidden, loading)
        }.combine(_podcastsError) { state, err -> state.copy(podcastsError = err) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SlovoProfileUiState())

    fun switchProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setActive(profileId) }
    }

    fun setProfilePin(profileId: Long, pin: String) {
        viewModelScope.launch {
            val trimmed = pin.trim()
            val hash = if (trimmed.isBlank()) null else PinHasher.hash(trimmed)
            profileRepository.setLoginPinHash(profileId, hash)
        }
    }

    fun clearProfilePin(profileId: Long) {
        viewModelScope.launch { profileRepository.setLoginPinHash(profileId, null) }
    }

    /** Načte všechny ABS podcasty (napříč podcastovými knihovnami) + aktuální skrytí pro Děti. */
    fun loadPodcastCuration() {
        viewModelScope.launch {
            _podcastsLoading.value = true
            _podcastsError.value = null
            runCatching {
                val libs = absRepo.getPodcastLibraries()
                libs.flatMap { absRepo.getPodcasts(it.id) }
            }
                .onSuccess { _podcasts.value = it }
                .onFailure { e ->
                    Timber.w(e, "[SLOVO] loadPodcastCuration selhalo")
                    _podcastsError.value = e.message ?: "Načtení podcastů selhalo"
                }
            val kids = profileRepository.getAll().firstOrNull { it.profileUuid == SlovoProfiles.UUID_KIDS }
            _kidsHidden.value = kids?.let { ProfileConfig.fromJson(it.configJson).hiddenPodcastIds }.orEmpty()
            _podcastsLoading.value = false
        }
    }

    /** Přepne viditelnost jednoho podcastu pro Dětský profil (zapisuje do KIDS configu, ne do aktivního). */
    fun setPodcastVisibleForKids(podcastId: String, visible: Boolean) {
        viewModelScope.launch {
            val kids = profileRepository.getAll().firstOrNull { it.profileUuid == SlovoProfiles.UUID_KIDS } ?: return@launch
            profileRepository.updateConfig(kids.id) { cfg ->
                cfg.copy(
                    hiddenPodcastIds = if (visible) cfg.hiddenPodcastIds - podcastId else cfg.hiddenPodcastIds + podcastId,
                )
            }
            _kidsHidden.value = if (visible) _kidsHidden.value - podcastId else _kidsHidden.value + podcastId
        }
    }
}
