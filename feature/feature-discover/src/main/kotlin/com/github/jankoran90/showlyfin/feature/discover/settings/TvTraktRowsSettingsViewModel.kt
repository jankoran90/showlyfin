package com.github.jankoran90.showlyfin.feature.discover.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** CONVERGE V1 — jedna volitelná Trakt řada v Nastavení (id + název k zobrazení). */
data class TraktRowOption(val id: String, val title: String)

/**
 * CONVERGE (SHW-97) V1 — VM bloku „Řady Traktu" v TV Nastavení. Na rozdíl od knihoven neměla sekce Trakt
 * žádný store — přidána pole [ProfileConfig.traktRowOrder] + [ProfileConfig.hiddenTraktRows] (sync TV↔telefon),
 * aplikuje je [com.github.jankoran90.showlyfin.feature.discover.trakt.TvTraktViewModel]. Kandidáty řad staví
 * bez načítání položek: pevné (Watchlist/Zhlédnuto/Doporučeno) + userovy seznamy z [TraktRowLoader.myLists]
 * (id `list_<traktId>` shodné s `TvTraktViewModel`). Nepřihlášený → jen pevné řady.
 */
@HiltViewModel
class TvTraktRowsSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val loader: TraktRowLoader,
) : ViewModel() {

    val config: StateFlow<ProfileConfig> = profileRepository.activeConfig

    private val _candidates = MutableStateFlow(FIXED)
    val candidates: StateFlow<List<TraktRowOption>> = _candidates.asStateFlow()

    init {
        viewModelScope.launch {
            val lists = runCatching { loader.myLists() }.getOrDefault(emptyList())
            _candidates.value = FIXED + lists.map { TraktRowOption("list_${it.ids.trakt}", it.name) }
        }
    }

    fun setVisible(id: String, visible: Boolean) = persist { cfg ->
        cfg.copy(hiddenTraktRows = if (visible) cfg.hiddenTraktRows - id else cfg.hiddenTraktRows + id)
    }

    fun move(available: List<String>, id: String, up: Boolean) = persist { cfg ->
        val ordered = cfg.orderedTraktRows(available).toMutableList()
        val i = ordered.indexOf(id)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j < 0 || j >= ordered.size) return@persist cfg
        ordered[i] = ordered[j].also { ordered[j] = ordered[i] }
        cfg.copy(traktRowOrder = ordered)
    }

    private fun persist(transform: (ProfileConfig) -> ProfileConfig) {
        val id = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch { profileRepository.updateConfig(id) { transform(it) } }
    }

    companion object {
        private val FIXED = listOf(
            TraktRowOption("watchlist", "Watchlist"),
            TraktRowOption("history", "Zhlédnuto"),
            TraktRowOption("recommended", "Doporučeno"),
        )
    }
}
