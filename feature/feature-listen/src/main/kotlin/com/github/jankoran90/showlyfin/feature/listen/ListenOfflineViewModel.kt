package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * LEVER (SHW-61) L5: správa stažených podcastů (offline „na chatu bez wifi") v Nastavení → Poslech.
 * Pracuje JEN s typem [OfflineRequest.TYPE_PODCAST] — filmy (stejný manager) se nedotknou.
 */
@HiltViewModel
class ListenOfflineViewModel @Inject constructor(
    private val offline: OfflineDownloadManager,
    private val absPrefs: com.github.jankoran90.showlyfin.data.abs.AbsPreferences,
) : ViewModel() {

    private val podcastTypes = setOf(OfflineRequest.TYPE_PODCAST)

    /** RESONANCE (SHW-81) A: řazení epizod v offline detailu — nejnovější nahoře (default) vs nejstarší.
     * Parita Nastavení k přepínači v liště offline detailu (dřív šlo měnit jen tam). */
    var offlinePodcastNewestFirst: Boolean
        get() = absPrefs.offlinePodcastNewestFirst
        set(value) { absPrefs.offlinePodcastNewestFirst = value }

    val downloads: StateFlow<List<OfflineDownload>> = offline.downloads
        .map { list -> list.filter { it.type == OfflineRequest.TYPE_PODCAST } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            offline.downloads.value.filter { it.type == OfflineRequest.TYPE_PODCAST },
        )

    fun usedBytes(): Long = offline.usedBytes(podcastTypes)
    fun freeBytes(): Long = offline.freeBytes()

    /** Smaže všechny stažené podcasty (ne filmy). */
    fun deleteAll() = offline.deleteAll(podcastTypes)
}
