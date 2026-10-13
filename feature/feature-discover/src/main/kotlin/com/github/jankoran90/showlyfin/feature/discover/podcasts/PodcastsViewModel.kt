package com.github.jankoran90.showlyfin.feature.discover.podcasts

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.CtvTitle
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.data.uploader.subtitle.ActiveTranslationsRepository
import com.github.jankoran90.showlyfin.data.uploader.subtitle.SubtitleTranslateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import com.github.jankoran90.showlyfin.data.uploader.youtubeVideoUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * AMFITEÁTR (FLM-05) — sekce Podcasty (TV i telefon Filmů, sdílený mozek): video podcasty ze STEJNÝCH zdrojů jako Slovo (server
 * `/api/sources`, [PodcastSourcesRepository]). Viditelnost per profil = [PodcastSource.isVisibleTo]
 * (jediné místo pravidla; dítě jen schválené zdroje). Pozice = [VideoResumeStore] (sdílená se Slovem
 * přes server, klíče `yt:`/`ctv:`/`rss:` — přehrávač je ukládá sám podle `resumeKey`).
 *
 * Na TV jen to, co má video: YouTube kanály, pořady ČT (otevřou kartu ČT, odkaz na video vzniká až na
 * zařízení) a RSS podcasty s video epizodou v Jellyfinu (`jfItemId`).
 */
@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val sourcesRepo: PodcastSourcesRepository,
    private val remote: UploaderRemoteDataSource,
    private val profiles: ProfileRepository,
    private val resume: VideoResumeStore,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
    private val activeTranslations: ActiveTranslationsRepository,
    @param:ApplicationContext private val appContext: Context,
    watchedStore: com.github.jankoran90.showlyfin.core.domain.resume.CtvWatchedStore,
) : ViewModel() {

    /** Dokoukané díly (`yt:`/`ctv:`, per profil, sdílené mezi zařízeními) → štítek „zhlédnuto" na kartě. */
    val watched: StateFlow<Set<String>> = watchedStore.watched

    data class UiState(
        val loading: Boolean = true,
        /** Zdroje s videem, které profil vidí — v pořadí ze serveru (prémiové nahoře řeší repo). */
        val sources: List<PodcastSource> = emptyList(),
        /** `source.key` → načtené díly s videem (nejnovější první). */
        val episodes: Map<String, List<SourceEpisode>> = emptyMap(),
        val error: String? = null,
        /** Nesledované kanály (z hledání na YouTube), jejichž díly se právě načítají. */
        val loadingSources: Set<String> = emptySet(),
        /**
         * Rozkoukaná YouTube videa MIMO sledované kanály (pustil je z hledání, i na jiném zařízení) —
         * user 2026-10-13: „přeložím a rozkoukám na telefonu → uvidím na TV". Jdou do řady Pokračovat.
         */
        val orphans: List<SourceEpisode> = emptyList(),
    )

    /** Co se má stát po výběru dílu — rozhodne navigátor (přehrávač / karta ČT). */
    sealed interface PlayTarget {
        data class External(
            val url: String, val title: String, val posterUrl: String?,
            val resumeKey: String, val subtitleQuery: SubtitleQuery?,
        ) : PlayTarget
        data class Jellyfin(val itemId: String) : PlayTarget
        data class CtvProgram(val title: CtvTitle) : PlayTarget
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Pozice dílů aktivního profilu (reaktivně — po návratu z přehrávače se řada Pokračovat překreslí). */
    val marks: StateFlow<Map<String, VideoResumeStore.Mark>> = resume.marks

    private var loadJob: Job? = null

    /** Díly nesledovaných kanálů otevřených z hledání na YouTube (`source.key` → díly); přežijí reload. */
    private val external = mutableMapOf<String, List<SourceEpisode>>()

    /** F2: `yt:<id>` dílů, které už mají hotový český AI překlad (štítek CZ na kartě). */
    private val _translated = MutableStateFlow<Set<String>>(emptySet())
    val translated: StateFlow<Set<String>> = _translated.asStateFlow()

    /** F2: právě se překládající (podle serveru) — karta ukáže „Překládá se". */
    val translating: StateFlow<Set<String>> = activeTranslations.jobs
        .map { jobs -> jobs.filter { it.isActive }.mapNotNull { j -> j.jobId.removePrefix("ai_yt2_").takeIf { it != j.jobId } }.map { "yt:$it" }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        // Codex review: přidání / odebrání zdroje (Spravovat zdroje, Slovo) → Pořady načti znovu hned,
        // ne až po [STALE_MS]. drop(1) = počáteční stav; reload sám refresh() dělá, distinct brání smyčce.
        viewModelScope.launch {
            sourcesRepo.sources.map { list -> list.map { it.key }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (lastLoadAt > 0L) reload() }
        }
        // Nová rozkoukaná YouTube videa (i z jiného zařízení) → dohledej ta, co nejsou ve sledovaných kanálech.
        viewModelScope.launch {
            resume.marks.map { m -> m.keys.filter { it.startsWith("yt:") }.toSet() }
                .distinctUntilChanged()
                .collect { if (!_state.value.loading) resolveOrphans(it) }
        }
        // Doběhlý překlad (dle serveru) → obnov štítky CZ.
        viewModelScope.launch {
            activeTranslations.jobs.map { jobs -> jobs.count { it.status == "done" } }
                .distinctUntilChanged().collect { refreshTranslated() }
        }
        // Přepnutí profilu = jiné zdroje i pozice → načti znovu (stav předchozího profilu nesmí přetéct).
        viewModelScope.launch {
            combine(profiles.activeProfile, profiles.activeConfig) { p, cfg ->
                Triple(p?.id, cfg.sharedSourceKeys, cfg.visibleForKidsSourceKeys)
            }.distinctUntilChanged().collect { reload() }
        }
    }

    private var lastLoadAt = 0L
    private var lastPerSource = -1

    /**
     * Vstup do sekce / kanálu (Codex review): VM žije na úrovni aktivity, takže bez tohohle by se po
     * návratu ukázal starý stav (nové díly, změna „Dílů na kanál" v Nastavení). Načte znovu, když je
     * stav starší než [STALE_MS] nebo se změnilo nastavení počtu dílů.
     */
    fun onEnter() {
        val per = PodcastPrefs.perSource(prefs)
        if (System.currentTimeMillis() - lastLoadAt > STALE_MS || per != lastPerSource) reload()
    }

    fun reload() {
        lastLoadAt = System.currentTimeMillis()
        lastPerSource = PodcastPrefs.perSource(prefs)
        // Díly nesledovaných kanálů zastarávají stejně jako ostatní → karta je po reloadu načte znovu (Codex review).
        external.clear()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                sourcesRepo.refresh()
                val visible = visibleVideoSources()
                val gate = Semaphore(4)
                val loaded = visible.map { src ->
                    async { gate.withPermit { src to loadVideoEpisodes(src) } }
                }.awaitAll()
                // RSS podcast bez jediné video epizody na TV nepatří (jen audio → Slovo).
                val withVideo = loaded.filter { (src, eps) -> src.type != "rss" || eps.isNotEmpty() }
                _state.value = UiState(
                    loading = false,
                    sources = withVideo.map { it.first },
                    episodes = withVideo.associate { (src, eps) -> src.key to eps } + external,
                    orphans = _state.value.orphans,
                )
                resolveOrphans(resume.marks.value.keys)
                refreshTranslated()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AMFITEATR] načtení podcastů selhalo")
                _state.update { it.copy(loading = false, error = "Podcasty se nepodařilo načíst. Zkontroluj připojení k serveru.") }
            }
        }
    }

    private fun visibleVideoSources(): List<PodcastSource> {
        val profile = profiles.activeProfile.value
        val cfg = profiles.activeConfig.value
        val isKids = profile?.isAdmin == false
        return sourcesRepo.sources.value
            .filter { it.type in VIDEO_TYPES }
            .filter { it.isVisibleTo(profile?.profileUuid, isKids, cfg.sharedSourceKeys, cfg.visibleForKidsSourceKeys) }
    }

    private suspend fun loadVideoEpisodes(src: PodcastSource): List<SourceEpisode> =
        runCatching { sourcesRepo.loadEpisodes(src, PodcastPrefs.perSource(prefs)) }
            .onFailure { if (it is CancellationException) throw it; Timber.w(it, "[AMFITEATR] díly %s", src.key) }
            .getOrDefault(emptyList())
            .let { eps -> if (src.type == "rss") eps.filter { !it.jfItemId.isNullOrBlank() } else eps }
            .sortedByDescending { dateSortKey(it.date) }

    private suspend fun refreshTranslated() {
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        if (base.isBlank()) return
        val ids = (_state.value.sources.filter { it.type == "youtube" }.map { it.key } + external.keys)
            .flatMap { key -> _state.value.episodes[key].orEmpty().map { "yt:${it.id}" } } +
            _state.value.orphans.map { "yt:${it.id}" }
        if (ids.isEmpty()) return
        runCatching { remote.getDoneTranslations(base, prefs.getString("uploader_session_cookie", "").orEmpty(), ids) }
            .onSuccess { _translated.value = it }
            .onFailure { if (it is CancellationException) throw it; Timber.d(it, "[AMFITEATR] stav překladů") }
    }

    /** Jde díl přeložit do češtiny? (zatím jen YouTube — ČT má vlastní české titulky, RSS video z JF ne). */
    fun canTranslate(source: PodcastSource): Boolean = source.type == "youtube"

    /**
     * F2 — „Přeložit do češtiny" (po potvrzení v UI): překlad na pozadí přes stejný worker jako přehrávač
     * (průběžná notifikace, dokončení, auto-nasazení titulků při přehrání). Model = výchozí serveru (Haiku).
     */
    fun translate(source: PodcastSource, ep: SourceEpisode) {
        if (!canTranslate(source)) return
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        if (base.isBlank()) return
        SubtitleTranslateWorker.enqueue(
            context = appContext,
            base = base,
            cookie = prefs.getString("uploader_session_cookie", "").orEmpty(),
            imdb = "yt:${ep.id}",
            title = ep.title,
            season = null,
            episode = null,
            progressive = true,
        )
    }

    private val orphanJob = java.util.concurrent.atomic.AtomicReference<Job?>(null)

    /** Rozkoukané `yt:` mimo načtené díly → metadata videa (cache v repu; jen dospělý, max [MAX_ORPHANS]). */
    private fun resolveOrphans(keys: Set<String>) {
        if (profiles.activeProfile.value?.isAdmin == false) return
        // Jen díly SLEDOVANÝCH zdrojů — nesledovaný kanál otevřený z hledání v řadách není (Codex review).
        val known = _state.value.sources.flatMap { _state.value.episodes[it.key].orEmpty() }
            .map { it.resumeKey ?: "yt:${it.id}" }.toSet()
        val wanted = keys.filter { it.startsWith("yt:") && it !in known }
            .sortedByDescending { resume.marks.value[it]?.updatedAt ?: 0L }
            .take(MAX_ORPHANS)
        orphanJob.getAndSet(viewModelScope.launch {
            val eps = wanted.mapNotNull { key ->
                runCatching { sourcesRepo.resolveYoutubeEpisode(key.removePrefix("yt:")) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            }
            _state.update { it.copy(orphans = eps) }
            if (eps.isNotEmpty()) refreshTranslated()
        })?.cancel()
    }

    /** Je kanál mezi sledovanými zdroji profilu? (Kanál z hledání na YouTube nemusí být.) */
    fun isFollowed(source: PodcastSource): Boolean = _state.value.sources.any { it.key == source.key }

    /** Sledovat smí jen dospělý profil (dětem zdroje schvaluje rodič). */
    fun canFollow(): Boolean = profiles.activeProfile.value?.isAdmin != false

    /** Karta nesledovaného kanálu (z hledání na YouTube): načti jeho díly, aniž by se stal zdrojem. */
    fun openExternal(source: PodcastSource) {
        if (isFollowed(source) || source.key in external || source.key in _state.value.loadingSources) return
        viewModelScope.launch {
            _state.update { it.copy(loadingSources = it.loadingSources + source.key) }
            val eps = loadVideoEpisodes(source)
            if (eps.isNotEmpty()) external[source.key] = eps
            _state.update {
                it.copy(loadingSources = it.loadingSources - source.key, episodes = it.episodes + (source.key to eps))
            }
            refreshTranslated()
        }
    }

    /** „Sledovat" — kanál z hledání přidá mezi zdroje (sdílené se Slovem), pak sekce načte znovu. */
    fun follow(source: PodcastSource, onDone: (Boolean) -> Unit = {}) {
        if (!canFollow()) return
        viewModelScope.launch {
            val ok = sourcesRepo.add(source.type, source.ref, source.title, source.thumbnail, profiles.activeProfile.value?.profileUuid)
            if (ok) { external.remove(source.key); reload() }
            onDone(ok)
        }
    }

    fun sourceOf(sourceKey: String): PodcastSource? = _state.value.sources.firstOrNull { it.key == sourceKey }
        ?: _state.value.orphans.map { it.orphanSource() }.firstOrNull { it.key == sourceKey }

    fun episodeOf(sourceKey: String, episodeId: String): SourceEpisode? =
        _state.value.episodes[sourceKey]?.firstOrNull { it.id == episodeId }
            ?: _state.value.orphans.firstOrNull { it.id == episodeId && it.orphanSource().key == sourceKey }

    /** Kam vede výběr dílu. ČT → karta pořadu (video se resolvuje na zařízení, umí to `TvCtvScreen`). */
    fun playTarget(source: PodcastSource, ep: SourceEpisode): PlayTarget? {
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        return when (source.type) {
            "youtube" -> {
                if (base.isBlank()) return null
                PlayTarget.External(
                    url = remote.youtubeVideoUrl(base, cookie, ep.id, videoQuality()),
                    title = ep.title,
                    posterUrl = ep.imageUrl,
                    resumeKey = ep.resumeKey ?: "yt:${ep.id}",
                    // `yt:<id>` = klíč AI překladu titulků (stejně jako Slovo `youtubeSubtitleQuery`).
                    subtitleQuery = SubtitleQuery(imdb = "yt:${ep.id}", title = ep.title, origTitle = ep.title),
                )
            }
            "ctv" -> PlayTarget.CtvProgram(ctvTitleOf(source))
            "rss" -> ep.jfItemId?.takeIf { it.isNotBlank() }?.let { PlayTarget.Jellyfin(it) }
            else -> null
        }
    }

    fun ctvTitleOf(source: PodcastSource): CtvTitle =
        CtvTitle(sidp = source.ref, type = "series", title = source.title, thumbnail = source.thumbnail)

    /** Kvalita videa sdílená se Slovem (`podcast_stream_quality`: 360 / 720 / max). */
    private fun videoQuality(): String = PodcastPrefs.quality(prefs)

    companion object {
        val VIDEO_TYPES = setOf("youtube", "ctv", "rss")
        private const val MAX_ORPHANS = 12
        private const val STALE_MS = 3 * 60_000L

        /**
         * Datum dílu → řaditelný `yyyyMMdd`. Zdroje ho posílají různě: YouTube `20261012`, ČT ISO
         * `2026-10-12T…`, RSS RFC 822 `Mon, 12 Oct 2026 …`. Neznámý tvar = "" (na konec).
         */
        fun dateSortKey(date: String?): String {
            val d = date?.trim().orEmpty()
            if (d.isEmpty()) return ""
            if (Regex("""^\d{8}$""").matches(d)) return d
            Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(d)?.let { m -> return m.groupValues.drop(1).joinToString("") }
            val rfc = Regex("""(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})""").find(d) ?: return ""
            val month = MONTHS.indexOf(rfc.groupValues[2].lowercase()) + 1
            if (month <= 0) return ""
            return "%s%02d%02d".format(rfc.groupValues[3], month, rfc.groupValues[1].toInt())
        }

        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    }
}
