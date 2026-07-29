package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.CTV_ID_SCHEME
import com.github.jankoran90.showlyfin.data.uploader.CTV_SCHEME
import com.github.jankoran90.showlyfin.data.uploader.CTV_SHOW_SCHEME
import com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.model.CtvNumbering
import com.github.jankoran90.showlyfin.data.uploader.model.CtvTitle
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStreamQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * VLTAVA (SHW-110) F6 — mozek karty titulu z ČT iVysílání. Sdílený telefonem i TV (jako
 * [SearchViewModel]), aby obě appky uměly z hledání ČT totéž.
 *
 * Film se hraje rovnou (`ctv:<idec>`), pořad s díly nejdřív natáhne díly z `/api/ctv/feed`.
 * Odkaz na video si VŽDY vytáhne ZAŘÍZENÍ ([CtvStreamResolver]) — playlist API ČT je geoblokované
 * na náš server (Hetzner DE = 403), takže z domácí české sítě je to jediná cesta, jak stream dostat.
 */
@HiltViewModel
class CtvTitleViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    private val ctvResolver: CtvStreamResolver,
    private val workingSources: WorkingSourceStore,
    private val watchedStore: com.github.jankoran90.showlyfin.core.domain.resume.CtvWatchedStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Hotová adresa k přehrání + název do přehrávače (jednorázový signál, viz [consumePlay]). */
    data class PlayRequest(val url: String, val title: String, val posterUrl: String?, val resumeKey: String)

    /**
     * VLTAVA F5 (user 2026-07-28 „udělej obrazovku se sériemi a epizodami stejně jako máme u JF seriálů",
     * 2026-07-29 „píšeme hlavně opravdu číslo dílu a série, vezmi to 1:1") — jedna SÉRIE ČT pořadu.
     *
     * Čísla série a dílu ČT neposílá, dopočítává je [CtvNumbering] (z `idec`, případně z „N/M" v názvu).
     * Pořad s jedinou sérií → lišta sérií se nekreslí, ale „S01E04" u dílu zůstává.
     */
    data class CtvSeason(val label: String, val number: Int, val episodes: List<CtvNumbering.Numbered>)

    data class UiState(
        val title: CtvTitle? = null,
        val loadingEpisodes: Boolean = false,
        /** Díly SEŘAZENÉ od prvního, s dopočítanými čísly série/dílu. */
        val episodes: List<CtvNumbering.Numbered> = emptyList(),
        /** Série (od první) — prázdné, dokud se díly nenačtou. */
        val seasons: List<CtvSeason> = emptyList(),
        /** Vybraná série; default = ta, ve které je první nedokoukaný díl (kde člověk skončil). */
        val selectedSeason: String? = null,
        val resolvingIdec: String? = null,
        val error: String? = null,
        // VLTAVA F6b — je titul ve Filmotéce (= má uložený zdroj pod identitou `ctvid:<sidp>`)?
        val inFilmoteka: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Reaktivní množina klíčů `ctv:<idec>` zhlédnutých dílů — fajfka u dílu se překreslí sama. */
    val watchedKeys = watchedStore.watched

    private val _play = MutableStateFlow<PlayRequest?>(null)
    val play = _play.asStateFlow()

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var loadedFor: String? = null

    fun load(title: CtvTitle) {
        if (loadedFor == title.sidp) return
        loadedFor = title.sidp
        _state.value = UiState(title = title, inFilmoteka = isSaved(title.sidp))
        viewModelScope.launch {
            // F6b: titul otevřený z Filmotéky nese jen identitu (`ctvid:<sidp>`) + název → dotáhni zbytek
            // (popis, obrázek, `idec` k přehrání). `idec` schválně NEUKLÁDÁME — je krátkodobé.
            val full = if (title.idec.isNullOrBlank() && title.episodesAnchor.isNullOrBlank()) {
                val fresh = uploaderDs.getCtvTitle(baseUrl, cookie, title.sidp)
                if (fresh == null) {
                    // 🔴 Dřív se to spolklo (`?: title`) a karta zůstala TIŠE prázdná — nešlo poznat,
                    // jestli titul nic nemá, nebo jen selhalo spojení (user 2026-07-28 „karta pořadu
                    // nejde otevřít"). Řekni to nahlas a nabídni zkusit znovu ([retry]).
                    Timber.w("[VLTAVA] ČT titul %s se nepodařilo načíst", title.sidp)
                    loadedFor = null
                    _state.update { it.copy(error = "Načtení z ČT se nepovedlo. Zkus to prosím znovu.") }
                    return@launch
                }
                _state.update { it.copy(title = fresh) }
                healSavedRecord(fresh)
                fresh
            } else {
                title
            }
            // Film žádné díly nemá — kotva epizod je jen u pořadů (ČT sama říká `type`).
            if (full.isMovie || full.episodesAnchor.isNullOrBlank()) return@launch
            _state.update { it.copy(loadingEpisodes = true) }
            // Od NEJSTARŠÍHO dílu — pořad se ve Filmech chová jako seriál (user 2026-07-28).
            runCatching { uploaderDs.getCtvFeed(baseUrl, cookie, full.sidp, limit = 100, order = "oldest") }
                .onSuccess { feed ->
                    // 🔴 Pořadí dílů NEBEREME z feedu: `date` je poslední repríza, ne premiéra (viz
                    // [CtvNumbering]) — proto appka nabízela jako „další díl" 2. díl Magických hlubin.
                    val numbered = CtvNumbering.number(feed.episodes)
                    val seasons = groupSeasons(numbered)
                    _state.update {
                        it.copy(
                            loadingEpisodes = false,
                            episodes = numbered,
                            seasons = seasons,
                            selectedSeason = defaultSeason(seasons),
                        )
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "[VLTAVA] díly ČT pořadu %s se nenačetly", full.sidp)
                    loadedFor = null
                    _state.update {
                        it.copy(loadingEpisodes = false, error = "Díly pořadu se nepodařilo načíst.")
                    }
                }
        }
    }

    /**
     * VLTAVA F6b — přepínač „mám to ve Filmotéce". ČT titul nemá TMDB ani IMDb identitu, takže se
     * ukládá jako zapamatovaný zdroj pod **syntetickou identitou** `ctvid:<sidp>`; tím je v jednom
     * kroku (a) členem Filmotéky a (b) rovnou přehratelný. Zapisuje se jako **user-confirmed**
     * (`auto=false`), takže se ho serverový reverify ani auto-cache nikdy nedotknou.
     *
     * Zdroj = `ctv:<idec>` u filmu, `ctvshow:<sidp>` u pořadu s díly (ten se otevře seznamem dílů).
     */
    fun toggleFilmoteka() {
        val t = _state.value.title ?: return
        val identity = CTV_ID_SCHEME + t.sidp
        if (_state.value.inFilmoteka) {
            workingSources.clear(identity, null)
            _state.update { it.copy(inFilmoteka = false) }
            return
        }
        val url = when {
            t.isMovie && !t.idec.isNullOrBlank() -> CTV_SCHEME + t.idec
            !t.isMovie -> CTV_SHOW_SCHEME + t.sidp
            else -> null
        }
        if (url == null) {
            _state.update { it.copy(error = "Tenhle titul zatím nemá co přehrát.") }
            return
        }
        val yr = t.year?.let { " ($it)" } ?: ""
        workingSources.save(
            imdb = identity,
            tmdb = null,
            title = t.title,
            stream = UploaderStream(
                name = "Česká televize",
                description = "${t.title}$yr · iVysílání · CZ",
                url = url,
                addon = "Česká televize",
                quality = UploaderStreamQuality(
                    resolution = "1080p", audioLanguage = "CZ", source = "WEB", videoCodec = "H.264",
                ),
            ),
            // Obálka do Filmotéky: nejdřív svislý plakát ČT (sedí do karet 2:3), jinak 16:9 náhled
            // (karta si ho pak vykreslí celý, viz `wideArtwork` v PosterCard) — user 2026-07-28.
            poster = t.poster ?: t.thumbnail,
            // Popis/rok/žánry si titul nese s sebou — ČT je má, ale TMDB je pro něj nikdy nedohledá.
            overview = t.description,
            year = t.year,
            genres = t.genres?.distinct(),
        )
        _state.update { it.copy(inFilmoteka = true) }
    }

    private fun isSaved(sidp: String): Boolean =
        workingSources.get(CTV_ID_SCHEME + sidp, null) != null

    /**
     * Titul uložený STARŠÍ verzí appky nemá v záznamu obrázek ani popis (přibyly až teď) → Filmotéka by
     * u něj zůstala prázdná napořád. Při otevření karty záznam potichu doplníme z čerstvých dat ČT.
     * `firstSavedAtMs` si `save()` drží, takže titul nepřeskočí v „Nedávno přidané".
     */
    private fun healSavedRecord(fresh: CtvTitle) {
        val identity = CTV_ID_SCHEME + fresh.sidp
        val saved = workingSources.get(identity, null) ?: return
        val poster = fresh.poster ?: fresh.thumbnail
        val needsArt = saved.poster.isNullOrBlank() && !poster.isNullOrBlank()
        val needsText = saved.overview.isNullOrBlank() && !fresh.description.isNullOrBlank()
        val needsFacts = (saved.year == null && fresh.year != null) ||
            (saved.genres.isNullOrEmpty() && !fresh.genres.isNullOrEmpty())
        if (!needsArt && !needsText && !needsFacts) return
        workingSources.save(
            imdb = identity,
            tmdb = null,
            title = saved.title.ifBlank { fresh.title },
            stream = saved.stream,
            poster = saved.poster ?: poster,
            overview = saved.overview ?: fresh.description,
            year = saved.year ?: fresh.year,
            genres = saved.genres?.takeIf { it.isNotEmpty() } ?: fresh.genres?.distinct(),
        )
    }

    /** Přehraj film (idec titulu) nebo konkrétní díl — resolve běží TADY, na zařízení. */
    fun playIdec(idec: String, label: String, posterUrl: String?) {
        if (idec.isBlank() || _state.value.resolvingIdec != null) return
        _state.update { it.copy(resolvingIdec = idec, error = null) }
        viewModelScope.launch {
            when (val r = ctvResolver.resolve(idec)) {
                is CtvStreamResolver.Result.Ok -> {
                    Timber.i("[VLTAVA] ČT hledání → play idec=%s", idec)
                    _state.update { it.copy(resolvingIdec = null) }
                    // Klíč pozice = konkrétní díl (`ctv:<idec>`), ne pořad → každý díl si pamatuje svoje.
                    _play.value = PlayRequest(r.url, label, posterUrl, CTV_SCHEME + idec)
                }
                else -> _state.update { it.copy(resolvingIdec = null, error = errorText(r)) }
            }
        }
    }

    /**
     * VLTAVA F6c (user 2026-07-28 „budu potřebovat něco jako označit řady a díly jako zhlédnuté") —
     * přepínač „zhlédnuto" u jednoho dílu ČT. Zhlédnutý díl přeskočí řada „Další díly".
     */
    fun toggleEpisodeWatched(idec: String) {
        val key = CTV_SCHEME + idec
        if (watchedStore.isWatched(key)) watchedStore.clear(key) else watchedStore.markWatched(key)
    }

    /**
     * „Vše až po tento díl" — u pořadu s desítkami dílů je proklikávání jednoho po druhém k ničemu.
     * Díly chodí od nejstaršího, takže označíme všechno od začátku po vybraný (včetně).
     */
    fun markWatchedUpTo(idec: String) {
        val eps = _state.value.episodes
        val idx = eps.indexOfFirst { it.episode.id == idec }
        if (idx < 0) return
        eps.take(idx + 1).forEach { watchedStore.markWatched(CTV_SCHEME + it.episode.id) }
    }

    /**
     * Označ/odznač CELOU sérii (parita s „Označit sezónu" u seriálů z Jellyfinu, user 2026-07-28
     * „budu potřebovat něco jako označit řady a díly jako zhlédnuté").
     */
    fun toggleSeasonWatched(label: String) {
        val eps = _state.value.seasons.firstOrNull { it.label == label }?.episodes
            ?: _state.value.episodes
        val allWatched = eps.all { watchedStore.isWatched(CTV_SCHEME + it.episode.id) }
        eps.forEach { n ->
            val key = CTV_SCHEME + n.episode.id
            if (allWatched) watchedStore.clear(key) else watchedStore.markWatched(key)
        }
    }

    fun selectSeason(label: String) { _state.update { it.copy(selectedSeason = label) } }

    /** Díly vybrané série (nebo všechny, když série nemáme) — to, co kreslí obrazovka. */
    fun visibleEpisodes(state: UiState = _state.value): List<CtvNumbering.Numbered> {
        val sel = state.selectedSeason ?: return state.episodes
        return state.seasons.firstOrNull { it.label == sel }?.episodes ?: state.episodes
    }

    /** Série = skupiny z [CtvNumbering] (prefix `idec`); jediná série = lištu nekreslíme. */
    private fun groupSeasons(episodes: List<CtvNumbering.Numbered>): List<CtvSeason> {
        if (episodes.isEmpty()) return emptyList()
        val groups = episodes.groupBy { it.seasonNumber }
        if (groups.size <= 1) return emptyList()
        return groups.entries.sortedBy { it.key }
            .map { (number, eps) -> CtvSeason("Série $number", number, eps) }
    }

    /** Výchozí série = ta s prvním nedokoukaným dílem (kde člověk skončil); jinak první. */
    private fun defaultSeason(seasons: List<CtvSeason>): String? {
        if (seasons.isEmpty()) return null
        val watched = watchedStore.watched.value
        return seasons.firstOrNull { s -> s.episodes.any { CTV_SCHEME + it.episode.id !in watched } }?.label
            ?: seasons.first().label
    }

    fun consumePlay() { _play.value = null }

    fun dismissError() { _state.update { it.copy(error = null) } }

    /** Zkus načtení karty znovu (po chybě spojení) — `loadedFor` je po chybě už vynulované. */
    fun retry() {
        val t = _state.value.title ?: return
        _state.update { it.copy(error = null) }
        loadedFor = null
        load(t)
    }

    /** Pravdivá hláška místo černé obrazovky (zrcadlí `DetailViewModel.ctvError`). */
    private fun errorText(r: CtvStreamResolver.Result): String = when (r) {
        is CtvStreamResolver.Result.DrmRequired ->
            "Titul je chráněný (DRM) a tohle zařízení ho nepřehraje."
        is CtvStreamResolver.Result.OutsideCz ->
            "Česká televize pouští video jen z české sítě — zkus to doma, bez VPN."
        is CtvStreamResolver.Result.Failed -> "Přehrání z ČT selhalo: ${r.reason}"
        is CtvStreamResolver.Result.Ok -> ""
    }
}
