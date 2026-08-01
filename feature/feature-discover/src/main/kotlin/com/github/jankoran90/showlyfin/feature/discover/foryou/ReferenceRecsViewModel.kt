package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorRecs
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReferenceRecsUiState(
    /** Filmy, ze kterých se vybírá = historie sledování („na co ses díval"). */
    val choices: List<MediaItem> = emptyList(),
    /** Právě zvolené reference (1..N). */
    val picked: List<MediaItem> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    /**
     * Trefy, které divák UŽ ZNÁ (viděl/hodnotil/má v „Chci vidět"). Ukazují se pod výsledky označené —
     * user 2026-08-01: sekce hlásila „Nic nového nevypadlo", ačkoli kurátor poslal dvanáct sedících
     * titulů; jen je znal všechny. Prázdná obrazovka byla horší odpověď než „tohle sedí, ale znáš to".
     */
    val known: List<MediaItem> = emptyList(),
    val loadingChoices: Boolean = true,
    val loadingResults: Boolean = false,
    /** Doporučení doběhla aspoň jednou (odliší „ještě nic nechtěl" od „nic nenašel"). */
    val ran: Boolean = false,
    /** Text v našeptávacím poli (user 2026-08-01: „chybí text input s našeptáváním, historie je dlouhá"). */
    val query: String = "",
    /** Návrhy z TMDB k rozepsanému [query] — reference nemusí být jen z historie. */
    val suggestions: List<MediaItem> = emptyList(),
    val loadingSuggestions: Boolean = false,
)

/**
 * „Doporuč mi podle TOHOHLE" — doporučení vázaná na ručně vybrané filmy (user 2026-07-31: „důležité je
 * možnost volit referenci, na jaký film nebo filmy se doporučení váže; může se použít více filmů, ale
 * i jeden film, který sváže výběr do jednoho balíčku").
 *
 * Nabídka k výběru = historie sledování — reference má být to, co divák zná. Výsledek počítá [CuratorLoader.recommendFromReferences]: jeden titul jde na „co je
 * podobné X", víc titulů hledá jejich průnik.
 */
@HiltViewModel
class ReferenceRecsViewModel @Inject constructor(
    private val curator: CuratorLoader,
    private val traktRows: TraktRowLoader,
    private val tmdb: TmdbRemoteDataSource,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReferenceRecsUiState())
    val state: StateFlow<ReferenceRecsUiState> = _state.asStateFlow()

    private var lastProfileId: Long? = null
    private var suggestJob: Job? = null

    init {
        profileRepository.activeProfile
            .onEach { p ->
                if (p?.id != lastProfileId) {
                    lastProfileId = p?.id
                    // Jiný profil = jiná historie i jiná doporučení → začni znovu.
                    _state.value = ReferenceRecsUiState()
                    loadChoices()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadChoices() {
        viewModelScope.launch {
            val history = runCatching { traktRows.history("all") }.getOrDefault(emptyList())
            _state.update { it.copy(choices = history, loadingChoices = false) }
        }
    }

    /**
     * Našeptávání referencí z TMDB (filmy i seriály). Historie sledování je dlouhá a chipy se v ní
     * nedají uhledat (user 2026-08-01), a reference navíc nemusí být nic, co divák viděl — může chtít
     * doporučení „ve stylu" filmu, který jen zná. Debounce, ať se neptáme na každé písmeno.
     */
    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        suggestJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(suggestions = emptyList(), loadingSuggestions = false) }
            return
        }
        _state.update { it.copy(loadingSuggestions = true) }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val movies = runCatching { tmdb.searchMovies(q) }.getOrDefault(emptyList()).map { m ->
                MediaItem(
                    traktId = 0L, tmdbId = m.id, imdbId = null,
                    title = m.title ?: m.original_title.orEmpty(),
                    year = m.release_date?.take(4)?.toIntOrNull(),
                    overview = null, rating = null, genres = null,
                    type = MediaType.MOVIE, posterPath = m.poster_path,
                )
            }
            val shows = runCatching { tmdb.searchShows(q) }.getOrDefault(emptyList()).map { s ->
                MediaItem(
                    traktId = 0L, tmdbId = s.id, imdbId = null,
                    title = s.name ?: s.original_name.orEmpty(),
                    year = s.first_air_date?.take(4)?.toIntOrNull(),
                    overview = null, rating = null, genres = null,
                    type = MediaType.SHOW, posterPath = s.poster_path,
                )
            }
            // Prolož filmy a seriály podle pořadí z TMDB (= relevance), ať seriál nepřebije celý seznam.
            val merged = (movies + shows)
                .filter { it.title.isNotBlank() }
                .distinctBy { it.tmdbId }
                .take(SUGGEST_LIMIT)
            _state.update { it.copy(suggestions = merged, loadingSuggestions = false) }
        }
    }

    /**
     * Nastav JEDINOU referenci a rovnou spusť doporučení — vstup z ⋮ menu karty filmu („Doporuč
     * podobné", user 2026-08-01). Nahrazuje dosavadní výběr, aby bylo jasné, na čem výsledek stojí.
     */
    fun setReference(item: MediaItem) {
        _state.update {
            it.copy(picked = listOf(item), results = emptyList(), known = emptyList(), ran = false, query = "", suggestions = emptyList())
        }
        run()
    }

    /** Vyber našeptaný titul jako referenci a vyčisti pole (další se hledá od nuly). */
    fun pickSuggestion(item: MediaItem) {
        toggle(item)
        onQueryChange("")
    }

    fun toggle(item: MediaItem) {
        _state.update { s ->
            val key = refKey(item)
            val already = s.picked.any { refKey(it) == key }
            s.copy(picked = if (already) s.picked.filterNot { refKey(it) == key } else s.picked + item)
        }
    }

    fun clearPicked() = _state.update {
        it.copy(picked = emptyList(), results = emptyList(), known = emptyList(), ran = false)
    }

    /** Spusť doporučení pro aktuální výběr. Mozek je LLM → na `pending` si počkáme a zopakujeme dotaz. */
    fun run() {
        val picks = _state.value.picked
        if (picks.isEmpty()) return
        _state.update { it.copy(loadingResults = true) }
        viewModelScope.launch {
            val recs = runCatching { curator.recommendFromReferences(picks, RESULT_LIMIT, pollUntilReady = true) }
                .getOrDefault(CuratorRecs())
            _state.update {
                it.copy(results = recs.fresh, known = recs.known, loadingResults = false, ran = true)
            }
        }
    }

    private fun refKey(m: MediaItem): String = m.tmdbId?.toString() ?: m.imdbId ?: m.displayTitle

    private companion object {
        const val RESULT_LIMIT = 30
        const val SUGGEST_DEBOUNCE_MS = 350L
        const val SUGGEST_LIMIT = 12
    }
}
