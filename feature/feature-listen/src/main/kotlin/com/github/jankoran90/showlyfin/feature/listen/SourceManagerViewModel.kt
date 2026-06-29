package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRESET (SHW-65): správa sdílených ZDROJŮ Poslechu (YouTube kanály + RSS podcasty). Otevírá se
 * z postranního menu nad „Správa" (rozhodnutí usera 2026-06-16). Nahoře hledání podle názvu → přidat,
 * dole seznam aktuálních zdrojů → odebrat. Vše SDÍLENÉ na serveru (přidám/odeberu → projeví se u celé
 * rodiny). Zdroje pak splynou se sekcí Poslech → Podcasty (přehrávání).
 */
@HiltViewModel
class SourceManagerViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
) : ViewModel() {

    enum class TypeFilter(val apiValue: String, val label: String) {
        ALL("all", "Vše"), PODCAST("rss", "Podcasty"), YOUTUBE("youtube", "YouTube"), CTV("ctv", "ČT")
    }

    data class UiState(
        val query: String = "",
        val typeFilter: TypeFilter = TypeFilter.ALL,
        val searching: Boolean = false,
        val searched: Boolean = false,
        val results: List<SourceSearchResult> = emptyList(),
        val sources: List<PodcastSource> = emptyList(),
        val notConfigured: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        repo.sources
            .onEach { srcs -> _state.update { it.copy(sources = srcs) } }
            .launchIn(viewModelScope)
        viewModelScope.launch { repo.refresh() }
        _state.update { it.copy(notConfigured = !repo.isConfigured) }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        scheduleSearch()
    }

    fun setTypeFilter(f: TypeFilter) {
        if (f == _state.value.typeFilter) return
        _state.update { it.copy(typeFilter = f) }
        scheduleSearch()
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        val q = _state.value.query.trim()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false, searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)   // debounce: nepalit dotaz na každý znak
            _state.update { it.copy(searching = true) }
            val res = runCatching { repo.search(q, _state.value.typeFilter.apiValue) }.getOrDefault(emptyList())
            _state.update { it.copy(searching = false, searched = true, results = res) }
        }
    }

    /** Je tenhle výsledek už mezi přidanými? (dedup dle type+ref, jako backend store). */
    fun isAdded(r: SourceSearchResult): Boolean =
        _state.value.sources.any { it.type == r.type && it.ref == r.ref }

    fun add(r: SourceSearchResult) {
        viewModelScope.launch {
            val ok = repo.add(r.type, r.ref, r.title, r.thumbnail)
            _state.update { it.copy(message = if (ok) "Přidáno: ${r.title}" else "Přidání se nezdařilo") }
        }
    }

    fun remove(source: PodcastSource) {
        viewModelScope.launch {
            val ok = repo.remove(source.id)
            if (!ok) _state.update { it.copy(message = "Odebrání se nezdařilo") }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
}
