package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * COUCH (SHW-88) — UI stav rodičovské kontroly (věkový strop obsahu) pro aktivní profil. Tenký obal nad
 * [ParentalControlsRepository]: čte efektivní strop + explicitní volbu, umí ji nastavit. Filtr běží
 * napříč objevovacími plochami (viz [com.github.jankoran90.showlyfin.core.domain.ContentAgeGate]).
 */
@HiltViewModel
class ParentalPrefsViewModel @Inject constructor(
    private val parental: ParentalControlsRepository,
) : ViewModel() {

    data class State(
        /** Explicitní volba stropu (roky; 0 = „Vypnuto/dle Jellyfinu"). */
        val explicitCap: Int = 0,
        /** Efektivní strop (nejpřísnější ze zdrojů; null = žádný). Jen pro info text. */
        val effectiveCap: Int? = null,
        val hideUnrated: Boolean = false,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        // Reaguj na přepnutí profilu (jiný strop / jiná volba).
        parental.profile
            .onEach { _state.value = read() }
            .launchIn(viewModelScope)
    }

    private fun read() = State(
        explicitCap = parental.explicitAgeCap(),
        effectiveCap = parental.profile.value.effectiveAgeCap,
        hideUnrated = parental.hideUnrated(),
    )

    fun setCap(years: Int) {
        parental.setContentAgeCap(years)
        _state.value = read()
    }

    fun setHideUnrated(enabled: Boolean) {
        parental.setHideUnrated(enabled)
        _state.value = read()
    }

    companion object {
        /** Nabízené hodnoty stropu (roky). 0 = vypnuto / řídí Jellyfin parental rating. */
        val AGE_CAP_OPTIONS = listOf(0, 7, 10, 12, 15)
    }
}
