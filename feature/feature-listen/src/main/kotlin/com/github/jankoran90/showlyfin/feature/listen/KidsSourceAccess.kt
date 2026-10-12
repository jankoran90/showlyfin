package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping

/**
 * Co smí dětský profil otevřít z vlastních zdrojů (user 2026-10-12: z přehrávače Ďábelských kár se
 * dítě proklikalo na CELÉ Na výbornou). Whitelist `visibleForKidsSourceKeys`: celý zdroj `type:ref`,
 * nebo jen série `type:ref#series:slug|Titulek`. Veřejné kvůli navigaci shellu (`:ui-slovo-phone`).
 */
object KidsSourceAccess {
    data class Series(val slug: String, val title: String)

    fun isWholeSourceApproved(kidsKeys: Set<String>, sourceKey: String): Boolean = sourceKey in kidsKeys

    /** Schválené série zdroje [sourceKey] (prázdné = žádná). */
    fun approvedSeries(kidsKeys: Set<String>, sourceKey: String): List<Series> =
        kidsKeys.mapNotNull { PodcastEpisodeSeriesGrouping.parseSeriesKey(it) }
            .filter { it.sourceKey == sourceKey }
            .map { Series(it.slug, it.title) }

    fun isSeriesApproved(kidsKeys: Set<String>, sourceKey: String, slug: String): Boolean =
        approvedSeries(kidsKeys, sourceKey).any { it.slug == slug }
}
