package com.github.jankoran90.showlyfin.data.offline

/**
 * Plan NOMAD (SHW-60) — offline stahování Sledování do telefonu.
 *
 * Datové modely sdílené stahovacím jádrem. Záměrně bez Roomu — stejně jako audioknihová
 * vrstva (EpisodeDownloadManager) držíme index v SharedPreferences (Gson). Manager nezná
 * zdroje obsahu: dostane resolvnutou [OfflineRequest] (URL + metadata) a stáhne ji.
 */

/** Stav položky ve frontě/stahování (UI badge + notifikace). */
enum class OfflineStatus { NONE, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }

/** Živý stav stahování jedné položky. */
data class OfflineState(
    val status: OfflineStatus = OfflineStatus.NONE,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
)

/**
 * Požadavek na stažení do telefonu. [videoUrl] je už resolvnutá přímá URL (JF static stream,
 * RD direct link, sdílej proxy…). [headers] = volitelné HTTP hlavičky (auth apod.).
 */
data class OfflineRequest(
    val key: String,
    val title: String,
    val subtitle: String? = null,      // řádek pod titulem (rok / „S1E2 · název")
    val type: String = TYPE_MOVIE,     // movie | episode
    val sourceLabel: String,           // Knihovna | Stremio | RealDebrid | sdílej.cz
    val videoUrl: String,
    val subtitleUrl: String? = null,   // .srt URL (CZ titulky), volitelné
    val posterUrl: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationSec: Double = 0.0,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_EPISODE = "episode"
    }
}

/** Stažený (dokončený) záznam v indexu = jeden řádek v sekci „Stažené". */
data class OfflineDownload(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val type: String = OfflineRequest.TYPE_MOVIE,
    val sourceLabel: String,
    val videoPath: String,
    val subtitlePath: String? = null,
    val posterUrl: String? = null,
    val posterPath: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val sizeBytes: Long = 0L,
    val durationSec: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val resumePositionMs: Long = 0L,
)
