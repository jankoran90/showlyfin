package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * NOMAD (SHW-60): obrazovka „Stažené" — offline obsah v telefonu. Tenký wrapper nad
 * [OfflineDownloadManager] (flows + akce mazání + info o místě).
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val manager: OfflineDownloadManager,
) : ViewModel() {
    val downloads = manager.downloads
    val states = manager.states

    fun titleFor(key: String) = manager.titleFor(key)
    fun cancel(key: String) = manager.cancel(key)
    fun delete(key: String) = manager.delete(key)
    fun deleteAll() = manager.deleteAll()

    fun usedBytes() = manager.usedBytes()
    fun freeBytes() = manager.freeBytes()
    fun isLowOnSpace() = manager.isLowOnSpace()
}
