package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CINEMATHEQUE (SHW-90) — lehký VM pro blok Nastavení Filmotéky. Drží [FilmotekaSettingsStore] per profil
 * (přepíná na aktivní profil) a vystavuje zdroje + výchozí osu pro toggly/stepper. Bez datové zátěže
 * (na rozdíl od [TvFilmotekaViewModel]) — settings blok se otevírá i mimo sekci Filmotéka.
 */
@HiltViewModel
class TvFilmotekaSettingsViewModel @Inject constructor(
    private val store: FilmotekaSettingsStore,
    private val profileRepository: ProfileRepository,
    private val jellyfinAuth: JellyfinAuthService,
) : ViewModel() {

    val sources: StateFlow<Set<FilmotekaSource>> = store.sources
    val defaultAxis: StateFlow<FilmotekaAxis> = store.defaultAxis
    val allSort: StateFlow<FilmotekaAllSort> = store.allSort
    val enabledRegions: StateFlow<Set<CinematographyRegion>> = store.enabledRegions
    val hybridGenres: StateFlow<Boolean> = store.hybridGenres

    // ORCHARD (user 07-19) — per-library výběr JF knihoven Filmotéky na TV (parita s telefonem). TV má JF creds
    // zděděné z telefonního loginu přes backend config. Nabídka knihoven z JF; výběr = filmotekaJfLibraries.
    private val _jfLibraries = MutableStateFlow<List<JellyfinAuthService.JfLibrary>>(emptyList())
    val jfLibraries: StateFlow<List<JellyfinAuthService.JfLibrary>> = _jfLibraries.asStateFlow()

    /** Normalizovaná id vybraných JF knihoven Filmotéky (null=všechny → prázdná množina = „nic zaškrtnuto"). */
    val selectedFilmotekaLibs: StateFlow<Set<String>> = profileRepository.activeConfig
        .map { cfg -> cfg.filmotekaJfLibraries.orEmpty().map { it.replace("-", "").lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        profileRepository.activeProfile
            .onEach { store.switchProfile(it?.id) }
            .launchIn(viewModelScope)
    }

    fun setSource(source: FilmotekaSource, enabled: Boolean) = store.setSourceEnabled(source, enabled)
    fun setDefaultAxis(axis: FilmotekaAxis) = store.setDefaultAxis(axis)
    fun setAllSort(sort: FilmotekaAllSort) = store.setAllSort(sort)
    fun setRegion(region: CinematographyRegion, enabled: Boolean) = store.setRegionEnabled(region, enabled)
    fun setHybridGenres(enabled: Boolean) = store.setHybridGenresEnabled(enabled)

    /** Načte JF knihovny (pro výběr). Creds z aktivního profilu (zděděné na TV z telefonu přes backend). */
    fun loadJfLibraries() {
        val jf = profileRepository.activeConfig.value.credentials.jellyfin ?: return
        if (jf.url.isBlank() || jf.token.isBlank() || jf.userId.isBlank()) return
        viewModelScope.launch {
            _jfLibraries.value = jellyfinAuth.listLibraries(jf.url, jf.token, jf.userId)
        }
    }

    /** Zapni/vypni JF knihovnu pro Filmotéku (filmotekaJfLibraries aktivního profilu). Persist per profil. */
    fun toggleFilmotekaLibrary(libraryId: String) {
        val active = profileRepository.activeProfile.value ?: return
        val norm = libraryId.replace("-", "").lowercase()
        viewModelScope.launch {
            profileRepository.updateConfig(active.id) { cfg ->
                val current = cfg.filmotekaJfLibraries.orEmpty()
                val next = if (current.any { it.replace("-", "").lowercase() == norm }) {
                    current.filter { it.replace("-", "").lowercase() != norm }
                } else {
                    current + libraryId
                }
                cfg.copy(filmotekaJfLibraries = next)
            }
        }
    }
}
