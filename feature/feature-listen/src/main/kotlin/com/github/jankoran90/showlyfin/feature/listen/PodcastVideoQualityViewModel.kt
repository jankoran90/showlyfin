package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named

/**
 * CLARITY (SHW-75): volba kvality STREAMU videa podcastů (360p / 720p / Nejlepší) + (2026-08-22)
 * počet epizod tažených z YouTube kanálu. Čte/píše do `traktPreferences` přes [PodcastVideoQuality]/
 * [YoutubeFeedPrefs] (jeden zdroj pravdy s přehrávacími/kanálovými VM).
 */
@HiltViewModel
class PodcastVideoQualityViewModel @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _stream = MutableStateFlow(PodcastVideoQuality.stream(prefs))
    val stream: StateFlow<String> = _stream.asStateFlow()

    fun setStream(value: String) {
        PodcastVideoQuality.setStream(prefs, value)
        _stream.value = PodcastVideoQuality.stream(prefs)
    }

    private val _feedLimit = MutableStateFlow(YoutubeFeedPrefs.limit(prefs))
    val feedLimit: StateFlow<Int> = _feedLimit.asStateFlow()

    fun setFeedLimit(value: Int) {
        YoutubeFeedPrefs.setLimit(prefs, value)
        _feedLimit.value = YoutubeFeedPrefs.limit(prefs)
    }
}
