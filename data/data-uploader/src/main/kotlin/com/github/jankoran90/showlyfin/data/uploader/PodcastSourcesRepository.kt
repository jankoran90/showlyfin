package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.RssFeed
import com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * PRESET (SHW-65): sdílený seznam ZDROJŮ Poslechu (YouTube kanály + RSS podcasty) uložený NA SERVERU
 * (přidám na jednom telefonu → vidí celá rodina). Jeden zdroj pravdy pro appku = [sources] StateFlow,
 * který reaktivně čtou [ListenViewModel] (render v sekci Podcasty) i AddSourceViewModel (po přidání/
 * odebrání rovnou aktualizuje flow → seznam se přepíše bez ručního refreshe).
 *
 * Zdroj pravdy je SERVER (ne lokální prefs) → držíme jen poslední fetch; baseUrl/cookie čteme ze
 * sdílených `traktPreferences` (klíče `uploader_*`, stejně jako TUNER YouTube cesta).
 */
@Singleton
class PodcastSourcesRepository @Inject constructor(
    private val remote: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private val _sources = MutableStateFlow<List<PodcastSource>>(emptyList())
    val sources = _sources.asStateFlow()

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && cookie.isNotBlank()

    /**
     * Znovu načte sdílený seznam ze serveru. GATE pouze na `baseUrl` — cookie smí být PRÁZDNÁ nebo
     * stará: listSources pak dostane 401 a [UploaderAuthInterceptor] se přihlásí uloženým heslem a
     * požadavek zopakuje. Dřív gate na `isConfigured` (vyžadoval i cookie) → na cold startu, kde
     * `ProfileConfigApplier.apply` ZAHODÍ uploader cookie (vynucení reloginu), se zdroje tiše
     * vyprázdnily a vůbec se neposlal request (interceptor neměl co zachytit) → zůstaly prázdné,
     * dokud user nepřepnul záložku. To byl bug „v této knihovně nejsou žádné podcasty" po cold startu.
     */
    suspend fun refresh() {
        if (baseUrl.isBlank()) { _sources.value = emptyList(); return }
        runCatching { remote.listSources(baseUrl, cookie) }
            .onSuccess { _sources.value = it.normalized(); Timber.i("[PRESET] zdrojů načteno: %d", it.size) }
            .onFailure { Timber.w(it, "[PRESET] načtení zdrojů selhalo") }
    }

    /** Přidá zdroj (sdíleně na server) a aktualizuje flow. Vrací true při úspěchu. */
    suspend fun add(type: String, ref: String, title: String, thumbnail: String?): Boolean {
        if (!isConfigured) return false
        return runCatching { remote.addSource(baseUrl, cookie, type, ref, title, thumbnail) }
            .onSuccess { _sources.value = it.normalized() }
            .onFailure { Timber.w(it, "[PRESET] přidání zdroje selhalo") }
            .isSuccess
    }

    /** Odebere zdroj ze sdíleného store a aktualizuje flow. */
    suspend fun remove(id: String): Boolean {
        if (!isConfigured) return false
        return runCatching { remote.removeSource(baseUrl, cookie, id) }
            .onSuccess { _sources.value = it.normalized() }
            .onFailure { Timber.w(it, "[PRESET] odebrání zdroje selhalo") }
            .isSuccess
    }

    /** Hledání zdroje podle názvu (YouTube + CZ podcasty). [type] = all|youtube|rss. */
    suspend fun search(query: String, type: String): List<SourceSearchResult> =
        remote.searchSources(baseUrl, cookie, query, type)
            .map { it.copy(thumbnail = it.thumbnail.httpsUrl()) }

    /**
     * AGORA: objevovací procházení zdrojů. [country] = cz|us|gb|au, [mode] = active|new|az|popular,
     * [category] = id kategorie (string) nebo null, [exclude] = id kategorií k vynechání.
     * Vrací obohacené karty (popis/počet epizod/kategorie); thumbnail normalizován jako v [search].
     */
    suspend fun browse(
        country: String, mode: String, category: String? = null, exclude: List<String>? = null,
        page: Int = 1, pageSize: Int = 30,
    ): List<SourceSearchResult> =
        remote.browseSources(baseUrl, cookie, country, mode, category, exclude, page, pageSize)
            .results.map { it.copy(thumbnail = it.thumbnail.httpsUrl()) }

    /** AGORA: kategorie podcastů pro danou zemi (CZ kategorie ≠ Apple žánr). */
    suspend fun categories(country: String): List<SourceCategory> =
        remote.getCategories(baseUrl, cookie, country).categories

    /**
     * AGORA (F5): dohledá VIDEO verzi audio epizody na YouTube. [query] = název podcastu + název epizody.
     * Vrací kandidáty (řadí backend dle relevance) — výběr nejlepšího řeší volající (VM dle délky/uploadera).
     */
    suspend fun findEpisodeVideo(query: String, limit: Int = 6): List<com.github.jankoran90.showlyfin.data.uploader.model.EpisodeVideo> {
        if (!isConfigured || query.isBlank()) return emptyList()
        return runCatching { remote.findEpisodeVideo(baseUrl, cookie, query, limit) }
            .onFailure { Timber.w(it, "[AGORA] hledání video verze epizody selhalo") }
            .getOrDefault(emptyList())
    }

    /** Přímá přehrávací URL YouTube videa přes backend byte-proxy (kind=video). */
    fun youtubeVideoUrl(videoId: String): String = remote.ytStreamUrl(baseUrl, cookie, videoId, "video")

    /** Epizody RSS podcastu (přímé audio enclosure URL). */
    suspend fun loadRss(feedUrl: String, limit: Int = 50): RssFeed =
        remote.getRssFeed(baseUrl, cookie, feedUrl, limit).let { feed ->
            feed.copy(
                image = feed.image.httpsUrl(),
                episodes = feed.episodes.map { it.copy(image = it.image.httpsUrl()) },
            )
        }

    /**
     * CRUISE (SHW-70): epizody libovolného zdroje (YouTube/RSS/NaVýbornou) jako přehratelné [SourceEpisode]
     * s PŘÍMOU stream URL — pro Android Auto browse strom (a sdílitelné jinde). Datová vrstva vlastní
     * stavbu URL: YouTube = proxy `/api/yt/stream?kind=audio`, RSS = přímá enclosure `audioUrl`.
     */
    suspend fun loadEpisodes(source: PodcastSource, limit: Int = 50): List<SourceEpisode> {
        // WEFT (SHW-75/W4): gate jen na baseUrl, NE na cookie. Na cold startu je cookie prázdná
        // (ProfileConfigApplier ji při aplikaci profilu čistí) → `!isConfigured` dřív tiše vracel []
        // → Timeline „Nepodařilo se načíst nové epizody" dokud user nepřepnul záložku. Request
        // s prázdnou/starou cookie → 401 → UploaderAuthInterceptor relogin heslem → retry 200.
        // Stejná oprava jako ROSTER-FIX provedl v refresh().
        if (baseUrl.isBlank()) return emptyList()
        return runCatching {
            when (source.type) {
                "youtube" -> remote.getYtFeed(baseUrl, cookie, source.ref, limit).entries.map { ep ->
                    SourceEpisode(
                        id = ep.id,
                        title = ep.title,
                        subtitle = source.title,
                        streamUrl = remote.ytStreamUrl(baseUrl, cookie, ep.id, "audio"),
                        imageUrl = (ep.thumbnail ?: source.thumbnail).httpsUrl(),
                        date = ep.uploadDate,
                        resumeKey = "yt:${ep.id}",   // shoda s YoutubeChannelViewModel.episodeKey
                        description = ep.description,
                        durationSec = ep.duration ?: 0.0,
                    )
                }
                else -> loadRss(source.ref, limit).let { feed ->
                    feed.episodes.map { ep ->
                        SourceEpisode(
                            id = ep.id,
                            title = ep.title,
                            subtitle = source.title,
                            streamUrl = ep.audioUrl,
                            imageUrl = ep.image ?: feed.image ?: source.thumbnail,
                            date = ep.date,
                            resumeKey = "rss:${ep.id}",   // shoda s RssPodcastViewModel.episodeKey
                            description = ep.description,
                            durationSec = parseDurationSec(ep.duration),
                        )
                    }
                }
            }
        }.onFailure { Timber.w(it, "[CRUISE] načtení epizod zdroje '%s' selhalo", source.title) }
            .getOrDefault(emptyList())
    }
}

/**
 * Protocol-relative `//host/...` → `https:`. yt3/RSS thumbnaily chodí často bez schématu, Coil je pak
 * nenačte (prázdný cover zdroje). Obranná normalizace — backend (uploader/antenna) už normalizuje, tohle
 * je pojistka pro stará/cachovaná data. PRESET FIX 2.
 */
private fun String?.httpsUrl(): String? =
    if (this != null && startsWith("//")) "https:$this" else this

private fun List<PodcastSource>.normalized(): List<PodcastSource> =
    map { it.copy(thumbnail = it.thumbnail.httpsUrl()) }

/** itunes:duration → sekundy (RSS). Podporuje "HH:MM:SS", "MM:SS" i čisté sekundy. 0 = neznámé. */
private fun parseDurationSec(d: String?): Double {
    val t = d?.trim().orEmpty()
    if (t.isEmpty()) return 0.0
    if (":" in t) {
        val p = t.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (p.size) {
            3 -> (p[0] * 3600 + p[1] * 60 + p[2]).toDouble()
            2 -> (p[0] * 60 + p[1]).toDouble()
            else -> 0.0
        }
    }
    return t.toDoubleOrNull() ?: 0.0
}
