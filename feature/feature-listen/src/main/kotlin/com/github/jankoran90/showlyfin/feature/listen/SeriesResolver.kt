package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping

/**
 * Slovo 2026-10-13 (user „Po jedné stopě se dětem na Domů neukáže, Ďábelské káry ano") — JEDNO pravidlo,
 * do které série díl patří, pro místa mimo obrazovku pořadu (Domů, dětské filtry, stažené):
 * ruční kniha → ČT řada → u ČT číslovaný díl N/M = celý pořad → prefix před dvojtečkou.
 * Stejné pořadí jako obrazovky pořadu (ctvSeriesTitleOf / detectSeriesTitle).
 */
object SeriesResolver {
    fun seriesTitle(
        rules: SeriesRuleStore?,
        sourceKey: String,
        sourceTitle: String?,
        episodeTitle: String,
        seriesTitle: String?,
    ): String? =
        rules?.titleFor(sourceKey, episodeTitle)
            ?: seriesTitle?.takeIf { it.isNotBlank() }
            ?: PodcastEpisodeSeriesGrouping.numberedPart(episodeTitle)?.takeIf { sourceKey.startsWith("ctv:") }?.let { sourceTitle }
            ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(episodeTitle)

    fun seriesSlug(
        rules: SeriesRuleStore?,
        sourceKey: String,
        sourceTitle: String?,
        episodeTitle: String,
        seriesTitle: String?,
    ): String? = seriesTitle(rules, sourceKey, sourceTitle, episodeTitle, seriesTitle)?.let(PodcastEpisodeSeriesGrouping::seriesSlug)
}
