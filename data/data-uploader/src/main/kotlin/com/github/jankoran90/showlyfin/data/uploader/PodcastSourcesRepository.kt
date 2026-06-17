package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.RssFeed
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

    /** Znovu načte sdílený seznam ze serveru. Bez přihlášení k uploaderu → prázdný (žádná chyba). */
    suspend fun refresh() {
        if (!isConfigured) { _sources.value = emptyList(); return }
        runCatching { remote.listSources(baseUrl, cookie) }
            .onSuccess { _sources.value = it.normalized() }
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
        if (!isConfigured) return emptyList()
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
