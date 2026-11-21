package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.KidsDisplayPrefs
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Co rodič na obrazovce Pro rodiče vidí o schváleném obsahu (z whitelistu configu Dětí). */
data class KidsApprovedSummary(val shows: Int = 0, val series: Int = 0, val episodes: Int = 0)

data class SlovoParentUiState(
    /** Hash PINu, kterým se rodič ověří (PIN dospělého profilu); null = v appce žádný PIN není. */
    val guardPinHash: String? = null,
    val guardProfileName: String? = null,
    val display: KidsDisplayPrefs = KidsDisplayPrefs.DEFAULT,
    val approved: KidsApprovedSummary = KidsApprovedSummary(),
    /** false = v appce není dětský profil (Nastavení dospělého pak sekci nenabízí). */
    val hasKidsProfile: Boolean = false,
)

/**
 * AMPHORA F7 (DE1 + Z1) — obrazovka Pro rodiče v dětském profilu + sekce Nastavení › Zobrazení pro Děti
 * u dospělého: PIN dospělého, souhrn schváleného a zobrazení poličky ([ProfileConfig.kidsDisplay]) zapsané
 * do configu dětského profilu → server → stejná data vidí Rodičovská appka na webu.
 */
@HiltViewModel
class SlovoParentViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    init {
        // PIN nastavený na jiném zařízení platí i tady (vzor SlovoProfileViewModel).
        viewModelScope.launch { profileRepository.pullLoginPins() }
    }

    val uiState: StateFlow<SlovoParentUiState> = combine(
        profileRepository.observeAll(),
        profileRepository.activeProfile,
        profileRepository.activeConfig,
    ) { profiles, active, activeCfg ->
        val guard = profiles.firstOrNull { it.isAdmin && !it.loginPinHash.isNullOrBlank() }
        // Dětský profil = aktivní (Pro rodiče v telefonu dítěte), z dospělého = první dětský profil
        // (Nastavení › Děti a profily) — jeho místní kopie configu, úprava se ověří proti serveru.
        val cfg = if (active?.isAdmin == false) activeCfg
        else profiles.firstOrNull { !it.isAdmin }?.let { ProfileConfig.fromJson(it.configJson) }
        SlovoParentUiState(
            guardPinHash = guard?.loginPinHash,
            guardProfileName = guard?.name,
            display = cfg?.kidsDisplay ?: KidsDisplayPrefs.DEFAULT,
            approved = cfg?.let(::summarize) ?: KidsApprovedSummary(),
            hasKidsProfile = cfg != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlovoParentUiState())

    fun setDisplay(transform: (KidsDisplayPrefs) -> KidsDisplayPrefs) {
        viewModelScope.launch {
            val active = profileRepository.activeProfile.value
            val id = if (active?.isAdmin == false) active.id
            else profileRepository.observeAll().first().firstOrNull { !it.isAdmin }?.id
            id ?: return@launch
            profileRepository.updateConfigIfChanged(id) { cfg ->
                cfg.copy(kidsDisplay = transform(cfg.kidsDisplay ?: KidsDisplayPrefs.DEFAULT))
            }
        }
    }

    private fun summarize(cfg: ProfileConfig): KidsApprovedSummary {
        var shows = 0
        var series = 0
        var episodes = 0
        cfg.visibleForKidsSourceKeys.forEach { key ->
            when {
                "#ep:" in key -> episodes++
                "#series:" in key -> series++
                else -> shows++
            }
        }
        return KidsApprovedSummary(shows, series, episodes)
    }
}
