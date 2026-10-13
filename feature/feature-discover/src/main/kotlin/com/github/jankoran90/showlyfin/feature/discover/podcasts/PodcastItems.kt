package com.github.jankoran90.showlyfin.feature.discover.podcasts

import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/*
 * AMFITEÁTR (FLM-05) — karty a řady sekce Podcasty sdílené TV i telefonem Filmů (stejná data, stejná
 * pravidla; každý shell si je jen jinak vykreslí).
 */

/** Klíč karty → co reprezentuje (karta nese jen string, akce se dohledá ve VM). */
sealed interface PodcastItemKey {
    data class Source(val sourceKey: String) : PodcastItemKey
    data class Episode(val sourceKey: String, val episodeId: String) : PodcastItemKey

    companion object {
        fun source(src: PodcastSource) = "src|${src.key}"
        fun episode(src: PodcastSource, ep: SourceEpisode) = "ep|${src.key}|${ep.id}"

        fun parse(key: String): PodcastItemKey? {
            val parts = key.split('|')
            return when {
                parts.size == 2 && parts[0] == "src" -> Source(parts[1])
                parts.size >= 3 && parts[0] == "ep" -> Episode(parts[1], parts.drop(2).joinToString("|"))
                else -> null
            }
        }
    }
}

fun episodeItem(
    src: PodcastSource,
    ep: SourceEpisode,
    mark: VideoResumeStore.Mark?,
    translated: Set<String> = emptySet(),
    translating: Set<String> = emptySet(),
    watchedKeys: Set<String> = emptySet(),
): HomeRowItem =
    HomeRowItem(
        key = PodcastItemKey.episode(src, ep),
        title = ep.title,
        // F2: „CZ" = hotový český překlad, „Překládá se" = běží právě teď (na serveru).
        subtitle = listOfNotNull(
            when ("yt:${ep.id}") { in translated -> "CZ"; in translating -> "Překládá se"; else -> null },
            src.title, formatDate(ep.date),
        ).joinToString(" · "),
        landscapeUrl = ep.imageUrl ?: src.thumbnail,
        posterUrl = ep.imageUrl ?: src.thumbnail,
        progressPct = mark?.takeIf { it.durMs > 0 }?.let { (it.posMs * 100 / it.durMs).toInt().coerceIn(1, 99) },
        watched = (ep.resumeKey ?: "yt:${ep.id}") in watchedKeys,
    )

private fun sourceItem(src: PodcastSource, eps: List<SourceEpisode>): HomeRowItem =
    HomeRowItem(
        key = PodcastItemKey.source(src),
        title = src.title,
        subtitle = sourceTypeLabel(src.type) + (if (eps.isNotEmpty()) " · ${eps.size} dílů" else ""),
        landscapeUrl = src.thumbnail ?: eps.firstOrNull()?.imageUrl,
        posterUrl = src.thumbnail ?: eps.firstOrNull()?.imageUrl,
    )

fun sourceTypeLabel(type: String): String = when (type) {
    "youtube" -> "YouTube"
    "ctv" -> "Česká televize"
    else -> "Video podcast"
}

/** `yyyyMMdd` → „12. 10. 2026" (neznámý tvar = nic). */
fun formatDate(date: String?): String? {
    val k = PodcastsViewModel.dateSortKey(date)
    if (k.length != 8) return null
    return "${k.substring(6, 8).trimStart('0')}. ${k.substring(4, 6).trimStart('0')}. ${k.substring(0, 4)}"
}



/** Řady sekce: Pokračovat (rozkoukané, nejčerstvější první), Nové díly (bez rozkoukaných), Kanály. */
data class PodcastShelves(
    val continueItems: List<HomeRowItem>,
    val newItems: List<HomeRowItem>,
    val channelItems: List<HomeRowItem>,
)

fun podcastShelves(
    state: PodcastsViewModel.UiState,
    marks: Map<String, VideoResumeStore.Mark>,
    translated: Set<String>,
    translating: Set<String>,
    rowPrefs: PodcastPrefs.Rows,
    watchedKeys: Set<String> = emptySet(),
): PodcastShelves {
    if (state.sources.isEmpty()) return PodcastShelves(emptyList(), emptyList(), emptyList())
    val all = state.sources.flatMap { src -> state.episodes[src.key].orEmpty().map { src to it } }
    val keyOf = { ep: SourceEpisode -> ep.resumeKey ?: ep.id }
    val inProgress = all
        .mapNotNull { (src, ep) -> marks[keyOf(ep)]?.takeIf { it.posMs > 0 }?.let { Triple(src, ep, it) } }
        .sortedByDescending { it.third.updatedAt }
    val inProgressKeys = inProgress.map { keyOf(it.second) }.toSet()
    val newest = all
        .filter { (_, ep) -> keyOf(ep) !in inProgressKeys }
        .sortedByDescending { (_, ep) -> PodcastsViewModel.dateSortKey(ep.date) }
        .take(rowPrefs.newLimit)
    val cont = if (rowPrefs.continueRow) inProgress.map { (src, ep, mark) -> episodeItem(src, ep, mark, translated, translating, watchedKeys) } else emptyList()
    val new = if (rowPrefs.newRow) newest.map { (src, ep) -> episodeItem(src, ep, marks[keyOf(ep)], translated, translating, watchedKeys) } else emptyList()
    // Kanály = jediná cesta k celému seznamu dílů → když ostatní řady nic neukazují, ukaž je i přes vypnutý přepínač.
    val channels = if (rowPrefs.channelsRow || (cont.isEmpty() && new.isEmpty()))
        state.sources.map { src -> sourceItem(src, state.episodes[src.key].orEmpty()) } else emptyList()
    return PodcastShelves(cont, new, channels)
}
