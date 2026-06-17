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
import com.github.jankoran90.showlyfin.data.abs.model.AbsAudioTrack
import com.github.jankoran90.showlyfin.data.abs.model.AbsTrack
import com.github.jankoran90.showlyfin.data.abs.model.AbsPodcastFeedRequest
import com.github.jankoran90.showlyfin.data.abs.model.Chapter
import com.github.jankoran90.showlyfin.data.abs.model.FeedEpisode
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import com.github.jankoran90.showlyfin.data.abs.model.PodcastDetail
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import com.github.jankoran90.showlyfin.data.abs.model.PodcastServerAutoDownload
import com.google.gson.JsonObject
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
        val base = com.github.jankoran90.showlyfin.core.domain.normalizeServerUrl(url)
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
                lastUpdate = p?.lastUpdate,
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
        val parsedChapters = (m?.chapters ?: emptyList()).map {
            Chapter(it.id, it.title ?: "Kapitola ${it.id + 1}", it.start, it.end)
        }
        // Fallback: knihy bez embedded kapitol, ale rozdělené na víc souborů (např. HP Kámen mudrců /
        // Tajemná komnata) → každý soubor = kapitola, ať jde navigovat i zobrazit název části.
        val chapters = parsedChapters.ifEmpty { chaptersFromTracks(m?.tracks ?: emptyList()) }
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
            val title = ep.title?.takeIf { it.isNotBlank() } ?: "Epizoda ${ep.index ?: ""}".trim()
            val subtitle = ep.subtitle?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
            val description = ep.description?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
            PodcastEpisode(
                id = ep.id,
                itemId = itemId,
                title = title,
                subtitle = subtitle,
                description = description,
                publishedAt = ep.publishedAt,
                durationSec = ep.durationSec.takeIf { it > 0.0 } ?: p?.duration ?: 0.0,
                progress = p?.progress ?: 0.0,
                currentTimeSec = p?.currentTime ?: 0.0,
                isFinished = p?.isFinished ?: false,
                guest = extractGuest(title, subtitle, description),
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

    /**
     * Seznam VŠECH podcastů (napříč podcast knihovnami) i s aktuálním stavem ABS server
     * auto-downloadu — pro správu v Nastavení. Bez `minified`, aby `media.autoDownloadEpisodes` přišlo.
     */
    suspend fun getPodcastsWithServerAutoDownload(): List<PodcastServerAutoDownload> {
        val libs = getPodcastLibraries()
        return libs.flatMap { lib ->
            val url = api("/api/libraries/${lib.id}/items") + "?limit=500&page=0&sort=media.metadata.title&desc=0"
            runCatching { service.getLibraryItems(url, bearer()).results }.getOrElse { emptyList() }
                .map { item ->
                    PodcastServerAutoDownload(
                        itemId = item.id,
                        title = item.media?.metadata?.title ?: "—",
                        coverUrl = coverUrl(item.id),
                        autoDownload = item.media?.autoDownloadEpisodes ?: false,
                    )
                }
        }.sortedBy { it.title.lowercase() }
    }

    /**
     * Dostupné epizody z RSS feedu podcastu (ABS naparsuje feed). Newest-first.
     * `feedUrl` se bere z metadat položky; POST /api/podcasts/feed {rssFeed}.
     */
    suspend fun getNewServerEpisodes(itemId: String): List<FeedEpisode> {
        val item = service.getItem(api("/api/items/$itemId?expanded=1"), bearer())
        val feedUrl = item.media?.metadata?.feedUrl?.takeIf { it.isNotBlank() }
            ?: error("Podcast nemá RSS feed.")
        // Enclosure URL epizod, které server už má (pro volitelný filtr „skrýt už stažené").
        val existingUrls: Set<String> = item.media?.episodes
            ?.mapNotNull { it.enclosure?.url?.takeIf { u -> u.isNotBlank() } }
            ?.toSet().orEmpty()
        val hideDownloaded = prefs.rssHideDownloaded

        val resp = service.getPodcastFeed(api("/api/podcasts/feed"), bearer(), AbsPodcastFeedRequest(feedUrl))
        val episodes = resp.podcast?.episodes ?: emptyList()
        Timber.i("[ABS] feed $itemId ($feedUrl) → ${episodes.size} epizod (server má ${existingUrls.size}, hideDownloaded=$hideDownloaded)")
        return episodes.mapIndexedNotNull { i, obj ->
            runCatching {
                val title = obj.stringOrNull("title") ?: obj.stringOrNull("episode") ?: "Epizoda ${i + 1}"
                val published = obj.longOrNull("publishedAt") ?: parsePubDate(obj.stringOrNull("pubDate"))
                val subtitle = obj.stringOrNull("subtitle")?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
                val description = obj.stringOrNull("description")?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
                    ?: subtitle
                val id = obj.enclosureUrl() ?: obj.stringOrNull("guid") ?: "feed_$i"
                FeedEpisode(
                    id = id,
                    title = title,
                    publishedAt = published,
                    description = description,
                    durationSec = obj.doubleOrNull("duration"),
                    guest = extractGuest(title, subtitle, description),
                    raw = obj,
                )
            }.getOrNull()
        }
            .filterNot { hideDownloaded && it.id in existingUrls }
            .sortedByDescending { it.publishedAt ?: 0L }
    }

    /** Zařadí vybrané feed epizody ke stažení na ABS server (POST download-episodes, tělo = holé pole). */
    suspend fun downloadEpisodesToServer(itemId: String, episodes: List<FeedEpisode>): Result<Unit> = runCatching {
        require(episodes.isNotEmpty()) { "Nevybrána žádná epizoda." }
        val resp = service.downloadEpisodesToServer(
            api("/api/podcasts/$itemId/download-episodes"), bearer(),
            episodes.map { it.raw },
        )
        require(resp.isSuccessful) { "HTTP ${resp.code()}" }
        Timber.i("[ABS] download-episodes na server: ${episodes.size} epizod zařazeno (item $itemId)")
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
        val parsedChapters = s.chapters.map { Chapter(it.id, it.title ?: "Kapitola ${it.id + 1}", it.start, it.end) }
        // Fallback jako v detailu: bez embedded kapitol → každý soubor = kapitola (název části + navigace).
        val chapters = parsedChapters.ifEmpty { chaptersFromTracks(s.audioTracks) }
        return AbsPlayback(
            sessionId = s.id,
            title = s.displayTitle ?: "",
            author = s.displayAuthor,
            coverUrl = coverUrl(itemId),
            tracks = tracks,
            startPositionSec = s.currentTime ?: 0.0,
            durationSec = s.duration ?: tracks.sumOf { it.durationSec },
            chapters = chapters,
        )
    }

    /**
     * Syntetizuje kapitoly ze seznamu audio souborů — pro knihy BEZ embedded kapitol rozdělené na víc
     * souborů. Každý soubor = kapitola (název = title souboru, jinak „Část N"). Jeden soubor → prázdné
     * (jedna „kapitola" = celá kniha nedává smysl).
     */
    private fun chaptersFromTracks(tracks: List<AbsAudioTrack>): List<Chapter> {
        if (tracks.size < 2) return emptyList()
        return tracks
            .sortedBy { it.startOffset ?: it.index?.toDouble() ?: 0.0 }
            .mapIndexed { i, t ->
                val start = t.startOffset ?: 0.0
                Chapter(
                    index = t.index ?: i,
                    title = t.title?.takeIf { it.isNotBlank() } ?: "Část ${i + 1}",
                    startSec = start,
                    endSec = start + (t.duration ?: 0.0),
                )
            }
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

// ──────────────── Pomocné parsování feed epizod (JSON passthrough) ────────────────

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.runCatching { asString }?.getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.longOrNull(key: String): Long? =
    get(key)?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()

private fun JsonObject.doubleOrNull(key: String): Double? =
    get(key)?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()?.takeIf { it > 0.0 }

/** URL z enclosure objektu feed epizody (klíč pro deduplikaci/výběr). */
private fun JsonObject.enclosureUrl(): String? =
    get("enclosure")?.takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull("url")

/** RFC-822 pubDate → ms (best-effort, jinak null). */
private fun parsePubDate(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
    return runCatching { fmt.parse(s)?.time }.getOrNull()
}

// Jméno = 2–3 slova s velkým počátečním písmenem (čeština + diakritika), volitelně s prostřední spojkou.
private const val NAME = "\\p{Lu}[\\p{L}.'-]+(?:\\s+(?:[a-záčďéěíňóřšťúůýž]+\\s+)?\\p{Lu}[\\p{L}.'-]+){1,2}"

// Dvě po sobě jdoucí slova s velkým písmenem = jádro jména (detekce uvnitř delšího leadu).
private val NAME_CORE = Regex("\\p{Lu}[\\p{Ll}.'-]+\\s+\\p{Lu}[\\p{Ll}.'-]+")

// Klíčové fráze (CZ), po kterých následuje [profese] jméno hosta. Profese = volitelné malé slovo.
// POZOR: jen spouštěcí fráze jsou case-insensitive `(?i:…)`; jádro jména (NAME / \p{Lu}) MUSÍ
// zůstat case-sensitive, jinak se velká/malá písmena přestanou rozlišovat (chytalo „studia").
private val GUEST_REGEXES: List<Regex> = listOf(
    "(?i:hostem je|dnešním hostem(?: je)?|naším hostem je|host(?:em)?:)\\s+((?:\\p{Ll}+\\s+)?$NAME)",
    "(?i:rozhovor s|povídáme si s|povídám si s|mluvíme s|bavíme se s|zpovídáme|ptáme se|na návštěvě u|setkání s|s hostem)\\s+((?:\\p{Ll}+\\s+)?$NAME)",
    "(?i:říká|dodává|uvádí|míní|vypráví|popisuje|vzpomíná|svěřil[a]?|tvrdí)\\s+((?:\\p{Ll}+\\s+)?$NAME)",
).map { Regex(it) }

/** Lead vypadá jako (profese +) jméno: rozumná délka, obsahuje jádro jména, není to celá věta. */
private fun looksLikePersonLead(s: String): Boolean =
    s.length in 3..50 && !s.endsWith('.') && !s.contains('?') && NAME_CORE.containsMatchIn(s)

/**
 * Best-effort vyparsování hosta (vč. profese) z metadat epizody — CZ interview podcasty.
 * Priorita: (1) Vizitka-styl „Pořad: Profese Jméno: headline" = prostřední segment;
 * (2) krátký subtitle jako jméno; (3) klíčové fráze („…, říká režisérka Jana Nováková").
 * Když nic nesedí → null (UI zobrazí jen popis normálně).
 */
private fun extractGuest(title: String?, subtitle: String?, description: String?): String? {
    // 1) Titulek s dvojtečkou: vezmi první z prvních DVOU segmentů, který vypadá jako jméno.
    //    Pokrývá „Jméno: headline" (Boomer Talk, segment 0) i „Pořad: Profese Jméno: headline"
    //    (Vizitka, segment 1 — segment 0 „Vizitka" je jednoslovný, neprojde).
    title?.split(":")?.map { it.trim() }?.let { segs ->
        if (segs.size >= 2) segs.take(2).firstOrNull { looksLikePersonLead(it) }?.let { return it }
    }
    // 2) subtitle, který je sám o sobě jen jméno / profese+jméno
    subtitle?.trim()?.let { if (looksLikePersonLead(it)) return it }
    // 3) klíčové fráze napříč subtitle → title → description
    for (text in listOfNotNull(subtitle, title, description)) {
        for (re in GUEST_REGEXES) {
            re.find(text)?.groupValues?.getOrNull(1)?.trim()?.trim(',', '.', ';', ':', '–', '-')
                ?.takeIf { it.isNotBlank() && it.length <= 50 }?.let { return it }
        }
    }
    return null
}

/** Odstranění HTML tagů + dekódování základních entit z popisu epizody. */
private fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
