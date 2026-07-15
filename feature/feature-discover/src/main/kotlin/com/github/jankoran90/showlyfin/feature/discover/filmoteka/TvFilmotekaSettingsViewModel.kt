package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) : ViewModel() {

    val sources: StateFlow<Set<FilmotekaSource>> = store.sources
    val defaultAxis: StateFlow<FilmotekaAxis> = store.defaultAxis
    val allSort: StateFlow<FilmotekaAllSort> = store.allSort
    val enabledRegions: StateFlow<Set<CinematographyRegion>> = store.enabledRegions

    init {
        profileRepository.activeProfile
            .onEach { store.switchProfile(it?.id) }
            .launchIn(viewModelScope)
    }

    fun setSource(source: FilmotekaSource, enabled: Boolean) = store.setSourceEnabled(source, enabled)
    fun setDefaultAxis(axis: FilmotekaAxis) = store.setDefaultAxis(axis)
    fun setAllSort(sort: FilmotekaAllSort) = store.setAllSort(sort)
    fun setRegion(region: CinematographyRegion, enabled: Boolean) = store.setRegionEnabled(region, enabled)
}
