package com.github.jankoran90.showlyfin.feature.listen.kids

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.KidsSourceAccess
import com.github.jankoran90.showlyfin.feature.listen.SeriesResolver
import com.github.jankoran90.showlyfin.feature.listen.parseEpisodeDateMs
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Rodičovská správa R3 (2026-11-19) — v dětském profilu se samo přes Wi-Fi stáhne vše, co rodiče Matymu
 * zviditelnili (web rodic.jankoran.cz → config `slovo-kids`): knihy z dětské ABS knihovny, celé pořady
 * (N nejnovějších dílů, [KidsAutoDownloadPrefs.showLimit]), série celé, jednotlivé díly. Odebrané se
 * smaže i ze staženého.
 *
 * Pojistky mazání (incident g32 2026-11-19 — pahýl configu by smazal vše):
 * - maže se jen po configu ČERSTVĚ staženém ze serveru v tomhle běhu ([ProfileRepository.lastServerConfigFetchAt]);
 * - knihy jen z dětských knihoven, které se v tomhle běhu úspěšně načetly a nejsou prázdné;
 * - díly jen „adoptované" ([KidsAutoDownloadPrefs.adopted]) — cizí stažené dospělého zůstanou;
 * - u zdroje se schválenou sérií jen když se jeho díly podařilo načíst (jinak nevím → nechat);
 * - prázdný seznam schválených pořadů + existující stažené = podezřelé → nemazat.
 */
@Singleton
class KidsOfflineSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val abs: AbsRepository,
    private val books: AudiobookDownloadManager,
    private val absEpisodes: EpisodeDownloadManager,
    private val offline: OfflineDownloadManager,
    private val sourcesRepo: PodcastSourcesRepository,
    private val ruleStore: SeriesRuleStore,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = Mutex()
    /** Názvy zdrojů pro rozpoznání série (jen během průchodu, pod [running]). */
    private var sourceTitles: Map<String, String> = emptyMap()

    /**
     * Spusť na pozadí (start appky, návrat do popředí); běží-li už, nic. Bez Wi-Fi se průchod naplánuje
     * na připojení k Wi-Fi ([KidsOfflineSyncWorker.runWhenOnWifi]). [freshSince] = volající právě stáhl
     * config ze serveru (čas před stažením) → nestahovat znovu.
     */
    fun trigger(reason: String, freshSince: Long? = null) {
        if (!KidsAutoDownloadPrefs.isEnabled(context)) return
        if (profiles.activeProfile.value?.isAdmin != false) return
        if (!isOnUnmeteredNetwork()) {
            KidsOfflineSyncWorker.runWhenOnWifi(context)
            return
        }
        scope.launch { run(reason, waitForDownloads = false, freshSince = freshSince) }
    }

    /**
     * Jeden průchod. [waitForDownloads] = počkej, až stahování doběhnou (WorkManager — proces jinak může
     * po konci úlohy zaniknout). Vrací krátké shrnutí (nebo null, když se neběželo).
     */
    suspend fun run(reason: String, waitForDownloads: Boolean, freshSince: Long? = null): String? {
        if (!running.tryLock()) return null
        try {
            if (!KidsAutoDownloadPrefs.isEnabled(context)) return null
            if (profiles.activeProfile.value == null) profiles.restoreActive(preferTv = false)
            val profile = profiles.activeProfile.value ?: return null
            if (profile.isAdmin) return null
            if (!isOnUnmeteredNetwork()) {
                Timber.i("[kids-sync] $reason: bez Wi-Fi, přeskakuji")
                return null
            }
            val started = freshSince ?: System.currentTimeMillis().also { profiles.refreshActiveConfig() }
            // Mezitím přepnuto na dospělého → nic (config níž by byl jeho).
            val active = profiles.activeProfile.value?.takeIf { it.id == profile.id && !it.isAdmin } ?: return null
            val freshConfig = profiles.lastServerConfigFetchAt(active) >= started
            val cfg = profiles.activeConfig.value
            val mayDelete = freshConfig && KidsAutoDownloadPrefs.deleteRemoved(context)
            val keys = cfg.visibleForKidsSourceKeys

            val b = syncBooks(cfg.absLibraryWhitelist, mayDelete)
            val p = syncPodcasts(keys, mayDelete)
            val summary = buildString {
                append("Knihy: +${b.first}")
                if (b.second > 0) append(", smazáno ${b.second}")
                append(" · Díly: +${p.first}")
                if (p.second > 0) append(", smazáno ${p.second}")
                if (!freshConfig) append(" · bez spojení se serverem (nic se nemazalo)")
            }
            Timber.i("[kids-sync] $reason: $summary")
            KidsAutoDownloadPrefs.setLast(context, summary)
            if (waitForDownloads) awaitIdle(9 * 60_000L)
            return summary
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[kids-sync] $reason selhalo")
            return null
        } finally {
            running.unlock()
        }
    }

    // ── knihy ─────────────────────────────────────────────────────────────────
    /** (nově zařazeno, smazáno). */
    private suspend fun syncBooks(whitelist: Set<String>?, mayDelete: Boolean): Pair<Int, Int> {
        if (whitelist.isNullOrEmpty()) return 0 to 0 // bez dětských knihoven nic (nikdy „všechny“)
        val libs = runCatching { abs.getAudiobookLibraries() }.getOrNull()?.filter { it.id in whitelist } ?: return 0 to 0
        var queued = 0
        var deleted = 0
        for (lib in libs) {
            val list = runCatching { abs.getAudiobooks(lib.id) }.getOrNull() ?: continue
            list.filter { !books.isDownloaded(it.id) && books.stateFor(it.id).status != DownloadStatus.DOWNLOADING }
                .forEach {
                    books.download(it.id, it.title, it.author, it.coverUrl, it.libraryId, it.seriesName)
                    queued++
                }
            if (!mayDelete || list.isEmpty()) continue
            val ids = list.mapTo(HashSet()) { it.id }
            val gone = books.downloads.value.filter { it.libraryId == lib.id && it.itemId !in ids }
            if (gone.isEmpty()) continue
            // Offline poslech té knihy nejdřív na server (postup se na serveru přenesl s přesunem knihy).
            runCatching { books.pushOfflineProgress() }
            gone.forEach {
                Timber.i("[kids-sync] kniha odebrána z dětské knihovny → mažu staženou: ${it.title}")
                books.delete(it.itemId)
                deleted++
            }
        }
        return queued to deleted
    }

    // ── pořady, série, díly ───────────────────────────────────────────────────
    private suspend fun syncPodcasts(keys: Set<String>, mayDelete: Boolean): Pair<Int, Int> {
        val whole = keys.filterTo(HashSet()) {
            KidsSourceAccess.parseEpisodeKey(it) == null && PodcastEpisodeSeriesGrouping.parseSeriesKey(it) == null
        }
        val series = keys.mapNotNull { PodcastEpisodeSeriesGrouping.parseSeriesKey(it) }.groupBy({ it.sourceKey }, { it.slug })
        val episodes = KidsSourceAccess.approvedEpisodes(keys).groupBy({ it.sourceKey })
        val known = sourcesRepo.sources.value.associateBy { it.key }
        sourceTitles = known.mapValues { it.value.title }
        val adopted = KidsAutoDownloadPrefs.adopted(context).toMutableSet()
        val limit = KidsAutoDownloadPrefs.showLimit(context)
        var queued = 0
        // Díly zdroje se schválenou sérií (id), null = feed se nepodařilo načíst → u zdroje nic nemazat.
        val seriesIds = HashMap<String, Set<String>?>()

        val customKeys = (whole + series.keys + episodes.keys).filterNot { it.startsWith("abs:") }.toSet()
        for (sk in customKeys) {
            val src = known[sk] ?: ghostSource(sk, episodes[sk]?.firstOrNull()?.title)
            val needAll = sk in series || sk in episodes
            val eps = runCatching {
                sourcesRepo.loadEpisodes(src, limit = if (needAll) (if (src.type == "rss") 1000 else 500) else maxOf(limit, 20))
            }.getOrDefault(emptyList())
            val wanted = LinkedHashMap<String, SourceEpisode>()
            if (sk in whole) eps.filter { it.streamUrl.isNotBlank() }.take(limit).forEach { wanted[it.id] = it }
            series[sk]?.let { slugs ->
                val members = eps.filter { SeriesResolver.seriesSlug(ruleStore, sk, src.title, it.title, it.seriesTitle) in slugs }
                seriesIds[sk] = if (eps.isEmpty()) null else members.mapTo(HashSet()) { it.id }
                members.forEach { wanted[it.id] = it }
            }
            episodes[sk]?.forEach { e -> eps.firstOrNull { it.id == e.episodeId }?.let { wanted[it.id] = it } }
            for (ep in wanted.values.reversed()) { // nejstarší první → série se stahuje od 1. dílu
                if (ep.streamUrl.isBlank()) continue
                val req = offlineRequest(src, ep, src.title)
                adopted += "o:${req.key}"
                if (offline.isDownloaded(req.key) || offline.stateFor(req.key).status.isActive()) continue
                offline.enqueue(req)
                queued++
            }
        }

        // ABS podcasty: celý pořad = N nejnovějších, díl = ten díl.
        val absItems = (whole.filter { it.startsWith("abs:") } + episodes.keys.filter { it.startsWith("abs:") }).toSet()
        for (sk in absItems) {
            val itemId = sk.removePrefix("abs:")
            val detail = runCatching { abs.getPodcastDetail(itemId) }.getOrNull() ?: continue
            val wantedEps = buildList {
                if (sk in whole) addAll(detail.episodes.take(limit))
                val ids = episodes[sk].orEmpty().map { it.episodeId }.toSet()
                addAll(detail.episodes.filter { it.id in ids })
            }.distinctBy { it.id }
            for (ep in wantedEps) {
                adopted += "a:${ep.id}"
                if (absEpisodes.localFile(ep.id) != null || absEpisodes.stateFor(ep.id).status == DownloadStatus.DOWNLOADING) continue
                absEpisodes.download(ep, detail.podcast.title, detail.podcast.coverUrl)
                queued++
            }
        }

        // Adoptuj i ručně stažené díly, které jsou teď schválené (mazat se pak smí i ty).
        offline.downloads.value.filter { it.sourceKey != null && covered(it.key, it.sourceKey!!, it.title, it.seriesTitle, whole, series, episodes, seriesIds) == true }
            .forEach { adopted += "o:${it.key}" }
        absEpisodes.downloads.value.filter { absCovered(it.itemId, it.episodeId, whole, episodes) }
            .forEach { adopted += "a:${it.episodeId}" }

        var deleted = 0
        val suspicious = keys.isEmpty() && adopted.isNotEmpty()
        if (suspicious) Timber.w("[kids-sync] prázdný seznam schválených pořadů — mazání dílů vynechávám (pojistka)")
        if (mayDelete && !suspicious) {
            offline.downloads.value.filter { "o:${it.key}" in adopted }.forEach { d ->
                val sk = d.sourceKey ?: return@forEach
                if (covered(d.key, sk, d.title, d.seriesTitle, whole, series, episodes, seriesIds) == false) {
                    Timber.i("[kids-sync] díl už Dětem nepatří → mažu stažený: ${d.title}")
                    offline.delete(d.key)
                    adopted -= "o:${d.key}"
                    deleted++
                }
            }
            // Zařazené, ale nestažené (fronta) odebraného pořadu taky pryč.
            offline.pending.value.filter { "o:${it.key}" in adopted && it.sourceKey != null }.forEach { r ->
                if (covered(r.key, r.sourceKey!!, r.title, r.seriesTitle, whole, series, episodes, seriesIds) == false) {
                    offline.delete(r.key)
                    adopted -= "o:${r.key}"
                }
            }
            absEpisodes.downloads.value.filter { "a:${it.episodeId}" in adopted }.forEach { d ->
                if (!absCovered(d.itemId, d.episodeId, whole, episodes)) {
                    Timber.i("[kids-sync] ABS díl už Dětem nepatří → mažu stažený: ${d.title}")
                    absEpisodes.delete(d.episodeId)
                    adopted -= "a:${d.episodeId}"
                    deleted++
                }
            }
        }
        // Úklid evidence: co už v telefonu není, z ní vypadne.
        val present = HashSet<String>().apply {
            offline.downloads.value.forEach { add("o:${it.key}") }
            offline.pending.value.forEach { add("o:${it.key}") }
            absEpisodes.downloads.value.forEach { add("a:${it.episodeId}") }
        }
        adopted.retainAll { k ->
            when {
                k in present -> true
                k.startsWith("o:") -> offline.stateFor(k.removePrefix("o:")).status.isActive()
                else -> absEpisodes.stateFor(k.removePrefix("a:")).status == DownloadStatus.DOWNLOADING
            }
        }
        KidsAutoDownloadPrefs.setAdopted(context, adopted)
        return queued to deleted
    }

    /** true = patří Dětem, false = nepatří, null = nevím (díly zdroje se nenačetly) → nemazat. */
    private fun covered(
        key: String,
        sourceKey: String,
        title: String,
        seriesTitle: String?,
        whole: Set<String>,
        series: Map<String, List<String>>,
        episodes: Map<String, List<KidsSourceAccess.Episode>>,
        seriesIds: Map<String, Set<String>?>,
    ): Boolean? {
        if (sourceKey in whole) return true
        val epId = KidsSourceAccess.episodeIdOfResumeKey(key)
        if (episodes[sourceKey].orEmpty().any { it.episodeId == epId }) return true
        val slugs = series[sourceKey] ?: return false
        // Shoda podle názvu (jako offline Poslech dětí i jako stahování) stačí; jinak podle načtených dílů série.
        val slug = (ruleStore.titleFor(sourceKey, title) ?: seriesTitle ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(title))
            ?.let(PodcastEpisodeSeriesGrouping::seriesSlug)
        if (slug != null && slug in slugs) return true
        if (SeriesResolver.seriesSlug(ruleStore, sourceKey, sourceTitles[sourceKey], title, seriesTitle) in slugs) return true
        val ids = seriesIds[sourceKey] ?: return null
        return epId in ids
    }

    private fun absCovered(itemId: String, episodeId: String, whole: Set<String>, episodes: Map<String, List<KidsSourceAccess.Episode>>): Boolean =
        "abs:$itemId" in whole || episodes["abs:$itemId"].orEmpty().any { it.episodeId == episodeId }

    private fun com.github.jankoran90.showlyfin.data.offline.OfflineStatus.isActive() =
        this == com.github.jankoran90.showlyfin.data.offline.OfflineStatus.QUEUED ||
            this == com.github.jankoran90.showlyfin.data.offline.OfflineStatus.DOWNLOADING

    private fun ghostSource(key: String, title: String?) = PodcastSource(
        id = "", type = key.substringBefore(':'), ref = key.substringAfter(':'), title = title ?: "Pořad",
    )

    private fun ctvFileUrl(idec: String) = uploaderDs.ctvAudioFileUrl(
        prefs.getString("uploader_base_url", "") ?: "", prefs.getString("uploader_session_cookie", "") ?: "", idec,
    )

    /** Požadavek na stažení dílu vlastního zdroje — sdílený s ručním „Stáhnout" v Poslechu dětí. */
    fun offlineRequest(source: PodcastSource, ep: SourceEpisode, showTitle: String): OfflineRequest = OfflineRequest(
        key = ep.resumeKey ?: "${source.type}:${ep.id}",
        title = ep.title,
        subtitle = showTitle,
        type = OfflineRequest.TYPE_PODCAST,
        sourceLabel = "Podcast",
        // ČT díl je DASH manifest → stáhnout jde jen serverem složený m4a (2026-10-13).
        videoUrl = if (source.type == "ctv") ctvFileUrl(ep.id) else ep.streamUrl,
        posterUrl = ep.imageUrl,
        durationSec = ep.durationSec,
        description = ep.description,
        publishedAt = parseEpisodeDateMs(ep.date),
        sourceKey = source.key,
        seriesTitle = ep.seriesTitle,
    )

    private fun isOnUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Počkej, až žádné stahování neběží (nejdéle [timeoutMs]). */
    private suspend fun awaitIdle(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            delay(3_000)
            while (
                books.states.value.values.any { it.status == DownloadStatus.DOWNLOADING } ||
                absEpisodes.states.value.values.any { it.status == DownloadStatus.DOWNLOADING } ||
                offline.activeCount() > 0
            ) delay(5_000)
        }
    }
}
