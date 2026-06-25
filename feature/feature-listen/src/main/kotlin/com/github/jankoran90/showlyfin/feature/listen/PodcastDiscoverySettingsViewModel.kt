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
        /** AGORA-TABS: výchozí záložka sekce Podcasty (timeline|following|discover). */
        val defaultTab: String = "timeline",
        /** AGORA-TABS: výchozí časový rozsah Timeline ve dnech (7|30|90). */
        val timelineRangeDays: Int = 90,
        /** AGORA-TABS: výchozí typ zdroje filtru (all|rss|youtube). */
        val sourceType: String = "all",
        /** AGORA Timeline: zobrazit popis epizody „o čem to je". */
        val timelineShowDescription: Boolean = true,
        /** AGORA Timeline: počet řádků popisu ve sbaleném stavu (3|4|5). */
        val timelineDescriptionLines: Int = 3,
        /** AGORA Timeline: zobrazit datum vydání epizody. */
        val timelineShowDate: Boolean = true,
        /** AGORA Timeline: v Timeline ukázat jen STAŽENÉ (offline) epizody. */
        val onlyDownloaded: Boolean = false,
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
        defaultTab = prefs.podcastDefaultTab,
        timelineRangeDays = prefs.podcastTimelineRangeDays,
        sourceType = prefs.podcastSourceTypeFilter,
        timelineShowDescription = prefs.podcastTimelineShowDescription,
        timelineDescriptionLines = prefs.podcastTimelineDescriptionLines,
        timelineShowDate = prefs.podcastTimelineShowDate,
        onlyDownloaded = prefs.podcastOnlyDownloaded,
    )

    fun setOnlyDownloaded(value: Boolean) {
        prefs.podcastOnlyDownloaded = value
        _state.update { it.copy(onlyDownloaded = value) }
    }

    fun setTimelineShowDescription(value: Boolean) {
        prefs.podcastTimelineShowDescription = value
        _state.update { it.copy(timelineShowDescription = value) }
    }

    fun setTimelineDescriptionLines(value: Int) {
        prefs.podcastTimelineDescriptionLines = value
        _state.update { it.copy(timelineDescriptionLines = prefs.podcastTimelineDescriptionLines) }
    }

    fun setTimelineShowDate(value: Boolean) {
        prefs.podcastTimelineShowDate = value
        _state.update { it.copy(timelineShowDate = value) }
    }

    fun setDefaultTab(value: String) {
        prefs.podcastDefaultTab = value
        _state.update { it.copy(defaultTab = value) }
    }

    fun setTimelineRange(days: Int) {
        prefs.podcastTimelineRangeDays = days
        _state.update { it.copy(timelineRangeDays = prefs.podcastTimelineRangeDays) }
    }

    fun setSourceType(value: String) {
        prefs.podcastSourceTypeFilter = value
        _state.update { it.copy(sourceType = value) }
    }

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
