package com.github.jankoran90.showlyfin.feature.discover.podcasts

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.YtSearchChannel
import com.github.jankoran90.showlyfin.data.uploader.model.YtSearchVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * AMFITEÁTR (Filmy 1.2.129), user 2026-10-13: „hledat i na YouTube — musí se vždy zapnout". Přepínač v Hledat
 * (TV i telefon) začíná pokaždé vypnutý (obrazovka volá [reset] při novém vstupu), dětský profil ho nemá a
 * v Nastavení → Podcasty jde schovat úplně. Server hledá videa i kanály paralelně (~2 s, opakování z cache).
 * Přehrání / překlad / karta kanálu jdou přes [PodcastsViewModel] (syntetický zdroj + díl, viz [asSource]).
 */
@HiltViewModel
class YoutubeSearchViewModel @Inject constructor(
    private val remote: UploaderRemoteDataSource,
    private val profiles: ProfileRepository,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val on: Boolean = false,
        val loading: Boolean = false,
        val query: String = "",
        val videos: List<YtSearchVideo> = emptyList(),
        val channels: List<YtSearchChannel> = emptyList(),
        /** `yt:<id>` videí s hotovým českým překladem (štítek CZ). */
        val translated: Set<String> = emptySet(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null
    private var lastQuery = ""

    /** Nabízet přepínač? (Nastavení → Podcasty, a nikdy dětskému profilu.) */
    fun available(): Boolean =
        PodcastPrefs.searchYoutubeOffered(prefs) && profiles.activeProfile.value?.isAdmin != false

    /** Nový vstup do Hledat → přepínač zpět vypnutý, výsledky pryč. */
    fun reset() {
        job?.cancel()
        _state.value = UiState()
    }

    fun setOn(on: Boolean) {
        if (on && !available()) return
        _state.update { if (on) it.copy(on = true) else UiState() }
        if (on) search(lastQuery, debounce = false) else job?.cancel()
    }

    fun onQuery(query: String) {
        lastQuery = query
        val st = _state.value
        // Návrat z přehrávače znovu vyvolá onQuery se STEJNÝM dotazem → nové hledání by vrátilo jinou sadu
        // a právě pouštěné video by „zmizelo" (user 2026-10-13). Výsledky téhož dotazu nech.
        if (st.on && query.trim() == st.query && (st.loading || st.videos.isNotEmpty() || st.channels.isNotEmpty())) return
        if (st.on) search(query, debounce = true)
    }

    private fun search(query: String, debounce: Boolean) {
        job?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(loading = false, query = q, videos = emptyList(), channels = emptyList(), error = null) }
            return
        }
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        if (base.isBlank()) return
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        job = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            _state.update { it.copy(loading = true, query = q, error = null) }
            try {
                val res = remote.searchYoutubeGlobal(base, cookie, q)
                // Ne runCatching: pohltil by zrušení (nový dotaz) a starý výsledek by přepsal nový (Codex review).
                val translated = try {
                    remote.getDoneTranslations(base, cookie, res.videos.map { "yt:${it.id}" })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptySet()
                }
                _state.update {
                    it.copy(loading = false, videos = res.videos, channels = res.channels, translated = translated,
                        error = if (res.videos.isEmpty() && res.channels.isEmpty()) "Na YouTube nic nenalezeno." else null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[AMFITEATR] hledání na YouTube")
                // Chyba ≠ prázdný výsledek: staré výsledky nemaž, jen řekni, co se stalo.
                _state.update { it.copy(loading = false, error = "YouTube teď neodpovídá, zkus to znovu.") }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 600L
    }
}

/** Video z hledání jako díl syntetického zdroje → přehrání a překlad přes [PodcastsViewModel]. */
fun YtSearchVideo.asSource(): PodcastSource =
    PodcastSource(id = "yt-hledani", type = "youtube", ref = uploader.ifBlank { "youtube" }, title = uploader.ifBlank { "YouTube" })

fun YtSearchVideo.asEpisode(): SourceEpisode =
    SourceEpisode(
        id = id, title = title, subtitle = uploader, streamUrl = "", imageUrl = thumbnail,
        resumeKey = "yt:$id", durationSec = duration.toDouble(),
    )

fun YtSearchChannel.asSource(): PodcastSource =
    PodcastSource(id = "yt-hledani:$ref", type = "youtube", ref = ref, title = title, thumbnail = thumbnail)

/** „1:05:12" / „12:04" (0 = nic). */
fun durationLabel(sec: Int): String? {
    if (sec <= 0) return null
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
