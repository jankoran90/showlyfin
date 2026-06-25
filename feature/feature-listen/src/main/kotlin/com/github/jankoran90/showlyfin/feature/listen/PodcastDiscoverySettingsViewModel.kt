package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AGORA F4: konfigurace objevovací obrazovky podcastů pro Nastavení → Poslech → „Objevování podcastů".
 * Hráčka-friendly: výchozí země/režim, trvale skryté kategorie, min. epizod, počet karet na stránku,
 * přepínače popisu/počtu epizod. Persistuje do [AbsPreferences] (klíče `podcast_discovery_*`);
 * objevovací VM tyto defaulty čte při init.
 */
@HiltViewModel
class PodcastDiscoverySettingsViewModel @Inject constructor(
    private val prefs: AbsPreferences,
    private val repo: PodcastSourcesRepository,
) : ViewModel() {

    data class UiState(
        val country: String = "cz",
        val mode: String = "popular",
        val hiddenCategories: Set<String> = emptySet(),
        val minEpisodes: Int = 0,
        val pageSize: Int = 30,
        val showSummary: Boolean = true,
        val showEpisodeCount: Boolean = true,
        /** CZ kategorie pro multi-select „Trvale skryté kategorie" (načteno ze serveru). */
        val czCategories: List<SourceCategory> = emptyList(),
    )

    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()

    init {
        // Skryté kategorie nabízíme jen pro ČR (CZ kategorie ≠ Apple žánr).
        viewModelScope.launch {
            val cats = runCatching { repo.categories("cz") }.getOrDefault(emptyList())
            _state.update { it.copy(czCategories = cats) }
        }
    }

    private fun load() = UiState(
        country = prefs.discoveryCountry,
        mode = prefs.discoveryMode,
        hiddenCategories = prefs.discoveryHiddenCategories,
        minEpisodes = prefs.discoveryMinEpisodes,
        pageSize = prefs.discoveryPageSize,
        showSummary = prefs.discoveryShowSummary,
        showEpisodeCount = prefs.discoveryShowEpisodeCount,
    )

    fun setCountry(code: String) {
        prefs.discoveryCountry = code
        _state.update { it.copy(country = code) }
    }

    fun setMode(apiValue: String) {
        prefs.discoveryMode = apiValue
        _state.update { it.copy(mode = apiValue) }
    }

    fun toggleHiddenCategory(id: Int) {
        val cur = _state.value.hiddenCategories
        val next = if (id.toString() in cur) cur - id.toString() else cur + id.toString()
        prefs.discoveryHiddenCategories = next
        _state.update { it.copy(hiddenCategories = next) }
    }

    fun setMinEpisodes(value: Int) {
        prefs.discoveryMinEpisodes = value
        _state.update { it.copy(minEpisodes = prefs.discoveryMinEpisodes) }
    }

    fun setPageSize(value: Int) {
        prefs.discoveryPageSize = value
        _state.update { it.copy(pageSize = prefs.discoveryPageSize) }
    }

    fun setShowSummary(value: Boolean) {
        prefs.discoveryShowSummary = value
        _state.update { it.copy(showSummary = value) }
    }

    fun setShowEpisodeCount(value: Boolean) {
        prefs.discoveryShowEpisodeCount = value
        _state.update { it.copy(showEpisodeCount = value) }
    }
}
