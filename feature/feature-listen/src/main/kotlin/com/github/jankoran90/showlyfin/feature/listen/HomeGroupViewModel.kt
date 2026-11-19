package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel.ContinueItem
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRuleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Rozbalená skupina na Domů (user 2026-11-19 17:35: „seřazeno od prvního do posledního, vyznačené rozposlouchané,
 * skočí hned zvýrazněním na ten poslední poslouchaný") — dotáhne VŠECHNY díly pořadu / knihy série v pořadí.
 * Rozposlouchané položky skupiny se do výsledku vloží jako [ContinueItem] (s průběhem), ostatní jako holý díl / kniha.
 */
@HiltViewModel
class HomeGroupViewModel @Inject constructor(
    private val abs: AbsRepository,
    private val sourcesRepo: PodcastSourcesRepository,
    private val ruleStore: SeriesRuleStore,
    resumeStore: DirectResumeStore,
) : ViewModel() {

    /** Pozice direct dílů (`rss:`/`yt:`/`ctv:`) → v pruhu fajfka u dohraných (tester 2026-11-19). */
    val resumeMarks = resumeStore.marks

    sealed interface Entry {
        data class Listened(val item: ContinueItem) : Entry
        data class Episode(val sourceType: String, val sourceRef: String, val sourceTitle: String, val episode: SourceEpisode) : Entry
        data class Book(val book: Audiobook) : Entry
    }

    /** Klíč skupiny → seřazené položky (null = ještě se načítá / nenačetlo se → ukázat jen rozposlouchané). */
    private val _full = MutableStateFlow<Map<String, List<Entry>>>(emptyMap())
    val full = _full.asStateFlow()
    private val requested = HashSet<String>()

    fun load(key: String, items: List<ContinueItem>, refresh: Boolean = false) {
        if (!requested.add(key) && !refresh) return
        viewModelScope.launch {
            val list = runCatching {
                when (val first = items.first()) {
                    is ContinueItem.Book -> loadBooks(first.book, items)
                    is ContinueItem.Episode -> loadEpisodes(first, items)
                }
            }.getOrNull() ?: return@launch
            if (list.isNotEmpty()) _full.value = _full.value + (key to list)
        }
    }

    private suspend fun loadBooks(sample: Audiobook, items: List<ContinueItem>): List<Entry> {
        val series = seriesBase(sample.seriesName) ?: return emptyList()
        val listened = items.filterIsInstance<ContinueItem.Book>().associateBy { it.book.id }
        return abs.getAudiobooks(sample.libraryId)
            .filter { seriesBase(it.seriesName).equals(series, ignoreCase = true) }
            .sortedWith(compareBy<Audiobook> { seriesNumber(it.seriesName) ?: Double.MAX_VALUE }.thenBy { it.title })
            .map { b -> listened[b.id]?.let { Entry.Listened(it) } ?: Entry.Book(b) }
    }

    private suspend fun loadEpisodes(sample: ContinueItem.Episode, items: List<ContinueItem>): List<Entry> {
        val key = "${sample.sourceType}:${sample.sourceRef}"
        val src = sourcesRepo.sources.value.firstOrNull { it.type == sample.sourceType && it.ref == sample.sourceRef }
            ?: PodcastSource(id = "", type = sample.sourceType, ref = sample.sourceRef, title = sample.sourceTitle)
        val eps = sourcesRepo.loadEpisodes(src, limit = if (src.type == "rss") 1000 else 500)
            .filter { it.streamUrl.isNotBlank() }
            .reversed() // feed = nejnovější první → od 1. dílu
        if (eps.isEmpty()) return emptyList()
        val listened = items.filterIsInstance<ContinueItem.Episode>()
        val listenedIds = listened.associateBy { it.episode.id }
        fun slug(e: SourceEpisode) = SeriesResolver.seriesSlug(ruleStore, key, src.title.ifBlank { sample.sourceTitle }, e.title, e.seriesTitle)
        // Rozposlouchané z jedné série pořadu → jen ta série (Po jedné stopě do Iráku), jinak výsek kolem nich.
        val slugs = listened.map { slug(it.episode) }.toSet()
        val pool = if (slugs.size == 1 && slugs.first() != null) eps.filter { slug(it) == slugs.first() } else {
            val idx = eps.indices.filter { eps[it].id in listenedIds }
            if (idx.isEmpty()) emptyList()
            else eps.subList((idx.min() - WINDOW).coerceAtLeast(0), (idx.max() + WINDOW + 1).coerceAtMost(eps.size))
        }
        if (pool.isEmpty() || pool.size > MAX_ITEMS) return emptyList()
        // Rozposlouchaný díl mimo výsek (starší feed) se neztratí — zůstane mezi nimi na konci.
        val missing = listened.filter { l -> pool.none { it.id == l.episode.id } }
        return pool.map { e ->
            listenedIds[e.id]?.let { Entry.Listened(it) } ?: Entry.Episode(sample.sourceType, sample.sourceRef, sample.sourceTitle, e)
        } + missing.map { Entry.Listened(it) }
    }

    private companion object {
        const val WINDOW = 5
        const val MAX_ITEMS = 120
        fun seriesBase(raw: String?): String? = raw?.substringBefore(" #")?.trim()?.takeIf { it.isNotBlank() }
        fun seriesNumber(raw: String?): Double? = raw?.substringAfter(" #", "")?.substringBefore(",")?.trim()?.replace(',', '.')?.toDoubleOrNull()
    }
}
