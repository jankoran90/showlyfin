package com.github.jankoran90.showlyfin.feature.listen.kids

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.ui.KioskMode
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Rodičovská správa R3 (2026-11-19) — v dětském profilu se samo přes Wi-Fi stáhne vše, co rodiče Matymu
 * zviditelnili (web rodic.jankoran.cz → config `slovo-kids`): knihy z dětské ABS knihovny, celé pořady
 * (N nejnovějších dílů, [KidsAutoDownloadPrefs.showLimit]), série celé, jednotlivé díly. Odebrané se
 * smaže i ze staženého.
 *
 * Pojistky mazání (incident g32 2026-11-19 — pahýl configu by smazal vše; revize 2026-11-19):
 * - maže se jen po configu ČERSTVĚ staženém ze serveru v tomhle běhu ([ProfileRepository.lastServerConfigFetchAt]);
 * - jen „adoptované" stažení ([KidsAutoDownloadPrefs.adopted]) = stáhlo je samostahování nebo dítě; už
 *   stažené cizí se adoptuje jen na dětském telefonu ([KioskMode]) → stažené dospělého zůstanou;
 * - knihy jen z dětské knihovny, jejíž výpis se v tomhle běhu úplně načetl (neprázdný, < strop stránky),
 *   a jen když offline postup knihy došel na server;
 * - u zdroje se schválenou sérií jen když je zdroj známý a jeho díly se načetly (jinak nevím → nechat);
 * - prázdný seznam schválených + existující adoptované = podezřelé → nemazat;
 * - přepnutí na dospělého během průchodu → průchod končí (nic dalšího se nestáhne ani nesmaže).
 * Stahování spuštěná samostahováním se při odchodu z Wi-Fi zruší ([onNetworkChanged]) a dokončí se po návratu.
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
    /** Názvy známých zdrojů pro rozpoznání série (jen během průchodu, pod [running]). */
    private var sourceTitles: Map<String, String> = emptyMap()
    @Volatile private var lastRunAt = 0L

    /** Co samostahování právě zařadilo (`o:`/`a:`/`b:` klíč) — při odchodu z Wi-Fi se to zruší. */
    private val autoQueued = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var networkWatch = false

    private class SwitchedAway : Exception()
    private class LeftWifi : Exception()

    /**
     * Spusť na pozadí (start appky, návrat do popředí); běží-li už nebo běželo před chvílí, nic. Bez Wi-Fi se
     * průchod naplánuje na připojení k Wi-Fi ([KidsOfflineSyncWorker.runWhenOnWifi]). [freshSince] =
     * volající právě stáhl config ze serveru (čas před stažením) → nestahovat znovu.
     */
    fun trigger(reason: String, freshSince: Long? = null, force: Boolean = false) {
        if (!KidsAutoDownloadPrefs.isEnabled(context)) return
        if (profiles.activeProfile.value?.isAdmin != false) return
        if (!isOnUnmeteredNetwork()) {
            KidsOfflineSyncWorker.runWhenOnWifi(context)
            return
        }
        if (!force && System.currentTimeMillis() - lastRunAt < MIN_INTERVAL_MS) return
        scope.launch { run(reason, waitForDownloads = false, freshSince = freshSince) }
    }

    /** Ruční stažení dítětem (Poslech dětí) — patří Dětem, smí se po odebrání smazat. */
    fun adoptManual(keys: Collection<String>) {
        if (profiles.activeProfile.value?.isAdmin != false || keys.isEmpty()) return
        KidsAutoDownloadPrefs.applyAdoptedDelta(context, added = keys.toSet(), removed = emptySet())
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
            val profileId = profiles.activeProfile.value?.takeIf { !it.isAdmin }?.id ?: return null
            if (!isOnUnmeteredNetwork()) {
                Timber.i("[kids-sync] $reason: bez Wi-Fi, přeskakuji")
                return null
            }
            lastRunAt = System.currentTimeMillis()
            watchNetwork()
            // Evidence zrušitelných: jen to, co se ještě stahuje (dokončené už se rušit nesmí).
            autoQueued.removeAll { !isStillDownloading(it) }
            val started = freshSince ?: System.currentTimeMillis().also { profiles.refreshActiveConfig() }
            // Profil i config JEDNÍM čtením (ne dva oddělené StateFlow — revize 2026-11-19).
            val (active, cfg) = profiles.activeProfileWithConfig.value
            if (active == null || active.id != profileId || active.isAdmin) return null
            val freshConfig = profiles.lastServerConfigFetchAt(active) >= started
            val mayDelete = freshConfig && KidsAutoDownloadPrefs.deleteRemoved(context)
            // Před každým krokem: pořád Děti a pořád Wi-Fi (revize 2: síť se jinak hlídala jen na začátku).
            val ensureKids = {
                val now = profiles.activeProfileWithConfig.value.first
                if (now == null || now.id != profileId || now.isAdmin) throw SwitchedAway()
                // Jen jistě měřená síť; krátký výpadek (žádná aktivní síť při přechodu mezi Wi-Fi body) průchod neruší.
                if (isOnMeteredNetwork()) throw LeftWifi()
            }
            val b = syncBooks(cfg.absLibraryWhitelist, mayDelete, ensureKids)
            val p = syncPodcasts(cfg.visibleForKidsSourceKeys, mayDelete, ensureKids)
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
        } catch (e: SwitchedAway) {
            Timber.i("[kids-sync] $reason: přepnuto na jiný profil, průchod končí")
            return null
        } catch (e: LeftWifi) {
            Timber.i("[kids-sync] $reason: konec Wi-Fi, průchod končí (dokončí se na Wi-Fi)")
            onMeteredNetwork()
            KidsOfflineSyncWorker.runWhenOnWifi(context)
            return null
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
    private suspend fun syncBooks(whitelist: Collection<String>?, mayDelete: Boolean, ensureKids: () -> Unit): Pair<Int, Int> {
        if (whitelist.isNullOrEmpty()) return 0 to 0 // bez dětských knihoven nic (nikdy „všechny“)
        val libs = runCatching { abs.getAudiobookLibraries() }.getOrNull()?.filter { it.id in whitelist } ?: return 0 to 0
        val original = KidsAutoDownloadPrefs.adopted(context)
        val adopted = original.toMutableSet()
        // Revize 3: evidenci uložit i při přerušení průchodu (konec Wi-Fi, přepnutí profilu) — jinak by
        // mezitím dokončená stažení zůstala bez evidence a po odebrání se nikdy nesmazala.
        try {
            val kiosk = KioskMode.isActive(context)
            // Knihovny, které kdy byly dětské: odebere-li rodič celou knihovnu, její adoptované knihy pryč.
            val managed = KidsAutoDownloadPrefs.managedLibraries(context)
            KidsAutoDownloadPrefs.setManagedLibraries(context, managed + libs.map { it.id })
            var queued = 0
            var loadedLibs = 0
            val toDelete = ArrayList<String>()
            for (lib in libs) {
                val list = runCatching { abs.getAudiobooks(lib.id) }.getOrNull() ?: continue
                ensureKids()
                if (list.isNotEmpty()) loadedLibs++
                list.forEach { book ->
                    if (books.isDownloaded(book.id)) {
                        if (kiosk) adopted += "b:${book.id}"
                    } else if (books.stateFor(book.id).status != DownloadStatus.DOWNLOADING) {
                        books.download(book.id, book.title, book.author, book.coverUrl, book.libraryId, book.seriesName)
                        adopted += "b:${book.id}"
                        autoQueued += "b:${book.id}"
                        queued++
                    }
                }
                // Výpis je jedna stránka po 500 → u plné stránky nevím, jestli je celý → nemazat.
                if (!mayDelete || list.isEmpty() || list.size >= ABS_PAGE) continue
                val ids = list.mapTo(HashSet()) { it.id }
                books.downloads.value
                    .filter { it.libraryId == lib.id && it.itemId !in ids && "b:${it.itemId}" in adopted }
                    .forEach { toDelete += it.itemId }
            }
            // Odebraná celá knihovna: jen když whitelist pořád nese aspoň jednu knižní knihovnu, která se načetla —
            // pahýl configu (whitelist jen [Podcasty], incident g32) jinak vypadá jako „odebrané všechny knihy“.
            if (mayDelete && loadedLibs > 0) {
                val dropped = managed - whitelist.toSet()
                books.downloads.value
                    .filter { it.libraryId in dropped && "b:${it.itemId}" in adopted }
                    .forEach { toDelete += it.itemId }
            }
            var deleted = 0
            if (toDelete.isNotEmpty()) {
                ensureKids()
                // Offline poslech té knihy nejdřív na server; nedošel-li, knihu zatím nechat (příště znovu).
                runCatching { books.pushOfflineProgress() }
                ensureKids()
                toDelete.distinct().forEach { id ->
                    if (books.hasUnsyncedLocalProgress(id)) return@forEach
                    Timber.i("[kids-sync] kniha už Dětem nepatří → mažu staženou: ${books.downloadRecord(id)?.title}")
                    books.delete(id)
                    adopted -= "b:$id"
                    deleted++
                }
            }
            val present = books.downloads.value.mapTo(HashSet()) { "b:${it.itemId}" }
            adopted.removeAll { it.startsWith("b:") && it !in present && books.stateFor(it.removePrefix("b:")).status != DownloadStatus.DOWNLOADING }
            return queued to deleted
        } finally {
            KidsAutoDownloadPrefs.applyAdoptedDelta(context, added = adopted - original, removed = original - adopted)
        }
    }

    // ── pořady, série, díly ───────────────────────────────────────────────────
    private suspend fun syncPodcasts(keys: Set<String>, mayDelete: Boolean, ensureKids: () -> Unit): Pair<Int, Int> {
        val whole = keys.filterTo(HashSet()) {
            KidsSourceAccess.parseEpisodeKey(it) == null && PodcastEpisodeSeriesGrouping.parseSeriesKey(it) == null
        }
        val series = keys.mapNotNull { PodcastEpisodeSeriesGrouping.parseSeriesKey(it) }.groupBy({ it.sourceKey }, { it.slug })
        val episodes = KidsSourceAccess.approvedEpisodes(keys).groupBy({ it.sourceKey })
        // Studený proces (úloha na pozadí): seznam zdrojů ještě není → dotáhnout, jinak série nepoznám.
        if (sourcesRepo.sources.value.isEmpty()) runCatching { sourcesRepo.refresh() }
        val known = sourcesRepo.sources.value.associateBy { it.key }
        sourceTitles = known.mapValues { it.value.title }
        val original = KidsAutoDownloadPrefs.adopted(context)
        val adopted = original.toMutableSet()
        // Revize 3: evidenci uložit i při přerušení průchodu (konec Wi-Fi, přepnutí profilu) — jinak by
        // mezitím dokončená stažení zůstala bez evidence a po odebrání se nikdy nesmazala.
        try {
            val kiosk = KioskMode.isActive(context)
            val limit = KidsAutoDownloadPrefs.showLimit(context)
            var queued = 0
            // Díly zdroje se schválenou sérií (id), null = nevím (zdroj neznámý / díly se nenačetly) → nemazat.
            val seriesIds = HashMap<String, Set<String>?>()

            val customKeys = (whole + series.keys + episodes.keys).filterNot { it.startsWith("abs:") }.toSet()
            for (sk in customKeys) {
                val src = known[sk] ?: ghostSource(sk, episodes[sk]?.firstOrNull()?.title)
                val needAll = sk in series || sk in episodes
                val eps = runCatching {
                    sourcesRepo.loadEpisodes(src, limit = if (needAll) (if (src.type == "rss") 1000 else 500) else maxOf(limit, 20))
                }.getOrDefault(emptyList())
                ensureKids()
                val wanted = LinkedHashMap<String, SourceEpisode>()
                if (sk in whole) eps.filter { it.streamUrl.isNotBlank() }.take(limit).forEach { wanted[it.id] = it }
                series[sk]?.let { slugs ->
                    val members = eps.filter { SeriesResolver.seriesSlug(ruleStore, sk, known[sk]?.title, it.title, it.seriesTitle) in slugs }
                    seriesIds[sk] = if (sk !in known || members.isEmpty()) null else members.mapTo(HashSet()) { it.id }
                    members.forEach { wanted[it.id] = it }
                }
                episodes[sk]?.forEach { e -> eps.firstOrNull { it.id == e.episodeId }?.let { wanted[it.id] = it } }
                for (ep in wanted.values.reversed()) { // nejstarší první → série se stahuje od 1. dílu
                    if (ep.streamUrl.isBlank()) continue
                    val req = offlineRequest(src, ep, src.title)
                    if (offline.isDownloaded(req.key) || offline.stateFor(req.key).status.isActive()) continue
                    offline.enqueue(req)
                    adopted += "o:${req.key}"
                    autoQueued += "o:${req.key}"
                    queued++
                }
            }

            // ABS podcasty: celý pořad = N nejnovějších, díl = ten díl.
            val absItems = (whole.filter { it.startsWith("abs:") } + episodes.keys.filter { it.startsWith("abs:") }).toSet()
            for (sk in absItems) {
                val itemId = sk.removePrefix("abs:")
                val detail = runCatching { abs.getPodcastDetail(itemId) }.getOrNull() ?: continue
                ensureKids()
                val wantedEps = buildList {
                    if (sk in whole) addAll(detail.episodes.take(limit))
                    val ids = episodes[sk].orEmpty().map { it.episodeId }.toSet()
                    addAll(detail.episodes.filter { it.id in ids })
                }.distinctBy { it.id }
                for (ep in wantedEps) {
                    if (absEpisodes.localFile(ep.id) != null || absEpisodes.stateFor(ep.id).status == DownloadStatus.DOWNLOADING) continue
                    absEpisodes.download(ep, detail.podcast.title, detail.podcast.coverUrl)
                    adopted += "a:${ep.id}"
                    autoQueued += "a:${ep.id}"
                    queued++
                }
            }

            // Dětský telefon: i dřív stažené schválené díly patří Dětem (na sdíleném telefonu ne — mohou být dospělého).
            if (kiosk) {
                offline.downloads.value
                    .filter { it.sourceKey != null && covered(it.key, it.sourceKey!!, it.title, it.seriesTitle, whole, series, episodes, seriesIds) == true }
                    .forEach { adopted += "o:${it.key}" }
                absEpisodes.downloads.value.filter { absCovered(it.itemId, it.episodeId, whole, episodes) }
                    .forEach { adopted += "a:${it.episodeId}" }
            }

            var deleted = 0
            val suspicious = keys.isEmpty() && adopted.any { it.startsWith("o:") || it.startsWith("a:") }
            if (suspicious) Timber.w("[kids-sync] prázdný seznam schválených pořadů — mazání dílů vynechávám (pojistka)")
            if (mayDelete && !suspicious) {
                ensureKids()
                val gone = offline.downloads.value.filter { "o:${it.key}" in adopted }.filter { d ->
                    d.sourceKey != null && covered(d.key, d.sourceKey!!, d.title, d.seriesTitle, whole, series, episodes, seriesIds) == false
                }.map { it.key to it.title } + offline.pending.value.filter { "o:${it.key}" in adopted }.filter { r ->
                    r.sourceKey != null && covered(r.key, r.sourceKey!!, r.title, r.seriesTitle, whole, series, episodes, seriesIds) == false
                }.map { it.key to it.title }
                gone.distinctBy { it.first }.forEach { (key, title) ->
                    Timber.i("[kids-sync] díl už Dětem nepatří → mažu stažený: $title")
                    offline.delete(key)
                    adopted -= "o:$key"
                    deleted++
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
            // Úklid evidence: co už v telefonu není ani se nestahuje, z ní vypadne (knihy řeší [syncBooks]).
            val present = HashSet<String>().apply {
                offline.downloads.value.forEach { add("o:${it.key}") }
                offline.pending.value.forEach { add("o:${it.key}") }
                absEpisodes.downloads.value.forEach { add("a:${it.episodeId}") }
            }
            adopted.retainAll { k ->
                when {
                    k.startsWith("b:") || k in present -> true
                    k.startsWith("o:") -> offline.stateFor(k.removePrefix("o:")).status.isActive()
                    else -> absEpisodes.stateFor(k.removePrefix("a:")).status == DownloadStatus.DOWNLOADING
                }
            }
            return queued to deleted
        } finally {
            KidsAutoDownloadPrefs.applyAdoptedDelta(context, added = adopted - original, removed = original - adopted)
        }
    }

    /** true = patří Dětem, false = nepatří, null = nevím → nemazat. */
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

    private fun OfflineStatus.isActive() = this == OfflineStatus.QUEUED || this == OfflineStatus.DOWNLOADING

    private fun ghostSource(key: String, title: String?) = PodcastSource(
        id = "", type = key.substringBefore(':'), ref = key.substringAfter(':'), title = title ?: "Pořad",
    )

    private fun ctvFileUrl(idec: String) = uploaderDs.ctvAudioFileUrl(
        prefs.getString("uploader_base_url", "") ?: "", prefs.getString("uploader_session_cookie", "") ?: "", idec,
    )

    /** Požadavek na stažení dílu vlastního zdroje — sdílený s ručním „Stáhnout“ v Poslechu dětí. */
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

    /** Aktivní síť existuje a je měřená (mobilní data). Žádná síť = nevím → false. */
    private fun isOnMeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Revize 2026-11-19 (P1): přejde-li telefon během samostahování z Wi-Fi na mobilní data, zařazená a běžící
     * stahování samostahování se zruší (nedokončená se smažou) a průchod se naplánuje na návrat Wi-Fi.
     */
    private fun watchNetwork() {
        if (networkWatch) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) onMeteredNetwork()
                }
            })
            networkWatch = true
        }.onFailure { Timber.w(it, "[kids-sync] hlídání sítě nešlo zapnout") }
    }

    private fun onMeteredNetwork() {
        if (autoQueued.isEmpty()) return
        // Revize 3: síťový callback je globální — v dospělém profilu nic nerušit (stejný díl může stahovat rodič),
        // jen zapomenout evidenci samostahování (další průchod v Dětech si ji založí znovu).
        if (profiles.activeProfile.value?.isAdmin != false) {
            autoQueued.clear()
            return
        }
        var cancelled = 0
        autoQueued.toList().forEach { k ->
            val id = k.substring(2)
            if (isStillDownloading(k)) {
                when (k.take(2)) {
                    "o:" -> offline.delete(id)
                    "a:" -> absEpisodes.cancel(id)
                    "b:" -> {
                        books.cancel(id)
                        // Doběhla mezi kontrolou a zrušením → soubory pryč, záznam by lhal „staženo“ → uklidit celý.
                        if (books.downloadRecord(id) != null) books.delete(id)
                    }
                }
                cancelled++
            }
            autoQueued.remove(k)
        }
        if (cancelled > 0) {
            Timber.i("[kids-sync] mobilní data → zrušeno $cancelled stahování, dokončí se na Wi-Fi")
            KidsOfflineSyncWorker.runWhenOnWifi(context)
        }
    }

    private fun isStillDownloading(k: String): Boolean {
        val id = k.substring(2)
        return when (k.take(2)) {
            "o:" -> !offline.isDownloaded(id) && offline.stateFor(id).status.isActive()
            "a:" -> absEpisodes.localFile(id) == null && absEpisodes.stateFor(id).status == DownloadStatus.DOWNLOADING
            else -> !books.isDownloaded(id) && books.stateFor(id).status == DownloadStatus.DOWNLOADING
        }
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

    private companion object {
        const val MIN_INTERVAL_MS = 5 * 60_000L
        const val ABS_PAGE = 500
    }
}
