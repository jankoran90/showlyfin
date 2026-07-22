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
 * Root = CRUISE (SHW-70) přesně 4 záložky (AA strop ~4, bez overflow „víc"): **Pokračovat | Podcasty
 * | <audioknihovny jako vlastní záložky, label Dětské/Dospělý>**. Kapitoly (jen při hrané knize
 * s kapitolami) jdou ÚPLNĚ NAKONEC — kdyby přetekly přes 4, schová se nepodstatná Kapitoly, ne hlavní 4.
 */
internal suspend fun AudiobookPlayerService.rootChildren(): List<MediaItem> {
    val nodes = mutableListOf(
        browsableNode(AudiobookPlayerService.NODE_CONTINUE, "Pokračovat"),
        browsableNode(AudiobookPlayerService.NODE_PODCASTS, "Podcasty"),
    )
    // Každá audioknihovní knihovna = vlastní záložka (Dětské/Dospělý — oddělené knihovny, každá své obecenstvo).
    if (repo.isConfigured) {
        runCatching { repo.getAudiobookLibraries() }.getOrDefault(emptyList())
            .forEach { nodes += browsableNode("${AudiobookPlayerService.PREFIX_LIB}${it.id}", audiobookTabLabel(it.name)) }
    }
    if (currentPlayback?.chapters?.isNotEmpty() == true) nodes += browsableNode(AudiobookPlayerService.NODE_CHAPTERS, "Kapitoly")
    return nodes
}

/**
 * Sekce Podcasty → CRUISE (SHW-70): custom zdroje Poslechu (YouTube/RSS/NaVýbornou; premium pin nahoru,
 * pak abecedně) jako browsable `src:<id>` + ABS podcast knihovny (pokud je profil má). ABS-only profil
 * s jedinou knihovnou → expanduj rovnou (zachová původní chování); ABS-podcast kód NErušíme (in-app
 * Poslech ho dál používá pro profily s ABS podcast knihovnou).
 */
internal suspend fun AudiobookPlayerService.podcastSection(): List<MediaItem> {
    sourcesRepo.refresh()
    val custom = sourcesRepo.sources.value
        .sortedWith(
            compareByDescending<PodcastSource> { it.premium }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        .map(::sourceNode)
    val absLibs = if (repo.isConfigured) {
        runCatching { repo.getPodcastLibraries() }.getOrDefault(emptyList())
    } else emptyList()
    if (custom.isEmpty() && absLibs.size == 1) return libraryPodcasts(absLibs.first().id)
    // CRUISE: „Nejnovější epizody" (čerstvé napříč zdroji) jako PRVNÍ položka v Podcasty (5. věc, co se
    // nevejde jako 5. záložka nahoře → AA strop ~4). Jen když máme custom zdroje, ze kterých agregovat.
    val latest = if (custom.isNotEmpty()) listOf(browsableNode(AudiobookPlayerService.NODE_LATEST, "🆕 Nejnovější epizody")) else emptyList()
    return latest + custom + absLibs.map { browsableNode("${AudiobookPlayerService.PREFIX_PLIB}${it.id}", it.name) }
}

/** CRUISE: „Nejnovější epizody" napříč VŠEMI custom zdroji — round-robin nejnovějších (feedy jsou newest-first). */
internal suspend fun AudiobookPlayerService.latestEpisodes(): List<MediaItem> {
    sourcesRepo.refresh()
    val perSource = sourcesRepo.sources.value.map { it.id to sourcesRepo.loadEpisodes(it) }
    val result = mutableListOf<MediaItem>()
    var i = 0
    while (result.size < AudiobookPlayerService.LATEST_LIMIT) {
        var added = false
        for ((sid, eps) in perSource) {
            eps.getOrNull(i)?.let { result += directEpisodeItem(sid, it); added = true }
            if (result.size >= AudiobookPlayerService.LATEST_LIMIT) break
        }
        if (!added) break
        i++
    }
    return result
}

/** CRUISE: epizody custom zdroje jako přehratelné položky S URI (cache v itemCache → play je najde). */
internal suspend fun AudiobookPlayerService.sourceEpisodes(sourceId: String): List<MediaItem> {
    val src = sourcesRepo.sources.value.firstOrNull { it.id == sourceId } ?: return emptyList()
    return sourcesRepo.loadEpisodes(src).map { directEpisodeItem(sourceId, it) }
}

/**
 * CRUISE: CELÁ fronta epizod zdroje jako přehratelné direct položky (S URI). Při přehrávání z Android
 * Auto se tím epizoda nepouští samostatně, ale jako playlist → AA dostane skip ⏮⏭ (předchozí/další
 * EPIZODA, jedno ťuknutí — dlouhý stisk AA neumí) + auto-navázání další epizody; převíjení ±N vedle
 * Play zůstává (sdílený seek increment jako u audioknih). Funguje i pro cold resume (refresh+feed).
 */
internal suspend fun AudiobookPlayerService.directSourceEpisodes(sourceId: String): List<MediaItem> {
    val src = sourcesRepo.sources.value.firstOrNull { it.id == sourceId }
        ?: run { sourcesRepo.refresh(); sourcesRepo.sources.value.firstOrNull { it.id == sourceId } }
        ?: return emptyList()
    return sourcesRepo.loadEpisodes(src).map { directEpisodeItem(sourceId, it) }
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
            repo.getAudiobookLibraries()
                .flatMap { repo.getAudiobooks(it.id) }
                .filter { it.progress > 0.0 && !it.isFinished }
                .map { (it.lastUpdate ?: 0L) to bookItem(it) }
        }.getOrDefault(emptyList())
    } else emptyList()
    return (books + continueDirectEntries())
        .sortedByDescending { it.first }
        .map { it.second }
        .take(AudiobookPlayerService.CONTINUE_LIMIT)
}

/** CRUISE: rozposlouchané direct epizody → (čas posledního poslechu, položka), resolvnuté přes feedy zdrojů. */
internal suspend fun AudiobookPlayerService.continueDirectEntries(): List<Pair<Long, MediaItem>> {
    val marks = directResume.marks.value
    if (marks.isEmpty()) return emptyList()
    sourcesRepo.refresh()
    val byKey = HashMap<String, Pair<SourceEpisode, String>>()
    sourcesRepo.sources.value.forEach { src ->
        sourcesRepo.loadEpisodes(src).forEach { ep -> ep.resumeKey?.let { byKey[it] = ep to src.id } }
    }
    return marks.entries.mapNotNull { (key, mark) ->
        byKey[key]?.let { (ep, sid) -> mark.updatedAt to directEpisodeItem(sid, ep) }
    }
}

internal suspend fun AudiobookPlayerService.libraryBooks(libraryId: String): List<MediaItem> =
    repo.getAudiobooks(libraryId).map(::bookItem)

/** Podcasty v knihovně jako browsable uzly (klik → epizody). */
internal suspend fun AudiobookPlayerService.libraryPodcasts(libraryId: String): List<MediaItem> =
    repo.getPodcasts(libraryId).map(::podcastNode)

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
