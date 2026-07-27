package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.CtvEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.CtvTitle
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
        _state.value = UiState(title = title)
        // Film žádné díly nemá — kotva epizod je jen u pořadů (ČT sama říká `type`).
        val anchor = title.episodesAnchor
        if (title.isMovie || anchor.isNullOrBlank()) return
        _state.update { it.copy(loadingEpisodes = true) }
        viewModelScope.launch {
            runCatching { uploaderDs.getCtvFeed(baseUrl, cookie, title.sidp, limit = 100) }
                .onSuccess { feed ->
                    _state.update { it.copy(loadingEpisodes = false, episodes = feed.episodes) }
                }
                .onFailure { e ->
                    Timber.w(e, "[VLTAVA] díly ČT pořadu %s se nenačetly", title.sidp)
                    loadedFor = null
                    _state.update {
                        it.copy(loadingEpisodes = false, error = "Díly pořadu se nepodařilo načíst.")
                    }
                }
        }
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
