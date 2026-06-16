package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * NOMAD (SHW-60): obrazovka „Stažené" — offline FILMY/EPIZODY v telefonu. Tenký wrapper nad
 * [OfflineDownloadManager] (flows + akce mazání + info o místě).
 *
 * LEVER (SHW-61) L3: stažené PODCASTY/YouTube (`TYPE_PODCAST`) sdílí stejný manager, ale patří do
 * Poslechu (hrají se v audio přehrávači) → tato filmová sekce je odfiltruje (seznam i „Zabráno"/
 * „Smazat vše"). Filtr seznamu řeší obrazovka, agregace tady přes typové varianty manageru.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val manager: OfflineDownloadManager,
) : ViewModel() {
    private val filmTypes = setOf(OfflineRequest.TYPE_MOVIE, OfflineRequest.TYPE_EPISODE)

    val downloads = manager.downloads
    val states = manager.states

    /** True = klíč patří audio podcastu (Poslech) → tato sekce ho neukazuje. */
    fun isPodcast(key: String) = manager.typeOf(key) == OfflineRequest.TYPE_PODCAST

    fun titleFor(key: String) = manager.titleFor(key)
    fun cancel(key: String) = manager.cancel(key)
    fun delete(key: String) = manager.delete(key)
    fun deleteAll() = manager.deleteAll(filmTypes)

    fun usedBytes() = manager.usedBytes(filmTypes)
    fun freeBytes() = manager.freeBytes()
    fun isLowOnSpace() = manager.isLowOnSpace()
}
