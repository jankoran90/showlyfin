package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.core.ui.dilyCz
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
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
    private val kidsSync: com.github.jankoran90.showlyfin.feature.listen.kids.KidsOfflineSync,
    private val abs: com.github.jankoran90.showlyfin.data.abs.AbsRepository,
    private val absEpisodes: com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }


    /**
     * Slug série STAŽENÉHO dílu: ruční kniha → uložená řada (ČT) → prefix z názvu. Veřejné pro offline
     * Poslech dětí (karta série je vidět, jen když z ní je něco stažené).
     */
    fun downloadSlug(sourceKey: String, episodeTitle: String, seriesTitle: String?): String? =
        (ruleStore.titleFor(sourceKey, episodeTitle) ?: seriesTitle ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(episodeTitle))
            ?.let(PodcastEpisodeSeriesGrouping::seriesSlug)

    /**
     * Slug série dílu ze zdroje — stejné pořadí jako obrazovka pořadu: ruční kniha → ČT řada → číslovaný
     * díl N/M (celý pořad) → prefix před dvojtečkou (Codex 2026-10-13: ČT série jinak nešly najít).
     */
    private fun slugOf(source: PodcastSource, ep: SourceEpisode): String? =
        SeriesResolver.seriesSlug(ruleStore, source.key, source.title, ep.title, ep.seriesTitle)

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
                src in sourceKeys && (seriesSlug == null || downloadSlug(src, d.title, d.seriesTitle) == seriesSlug)
            }
            .map { it.key }

    fun downloadedCount(sourceKeys: Set<String>, seriesSlug: String?): Int = downloadedKeys(sourceKeys, seriesSlug).size

    /** User 2026-10-13 — „Odstranit stažené": smaže stažené díly pořadu/série z telefonu (i offline). */
    fun deleteDownloaded(sourceKeys: Set<String>, seriesSlug: String?) {
        val keys = downloadedKeys(sourceKeys, seriesSlug)
        keys.forEach(offline::delete)
        _message.value = "Z telefonu odstraněno: ${dilyCz(keys.size)}."
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
                val req = kidsSync.offlineRequest(source, ep, title)
                val key = req.key
                offline.enqueue(req)
                kidsSync.adoptManual(listOf("o:$key"))
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
            _message.value = if (queue) "Stahuji ${dilyCz(episodes.size)} a řadím je do fronty." else "Stahuji znovu ${dilyCz(episodes.size)}."
        }
    }

    // ── Rodičovská správa R3 (2026-11-19): jednotlivě schválené díly (`typ:ref#ep:id|Název`) ──────────

    /** Stažené ABS epizody (karta dílu ABS podcastu ukazuje „v telefonu"). */
    val absEpisodeDownloads = absEpisodes.downloads

    private val _episodeCovers = MutableStateFlow<Map<String, String>>(emptyMap())
    val episodeCovers = _episodeCovers.asStateFlow()
    private val episodeCoverRequested = HashSet<String>()

    private fun coverKey(e: KidsSourceAccess.Episode) = "${e.sourceKey}#ep:${e.episodeId}"

    private fun ghostSource(sourceKey: String) = PodcastSource(
        id = "", type = sourceKey.substringBefore(':'), ref = sourceKey.substringAfter(':'), title = "",
    )

    fun isEpisodeDownloaded(e: KidsSourceAccess.Episode): Boolean =
        if (e.sourceKey.startsWith("abs:")) absEpisodes.localFile(e.episodeId) != null
        else offline.isDownloaded(KidsSourceAccess.resumeKeyFor(e.sourceKey, e.episodeId))

    /** Obal karty dílu: ABS = obal pořadu, jinak obrázek dílu z feedu (offline z paměti feedu / stažený). */
    fun requestEpisodeCover(e: KidsSourceAccess.Episode, source: PodcastSource?) {
        val key = coverKey(e)
        if (!episodeCoverRequested.add(key)) return
        if (e.sourceKey.startsWith("abs:")) {
            _episodeCovers.value = _episodeCovers.value + (key to abs.coverUrl(e.sourceKey.removePrefix("abs:")))
            return
        }
        offline.get(KidsSourceAccess.resumeKeyFor(e.sourceKey, e.episodeId))?.posterUrl?.let {
            _episodeCovers.value = _episodeCovers.value + (key to it)
            return
        }
        viewModelScope.launch {
            val image = episodesOf(source ?: ghostSource(e.sourceKey)).firstOrNull { it.id == e.episodeId }?.imageUrl
                ?: source?.thumbnail
            if (image != null) _episodeCovers.value = _episodeCovers.value + (key to image)
            else episodeCoverRequested.remove(key)
        }
    }

    /** Klepnutí na kartu dílu → rovnou přehrát (stažený z telefonu, jinak stream). */
    fun playEpisode(e: KidsSourceAccess.Episode, source: PodcastSource?) {
        if (e.sourceKey.startsWith("abs:")) {
            val itemId = e.sourceKey.removePrefix("abs:")
            connection.playQueued(
                QueuedEpisode(itemId = itemId, episodeId = e.episodeId, title = e.title.ifBlank { "Díl" }, coverUrl = abs.coverUrl(itemId)),
            )
            return
        }
        viewModelScope.launch {
            val src = source ?: ghostSource(e.sourceKey)
            val key = KidsSourceAccess.resumeKeyFor(e.sourceKey, e.episodeId)
            val local = offline.get(key)?.takeIf { offline.isDownloaded(key) }
            val ep = if (local == null) episodesOf(src).firstOrNull { it.id == e.episodeId && it.streamUrl.isNotBlank() } else null
            if (local == null && ep == null) {
                _message.value = "Díl se teď nepodařilo načíst (zkontroluj připojení)."
                return@launch
            }
            val show = src.title.ifBlank { local?.subtitle.orEmpty() }.ifBlank { null }
            connection.playDirectEpisode(
                QueuedEpisode(
                    itemId = src.ref,
                    episodeId = key,
                    title = ep?.title ?: local?.title ?: e.title,
                    coverUrl = ep?.imageUrl ?: _episodeCovers.value[coverKey(e)],
                    description = ep?.description ?: local?.description,
                    podcastTitle = show,
                    // Stažený díl hraje přehrávač z lokálního souboru podle klíče (URL se pak nepoužije).
                    direct = DirectAudio(url = ep?.streamUrl.orEmpty(), durationSec = ep?.durationSec ?: local?.durationSec ?: 0.0, author = show),
                ),
            )
        }
    }

    /** Dlouhý stisk na kartě dílu → stáhnout do telefonu (když samostahování neběží). */
    fun downloadEpisode(e: KidsSourceAccess.Episode, source: PodcastSource?) {
        if (isEpisodeDownloaded(e)) {
            _message.value = "Díl už je v telefonu."
            return
        }
        if (e.sourceKey.startsWith("abs:")) {
            val itemId = e.sourceKey.removePrefix("abs:")
            absEpisodes.downloadByIds(itemId, e.episodeId, e.title.ifBlank { "Díl" }, abs.coverUrl(itemId))
            kidsSync.adoptManual(listOf("a:${e.episodeId}"))
            _message.value = "Stahuji díl do telefonu."
            return
        }
        viewModelScope.launch {
            val src = source ?: ghostSource(e.sourceKey)
            val ep = episodesOf(src).firstOrNull { it.id == e.episodeId && it.streamUrl.isNotBlank() }
            if (ep == null) {
                _message.value = "Díl se teď nepodařilo načíst (zkontroluj připojení)."
                return@launch
            }
            val req = kidsSync.offlineRequest(src, ep, src.title.ifBlank { e.title })
            offline.enqueue(req)
            kidsSync.adoptManual(listOf("o:${req.key}"))
            _message.value = "Stahuji díl do telefonu."
        }
    }
}
