package com.github.jankoran90.showlyfin.data.abs

import com.github.jankoran90.showlyfin.data.abs.api.AbsService
import com.github.jankoran90.showlyfin.data.abs.model.AbsDeviceInfo
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.abs.model.AbsLoginRequest
import com.github.jankoran90.showlyfin.data.abs.model.AbsMediaProgress
import com.github.jankoran90.showlyfin.data.abs.model.AbsMediaUpdate
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayRequest
import com.github.jankoran90.showlyfin.data.abs.model.AbsProgressUpdate
import com.github.jankoran90.showlyfin.data.abs.model.AbsSyncRequest
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.AudiobookDetail
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.AbsTrack
import com.github.jankoran90.showlyfin.data.abs.model.Chapter
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import com.github.jankoran90.showlyfin.data.abs.model.PodcastDetail
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vstupní bod pro poslechovou sekci. Drží uloženou ABS instanci + token, staví plná URL
 * a překládá syrové ABS responses na UI modely. Progress se mapuje z `/api/me`.
 */
@Singleton
class AbsRepository @Inject constructor(
    private val service: AbsService,
    private val prefs: AbsPreferences,
) {
    val isConfigured: Boolean get() = prefs.isConfigured
    val baseUrl: String get() = prefs.baseUrl

    /** Nastavení: skrývat dokončené podcast epizody. */
    var hideFinishedEpisodes: Boolean
        get() = prefs.hideFinishedEpisodes
        set(value) { prefs.hideFinishedEpisodes = value }

    private fun bearer(): String = "Bearer ${prefs.token}"
    private fun api(path: String): String = "${prefs.baseUrl}$path"

    fun coverUrl(itemId: String): String =
        "${prefs.baseUrl}/api/items/$itemId/cover?token=${prefs.token}"

    /** Přihlášení + uložení tokenu. Vrací Result se srozumitelnou chybou. */
    suspend fun login(url: String, username: String, password: String): Result<Unit> = runCatching {
        val base = url.trim().trimEnd('/')
        val resp = service.login("$base/login", AbsLoginRequest(username, password))
        val token = resp.user?.bearerToken
            ?: error("Server nevrátil token (zkontroluj jméno/heslo).")
        prefs.saveCredentials(base, username, password, token)
        Timber.i("[ABS] login OK jako '${resp.user?.username}' @ $base")
    }

    /** Audioknihovní knihovny (mediaType == book). */
    suspend fun getAudiobookLibraries(): List<AbsLibrary> =
        service.getLibraries(api("/api/libraries"), bearer())
            .libraries.filter { it.mediaType == "book" }

    /** Audioknihy v knihovně, obohacené o progress uživatele. */
    suspend fun getAudiobooks(libraryId: String): List<Audiobook> {
        val progressByItem: Map<String, AbsMediaProgress> = runCatching {
            service.getMe(api("/api/me"), bearer())
                .mediaProgress
                .filter { it.episodeId == null && it.libraryItemId != null }
                .associateBy { it.libraryItemId!! }
        }.getOrElse { emptyMap() }

        val url = api("/api/libraries/$libraryId/items") +
            "?limit=500&page=0&sort=media.metadata.title&desc=0&minified=1"
        return service.getLibraryItems(url, bearer()).results.map { item ->
            val m = item.media
            val md = m?.metadata
            val p = progressByItem[item.id]
            val dur = m?.duration ?: p?.duration ?: 0.0
            Audiobook(
                id = item.id,
                title = md?.title ?: "—",
                author = md?.authorName?.takeIf { it.isNotBlank() },
                narrator = md?.narratorName?.takeIf { it.isNotBlank() },
                seriesName = md?.seriesName?.takeIf { it.isNotBlank() },
                coverUrl = coverUrl(item.id),
                durationSec = dur,
                progress = p?.progress ?: 0.0,
                currentTimeSec = p?.currentTime ?: 0.0,
                isFinished = p?.isFinished ?: false,
            )
        }
    }

    /** Detail audioknihy + kapitoly + popis. */
    suspend fun getAudiobookDetail(itemId: String): AudiobookDetail {
        val item = service.getItem(api("/api/items/$itemId?expanded=1"), bearer())
        val m = item.media
        val md = m?.metadata
        val progress = runCatching {
            service.getMe(api("/api/me"), bearer())
                .mediaProgress.firstOrNull { it.libraryItemId == itemId }
        }.getOrNull()
        val book = Audiobook(
            id = item.id,
            title = md?.title ?: "—",
            author = md?.authorName?.takeIf { it.isNotBlank() },
            narrator = md?.narratorName?.takeIf { it.isNotBlank() },
            seriesName = md?.seriesName?.takeIf { it.isNotBlank() },
            coverUrl = coverUrl(item.id),
            durationSec = m?.duration ?: 0.0,
            progress = progress?.progress ?: 0.0,
            currentTimeSec = progress?.currentTime ?: 0.0,
            isFinished = progress?.isFinished ?: false,
        )
        val chapters = (m?.chapters ?: emptyList()).map {
            Chapter(it.id, it.title ?: "Kapitola ${it.id + 1}", it.start, it.end)
        }
        return AudiobookDetail(
            book = book,
            description = md?.description?.takeIf { it.isNotBlank() },
            publishedYear = md?.publishedYear,
            genres = md?.genres ?: emptyList(),
            chapters = chapters,
        )
    }

    // ──────────────────────────── Podcasty ────────────────────────────

    /** Podcastové knihovny (mediaType == podcast). */
    suspend fun getPodcastLibraries(): List<AbsLibrary> =
        service.getLibraries(api("/api/libraries"), bearer())
            .libraries.filter { it.mediaType == "podcast" }

    /** Podcasty v knihovně. numUnfinished = numEpisodes − dokončené epizody (odhad z /api/me). */
    suspend fun getPodcasts(libraryId: String): List<Podcast> {
        // dokončené epizody na podcast (libraryItemId): počítáme jen finished, ostatní bereme jako nepřehrané
        val finishedByItem: Map<String, Int> = runCatching {
            service.getMe(api("/api/me"), bearer())
                .mediaProgress
                .filter { it.episodeId != null && it.isFinished && it.libraryItemId != null }
                .groupingBy { it.libraryItemId!! }
                .eachCount()
        }.getOrElse { emptyMap() }

        val url = api("/api/libraries/$libraryId/items") +
            "?limit=500&page=0&sort=media.metadata.title&desc=0&minified=1"
        return service.getLibraryItems(url, bearer()).results.map { item ->
            val m = item.media
            val md = m?.metadata
            val total = m?.numEpisodes ?: m?.episodes?.size ?: 0
            val finished = finishedByItem[item.id] ?: 0
            Podcast(
                id = item.id,
                title = md?.title ?: "—",
                author = md?.authorDisplay,
                coverUrl = coverUrl(item.id),
                numEpisodes = total,
                numUnfinished = (total - finished).coerceAtLeast(0),
            )
        }
    }

    /** Detail podcastu + epizody (newest-first), obohacené o progress (klíč = episodeId). */
    suspend fun getPodcastDetail(itemId: String): PodcastDetail {
        val item = service.getItem(api("/api/items/$itemId?expanded=1"), bearer())
        val m = item.media
        val md = m?.metadata
        val progressByEpisode = runCatching {
            service.getMe(api("/api/me"), bearer())
                .mediaProgress
                .filter { it.episodeId != null && it.libraryItemId == itemId }
                .associateBy { it.episodeId!! }
        }.getOrElse { emptyMap() }

        val total = m?.numEpisodes ?: m?.episodes?.size ?: 0
        val finishedCount = progressByEpisode.values.count { it.isFinished }
        val podcast = Podcast(
            id = item.id,
            title = md?.title ?: "—",
            author = md?.authorDisplay,
            coverUrl = coverUrl(item.id),
            numEpisodes = total,
            numUnfinished = (total - finishedCount).coerceAtLeast(0),
        )

        val episodes = (m?.episodes ?: emptyList()).map { ep ->
            val p = progressByEpisode[ep.id]
            PodcastEpisode(
                id = ep.id,
                itemId = itemId,
                title = ep.title?.takeIf { it.isNotBlank() } ?: "Epizoda ${ep.index ?: ""}".trim(),
                subtitle = ep.subtitle?.takeIf { it.isNotBlank() },
                description = ep.description?.takeIf { it.isNotBlank() },
                publishedAt = ep.publishedAt,
                durationSec = ep.durationSec.takeIf { it > 0.0 } ?: p?.duration ?: 0.0,
                progress = p?.progress ?: 0.0,
                currentTimeSec = p?.currentTime ?: 0.0,
                isFinished = p?.isFinished ?: false,
            )
        }.sortedByDescending { it.publishedAt ?: 0L }

        return PodcastDetail(
            podcast = podcast,
            description = md?.description?.takeIf { it.isNotBlank() },
            episodes = episodes,
        )
    }

    /** Otevře play session epizody podcastu. Epizoda = jeden soubor (single track, bez kapitol). */
    suspend fun startEpisodePlayback(itemId: String, episodeId: String): AbsPlayback {
        val req = AbsPlayRequest(deviceInfo = AbsDeviceInfo(deviceId = prefs.deviceId))
        val s = service.play(api("/api/items/$itemId/play/$episodeId"), bearer(), req)
        val tracks = s.audioTracks
            .sortedBy { it.startOffset ?: it.index?.toDouble() ?: 0.0 }
            .mapIndexedNotNull { i, t ->
                val rel = t.contentUrl ?: return@mapIndexedNotNull null
                AbsTrack(
                    index = t.index ?: i,
                    url = "${prefs.baseUrl}$rel?token=${prefs.token}",
                    startOffsetSec = t.startOffset ?: 0.0,
                    durationSec = t.duration ?: 0.0,
                )
            }
        require(tracks.isNotEmpty()) { "Epizoda nemá audio stopu." }
        return AbsPlayback(
            sessionId = s.id,
            title = s.displayTitle ?: "",
            author = s.displayAuthor,
            coverUrl = coverUrl(itemId),
            tracks = tracks,
            startPositionSec = s.currentTime ?: 0.0,
            durationSec = s.duration ?: tracks.sumOf { it.durationSec },
            chapters = emptyList(),  // podcast epizoda = bez kapitol
        )
    }

    /** Stav ABS server auto-downloadu epizod podcastu (z media). */
    suspend fun getServerAutoDownload(itemId: String): Boolean =
        runCatching {
            service.getItem(api("/api/items/$itemId?expanded=1"), bearer()).media?.autoDownloadEpisodes ?: false
        }.getOrDefault(false)

    /** Zapne/vypne ABS server auto-download nových epizod podcastu (PATCH media). */
    suspend fun setServerAutoDownload(itemId: String, enabled: Boolean): Result<Unit> = runCatching {
        val resp = service.patchMedia(api("/api/items/$itemId/media"), bearer(), AbsMediaUpdate(enabled))
        require(resp.isSuccessful) { "HTTP ${resp.code()}" }
    }

    /** Označí epizodu jako přehranou/nepřehranou na serveru. */
    suspend fun setEpisodeFinished(itemId: String, episodeId: String, finished: Boolean) {
        runCatching {
            service.patchProgress(
                api("/api/me/progress/$itemId/$episodeId"), bearer(),
                AbsProgressUpdate(isFinished = finished),
            )
        }.onFailure { Timber.w(it, "[ABS] setEpisodeFinished selhal") }
    }

    /** Otevře ABS play session a vrátí streamovatelnou URL + uloženou pozici. */
    suspend fun startPlayback(itemId: String): AbsPlayback {
        val req = AbsPlayRequest(deviceInfo = AbsDeviceInfo(deviceId = prefs.deviceId))
        val s = service.play(api("/api/items/$itemId/play"), bearer(), req)
        // ABS audioknihy bývají rozdělené na víc souborů — bereme VŠECHNY a poskládáme za sebe.
        // currentTime, kapitoly i startOffset jsou v čase CELÉ knihy.
        val tracks = s.audioTracks
            .sortedBy { it.startOffset ?: it.index?.toDouble() ?: 0.0 }
            .mapIndexedNotNull { i, t ->
                val rel = t.contentUrl ?: return@mapIndexedNotNull null
                AbsTrack(
                    index = t.index ?: i,
                    url = "${prefs.baseUrl}$rel?token=${prefs.token}",
                    startOffsetSec = t.startOffset ?: 0.0,
                    durationSec = t.duration ?: 0.0,
                )
            }
        require(tracks.isNotEmpty()) { "Audiokniha nemá audio stopu." }
        return AbsPlayback(
            sessionId = s.id,
            title = s.displayTitle ?: "",
            author = s.displayAuthor,
            coverUrl = coverUrl(itemId),
            tracks = tracks,
            startPositionSec = s.currentTime ?: 0.0,
            durationSec = s.duration ?: tracks.sumOf { it.durationSec },
            chapters = s.chapters.map { Chapter(it.id, it.title ?: "Kapitola ${it.id + 1}", it.start, it.end) },
        )
    }

    /** Periodický sync pozice na server (drží Continue Listening). */
    suspend fun syncProgress(sessionId: String, currentTimeSec: Double, listenedSec: Double, durationSec: Double) {
        runCatching {
            service.syncProgress(
                api("/api/session/$sessionId/sync"), bearer(),
                AbsSyncRequest(currentTime = currentTimeSec, timeListening = listenedSec, duration = durationSec),
            )
        }.onFailure { Timber.w(it, "[ABS] sync selhal") }
    }

    /** Zavře session (na pauze/odchodu) — uloží finální pozici. */
    suspend fun closeSession(sessionId: String, currentTimeSec: Double, listenedSec: Double, durationSec: Double) {
        runCatching {
            service.closeSession(
                api("/api/session/$sessionId/close"), bearer(),
                AbsSyncRequest(currentTime = currentTimeSec, timeListening = listenedSec, duration = durationSec),
            )
        }.onFailure { Timber.w(it, "[ABS] close selhal") }
    }

    fun logout() = prefs.clear()
}
