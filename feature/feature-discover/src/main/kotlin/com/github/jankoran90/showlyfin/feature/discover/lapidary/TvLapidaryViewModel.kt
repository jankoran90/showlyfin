package com.github.jankoran90.showlyfin.feature.discover.lapidary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.lapidary.LapidaryCountry
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Jedna řada sekce Klenoty (země → tituly). Neutrální model — UI (ui-tv) ho mapuje na `TvRail`. */
data class LapidaryRail(
    val id: String,
    val title: String,
    val items: List<HomeRowItem>,
)

/** Stav sekce „Vzácné klenoty". */
data class LapidaryUiState(
    val rails: List<LapidaryRail> = emptyList(),
    val loading: Boolean = true,
)

/**
 * LAPIDARY (SHW-96) — VM sekce „Vzácné klenoty". Osy = ZEMĚ: pro každou zapnutou zemi jedna řada
 * (backend `GET /gems/catalog` přes [LapidaryLoader]), paralelně; prázdné země se vynechají. Reload na
 * změnu profilu (věkový gate + zapnuté země jsou per-profil). Vzor [com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel].
 */
@HiltViewModel
class TvLapidaryViewModel @Inject constructor(
    private val loader: LapidaryLoader,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LapidaryUiState())
    val state: StateFlow<LapidaryUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        profileRepository.activeProfile
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            val prefs = profileRepository.activeConfig.value.lapidary
            val countries = enabledCountries(prefs?.enabledCountries.orEmpty())
            val sort = prefs?.sort ?: "rank"
            val rails = coroutineScope {
                countries.map { c ->
                    async {
                        val items = loader.catalog(c.iso, sort)
                        if (items.isEmpty()) null
                        else LapidaryRail(
                            id = "lapidary_${c.iso}",
                            title = c.label,
                            items = items.map { it.toHomeRowItem(c.iso) },
                        )
                    }
                }.awaitAll().filterNotNull()
            }
            _state.value = LapidaryUiState(rails = rails, loading = false)
        }
    }

    /** Zapnuté země dle per-profil [com.github.jankoran90.showlyfin.core.domain.LapidaryPrefs]. Prázdné = všechny. */
    private fun enabledCountries(enabled: Set<String>): List<LapidaryCountry> =
        LapidaryCountry.entries.filter { enabled.isEmpty() || it.iso in enabled }

    private fun MediaItem.toHomeRowItem(countryIso: String) = HomeRowItem(
        key = "lapidary_${countryIso}_${tmdbId ?: imdbId ?: traktId}",
        title = displayTitle,
        year = year,
        posterUrl = posterUrl("w342"),
        landscapeUrl = backdropUrl("w780"),
        mediaItem = this,
    )
}
