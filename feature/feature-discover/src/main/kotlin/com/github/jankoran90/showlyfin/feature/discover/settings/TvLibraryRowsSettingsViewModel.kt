package com.github.jankoran90.showlyfin.feature.discover.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CONVERGE (SHW-97) V1 — VM bloku „Řady knihovny" v TV Nastavení. Čte per-profil pořadí knihoven
 * ([ProfileConfig.libraryOrder]) a whitelist viditelnosti ([ProfileConfig.jellyfinLibraryWhitelist]) přes
 * [ProfileRepository] (activeConfig ⊕ updateConfig) → sync TV↔telefon zadarmo (vzor [com.github.jankoran90
 * .showlyfin.feature.discover.lapidary.TvLapidarySettingsViewModel]). Datová vrstva už existuje; tento VM ji
 * jen edituje z TV. Seznam dostupných knihoven dodává obrazovka (z `LibraryRowsViewModel`), sem chodí jako ids.
 */
@HiltViewModel
class TvLibraryRowsSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /** Aktivní config profilu (řazení + whitelist). Blok si z něj počítá pořadí i viditelnost. */
    val config: StateFlow<ProfileConfig> = profileRepository.activeConfig

    /**
     * Přepni viditelnost knihovny. Materializuje whitelist z aktuální sady [available] mínus skryté; když
     * jsou po změně viditelné VŠECHNY, whitelist se nuluje na `null` (= všechny, i budoucí knihovny) — mirror
     * telefonu (`ProfileAuthoring`). Prázdný whitelist = záměrně žádná viditelná.
     */
    fun setVisible(available: List<String>, id: String, visible: Boolean) = persist { cfg ->
        val visibleNow = (cfg.jellyfinLibraryWhitelist?.filter { it in available } ?: available).toMutableSet()
        if (visible) visibleNow.add(id) else visibleNow.remove(id)
        val allVisible = available.all { it in visibleNow }
        cfg.copy(jellyfinLibraryWhitelist = if (allVisible) null else available.filter { it in visibleNow })
    }

    /** Posuň knihovnu v pořadí o krok nahoru/dolů (přepíše celé [ProfileConfig.libraryOrder]). */
    fun move(available: List<String>, id: String, up: Boolean) = persist { cfg ->
        val ordered = cfg.orderedLibraryIds(available).toMutableList()
        val i = ordered.indexOf(id)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j < 0 || j >= ordered.size) return@persist cfg
        ordered[i] = ordered[j].also { ordered[j] = ordered[i] }
        cfg.copy(libraryOrder = ordered)
    }

    private fun persist(transform: (ProfileConfig) -> ProfileConfig) {
        val id = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch { profileRepository.updateConfig(id) { transform(it) } }
    }
}
