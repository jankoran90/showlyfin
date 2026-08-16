package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Obrazovka „Domů" (user 2026-08-15: „naposledy přehráno a pokračovat, hezky v mřížce, vždy první").
 * Sjednocuje VŠECHNO rozposlouchané napříč zdroji: ABS audioknihy (`Audiobook.lastUpdate`) + direct
 * epizody RSS/YouTube/ČT ([DirectResumeStore], titul/cover dohledán přes feed zdroje — resumeKey je
 * sjednocený s Android Auto „Pokračovat", viz `AudiobookBrowseTree.continueItems()`, stejná logika,
 * jiný sink (Compose grid místo MediaItem stromu)). Seřazeno podle posledního poslechu, nejnovější první.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val sourcesRepo: PodcastSourcesRepository,
    private val directResume: DirectResumeStore,
    connection: AudiobookPlayerConnection,
) : ViewModel() {

    /** User (2026-08-15 16:49) — odznak „hraje" na dlaždici, když je zrovna aktivní v přehrávači. */
    val playerState = connection.state

    sealed interface ContinueItem {
        val updatedAt: Long

        data class Book(val book: Audiobook) : ContinueItem {
            override val updatedAt: Long = book.lastUpdate ?: 0L
        }

        data class Episode(
            val sourceType: String,
            val sourceRef: String,
            val sourceTitle: String,
            val episode: SourceEpisode,
            val progress: Float,
            override val updatedAt: Long,
        ) : ContinueItem
    }

    private val _items = MutableStateFlow<List<ContinueItem>>(emptyList())
    val items: StateFlow<List<ContinueItem>> = _items.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val books = runCatching {
                if (!repo.isConfigured) emptyList()
                else repo.getAudiobookLibraries()
                    .flatMap { repo.getAudiobooks(it.id) }
                    .filter { it.progress > 0.001 && !it.isFinished }
            }.getOrDefault(emptyList())
            val episodes = runCatching { continueDirectEpisodes() }.getOrDefault(emptyList())
            _items.value = (books.map(ContinueItem::Book) + episodes)
                .sortedByDescending { it.updatedAt }
            _isLoading.value = false
        }
    }

    /** Rozposlouchané direct epizody → dohledané přes feedy zdrojů (stejný join jako CRUISE Android Auto). */
    private suspend fun continueDirectEpisodes(): List<ContinueItem.Episode> {
        // User (2026-08-16, „doposlouchané zmizí z Domů") — od té doby, co [DirectResumeStore] mark
        // při dohrání NEMAŽE (jen ho nechá na isFinished), musí Domů dohrané výslovně vyfiltrovat.
        val marks = directResume.marks.value.filterValues { !it.isFinished }
        if (marks.isEmpty()) return emptyList()
        sourcesRepo.refresh()
        val byKey = HashMap<String, Pair<SourceEpisode, PodcastSource>>()
        sourcesRepo.sources.value.forEach { src ->
            runCatching { sourcesRepo.loadEpisodes(src) }.getOrDefault(emptyList()).forEach { ep ->
                ep.resumeKey?.let { byKey[it] = ep to src }
            }
        }
        return marks.entries.mapNotNull { (key, mark) ->
            byKey[key]?.let { (ep, src) ->
                val progress = if (mark.durMs > 0) (mark.posMs.toFloat() / mark.durMs).coerceIn(0f, 1f) else 0f
                ContinueItem.Episode(src.type, src.ref, src.title, ep, progress, mark.updatedAt)
            }
        }
    }
}
