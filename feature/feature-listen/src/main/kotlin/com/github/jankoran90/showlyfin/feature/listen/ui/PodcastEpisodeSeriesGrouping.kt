package com.github.jankoran90.showlyfin.feature.listen.ui

import com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SLOVO-KIDS-EPISODE (2026-08-15, user „šikovný skript", dynamicky/autonomně) — automatická detekce
 * SÉRIE uvnitř epizod JEDNOHO vlastního zdroje (RSS/YouTube) z názvu epizody, např. podcast „Na
 * Výbornou" vydává segmenty „Ďábelský káry: …", „Strašidelná noc: …", „Černá vysílačka: …",
 * „Nedokončená dobrodružství N. část: …" — každý název před dvojtečkou = kandidát na sérii. Čistě
 * heuristika nad texty, ŽÁDNÝ perzistovaný stav → nové epizody se samy zařadí, jakmile název sedí.
 */
internal object PodcastEpisodeSeriesGrouping {

    internal sealed interface EpisodeShelfItem<T> {
        data class Standalone<T>(val item: T) : EpisodeShelfItem<T>
        data class SeriesGroup<T>(val slug: String, val title: String, val members: List<T>) : EpisodeShelfItem<T>
    }

    /**
     * Kandidátní název série z titulku: „(tag) Název: zbytek" → „Název" (tag typu „(audiobonus)"/
     * „(video, plná verze)" na začátku se ignoruje). Číslovaný díl v prefixu („8. část, závěrečná",
     * „, 3.část") se odsekne, ať různě číslované díly TÉŽE série spadnou do STEJNÉHO klíče. Bez
     * dvojtečky nebo příliš krátký/dlouhý prefix = žádná série (jednorázová epizoda, ne segment).
     */
    fun detectSeriesTitle(rawTitle: String): String? {
        val stripped = rawTitle.replace(Regex("^\\([^)]*\\)\\s*"), "")
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 1) return null
        val prefix = stripped.substring(0, colonIdx).trim()
            .replace(Regex(",?\\s*\\d+\\.\\s*(?:část|díl|dil)\\b.*$", RegexOption.IGNORE_CASE), "")
            .trim()
        return prefix.takeIf { it.length in 3..60 }
    }

    /** Normalizovaný slug názvu série (malá písmena, bez diakritiky, jen a-z0-9-) — stabilní group-by klíč. */
    fun seriesSlug(seriesTitle: String): String {
        val noDiacritics = Normalizer.normalize(seriesTitle.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noDiacritics.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "serie" }
    }

    /**
     * Seskupí epizody do sérií (min. [minSize] členů, jinak zůstanou samostatné epizody). Pořadí
     * zachovává pozici PRVNÍHO výskytu série ve vstupu — feed bývá newest-first, takže série se
     * zobrazí na pozici svého nejnovějšího dílu.
     */
    fun <T> group(items: List<T>, titleOf: (T) -> String, minSize: Int = 2): List<EpisodeShelfItem<T>> {
        val bySlug = LinkedHashMap<String, MutableList<T>>()
        val titleBySlug = HashMap<String, String>()
        val firstIndex = HashMap<String, Int>()
        val standalone = mutableListOf<Pair<Int, T>>()
        items.forEachIndexed { idx, item ->
            val seriesTitle = detectSeriesTitle(titleOf(item))
            if (seriesTitle == null) {
                standalone += idx to item
            } else {
                val slug = seriesSlug(seriesTitle)
                bySlug.getOrPut(slug) { mutableListOf() }.add(item)
                titleBySlug.putIfAbsent(slug, seriesTitle)
                firstIndex.putIfAbsent(slug, idx)
            }
        }
        val positioned = mutableListOf<Pair<Int, EpisodeShelfItem<T>>>()
        standalone.forEach { (idx, item) -> positioned += idx to EpisodeShelfItem.Standalone(item) }
        bySlug.forEach { (slug, members) ->
            val idx = firstIndex.getValue(slug)
            if (members.size >= minSize) {
                positioned += idx to EpisodeShelfItem.SeriesGroup(slug, titleBySlug.getValue(slug), members)
            } else {
                members.forEach { item -> positioned += idx to EpisodeShelfItem.Standalone(item) }
            }
        }
        return positioned.sortedBy { it.first }.map { it.second }
    }

    /**
     * Composite klíč série pro [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.
     * visibleForKidsSourceKeys] — vedle klíčů celého zdroje (`type:ref`) nese i konkrétní sérii
     * (`type:ref#series:slug|Titulek`). Titulek uložen NEkódovaný (JSON string, plný Unicode bez
     * problému) → dětská obrazovka umí kartu popsat BEZ dalšího síťového dotazu na epizody.
     */
    fun buildSeriesKey(sourceKey: String, seriesTitle: String): String =
        "$sourceKey#series:${seriesSlug(seriesTitle)}|$seriesTitle"

    /**
     * WATCHDOG (2026-08-15, user „sbalené s datem posledního dílu, řazené sestupně") — nejnovější
     * datum mezi členy série (ms epoch), pro řazení tlačítka „Jen série". [dateOf] extrahuje syrový
     * datum string z konkrétního typu epizody (`RssEpisode.date`/`YtEpisode.uploadDate`/…).
     */
    fun <T> latestDateMs(members: List<T>, dateOf: (T) -> String?): Long? =
        members.mapNotNull { PodcastTimelineViewModel.parseEpisodeDate(dateOf(it)) }.maxOrNull()

    /** ms epoch → „d. M. yyyy" pro [PodcastSeriesRow] popisek. */
    fun formatSeriesDate(ms: Long): String = SimpleDateFormat("d. M. yyyy", Locale("cs")).format(ms)

    data class ParsedSeriesKey(val sourceKey: String, val slug: String, val title: String)

    /** null = [key] není klíč série (je to klíč celého zdroje, nebo nevalidní formát). */
    fun parseSeriesKey(key: String): ParsedSeriesKey? {
        val marker = "#series:"
        val markerIdx = key.indexOf(marker)
        if (markerIdx < 0) return null
        val sourceKey = key.substring(0, markerIdx)
        val rest = key.substring(markerIdx + marker.length)
        val pipeIdx = rest.indexOf('|')
        if (pipeIdx < 0) return null
        return ParsedSeriesKey(sourceKey, rest.substring(0, pipeIdx), rest.substring(pipeIdx + 1))
    }
}
