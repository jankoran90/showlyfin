package com.github.jankoran90.showlyfin.feature.discover.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * AMFITEÁTR F5 (user 2026-10-13: „hledač zdrojů a přidávač musíme ve Filmy app mít — ČT, RSS, YouTube").
 * Správa zdrojů Podcastů ve Filmech nad STEJNÝM serverovým seznamem jako Slovo (`/api/sources`) → co se
 * přidá tady, uvidí Slovo a naopak. Vzor: Slovo `SourceManagerViewModel` (feature-listen, Filmy ho nevidí).
 * Viditelnost per profil = [PodcastSource.isVisibleTo]; dospělý může zdroj schválit dětskému profilu.
 */
@HiltViewModel
class PodcastSourceManagerViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val profiles: ProfileRepository,
    @param:javax.inject.Named("traktPreferences") private val prefs: android.content.SharedPreferences,
) : ViewModel() {

    enum class TypeFilter(val apiValue: String, val label: String) {
        ALL("all", "Vše"), YOUTUBE("youtube", "YouTube"), CTV("ctv", "Česká televize"), PODCAST("rss", "Podcasty")
    }

    data class UiState(
        val query: String = "",
        val typeFilter: TypeFilter = TypeFilter.ALL,
        val searching: Boolean = false,
        val searched: Boolean = false,
        val results: List<SourceSearchResult> = emptyList(),
        /** Moje zdroje + sdílené mnou viditelné (cizí soukromé ne — nesmím je omylem smazat). */
        val sources: List<PodcastSource> = emptyList(),
        /** Klíče `type:ref` schválené dětskému profilu. */
        val kidsKeys: Set<String> = emptySet(),
        val isAdult: Boolean = true,
        val hasKidsProfile: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        combine(repo.sources, profiles.activeProfile, profiles.activeConfig, profiles.observeAll()) { srcs, active, cfg, all ->
            val my = active?.profileUuid
            val isKids = active?.isAdmin == false
            val kids = all.firstOrNull { !it.isAdmin }
            val kidsKeys = kids?.let { com.github.jankoran90.showlyfin.core.domain.ProfileConfig.fromJson(it.configJson).visibleForKidsSourceKeys }
                .orEmpty()
            _state.update {
                it.copy(
                    sources = srcs.filter { s ->
                        if (isKids) s.isVisibleTo(my, true, cfg.sharedSourceKeys, cfg.visibleForKidsSourceKeys)
                        else PodcastPrefs.sourcesScope(prefs) == "all" ||
                            s.addedBy.isNullOrBlank() || s.addedBy == my || s.key in cfg.sharedSourceKeys
                    },
                    kidsKeys = kidsKeys,
                    isAdult = !isKids,
                    hasKidsProfile = kids != null,
                )
            }
        }.launchIn(viewModelScope)
        viewModelScope.launch { runCatching { repo.refresh() } }
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
            delay(400)
            _state.update { it.copy(searching = true) }
            val res = try {
                repo.search(q, _state.value.typeFilter.apiValue)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AMFITEATR] hledání zdrojů")
                _state.update { it.copy(searching = false, message = "Hledání zdrojů se nepovedlo") }
                return@launch
            }
            _state.update { it.copy(searching = false, searched = true, results = res) }
        }
    }

    fun isAdded(r: SourceSearchResult): Boolean = _state.value.sources.any { it.type == r.type && it.ref == r.ref }

    /** Přidat zdroj (jen dospělý). Nový zdroj vidí ten, kdo ho přidal; dětem až po schválení. */
    fun add(r: SourceSearchResult) {
        if (!_state.value.isAdult) return
        viewModelScope.launch {
            val ok = repo.add(r.type, r.ref, r.title, r.thumbnail, profiles.activeProfile.value?.profileUuid)
            _state.update { it.copy(message = if (ok) "Přidáno: ${r.title}" else "Přidání se nepovedlo") }
        }
    }

    fun remove(source: PodcastSource) {
        if (!_state.value.isAdult || source.premium) return
        viewModelScope.launch {
            val ok = repo.remove(source.id)
            _state.update { it.copy(message = if (ok) "Odebráno: ${source.title}" else "Odebrání se nepovedlo") }
        }
    }

    /** Schválit / zrušit zdroj dětskému profilu (whitelist `visibleForKidsSourceKeys`). */
    fun setVisibleForKids(source: PodcastSource, visible: Boolean) {
        if (!_state.value.isAdult) return
        viewModelScope.launch {
            val kids = profiles.getAll().firstOrNull { !it.isAdmin } ?: return@launch
            profiles.updateConfig(kids.id) { cfg ->
                val s = cfg.visibleForKidsSourceKeys.toMutableSet().also { if (visible) it.add(source.key) else it.remove(source.key) }
                cfg.copy(visibleForKidsSourceKeys = s)
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
