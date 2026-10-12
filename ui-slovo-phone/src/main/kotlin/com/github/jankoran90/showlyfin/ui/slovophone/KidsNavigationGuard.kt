package com.github.jankoran90.showlyfin.ui.slovophone

import com.github.jankoran90.showlyfin.feature.listen.KidsSourceAccess
import com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore

/**
 * Pojistka navigace dětského profilu (user 2026-10-12: z přehrávače Ďábelských kár se dítě
 * proklikalo na CELÉ Na výbornou). Každý push detailu jde přes tohle — ať se na zdroj klikne
 * odkudkoli (přehrávač, mini-přehrávač, Domů, Poslech): celý schválený zdroj projde, zdroj se
 * schválenou jen SÉRIÍ se přepíše na tu sérii, neschválené se neotevře (null).
 */
internal fun kidsGuard(
    entry: SlovoDetailEntry,
    kidsKeys: Set<String>,
    links: List<PodcastLinkStore.LinkGroup>,
): SlovoDetailEntry? = when (entry) {
    is SlovoDetailEntry.RssPodcast -> guardSource("rss:${entry.feedUrl}", entry.seriesFilter, kidsKeys, entry) { s ->
        entry.copy(seriesFilter = s.slug, title = s.title)
    }
    is SlovoDetailEntry.YoutubeChannel -> guardSource("youtube:${entry.handle}", entry.seriesFilter, kidsKeys, entry) { s ->
        entry.copy(seriesFilter = s.slug, title = s.title)
    }
    is SlovoDetailEntry.CtvProgram -> guardSource("ctv:${entry.ctvId}", entry.seriesFilter, kidsKeys, entry) { s ->
        entry.copy(seriesFilter = s.slug, title = s.title)
    }
    is SlovoDetailEntry.MergedPodcast -> {
        val members = links.firstOrNull { it.id == entry.groupId }?.members.orEmpty()
        if (members.any { KidsSourceAccess.isWholeSourceApproved(kidsKeys, it) }) {
            entry
        } else {
            // Sloučená obrazovka ukazuje celý pořad → dítě dostane jen schválenou sérii člena.
            members.firstNotNullOfOrNull { key ->
                KidsSourceAccess.approvedSeries(kidsKeys, key).firstOrNull()?.let { s ->
                    seriesEntry(key, s, entry.highlightEpisodeKey)
                }
            }
        }
    }
    is SlovoDetailEntry.PodcastDetail -> entry.takeIf { "abs:${it.itemId}" in kidsKeys }
    else -> entry
}

private fun guardSource(
    sourceKey: String,
    seriesFilter: String?,
    kidsKeys: Set<String>,
    entry: SlovoDetailEntry,
    toSeries: (KidsSourceAccess.Series) -> SlovoDetailEntry,
): SlovoDetailEntry? = when {
    seriesFilter != null ->
        entry.takeIf { KidsSourceAccess.isSeriesApproved(kidsKeys, sourceKey, seriesFilter) || sourceKey in kidsKeys }
    KidsSourceAccess.isWholeSourceApproved(kidsKeys, sourceKey) -> entry
    else -> KidsSourceAccess.approvedSeries(kidsKeys, sourceKey).firstOrNull()?.let(toSeries)
}

private fun seriesEntry(sourceKey: String, s: KidsSourceAccess.Series, highlight: String?): SlovoDetailEntry {
    val type = sourceKey.substringBefore(':')
    val ref = sourceKey.substringAfter(':')
    return when (type) {
        "youtube" -> SlovoDetailEntry.YoutubeChannel(ref, s.title, highlight, seriesFilter = s.slug)
        "ctv" -> SlovoDetailEntry.CtvProgram(ref, s.title, highlight, seriesFilter = s.slug)
        else -> SlovoDetailEntry.RssPodcast(ref, s.title, highlight, seriesFilter = s.slug)
    }
}
