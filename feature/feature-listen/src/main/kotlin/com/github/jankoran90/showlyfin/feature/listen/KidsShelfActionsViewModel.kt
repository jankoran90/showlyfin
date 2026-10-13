package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import com.github.jankoran90.showlyfin.feature.listen.player.enqueue
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import android.content.SharedPreferences

/**
 * Poslech dětí — dlouhý stisk na dlaždici pořadu / série (user 2026-10-12: „nabídka stáhnout —
 * zařadí do fronty a stáhne všechny díly"). Díly v pořadí od nejstaršího (série se poslouchá
 * od 1. dílu), jen audio. Hotovo = hláška v [message].
 */
@HiltViewModel
class KidsShelfActionsViewModel @Inject constructor(
    private val sourcesRepo: PodcastSourcesRepository,
    private val offline: OfflineDownloadManager,
    private val connection: AudiobookPlayerConnection,
    private val ruleStore: SeriesRuleStore,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    private fun ctvFileUrl(idec: String) = uploaderDs.ctvAudioFileUrl(
        prefs.getString("uploader_base_url", "") ?: "", prefs.getString("uploader_session_cookie", "") ?: "", idec,
    )

    /** Slug série dílu: ruční kniha (pravidlo) má přednost, jinak série z názvu. */
    private fun slugOf(sourceKey: String, episodeTitle: String): String? =
        (ruleStore.titleFor(sourceKey, episodeTitle) ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(episodeTitle))
            ?.let(PodcastEpisodeSeriesGrouping::seriesSlug)

    /**
     * Slug série dílu ze zdroje — stejné pořadí jako obrazovka pořadu: ruční kniha → ČT řada → číslovaný
     * díl N/M (celý pořad) → prefix před dvojtečkou (Codex 2026-10-13: ČT série jinak nešly najít).
     */
    private fun slugOf(source: PodcastSource, ep: SourceEpisode): String? =
        (ruleStore.titleFor(source.key, ep.title)
            ?: ep.seriesTitle
            ?: PodcastEpisodeSeriesGrouping.numberedPart(ep.title)?.let { source.title }
            ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(ep.title))
            ?.let(PodcastEpisodeSeriesGrouping::seriesSlug)

    /** Jedno načtení feedu na zdroj (víc sérií téhož pořadu = jeden dotaz). Selhání se nepamatuje. */
    private val feedCache = HashMap<String, Deferred<List<SourceEpisode>>>()

    private suspend fun episodesOf(source: PodcastSource): List<SourceEpisode> {
        val d = feedCache.getOrPut(source.key) {
            viewModelScope.async { sourcesRepo.loadEpisodes(source, limit = if (source.type == "rss") 1000 else 500) }
        }
        val list = runCatching { d.await() }.getOrDefault(emptyList())
        if (list.isEmpty()) feedCache.remove(source.key)
        return list
    }

    /**
     * User 2026-10-13 („karta série ukazuje obrázek celého podcastu, v knize je obrázek série") — obálka
     * karty série = obrázek jejích dílů (nejstarší díl s obrázkem, stejně jako obálka knihy). Klíč `sourceKey#slug`, jen v paměti;
     * offline díly dodá offline paměť feedu.
     */
    private val _seriesCovers = MutableStateFlow<Map<String, String>>(emptyMap())
    val seriesCovers = _seriesCovers.asStateFlow()
    private val coverRequested = HashSet<String>()

    fun requestSeriesCover(source: PodcastSource, seriesSlug: String) {
        val key = "${source.key}#$seriesSlug"
        if (!coverRequested.add(key)) return
        viewModelScope.launch {
            val image = episodesOf(source)
                .lastOrNull { !it.imageUrl.isNullOrBlank() && slugOf(source, it) == seriesSlug } // feed od nejnovějšího → nejstarší díl = obálka knihy
                ?.imageUrl
            if (image != null) _seriesCovers.value = _seriesCovers.value + (key to image)
            else coverRequested.remove(key) // zkusí se znovu (třeba až bude síť)
        }
    }

    /** Stažené díly (živě) — menu pořadu z nich počítá „V telefonu: N dílů". */
    val downloads = offline.downloads

    /**
     * Klíče stažených dílů pořadu: [sourceKeys] = všichni členové (sloučený pořad RSS + YouTube),
     * [seriesSlug] != null = jen díly téhle série.
     */
    private fun downloadedKeys(sourceKeys: Set<String>, seriesSlug: String?): List<String> =
        offline.downloads.value
            .filter { d ->
                val src = d.sourceKey ?: return@filter false
                src in sourceKeys && (seriesSlug == null || slugOf(src, d.title) == seriesSlug)
            }
            .map { it.key }

    fun downloadedCount(sourceKeys: Set<String>, seriesSlug: String?): Int = downloadedKeys(sourceKeys, seriesSlug).size

    /** User 2026-10-13 — „Odstranit stažené": smaže stažené díly pořadu/série z telefonu (i offline). */
    fun deleteDownloaded(sourceKeys: Set<String>, seriesSlug: String?) {
        val keys = downloadedKeys(sourceKeys, seriesSlug)
        keys.forEach(offline::delete)
        _message.value = "Odstraněno ${keys.size} dílů z telefonu."
    }

    /** „Stáhnout znovu" (po úpravě dílů na serveru): smaž stažené a stáhni nanovo, fronta přehrávání beze změny. */
    fun redownload(source: PodcastSource, sourceKeys: Set<String>, seriesSlug: String?, title: String) {
        downloadedKeys(sourceKeys, seriesSlug).forEach(offline::delete)
        downloadAndQueue(source, seriesSlug, title, queue = false)
    }

    /** [seriesSlug] != null = jen díly téhle série (dítěti schválená jen série). */
    fun downloadAndQueue(source: PodcastSource, seriesSlug: String?, title: String, queue: Boolean = true) {
        viewModelScope.launch {
            val episodes = episodesOf(source)
                .filter { ep -> seriesSlug == null || slugOf(source, ep) == seriesSlug }
                .filter { it.streamUrl.isNotBlank() }
                .reversed() // feed = nejnovější první → fronta od 1. dílu
            if (episodes.isEmpty()) {
                _message.value = "Žádné díly ke stažení (zkontroluj připojení)."
                return@launch
            }
            episodes.forEach { ep ->
                val key = ep.resumeKey ?: "${source.type}:${ep.id}"
                offline.enqueue(
                    OfflineRequest(
                        key = key,
                        title = ep.title,
                        subtitle = title,
                        type = OfflineRequest.TYPE_PODCAST,
                        sourceLabel = "Podcast",
                        // ČT díl je DASH manifest → stáhnout jde jen serverem složený m4a (2026-10-13).
                        videoUrl = if (source.type == "ctv") ctvFileUrl(ep.id) else ep.streamUrl,
                        posterUrl = ep.imageUrl,
                        durationSec = ep.durationSec,
                        description = ep.description,
                        publishedAt = parseEpisodeDateMs(ep.date),
                        sourceKey = source.key,
                    ),
                )
                if (queue) connection.enqueue(
                    QueuedEpisode(
                        itemId = source.ref,
                        episodeId = key,
                        title = ep.title,
                        coverUrl = ep.imageUrl,
                        description = ep.description,
                        podcastTitle = title,
                        direct = DirectAudio(url = ep.streamUrl, durationSec = ep.durationSec, author = title),
                    ),
                    atFront = false,
                )
            }
            _message.value = if (queue) "Stahuji ${episodes.size} dílů a řadím je do fronty." else "Stahuji znovu ${episodes.size} dílů."
        }
    }
}
