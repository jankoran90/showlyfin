package com.github.jankoran90.showlyfin.feature.listen

import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping

/**
 * Co smí dětský profil otevřít z vlastních zdrojů (user 2026-10-12: z přehrávače Ďábelských kár se
 * dítě proklikalo na CELÉ Na výbornou). Whitelist `visibleForKidsSourceKeys`: celý zdroj `type:ref`,
 * jen série `type:ref#series:slug|Titulek`, nebo (Rodičovská správa 2026-11-19) jen jeden díl
 * `type:ref#ep:idDílu|Název` (ABS podcast `abs:itemId#ep:epId|Název`). Veřejné kvůli navigaci shellu
 * (`:ui-slovo-phone`).
 */
object KidsSourceAccess {
    data class Series(val slug: String, val title: String)

    /** Jeden schválený díl: [episodeId] = RSS guid / ČT idec / YouTube id / ABS id epizody. */
    data class Episode(val sourceKey: String, val episodeId: String, val title: String)

    private const val EP_MARKER = "#ep:"

    /** null = [key] není klíč dílu. Shodně se serverem: zdroj = vše před `#ep:`, id do první `|`. */
    fun parseEpisodeKey(key: String): Episode? {
        val idx = key.indexOf(EP_MARKER)
        if (idx <= 0) return null
        val rest = key.substring(idx + EP_MARKER.length)
        val id = rest.substringBefore('|')
        if (id.isBlank()) return null
        return Episode(key.substring(0, idx), id, rest.substringAfter('|', ""))
    }

    /** Klíč zdroje z klíče pořadu / série / dílu. */
    fun sourceOf(key: String): String =
        parseEpisodeKey(key)?.sourceKey ?: PodcastEpisodeSeriesGrouping.parseSeriesKey(key)?.sourceKey ?: key

    fun isWholeSourceApproved(kidsKeys: Set<String>, sourceKey: String): Boolean = sourceKey in kidsKeys

    /** Schválené série zdroje [sourceKey] (prázdné = žádná). */
    fun approvedSeries(kidsKeys: Set<String>, sourceKey: String): List<Series> =
        kidsKeys.mapNotNull { PodcastEpisodeSeriesGrouping.parseSeriesKey(it) }
            .filter { it.sourceKey == sourceKey }
            .map { Series(it.slug, it.title) }

    fun isSeriesApproved(kidsKeys: Set<String>, sourceKey: String, slug: String): Boolean =
        approvedSeries(kidsKeys, sourceKey).any { it.slug == slug }

    /** Všechny jednotlivě schválené díly (napříč zdroji). */
    fun approvedEpisodes(kidsKeys: Set<String>): List<Episode> = kidsKeys.mapNotNull(::parseEpisodeKey)

    fun isEpisodeApproved(kidsKeys: Set<String>, sourceKey: String, episodeId: String): Boolean =
        approvedEpisodes(kidsKeys).any { it.sourceKey == sourceKey && it.episodeId == episodeId }

    /**
     * Id dílu ze klíče staženého / přehrávaného dílu (`rss:<guid>`, `yt:<id>`, `ctv:<idec>`; viz
     * `PodcastSourcesRepository.loadEpisodes` resumeKey). Prefix nemá dvojtečku → vše za první.
     */
    fun episodeIdOfResumeKey(resumeKey: String): String = resumeKey.substringAfter(':')

    /** Klíč přehrávače / stažení dílu vlastního zdroje (opak [episodeIdOfResumeKey]). */
    fun resumeKeyFor(sourceKey: String, episodeId: String): String = when (sourceKey.substringBefore(':')) {
        "youtube" -> "yt:$episodeId"
        "ctv" -> "ctv:$episodeId"
        else -> "rss:$episodeId"
    }
}
