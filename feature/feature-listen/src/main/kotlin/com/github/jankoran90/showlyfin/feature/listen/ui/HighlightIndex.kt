package com.github.jankoran90.showlyfin.feature.listen.ui

/**
 * Revize/test F8 (P1): pozice zvýrazněného dílu v TOM seznamu, který se opravdu kreslí — hlavička
 * ([SourceHero]) + rozposlouchané nahoře (plochý seznam) nebo série + samostatné díly (admin pohled).
 * Sbalená série = její řádek. -1 = díl v seznamu není.
 */
internal fun <T> renderedIndexOf(
    flat: List<T>,
    shelf: List<PodcastEpisodeSeriesGrouping.EpisodeShelfItem<T>>?,
    expanded: Set<String>,
    match: (T) -> Boolean,
): Int {
    val header = 1
    if (shelf == null) return flat.indexOfFirst(match).let { if (it >= 0) it + header else -1 }
    var pos = header
    for (item in shelf) {
        when (item) {
            is PodcastEpisodeSeriesGrouping.EpisodeShelfItem.Standalone -> {
                if (match(item.item)) return pos
                pos++
            }
            is PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup -> {
                val open = item.slug in expanded
                if (!open && item.members.any(match)) return pos
                pos++
                if (open) {
                    item.members.forEach { if (match(it)) return pos; pos++ }
                }
            }
        }
    }
    return -1
}
