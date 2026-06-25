package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import com.github.jankoran90.showlyfin.feature.listen.player.FavoriteSourcesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AGORA (objevovací modul podcastů): procházení celé scény (CZ ceskepodcasty + Apple popularita +
 * zahraničí us/gb/au) dle ZEMĚ × REŽIMU × KATEGORIE. Karty jsou obohacené (popis, počet epizod,
 * kategorie) a jdou rovnou přidat do sdílených zdrojů rodiny ([PodcastSourcesRepository.add]) —
 * stejný store jako [SourceManagerViewModel], takže „Přidáno" se sdílí napříč oběma obrazovkami.
 *
 * F3: srdíčka (lokální [FavoriteSourcesStore]) + režim „Oblíbené" (vykreslí lokální záložky místo
 * serverového browse). F4: filtr vyloučení kategorií + defaulty/prahy z Nastavení ([AbsPreferences]).
 * Stránkování: donačítání další stránky na konci gridu (append, ne nahrazení).
 */
@HiltViewModel
class PodcastDiscoveryViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val favorites: FavoriteSourcesStore,
    private val prefs: AbsPreferences,
) : ViewModel() {

    /** Země — segment nahoře (CZ výchozí). */
    enum class Country(val code: String, val flag: String, val label: String) {
        CZ("cz", "🇨🇿", "ČR"),
        US("us", "🇺🇸", "USA"),
        GB("gb", "🇬🇧", "UK"),
        AU("au", "🇦🇺", "Austrálie");

        companion object {
            fun fromCode(code: String?): Country = entries.firstOrNull { it.code == code } ?: CZ
        }
    }

    /** Režim řazení/výběru — FilterChip řádek. FAVORITES = lokální srdíčka (mimo server). */
    enum class Mode(val apiValue: String, val label: String) {
        POPULAR("popular", "Populární"),
        ACTIVE("active", "Aktivní"),
        NEW("new", "Nové"),
        AZ("az", "A-Z"),
        FAVORITES("favorites", "Oblíbené");

        companion object {
            fun fromApi(value: String?): Mode = entries.firstOrNull { it.apiValue == value } ?: POPULAR
        }
    }

    data class UiState(
        val country: Country = Country.CZ,
        val mode: Mode = Mode.POPULAR,
        val selectedCategory: SourceCategory? = null,
        val excluded: Set<Int> = emptySet(),
        val categories: List<SourceCategory> = emptyList(),
        val results: List<SourceSearchResult> = emptyList(),
        val loading: Boolean = false,
        /** Donačítá se další stránka (spinner v patičce gridu). */
        val loadingMore: Boolean = false,
        /** Server vrátil prázdnou stránku → konec (už nedonačítáme). */
        val endReached: Boolean = false,
        /** type:ref přidaných (sdílených) zdrojů (z repo.sources) → karta ukáže „Přidáno". */
        val addedRefs: Set<String> = emptySet(),
        /** type:ref oblíbených (lokální srdíčka) → karta ukáže plné/prázdné srdce. */
        val favoriteRefs: Set<String> = emptySet(),
        /** F4: zobrazovací prahy/přepínače z Nastavení. */
        val showSummary: Boolean = true,
        val showEpisodeCount: Boolean = true,
        val minEpisodes: Int = 0,
        val notConfigured: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var browseJob: Job? = null
    private var page = 1
    private val pageSize get() = prefs.discoveryPageSize

    init {
        // Sdílený store → „Přidáno" se synchronizuje s SourceManagerem (dedup dle type+ref).
        repo.sources
            .onEach { srcs -> _state.update { it.copy(addedRefs = srcs.map { s -> "${s.type}:${s.ref}" }.toSet()) } }
            .launchIn(viewModelScope)
        // Lokální srdíčka → reaktivní plné/prázdné srdce + obsah režimu Oblíbené.
        favorites.favorites
            .onEach { favs ->
                _state.update { st ->
                    val refs = favs.map { "${it.type}:${it.ref}" }.toSet()
                    // Když jsme zrovna v režimu Oblíbené, drž grid v synchru s úpravou srdíček.
                    if (st.mode == Mode.FAVORITES) st.copy(favoriteRefs = refs, results = applyClientFilters(favs, st))
                    else st.copy(favoriteRefs = refs)
                }
            }
            .launchIn(viewModelScope)

        // F4/F6: výchozí stav z Nastavení (země/režim/skryté kategorie/prahy).
        val defaultExcluded = prefs.discoveryHiddenCategories.mapNotNull { it.toIntOrNull() }.toSet()
        _state.update {
            it.copy(
                country = Country.fromCode(prefs.discoveryCountry),
                mode = Mode.fromApi(prefs.discoveryMode),
                excluded = defaultExcluded,
                showSummary = prefs.discoveryShowSummary,
                showEpisodeCount = prefs.discoveryShowEpisodeCount,
                minEpisodes = prefs.discoveryMinEpisodes,
                notConfigured = !repo.isConfigured,
            )
        }
        viewModelScope.launch { repo.refresh() }
        refresh()
    }

    fun setCountry(c: Country) {
        if (c == _state.value.country) return
        // Změna země → kategorie přestávají platit (CZ kategorie ≠ Apple žánr). Skryté kategorie z
        // Nastavení jsou CZ-specifické → na cizí zemi je shodíme.
        val keepExcluded = if (c == Country.CZ) _state.value.excluded else emptySet()
        _state.update { it.copy(country = c, selectedCategory = null, excluded = keepExcluded, categories = emptyList()) }
        refresh()
    }

    fun setMode(m: Mode) {
        if (m == _state.value.mode) return
        _state.update { it.copy(mode = m) }
        refresh()
    }

    fun selectCategory(cat: SourceCategory?) {
        if (cat?.id == _state.value.selectedCategory?.id) return
        _state.update { it.copy(selectedCategory = cat) }
        refresh()
    }

    /** AGORA-TABS: běhová změna min. počtu epizod z filtru sekce Podcasty (klientský filtr karet). */
    fun setMinEpisodes(value: Int) {
        if (value == _state.value.minEpisodes) return
        _state.update { it.copy(minEpisodes = value.coerceAtLeast(0)) }
        refresh()
    }

    /** AGORA-TABS: aktuálně vyloučené kategorie (pro badge filtru v tab řadě). */
    val excludedCount: Int get() = _state.value.excluded.size

    fun toggleExclude(id: Int) {
        _state.update {
            val ex = if (id in it.excluded) it.excluded - id else it.excluded + id
            // Vyloučenou kategorii nelze mít zároveň vybranou jako filtr.
            val sel = it.selectedCategory?.takeUnless { c -> c.id == id && id !in it.excluded }
            it.copy(excluded = ex, selectedCategory = sel)
        }
        refresh()
    }

    /** F3: přepnutí srdíčka u karty (lokální záložka). */
    fun toggleFavorite(result: SourceSearchResult) {
        favorites.toggle(result)
    }

    fun isFavorite(r: SourceSearchResult): Boolean = "${r.type}:${r.ref}" in _state.value.favoriteRefs

    /** Znovu načte (od první stránky) kategorie + karty dle aktuálních filtrů. */
    fun refresh() {
        browseJob?.cancel()
        page = 1
        val s = _state.value
        // Režim Oblíbené = lokální data, žádný server (filtr země/kategorie se na ně neaplikuje).
        if (s.mode == Mode.FAVORITES) {
            _state.update {
                it.copy(loading = false, loadingMore = false, endReached = true, results = applyClientFilters(favorites.favorites.value, it))
            }
            return
        }
        browseJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, endReached = false) }
            // Kategorie načítáme jen když je ještě nemáme pro danou zemi (mění se s setCountry).
            if (s.categories.isEmpty()) {
                val cats = runCatching { repo.categories(s.country.code) }.getOrDefault(emptyList())
                _state.update { it.copy(categories = cats) }
            }
            val res = runCatching {
                repo.browse(
                    country = s.country.code,
                    mode = s.mode.apiValue,
                    category = s.selectedCategory?.id?.toString(),
                    exclude = s.excluded.map { it.toString() },
                    page = page,
                    pageSize = pageSize,
                )
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(loading = false, endReached = res.size < pageSize, results = applyClientFilters(res, it))
            }
        }
    }

    /** Stránkování: donačte další stránku a APPENDuje (mimo režim Oblíbené / když je konec). */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.mode == Mode.FAVORITES) return
        browseJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val next = page + 1
            val res = runCatching {
                repo.browse(
                    country = s.country.code,
                    mode = s.mode.apiValue,
                    category = s.selectedCategory?.id?.toString(),
                    exclude = s.excluded.map { it.toString() },
                    page = next,
                    pageSize = pageSize,
                )
            }.getOrDefault(emptyList())
            if (res.isEmpty()) {
                _state.update { it.copy(loadingMore = false, endReached = true) }
            } else {
                page = next
                // Dedup dle type:ref (server může mezi stránkami vrátit překryv).
                _state.update { st ->
                    val have = st.results.map { "${it.type}:${it.ref}" }.toSet()
                    val appended = applyClientFilters(res.filterNot { "${it.type}:${it.ref}" in have }, st)
                    st.copy(loadingMore = false, endReached = res.size < pageSize, results = st.results + appended)
                }
            }
        }
    }

    /**
     * Klientské filtry nezávislé na serveru:
     *  - min. počet epizod (skryje karty pod prahem; karty bez počtu necháme projít),
     *  - vyloučené kategorie podle NÁZVU (server `exclude` řeší CZ ID, ale Apple/popular vrací jen
     *    název kategorie → odfiltrujeme klientsky dle názvu vyloučené kategorie).
     */
    private fun applyClientFilters(list: List<SourceSearchResult>, s: UiState): List<SourceSearchResult> {
        val excludedNames = s.categories.filter { it.id in s.excluded }.map { it.name.trim().lowercase() }.toSet()
        return list.filter { r ->
            val epOk = s.minEpisodes <= 0 || (r.episodeCount ?: Int.MAX_VALUE) >= s.minEpisodes
            val catOk = excludedNames.isEmpty() || r.category?.trim()?.lowercase() !in excludedNames
            epOk && catOk
        }
    }

    fun isAdded(r: SourceSearchResult): Boolean = "${r.type}:${r.ref}" in _state.value.addedRefs

    fun add(result: SourceSearchResult) {
        viewModelScope.launch {
            val ok = repo.add(result.type, result.ref, result.title, result.thumbnail)
            _state.update { it.copy(message = if (ok) "Přidáno: ${result.title}" else "Přidání se nezdařilo") }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
}
