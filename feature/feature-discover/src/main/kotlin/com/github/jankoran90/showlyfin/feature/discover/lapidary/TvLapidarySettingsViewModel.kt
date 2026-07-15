package com.github.jankoran90.showlyfin.feature.discover.lapidary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.LapidaryPrefs
import com.github.jankoran90.showlyfin.core.domain.lapidary.LapidaryCountry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LAPIDARY (SHW-96) — VM bloku „Vzácné klenoty" v Nastavení (SDÍLENÝ TV i telefon → parita, jeden zdroj
 * pravdy). Čte/zapisuje [LapidaryPrefs] per profil přes [ProfileRepository] (activeConfig ⊕ updateConfig)
 * → volby se synchronizují TV↔telefon (vzor [com.github.jankoran90.showlyfin.feature.discover.curator.CuratorSettingsViewModel]).
 */
@HiltViewModel
class TvLapidarySettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _prefs = MutableStateFlow(LapidaryPrefs.DEFAULT)
    val prefs: StateFlow<LapidaryPrefs> = _prefs.asStateFlow()

    init {
        profileRepository.activeConfig
            .map { it.lapidary ?: LapidaryPrefs.DEFAULT }
            .distinctUntilChanged()
            .onEach { _prefs.value = it }
            .launchIn(viewModelScope)
    }

    private fun persist(transform: (LapidaryPrefs) -> LapidaryPrefs) {
        val id = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            profileRepository.updateConfig(id) { cfg ->
                cfg.copy(lapidary = transform(cfg.lapidary ?: LapidaryPrefs.DEFAULT))
            }
        }
    }

    /** Je země zapnutá? Prázdná množina = všechny (výchozí i forward-compat pro nově přidané země). */
    fun isEnabled(prefs: LapidaryPrefs, iso: String): Boolean =
        prefs.enabledCountries.isEmpty() || iso in prefs.enabledCountries

    fun setCountry(iso: String, enabled: Boolean) = persist {
        // Materializuj „vše" na explicitní množinu při prvním vypnutí, pak přidávej/odebírej.
        val cur = it.enabledCountries.ifEmpty { LapidaryCountry.DEFAULT_ENABLED }
        it.copy(enabledCountries = if (enabled) cur + iso else cur - iso)
    }

    fun setSort(sort: String) = persist { it.copy(sort = sort) }
}
