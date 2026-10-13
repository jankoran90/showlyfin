package com.github.jankoran90.showlyfin.feature.listen.ui

/**
 * Slovo 2026-10-13 (user „karta knihy na Domů — ať je vše stejné nehledě na typ zdroje") — rozposlouchaný
 * díl ze série se na Domů ukáže jako kniha: název knihy + „kapitola 2/13 · název dílu". Stejné pořadí
 * pravidel jako obrazovka pořadu: ruční kniha → ČT řada → číslovaný díl N/M (celý pořad) → prefix před dvojtečkou.
 */
internal data class HomeBookLabel(val bookTitle: String, val chapterLine: String)

internal fun homeBookLabel(
    episodeTitle: String,
    sourceTitle: String,
    seriesTitle: String?,
    ruleTitle: String?,
): HomeBookLabel? {
    val numbered = PodcastEpisodeSeriesGrouping.numberedPart(episodeTitle)
    val book = ruleTitle
        ?: seriesTitle
        ?: numbered?.let { sourceTitle }
        ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(episodeTitle)
        ?: return null
    val partNo = Regex("(\\d+)\\.\\s*(?:část|díl)", RegexOption.IGNORE_CASE).find(episodeTitle)?.groupValues?.get(1)
    val chapter = when {
        numbered != null -> "kapitola ${numbered.first}/${numbered.second}"
        partNo != null -> "kapitola $partNo"
        else -> "kapitola"
    }
    // Název dílu bez opakování knihy / čísla („3/13 Západní Saharou…" → „Západní Saharou…").
    val rest = episodeTitle
        .replace(Regex("^\\s*\\d{1,3}\\s*/\\s*\\d{1,3}\\s*"), "")
        .let { t -> t.substringAfter(':', t).trim() }
        .ifBlank { episodeTitle }
    return HomeBookLabel(book, "$chapter · $rest")
}
