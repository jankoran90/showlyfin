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
import com.github.jankoran90.showlyfin.data.uploader.model.CtvEpisode
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
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Hotová adresa k přehrání + název do přehrávače (jednorázový signál, viz [consumePlay]). */
    data class PlayRequest(val url: String, val title: String, val posterUrl: String?)

    data class UiState(
        val title: CtvTitle? = null,
        val loadingEpisodes: Boolean = false,
        val episodes: List<CtvEpisode> = emptyList(),
        val resolvingIdec: String? = null,
        val error: String? = null,
        // VLTAVA F6b — je titul ve Filmotéce (= má uložený zdroj pod identitou `ctvid:<sidp>`)?
        val inFilmoteka: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

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
                uploaderDs.getCtvTitle(baseUrl, cookie, title.sidp)?.also { fresh ->
                    _state.update { it.copy(title = fresh) }
                } ?: title
            } else {
                title
            }
            // Film žádné díly nemá — kotva epizod je jen u pořadů (ČT sama říká `type`).
            if (full.isMovie || full.episodesAnchor.isNullOrBlank()) return@launch
            _state.update { it.copy(loadingEpisodes = true) }
            runCatching { uploaderDs.getCtvFeed(baseUrl, cookie, full.sidp, limit = 100) }
                .onSuccess { feed ->
                    _state.update { it.copy(loadingEpisodes = false, episodes = feed.episodes) }
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
            poster = t.thumbnail,
        )
        _state.update { it.copy(inFilmoteka = true) }
    }

    private fun isSaved(sidp: String): Boolean =
        workingSources.get(CTV_ID_SCHEME + sidp, null) != null

    /** Přehraj film (idec titulu) nebo konkrétní díl — resolve běží TADY, na zařízení. */
    fun playIdec(idec: String, label: String, posterUrl: String?) {
        if (idec.isBlank() || _state.value.resolvingIdec != null) return
        _state.update { it.copy(resolvingIdec = idec, error = null) }
        viewModelScope.launch {
            when (val r = ctvResolver.resolve(idec)) {
                is CtvStreamResolver.Result.Ok -> {
                    Timber.i("[VLTAVA] ČT hledání → play idec=%s", idec)
                    _state.update { it.copy(resolvingIdec = null) }
                    _play.value = PlayRequest(r.url, label, posterUrl)
                }
                else -> _state.update { it.copy(resolvingIdec = null, error = errorText(r)) }
            }
        }
    }

    fun consumePlay() { _play.value = null }

    fun dismissError() { _state.update { it.copy(error = null) } }

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
