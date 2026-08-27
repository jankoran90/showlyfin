package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.uploader.SpotlightRepository
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SPOTLIGHT (FLM-02) — stav sekce „Novinky": nové tituly od sledovaných tvůrců, seskupené po lidech.
 * Výpočet dělá server (týdenní dávka, pátek); tady se jen vyzvedne a přeloží do řad.
 */
@HiltViewModel
class FilmyNovinkyViewModel @Inject constructor(
    private val repo: SpotlightRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        /** Řada na jednoho tvůrce („Ti West — režiséra"), tituly seřazené od nejnovějšího. */
        val rails: List<FilmyRailData> = emptyList(),
        /** true = server neodpověděl (jiné než „nic nového"). */
        val offline: Boolean = false,
        val followedCount: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, offline = false)
            val items = repo.feed()
            if (items == null) {
                _state.value = UiState(loading = false, offline = true)
                return@launch
            }
            // Seskupení po tvůrci — u novinky je „od koho" hlavní informace, ne jen titul samotný.
            val rails = items
                .groupBy { it.personId() }
                .map { (_, titles) ->
                    val first = titles.first()
                    FilmyRailData(
                        id = "spotlight_${first.personName}_${first.personRole}",
                        title = listOf(first.personName, first.personRole)
                            .filter { it.isNotBlank() }
                            .joinToString(" — "),
                        items = titles.map { it.toRowItem() },
                    )
                }
            _state.value = UiState(loading = false, rails = rails, followedCount = rails.size)
        }
    }

    private fun SpotlightRepository.NewTitle.personId(): String = "$personName|$personRole"

    private fun SpotlightRepository.NewTitle.toRowItem() = HomeRowItem(
        key = "spotlight_${if (isShow) "tv" else "movie"}_$tmdbId",
        title = title,
        year = year,
        posterUrl = posterUrl,
        mediaItem = MediaItem(
            traktId = 0L,
            tmdbId = tmdbId,
            imdbId = null,
            title = title,
            year = year,
            overview = null,
            rating = rating,
            genres = null,
            type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
        ),
    )
}
