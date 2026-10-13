package com.github.jankoran90.showlyfin.feature.listen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.offline.OfflineState
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

/**
 * Slovo 2026-10-13 (user „sekce stahování v sidebaru, kde je vidět fronta, staženo a stahování a
 * rychlost a ETA") — přehled stahování dílů do telefonu (RSS / YouTube / ČT). Audioknihy mají vlastní
 * správce stažení a tady zatím nejsou.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val offline: OfflineDownloadManager,
    private val connection: AudiobookPlayerConnection,
    private val profiles: ProfileRepository,
    private val ruleStore: SeriesRuleStore,
) : ViewModel() {

    data class Item(val request: OfflineRequest, val state: OfflineState)

    data class UiState(
        val active: Item? = null,
        val queued: List<Item> = emptyList(),
        val failed: List<Item> = emptyList(),
        val downloaded: List<OfflineDownload> = emptyList(),
        val usedBytes: Long = 0L,
        val freeBytes: Long = 0L,
        /** Odhad do konce celé fronty (aktuální + čekající), null = zatím neznámá rychlost. */
        val queueEtaSec: Long? = null,
        /** Dětský profil: jen schválené pořady/série, bez mazání. */
        val isKids: Boolean = false,
    )

    val state: StateFlow<UiState> = combine(
        offline.pending, offline.states, offline.downloads, profiles.activeProfileWithConfig,
    ) { pending, states, done, (profile, config) ->
        // User 2026-10-13 („děti nemají Stahování v sidebaru") — dítě vidí jen díly schválených pořadů / sérií
        // (whitelist, neznámý zdroj = skrýt), stejné pravidlo jako jeho offline Poslech.
        val kidsKeys = if (profile?.isAdmin == false) config.visibleForKidsSourceKeys else null
        fun allowed(sourceKey: String?, title: String, series: String?) = kidsKeys == null || kidsMayShow(kidsKeys, sourceKey, title, series)
        // Pořad / série pohromadě a kapitoly popořadě („3/13" podle čísla, jinak podle data vydání).
        val podcastDone = done.filter { it.type == OfflineRequest.TYPE_PODCAST && allowed(it.sourceKey, it.title, it.seriesTitle) }
            .sortedWith(
                compareBy<OfflineDownload>({ it.subtitle.orEmpty() })
                    .thenBy { PodcastEpisodeSeriesGrouping.numberedPart(it.title)?.first ?: Int.MAX_VALUE }
                    .thenBy { it.publishedAt ?: Long.MAX_VALUE },
            )
        val items = pending.filter { it.type == OfflineRequest.TYPE_PODCAST && allowed(it.sourceKey, it.title, it.seriesTitle) }
            .map { Item(it, states[it.key] ?: OfflineState()) }
        val active = items.firstOrNull { it.state.status == OfflineStatus.DOWNLOADING }
        val queued = items.filter { it.state.status == OfflineStatus.QUEUED }
        UiState(
            active = active,
            queued = queued,
            failed = items.filter { it.state.status == OfflineStatus.FAILED },
            downloaded = podcastDone,
            usedBytes = podcastDone.sumOf { it.sizeBytes },
            freeBytes = offline.freeBytes(),
            queueEtaSec = queueEta(active, queued, podcastDone),
            isKids = kidsKeys != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun kidsMayShow(kidsKeys: Set<String>, sourceKey: String?, title: String, seriesTitle: String?): Boolean {
        if (sourceKey == null) return false
        if (KidsSourceAccess.isWholeSourceApproved(kidsKeys, sourceKey)) return true
        val series = ruleStore.titleFor(sourceKey, title) ?: seriesTitle ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(title) ?: return false
        return KidsSourceAccess.isSeriesApproved(kidsKeys, sourceKey, PodcastEpisodeSeriesGrouping.seriesSlug(series))
    }

    /**
     * Fronta má jen délky dílů, ne velikosti → velikost odhadni z bajtů na sekundu zvuku (aktuální
     * stahování, jinak průměr už stažených) a vyděl aktuální rychlostí.
     */
    private fun queueEta(active: Item?, queued: List<Item>, done: List<OfflineDownload>): Long? {
        val speed = active?.state?.bytesPerSec?.takeIf { it > 0 } ?: return null
        val bytesPerAudioSec = active.state.totalBytes.takeIf { it > 0 && active.request.durationSec > 0 }
            ?.let { it / active.request.durationSec }
            ?: done.filter { it.durationSec > 0 }.takeIf { it.isNotEmpty() }
                ?.let { list -> list.sumOf { it.sizeBytes } / list.sumOf { it.durationSec } }
            ?: return active.state.etaSec
        val queuedBytes = queued.sumOf { it.request.durationSec * bytesPerAudioSec }.toLong()
        return (active.state.etaSec ?: 0L) + queuedBytes / speed
    }

    fun cancel(key: String) = offline.cancel(key)
    fun cancelAll() = (state.value.queued + listOfNotNull(state.value.active)).forEach { offline.cancel(it.request.key) }
    fun retry(key: String) = offline.retry(key)
    fun delete(key: String) = offline.delete(key)

    /** Přehraj stažený díl (z telefonu, bez sítě) a zařaď ho do fronty. */
    fun play(dl: OfflineDownload) {
        val file = File(dl.videoPath).takeIf(File::exists) ?: return
        connection.playDirectEpisode(
            QueuedEpisode(
                // ref zdroje (ne celý klíč) → ťuk na obrázek v přehrávači otevře pořad; neznámý zdroj = offline detail.
                itemId = dl.sourceKey?.substringAfter(':') ?: "offline",
                episodeId = dl.key,
                title = dl.title,
                coverUrl = dl.posterUrl,
                description = dl.description,
                podcastTitle = dl.subtitle,
                direct = DirectAudio(url = Uri.fromFile(file).toString(), durationSec = dl.durationSec, author = dl.subtitle),
            ),
        )
    }
}
