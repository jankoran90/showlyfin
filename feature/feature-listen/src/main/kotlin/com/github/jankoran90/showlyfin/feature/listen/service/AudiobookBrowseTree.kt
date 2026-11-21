package com.github.jankoran90.showlyfin.feature.listen.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode

// Android Auto browse strom — vyříznuto z AudiobookPlayerService (anti-monolit). Extension funkce nad
// Service (přístup k repo/sourcesRepo/directResume + internal itemCache/currentPlayback). Buildery
// položek jsou v AudiobookAutoMedia.kt (top-level internal, stejný balíček).

/**
 * Root = karty nahoře (AMPHORA F5 / IN2, Nastavení › Android Auto): výchozí **Pokračovat | Nové díly |
 * Podcasty | Knihy**, volitelně Záložky a knihovny jako samostatné karty. AA ukáže nejvýš ~4 (bez „víc“),
 * proto se bere [AutoTabsPrefs.MAX_TABS] prvních. Kapitoly (jen při hrané knize s kapitolami) jdou ÚPLNĚ
 * NAKONEC — kdyby přetekly, schová se nepodstatná Kapitoly, ne hlavní karty.
 */
internal suspend fun AudiobookPlayerService.rootChildren(): List<MediaItem> {
    val nodes = mutableListOf<MediaItem>()
    for (tab in autoTabs.tabs) when (tab) {
        AutoTabsPrefs.Tab.CONTINUE -> nodes += browsableNode(AudiobookPlayerService.NODE_CONTINUE, tab.label)
        AutoTabsPrefs.Tab.NEW -> nodes += browsableNode(AudiobookPlayerService.NODE_LATEST, tab.label)
        AutoTabsPrefs.Tab.PODCASTS -> nodes += browsableNode(AudiobookPlayerService.NODE_PODCASTS, tab.label)
        AutoTabsPrefs.Tab.BOOKMARKS -> nodes += browsableNode(AudiobookPlayerService.NODE_BOOKMARKS, tab.label)
        AutoTabsPrefs.Tab.BOOKS -> if (repo.isConfigured) {
            val libs = runCatching { repo.getAudiobookLibraries() }.getOrDefault(emptyList()).let(::visibleLibraries)
            when {
                libs.isEmpty() -> Unit
                // Každá knihovna = vlastní karta (Dětské/Dospělý — oddělené knihovny, každá své obecenstvo).
                autoTabs.librariesAsTabs ->
                    libs.forEach { nodes += browsableNode("${AudiobookPlayerService.PREFIX_LIB}${it.id}", audiobookTabLabel(it.name)) }
                else -> nodes += browsableNode(AudiobookPlayerService.NODE_BOOKS, tab.label)
            }
        }
    }
    val top = nodes.take(AutoTabsPrefs.MAX_TABS).toMutableList()
    if (currentPlayback?.chapters?.isNotEmpty() == true) top += browsableNode(AudiobookPlayerService.NODE_CHAPTERS, "Kapitoly")
    return top
}

/** Karta Knihy: jedna knihovna → rovnou knihy, víc → složka na každou knihovnu (Dětské, Dospělý). */
internal suspend fun AudiobookPlayerService.booksSection(): List<MediaItem> {
    if (!repo.isConfigured) return emptyList()
    val libs = runCatching { repo.getAudiobookLibraries() }.getOrDefault(emptyList()).let(::visibleLibraries)
    if (libs.size == 1) return libraryBooks(libs.first().id)
    return libs.map { browsableNode("${AudiobookPlayerService.PREFIX_LIB}${it.id}", audiobookTabLabel(it.name)) }
}

/**
 * Sekce Podcasty → CRUISE (SHW-70): custom zdroje Poslechu (YouTube/RSS/NaVýbornou; premium pin nahoru,
 * pak abecedně) jako browsable `src:<id>` + ABS podcast knihovny (pokud je profil má). ABS-only profil
 * s jedinou knihovnou → expanduj rovnou (zachová původní chování); ABS-podcast kód NErušíme (in-app
 * Poslech ho dál používá pro profily s ABS podcast knihovnou).
 */
internal suspend fun AudiobookPlayerService.podcastSection(): List<MediaItem> {
    sourcesRepo.refresh()
    val custom = visibleSources()
        .sortedWith(
            compareByDescending<PodcastSource> { it.premium }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        .map(::sourceNode)
    val absLibs = if (repo.isConfigured) {
        runCatching { repo.getPodcastLibraries() }.getOrDefault(emptyList()).let(::visibleLibraries)
    } else emptyList()
    if (custom.isEmpty() && absLibs.size == 1) return libraryPodcasts(absLibs.first().id)
    // CRUISE: „Nejnovější epizody" (čerstvé napříč zdroji) jako PRVNÍ položka v Podcasty (5. věc, co se
    // nevejde jako 5. záložka nahoře → AA strop ~4). Jen když máme custom zdroje, ze kterých agregovat.
    val latest = if (custom.isNotEmpty() && AutoTabsPrefs.Tab.NEW !in autoTabs.tabs) listOf(browsableNode(AudiobookPlayerService.NODE_LATEST, "🆕 Nejnovější epizody")) else emptyList()
    return latest + custom + absLibs.map { browsableNode("${AudiobookPlayerService.PREFIX_PLIB}${it.id}", it.name) }
}

/** CRUISE: „Nejnovější epizody" napříč VŠEMI custom zdroji — round-robin nejnovějších (feedy jsou newest-first). */
internal suspend fun AudiobookPlayerService.latestEpisodes(): List<MediaItem> {
    sourcesRepo.refresh()
    val perSource = visibleSources().map { it to sourcesRepo.loadEpisodes(it) }
    val result = mutableListOf<MediaItem>()
    var i = 0
    while (result.size < AudiobookPlayerService.LATEST_LIMIT) {
        var added = false
        for ((src, eps) in perSource) {
            eps.getOrNull(i)?.let { result += directEpisodeItem(src.id, it, "show:${src.ref}"); added = true }
            if (result.size >= AudiobookPlayerService.LATEST_LIMIT) break
        }
        if (!added) break
        i++
    }
    return result
}

/** CRUISE: epizody custom zdroje jako přehratelné položky S URI (cache v itemCache → play je najde). */
internal suspend fun AudiobookPlayerService.sourceEpisodes(sourceId: String): List<MediaItem> {
    val src = visibleSources().firstOrNull { it.id == sourceId } ?: return emptyList()
    return sourcesRepo.loadEpisodes(src).map { directEpisodeItem(sourceId, it, "show:${src.ref}") }
}

/**
 * CRUISE: CELÁ fronta epizod zdroje jako přehratelné direct položky (S URI). Při přehrávání z Android
 * Auto se tím epizoda nepouští samostatně, ale jako playlist → AA dostane skip ⏮⏭ (předchozí/další
 * EPIZODA, jedno ťuknutí — dlouhý stisk AA neumí) + auto-navázání další epizody; převíjení ±N vedle
 * Play zůstává (sdílený seek increment jako u audioknih). Funguje i pro cold resume (refresh+feed).
 */
internal suspend fun AudiobookPlayerService.directSourceEpisodes(sourceId: String): List<MediaItem> {
    val src = visibleSources().firstOrNull { it.id == sourceId }
        ?: run { sourcesRepo.refresh(); visibleSources().firstOrNull { it.id == sourceId } }
        ?: return emptyList()
    return sourcesRepo.loadEpisodes(src).map { directEpisodeItem(sourceId, it, "show:${src.ref}") }
        .also { items -> items.forEach { itemCache[it.mediaId] = it } }
}

/**
 * „Pokračovat" = VŠECHNO rozposlouchané dohromady (audioknihy + direct epizody RSS/YT/NaVýbornou),
 * seřazené dle POSLEDNÍHO POSLECHU (nejnovější nahoře → starší). Čas: audiokniha `lastUpdate` (ABS
 * mediaProgress), direct epizoda `updatedAt` (DirectResumeStore). Dřív: jen audioknihy řazené dle pozice.
 */
internal suspend fun AudiobookPlayerService.continueItems(): List<MediaItem> {
    val books: List<Pair<Long, MediaItem>> = if (repo.isConfigured) {
        runCatching {
            audiobookOwnership.refresh()
            visibleBooks(visibleLibraries(repo.getAudiobookLibraries()).flatMap { repo.getAudiobooks(it.id) })
                .filter { it.progress > 0.0 && !it.isFinished }
                .map { (it.lastUpdate ?: 0L) to bookItem(it) }
        }.getOrDefault(emptyList())
    } else emptyList()
    // AMPHORA F5: záložky na začátek, když nejsou samostatná karta.
    return bookmarksEntry() + (books + continueDirectEntries())
        .sortedByDescending { it.first }
        .map { it.second }
        .take(AudiobookPlayerService.CONTINUE_LIMIT)
}

/** CRUISE: rozposlouchané direct epizody → (čas posledního poslechu, položka), resolvnuté přes feedy zdrojů. */
internal suspend fun AudiobookPlayerService.continueDirectEntries(): List<Pair<Long, MediaItem>> {
    // User (2026-08-16) — dohrané epizody (isFinished) mark drží pro badge/reset, ale do „Pokračovat" nepatří.
    val marks = directResume.marks.value.filterValues { !it.isFinished }
    if (marks.isEmpty()) return emptyList()
    sourcesRepo.refresh()
    val byKey = HashMap<String, Pair<SourceEpisode, String>>()
    visibleSources().forEach { src ->
        sourcesRepo.loadEpisodes(src).forEach { ep -> ep.resumeKey?.let { byKey[it] = ep to src.id } }
    }
    return marks.entries.mapNotNull { (key, mark) ->
        byKey[key]?.let { (ep, sid) -> mark.updatedAt to directEpisodeItem(sid, ep) }
    }
}

internal suspend fun AudiobookPlayerService.libraryBooks(libraryId: String): List<MediaItem> {
    if (!isLibraryVisible(libraryId)) return emptyList()
    audiobookOwnership.refresh()
    return visibleBooks(repo.getAudiobooks(libraryId)).map(::bookItem)
}

/** Podcasty v knihovně jako browsable uzly (klik → epizody). */
internal suspend fun AudiobookPlayerService.libraryPodcasts(libraryId: String): List<MediaItem> =
    visiblePodcasts(repo.getPodcasts(libraryId)).map(::podcastNode)

/** Epizody podcastu jako playable položky (newest-first; respektuje skrývání přehraných). */
internal suspend fun AudiobookPlayerService.podcastEpisodes(podcastItemId: String): List<MediaItem> {
    val eps = repo.getPodcastDetail(podcastItemId).episodes
    val visible = if (repo.hideFinishedEpisodes) eps.filterNot { it.isFinished } else eps
    val cover = repo.coverUrl(podcastItemId)
    return visible.map { episodeItem(podcastItemId, it, cover) }
}

/** Kapitoly aktuální knihy jako playable položky (`chap:<index>`) — klik skočí na kapitolu. */
internal fun AudiobookPlayerService.chapterItems(): List<MediaItem> {
    val pb = currentPlayback ?: return emptyList()
    val artwork = pb.coverUrl?.let(Uri::parse)
    return pb.chapters.mapIndexed { i, ch ->
        MediaItem.Builder()
            .setMediaId("${AudiobookPlayerService.PREFIX_CHAPTER}$i")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(ch.title)
                    .setArtist(pb.title)
                    .setArtworkUri(artwork)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                    .build(),
            )
            .build()
    }
}
