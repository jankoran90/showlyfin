package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReferenceRecsUiState(
    /** Filmy, ze kterých se vybírá = historie sledování („na co ses díval"). */
    val choices: List<MediaItem> = emptyList(),
    /** Právě zvolené reference (1..N). */
    val picked: List<MediaItem> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val loadingChoices: Boolean = true,
    val loadingResults: Boolean = false,
    /** Doporučení doběhla aspoň jednou (odliší „ještě nic nechtěl" od „nic nenašel"). */
    val ran: Boolean = false,
)

/**
 * „Doporuč mi podle TOHOHLE" — doporučení vázaná na ručně vybrané filmy (user 2026-07-31: „důležité je
 * možnost volit referenci, na jaký film nebo filmy se doporučení váže; může se použít více filmů, ale
 * i jeden film, který sváže výběr do jednoho balíčku").
 *
 * Nabídka k výběru = historie sledování — reference má být to, co divák zná. Výsledek počítá [CuratorLoader.recommendFromReferences]: jeden titul jde na „co je
 * podobné X", víc titulů hledá jejich průnik.
 */
@HiltViewModel
class ReferenceRecsViewModel @Inject constructor(
    private val curator: CuratorLoader,
    private val traktRows: TraktRowLoader,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReferenceRecsUiState())
    val state: StateFlow<ReferenceRecsUiState> = _state.asStateFlow()

    private var lastProfileId: Long? = null

    init {
        profileRepository.activeProfile
            .onEach { p ->
                if (p?.id != lastProfileId) {
                    lastProfileId = p?.id
                    // Jiný profil = jiná historie i jiná doporučení → začni znovu.
                    _state.value = ReferenceRecsUiState()
                    loadChoices()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadChoices() {
        viewModelScope.launch {
            val history = runCatching { traktRows.history("all") }.getOrDefault(emptyList())
            _state.update { it.copy(choices = history, loadingChoices = false) }
        }
    }

    fun toggle(item: MediaItem) {
        _state.update { s ->
            val key = refKey(item)
            val already = s.picked.any { refKey(it) == key }
            s.copy(picked = if (already) s.picked.filterNot { refKey(it) == key } else s.picked + item)
        }
    }

    fun clearPicked() = _state.update { it.copy(picked = emptyList(), results = emptyList(), ran = false) }

    /** Spusť doporučení pro aktuální výběr. Mozek je LLM → na `pending` si počkáme a zopakujeme dotaz. */
    fun run() {
        val picks = _state.value.picked
        if (picks.isEmpty()) return
        _state.update { it.copy(loadingResults = true) }
        viewModelScope.launch {
            val items = runCatching { curator.recommendFromReferences(picks, RESULT_LIMIT, pollUntilReady = true) }
                .getOrDefault(emptyList())
            _state.update { it.copy(results = items, loadingResults = false, ran = true) }
        }
    }

    private fun refKey(m: MediaItem): String = m.tmdbId?.toString() ?: m.imdbId ?: m.displayTitle

    private companion object {
        const val RESULT_LIMIT = 30
    }
}
