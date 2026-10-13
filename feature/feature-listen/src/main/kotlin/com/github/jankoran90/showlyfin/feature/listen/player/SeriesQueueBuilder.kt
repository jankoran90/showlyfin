package com.github.jankoran90.showlyfin.feature.listen.player

import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.SeriesResolver
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slovo 2026-10-13 (emulátor: ▶ na kartě knihy na Domů zařadil jen jeden díl, kniha pak nepokračovala) —
 * po spuštění dílu ze série zařadí hned za něj zbytek té série v pořadí knihy (stejně jako „Přehrát"
 * v knize). Díly mimo sérii nic nedělají. Bez sítě se použije offline paměť dílů zdroje.
 */
@Singleton
class SeriesQueueBuilder @Inject constructor(
    private val sourcesRepo: PodcastSourcesRepository,
    private val rules: SeriesRuleStore,
    private val connection: AudiobookPlayerConnection,
) {
    suspend fun enqueueRestOfSeries(sourceType: String, sourceRef: String, current: SourceEpisode, toQueued: (SourceEpisode) -> QueuedEpisode) {
        val source = sourcesRepo.sources.value.firstOrNull { it.type == sourceType && it.ref == sourceRef } ?: return
        val slug = SeriesResolver.seriesSlug(rules, source.key, source.title, current.title, current.seriesTitle) ?: return
        val members = runCatching { sourcesRepo.loadEpisodes(source, limit = if (source.type == "rss") 1000 else 500) }
            .getOrDefault(emptyList())
            .filter { SeriesResolver.seriesSlug(rules, source.key, source.title, it.title, it.seriesTitle) == slug }
            // ČT bez řad: „1/3" a „1/13" jsou různé knihy (obrazovka pořadu je dělí podle celku) — Codex 2026-10-13.
            .filter { ep ->
                val total = PodcastEpisodeSeriesGrouping.numberedPart(current.title)?.second
                current.seriesTitle != null || total == null || PodcastEpisodeSeriesGrouping.numberedPart(ep.title)?.second == total
            }
        val ordered = PodcastEpisodeSeriesGrouping.chapterOrder(members, { it.title }, { it.date })
        val idx = ordered.indexOfFirst { it.id == current.id }
        if (idx < 0) return
        val rest = ordered.drop(idx + 1).map(toQueued)
        if (rest.isEmpty()) return
        // Během načítání se mohl přepnout profil / spustit jiný díl → do cizí fronty nic nezapisuj (Codex P1).
        val currentKey = current.resumeKey ?: current.id
        if (connection.state.value.currentEpisodeId != currentKey) return
        val restKeys = rest.mapTo(HashSet()) { it.episodeId }
        val q = connection.queue.value.filterNot { it.episodeId in restKeys }
        val curIdx = q.indexOfFirst { it.episodeId == currentKey }
        if (curIdx < 0) return
        connection.setQueue(q.take(curIdx + 1) + rest + q.drop(curIdx + 1))
    }
}
