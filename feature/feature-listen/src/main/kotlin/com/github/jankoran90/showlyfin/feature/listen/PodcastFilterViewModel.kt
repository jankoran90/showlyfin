package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * AGORA-TABS: stav filtru sekce Podcasty (časový rozsah Timeline + typ zdroje). Persistuje do
 * [AbsPreferences] (sdíleno s blokem Nastavení „Objevování podcastů") a vystavuje reaktivní stav pro
 * filtr bottom sheet i pro výpočet počtu aktivních filtrů (badge u ikony filtru).
 */
@HiltViewModel
class PodcastFilterViewModel @Inject constructor(
    private val prefs: AbsPreferences,
) : ViewModel() {

    data class UiState(
        val timelineRangeDays: Int = 90,
        val sourceType: String = "all",   // all|rss|youtube
        val minEpisodes: Int = 0,
        /** „Jen stažené" — v Timeline ukáže pouze offline (stažené) epizody. */
        val onlyDownloaded: Boolean = false,
    )

    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()

    private fun load() = UiState(
        timelineRangeDays = prefs.podcastTimelineRangeDays,
        sourceType = prefs.podcastSourceTypeFilter,
        minEpisodes = prefs.discoveryMinEpisodes,
        onlyDownloaded = prefs.podcastOnlyDownloaded,
    )

    /** Znovu načte z prefs (např. po návratu z Nastavení). */
    fun reload() = _state.update { load() }

    fun setTimelineRange(days: Int) {
        prefs.podcastTimelineRangeDays = days
        _state.update { it.copy(timelineRangeDays = prefs.podcastTimelineRangeDays) }
    }

    fun setSourceType(type: String) {
        prefs.podcastSourceTypeFilter = type
        _state.update { it.copy(sourceType = type) }
    }

    fun setMinEpisodes(value: Int) {
        prefs.discoveryMinEpisodes = value
        _state.update { it.copy(minEpisodes = prefs.discoveryMinEpisodes) }
    }

    fun setOnlyDownloaded(value: Boolean) {
        prefs.podcastOnlyDownloaded = value
        _state.update { it.copy(onlyDownloaded = value) }
    }

    /** Počet aktivních filtrů (mimo výchozí) → badge u ikony filtru. [excludedCategories] dodá Objev VM. */
    fun activeCount(excludedCategories: Int): Int {
        val s = _state.value
        var n = excludedCategories
        if (s.timelineRangeDays != 90) n++
        if (s.sourceType != "all") n++
        if (s.minEpisodes > 0) n++
        if (s.onlyDownloaded) n++
        return n
    }
}
