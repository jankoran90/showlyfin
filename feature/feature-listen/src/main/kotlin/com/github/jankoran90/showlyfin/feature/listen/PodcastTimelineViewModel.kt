package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * AGORA-TABS (SHW): Timeline sekce Podcastů — chronologický feed NOVÝCH epizod agregovaný ze VŠECH
 * sledovaných vlastních zdrojů (RSS + YouTube + NaVýbornou). Reuse datové vrstvy
 * [PodcastSourcesRepository.loadEpisodes] (jednotné [SourceEpisode] s přímou stream URL), merge +
 * řazení sestupně dle data + bucketování po čase (Dnes / Tento týden / Minulý týden / po týdnech a
 * měsících až 3 měsíce zpět). Přehrání přes sdílený poslechový přehrávač ([AudiobookPlayerConnection]),
 * stejnou cestou jako RSS/YT obrazovky (pozice/resume sdílené přes `rss:` / `yt:` klíče).
 */
@HiltViewModel
class PodcastTimelineViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val connection: AudiobookPlayerConnection,
    private val offline: OfflineDownloadManager,
    private val linkStore: com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore,
    private val profileRepository: com.github.jankoran90.showlyfin.core.data.ProfileRepository,
    private val prefs: AbsPreferences,
) : ViewModel() {

    /** Jedna epizoda v timeline (sjednocená napříč zdroji) + odkaz na původní zdroj kvůli typu/obálce. */
    data class TimelineItem(
        val episode: SourceEpisode,
        val sourceTitle: String,
        val sourceType: String,        // "youtube" | "rss"
        val sourceRef: String,         // youtube: channel_id/@handle ; rss: feed_url (→ navigace na obsah zdroje)
        val timestampMs: Long,
    ) {
        /** Stabilní klíč epizody (shoda s RSS/YT `episodeKey` → sdílené resume i offline index). */
        val key: String get() = episode.resumeKey ?: episode.id
    }

    /** Sekce timeline = časový bucket s hlavičkou + epizody v něm. */
    data class Bucket(val label: String, val items: List<TimelineItem>)

    /** Zobrazovací volby Timeline z Nastavení (parita „hráčka" — vše konfigurovatelné). */
    data class DisplayPrefs(
        val showDescription: Boolean = true,
        val descriptionLines: Int = 3,
        val showDate: Boolean = true,
    )

    data class UiState(
        val loading: Boolean = false,
        val buckets: List<Bucket> = emptyList(),
        /** Žádné zdroje vůbec → prázdný stav s odkazem na Objev. */
        val noSources: Boolean = false,
        /** Zdroje jsou, ale v rozsahu žádné nové epizody. */
        val empty: Boolean = false,
        val error: String? = null,
        /** Zobrazovací volby (popis/datum/řádky) — čteny z [AbsPreferences] při každém načtení. */
        val display: DisplayPrefs = DisplayPrefs(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Stav přehrávače → zvýraznění právě hrané epizody v řádku. */
    val playerState = connection.state

    /** Stav offline stahování epizod (badge / akce Stáhnout). Klíč = [TimelineItem.key]. */
    val offlineStates = offline.states

    /** Aktuálně promítnuté zdroje (zrcadlo repo.sources) — kvůli rozhodnutí „noSources". */
    private var sources: List<PodcastSource> = emptyList()
    private var loadJob: Job? = null

    init {
        // Reaktivně sleduj sdílený seznam zdrojů — přidání/odebrání → přepočti timeline.
        repo.sources
            .onEach { srcs ->
                val changed = srcs.map { it.id } != sources.map { it.id }
                sources = srcs
                if (changed) load()
            }
            .launchIn(viewModelScope)
        // WEFT (SHW-75/W5): přefiltruj časovou osu při změně per-profil skrytí (nebo přepnutí profilu).
        profileRepository.activeConfig
            .map { it.hiddenTimelineSourceKeys }
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
    }

    /** Veřejné: znovu načti timeline (pull-to-refresh / vstup do tabu). */
    fun refresh() = load()

    /**
     * Agreguje epizody všech zdrojů paralelně, parsuje datum, odfiltruje starší než nastavený rozsah,
     * seřadí sestupně a rozdělí do časových bucketů. Epizody bez data (datum se NEPODAŘILO naparsovat)
     * NESMÍ spadnout do „Dnes" — dostanou sentinel ([NO_DATE_TS]) a jdou do samostatného bucketu
     * „Bez data" na KONEC timeline (cutoff rozsahu se na ně neaplikuje, ať nezmizí).
     */
    private fun load() {
        loadJob?.cancel()
        val display = readDisplayPrefs()
        val srcs = sources
        if (srcs.isEmpty()) {
            _state.update { UiState(noSources = true, display = display) }
            return
        }
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, noSources = false, display = display) }
            val rangeMs = prefs.podcastTimelineRangeDays.toLong() * DAY_MS
            val cutoff = System.currentTimeMillis() - rangeMs
            val typeFilter = prefs.podcastSourceTypeFilter   // all|rss|youtube
            val onlyDownloaded = prefs.podcastOnlyDownloaded

            val collected = runCatching {
                withContext(Dispatchers.IO) {
                    srcs
                        .filter { typeFilter == "all" || it.type == typeFilter }
                        .map { src ->
                            async {
                                repo.loadEpisodes(src, limit = EPISODES_PER_SOURCE).mapNotNull { ep ->
                                    val item = TimelineItem(
                                        episode = ep,
                                        sourceTitle = src.title,
                                        sourceType = src.type,
                                        sourceRef = src.ref,
                                        // Bez data → sentinel: NIKDY do „Dnes", vždy do bucketu „Bez data".
                                        timestampMs = parseEpisodeDate(ep.date) ?: NO_DATE_TS,
                                    )
                                    when {
                                        // „Jen stažené" (filtr) → ukaž jen offline epizody.
                                        onlyDownloaded && !offline.isDownloaded(item.key) -> null
                                        // Cutoff rozsahu platí jen pro DATOVANÉ; sentinel ho ignoruje.
                                        item.timestampMs != NO_DATE_TS && item.timestampMs < cutoff -> null
                                        else -> item
                                    }
                                }
                            }
                        }
                        .awaitAll()
                        .flatten()
                }
            }.getOrElse { e ->
                // WEFT (SHW-75/W4): zrušení jobu (rychlý re-load: nový zdroj/refresh přeruší běžící
                // agregaci) NESMÍ spadnout do chybového stavu — jinak na cold startu krátce blikne
                // „Nepodařilo se načíst" i když nový load právě běží. CancellationException re-throw.
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "[AGORA-TABS] agregace timeline selhala")
                _state.update { it.copy(loading = false, error = "Nepodařilo se načíst nové epizody.") }
                return@launch
            }

            // TWINE (SHW-74 / plán F7): propojené pořady (audio+video) → epizoda v Timeline jen 1×.
            // U slinkovaných zdrojů spáruj audio↔video; spárované VIDEO duplikáty zahoď (audio nese
            // správnější datum + jde rovnou přehrát). Nespárované audio i video zůstanou.
            val deduped = dedupeLinked(collected)

            // WEFT (SHW-75/W5): odfiltruj pořady skryté na časové ose pro tento profil (klíč `type:ref`).
            val hidden = profileRepository.activeConfig.value.hiddenTimelineSourceKeys
            val visible = if (hidden.isEmpty()) deduped
                else deduped.filter { "${it.sourceType}:${it.sourceRef}" !in hidden }

            // Sestupně dle data; sentinel (Long.MIN_VALUE) přirozeně skončí úplně dole → bucket na konci.
            val sorted = visible.sortedByDescending { it.timestampMs }
            val buckets = bucketize(sorted)
            _state.update {
                it.copy(
                    loading = false,
                    buckets = buckets,
                    empty = buckets.isEmpty(),
                    noSources = false,
                    display = display,
                )
            }
        }
    }

    /** Čte zobrazovací volby Timeline z [AbsPreferences] (parita Nastavení). */
    private fun readDisplayPrefs() = DisplayPrefs(
        showDescription = prefs.podcastTimelineShowDescription,
        descriptionLines = prefs.podcastTimelineDescriptionLines,
        showDate = prefs.podcastTimelineShowDate,
    )

    /**
     * Sestaví položku fronty z timeline epizody. Když je epizoda STAŽENÁ, hraje se z lokálního
     * `file://` (offline, šetří data) — stejně jako [RssPodcastViewModel.toQueued].
     */
    private fun toQueued(item: TimelineItem): QueuedEpisode {
        val ep = item.episode
        val localUrl = offline.localVideo(item.key)?.let { android.net.Uri.fromFile(it).toString() }
        return QueuedEpisode(
            // WEFT (SHW-75/W2-FIX): nes REF zdroje, ne název pořadu. `currentSourceTarget()` z něj odvodí
            // cíl skoku z coveru přehrávače → u propojeného (TWINE) zdroje `groupFor(type, ref)` najde
            // sloučenou skupinu a otevře SLOUČENOU obrazovku (dřív název → nematchlo → spadlo na NaVýbornou).
            itemId = item.sourceRef,
            episodeId = item.key,
            title = ep.title,
            coverUrl = ep.imageUrl,
            description = ep.description,
            podcastTitle = item.sourceTitle,
            direct = DirectAudio(
                url = localUrl ?: ep.streamUrl,
                durationSec = ep.durationSec,
                author = item.sourceTitle,
            ),
        )
    }

    /** Přehraj epizodu z timeline přes poslechový přehrávač (přímá stream URL, sdílený resume klíč). */
    fun play(item: TimelineItem) = connection.playDirectEpisode(toQueued(item))

    /** Přidej epizodu na konec fronty. */
    /** WEFT (SHW-75/W1): fronta s volbou pozice (další/na konec) — parita s RSS/YT/Merged. */
    fun enqueue(item: TimelineItem, atFront: Boolean = false) = connection.enqueue(toQueued(item), atFront = atFront)

    /** WEFT (SHW-75/W1): smaž staženou epizodu z telefonu (z ⋮ menu řádku). */
    fun deleteOffline(item: TimelineItem) = offline.delete(item.key)

    /** WEFT (SHW-75/W5): skryj POŘAD této epizody na časové ose / ve Sledovaných (per profil, write-through). */
    fun setSourceHidden(item: TimelineItem, timeline: Boolean) {
        val key = "${item.sourceType}:${item.sourceRef}"
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            profileRepository.updateConfig(profileId) { c ->
                if (timeline) c.copy(hiddenTimelineSourceKeys = c.hiddenTimelineSourceKeys + key)
                else c.copy(hiddenFollowingSourceKeys = c.hiddenFollowingSourceKeys + key)
            }
        }
    }

    /**
     * Stáhni epizodu do telefonu (offline poslech) přes sdílený [OfflineDownloadManager]
     * (`TYPE_PODCAST`). Idempotentní (stažené/stahující se přeskočí). Klíč = [TimelineItem.key],
     * shodný s in-app přehrávačem i RSS/YT obrazovkami.
     */
    fun download(item: TimelineItem) {
        val ep = item.episode
        if (ep.streamUrl.isBlank()) return
        offline.enqueue(
            OfflineRequest(
                key = item.key,
                title = ep.title.ifBlank { item.sourceTitle },
                subtitle = item.sourceTitle,
                type = OfflineRequest.TYPE_PODCAST,
                sourceLabel = if (item.sourceType == "youtube") "YouTube" else "Podcast",
                videoUrl = ep.streamUrl,
                posterUrl = ep.imageUrl,
                durationSec = ep.durationSec,
            ),
        )
    }

    /**
     * TWINE: u zdrojů ve stejném propojení spáruj audio (RSS) ↔ video (YouTube) epizody a zahoď
     * spárované VIDEO duplikáty (audio zůstává — má správnější datum a hraje rovnou). Nepropojené
     * zdroje i nespárované epizody se nemění. Bez propojení = no-op.
     */
    private fun dedupeLinked(items: List<TimelineItem>): List<TimelineItem> {
        val links = linkStore.links.value
        if (links.isEmpty()) return items
        fun groupId(item: TimelineItem): String? {
            val k = linkStore.key(item.sourceType, item.sourceRef)
            return links.firstOrNull { k in it.members }?.id
        }
        val byGroup = items.groupBy { groupId(it) }
        val result = ArrayList<TimelineItem>(items.size)
        result.addAll(byGroup[null].orEmpty())   // nepropojené beze změny
        byGroup.forEach { (gid, group) ->
            if (gid == null) return@forEach
            val audio = group.filter { it.sourceType != "youtube" }
            val video = group.filter { it.sourceType == "youtube" }
            val pairedVideoIds = PodcastPairing
                .pairEpisodes(audio.map { it.episode }, video.map { it.episode })
                .mapNotNull { if (it.audio != null && it.video != null) it.video.id else null }
                .toSet()
            result.addAll(audio)
            result.addAll(video.filter { it.episode.id !in pairedVideoIds })
        }
        return result
    }

    // ───────────────────────── Bucketování dle času ─────────────────────────

    /**
     * Rozdělí seřazené (sestupně) epizody do bucketů: Dnes, Včera, Tento týden, Minulý týden, pak po
     * týdnech (Před N týdny) do ~4 týdnů zpět a dál po měsících (Tento měsíc už pokryt → starší měsíce).
     * Prázdné buckety se vynechají.
     */
    private fun bucketize(items: List<TimelineItem>): List<Bucket> {
        if (items.isEmpty()) return emptyList()
        val now = Calendar.getInstance()
        val today0 = now.startOfDay()
        val yesterday0 = today0 - DAY_MS
        val weekStart = now.startOfWeek()
        val lastWeekStart = weekStart - WEEK_MS

        val grouped = LinkedHashMap<String, MutableList<TimelineItem>>()
        val noDate = mutableListOf<TimelineItem>()   // sentinel epizody → samostatný bucket na KONEC
        fun bucket(label: String, item: TimelineItem) {
            grouped.getOrPut(label) { mutableListOf() }.add(item)
        }
        for (it in items) {
            val ts = it.timestampMs
            if (ts == NO_DATE_TS) {
                // Bez data: NIKDY do „Dnes" — vlastní bucket „Bez data" připojený až nakonec.
                noDate.add(it)
                continue
            }
            val label = when {
                ts >= today0 -> "Dnes"
                ts >= yesterday0 -> "Včera"
                ts >= weekStart -> "Tento týden"
                ts >= lastWeekStart -> "Minulý týden"
                else -> olderLabel(ts, weekStart)
            }
            bucket(label, it)
        }
        val result = grouped.map { (label, list) -> Bucket(label, list) }.toMutableList()
        if (noDate.isNotEmpty()) result.add(Bucket("Bez data", noDate))
        return result
    }

    /** Pro epizody starší než „minulý týden": po týdnech do ~5 týdnů, pak po měsících. */
    private fun olderLabel(ts: Long, weekStart: Long): String {
        val weeksAgo = ((weekStart - ts) / WEEK_MS).toInt() + 1   // 1 = minulý týden už řešen výš
        return if (weeksAgo in 1..4) {
            "Před $weeksAgo týdny"
        } else {
            val fmt = SimpleDateFormat("LLLL yyyy", Locale("cs"))
            fmt.format(Date(ts)).replaceFirstChar { it.uppercase(Locale("cs")) }
        }
    }

    private fun Calendar.startOfDay(): Long {
        val c = clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun Calendar.startOfWeek(): Long {
        val c = clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return c.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val WEEK_MS = 7L * DAY_MS
        private const val EPISODES_PER_SOURCE = 15

        /** Sentinel pro epizodu bez naparsovatelného data → bucket „Bez data" na konci (NE „Dnes"). */
        const val NO_DATE_TS = Long.MIN_VALUE

        /**
         * Robustní parser data epizody. RSS chodí RFC822 ("Tue, 24 Jun 2026 10:00:00 +0200") nebo
         * ISO-8601; YouTube uploadDate bývá "YYYYMMDD" nebo ISO. Vrací epoch ms nebo null.
         */
        fun parseEpisodeDate(raw: String?): Long? {
            val s = raw?.trim().orEmpty()
            if (s.isEmpty()) return null
            // YYYYMMDD (YouTube)
            if (s.length == 8 && s.all { it.isDigit() }) {
                return runCatching {
                    SimpleDateFormat("yyyyMMdd", Locale.US).parse(s)?.time
                }.getOrNull()
            }
            for (pattern in DATE_PATTERNS) {
                runCatching {
                    SimpleDateFormat(pattern, Locale.US).parse(s)?.time
                }.getOrNull()?.let { return it }
            }
            return null
        }

        private val DATE_PATTERNS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
    }
}
