package com.github.jankoran90.showlyfin.feature.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val uploaderDs: UploaderRemoteDataSource,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaybackUiState(subtitleStyle = loadStyle()))
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var query: SubtitleQuery? = null
    // PICKUP: klíč pozice je oddělený od `query` (titulky), protože resume musí fungovat i BEZ imdb.
    private var resumeKey: String? = null

    /** Play an arbitrary external HTTP(S) URL (e.g. RealDebrid direct link from Stremio). */
    fun loadExternal(url: String, title: String, subtitleQuery: SubtitleQuery? = null) {
        // PICKUP: u externích streamů (Stremio/RD) si pozici pamatujeme lokálně (Jellyfin ji řeší přes
        // server). Titulky vyžadují imdb (gate beze změny), ALE resume klíčujeme přes resumeKeyOf, který
        // má fallback na název+rok — obsah z Objevit/doporučení má imdb zatím prázdné (TMDB ho dohledá
        // později), takže gate na imdb by resume tiše vypnul (pozice se neuloží → dialog se nikdy nenabídne).
        query = subtitleQuery?.takeIf { it.imdb.isNotBlank() }
        resumeKey = subtitleQuery?.let { resumeKeyOf(it) }
        val savedResume = resumeKey?.let { prefs.getLong("resume_$it", 0L) } ?: 0L
        _state.update {
            it.copy(
                isLoading = false, title = title, streamUrl = url,
                positionMs = 0L, resumePositionMs = savedResume,
            )
        }
        if (query != null && uploaderBaseUrl.isNotBlank()) {
            loadSubtitles()
        }
    }

    /** PICKUP: ulož/aktualizuj pozici externího streamu pro pozdější „Pokračovat".
     *  Ukládá jen smysluplný úsek (od ~5 s); v posledních 30 s = dokoukáno → resume zahodí. */
    fun saveExternalPosition(positionMs: Long, durationMs: Long) {
        val key = resumeKey ?: return
        when {
            durationMs > 0L && positionMs >= durationMs - 30_000L ->
                prefs.edit().remove("resume_$key").apply()
            positionMs >= 5_000L ->
                prefs.edit().putLong("resume_$key", positionMs).apply()
        }
    }

    private fun loadSubtitles() {
        val q = query ?: return
        _state.update { it.copy(subtitlesLoading = true, subtitleError = null) }
        viewModelScope.launch {
            val resp = runCatching {
                uploaderDs.getSubtitles(
                    uploaderBaseUrl, uploaderCookie, q.imdb,
                    title = q.title, origTitle = q.origTitle, year = q.year,
                    season = q.season, episode = q.episode, release = q.release, fps = q.fps,
                )
            }.getOrElse { e ->
                timber.log.Timber.w(e, "[Titulky] getSubtitles selhalo imdb=${q.imdb}")
                _state.update { it.copy(subtitlesLoading = false, subtitleError = e.message ?: "Titulky nedostupné") }
                return@launch
            }
            _state.update { it.copy(subtitlesLoading = false, subtitleCandidates = resp.subtitles) }
            timber.log.Timber.i("[Titulky] ${resp.subtitles.size} CZ kandidátů, best=${resp.best}")
            if (resp.subtitles.isEmpty()) return@launch

            // Per-source: aplikuj uložený offset (před výběrem, ať se .srt zapíše s ním) + vyber uloženou stopu.
            val key = sourceKey(q)
            val storedOffset = loadSourceOffset(key)
            if (storedOffset != _state.value.subtitleStyle.offsetMs) {
                _state.update { it.copy(subtitleStyle = it.subtitleStyle.copy(offsetMs = storedOffset)) }
            }
            when (val storedId = loadSourceSelectedId(key)) {
                "OFF" -> selectSubtitle(-1, persist = false)
                null -> selectSubtitle(if (resp.best in resp.subtitles.indices) resp.best else 0, persist = false)
                else -> {
                    val idx = resp.subtitles.indexOfFirst { it.id == storedId }
                    selectSubtitle(if (idx >= 0) idx else (resp.best.takeIf { it in resp.subtitles.indices } ?: 0), persist = false)
                }
            }
        }
    }

    /** Vybere titulkovou stopu (index do subtitleCandidates), -1 = vypnout. persist=false při auto-výběru z paměti. */
    fun selectSubtitle(index: Int, persist: Boolean = true) {
        if (persist) query?.let { q ->
            val key = sourceKey(q)
            if (index < 0) saveSourceSelectedId(key, "OFF")
            else _state.value.subtitleCandidates.getOrNull(index)?.let { saveSourceSelectedId(key, it.id) }
        }
        if (index < 0) {
            _state.update { it.copy(selectedSubtitleIndex = -1, subtitleCues = emptyList(), subtitleRuntimeOk = "-") }
            return
        }
        val cand = _state.value.subtitleCandidates.getOrNull(index) ?: return
        val q = query
        _state.update { it.copy(subtitlesLoading = true, subtitleError = null) }
        viewModelScope.launch {
            val dl = runCatching {
                uploaderDs.downloadSubtitle(
                    uploaderBaseUrl, uploaderCookie, cand.id,
                    season = q?.season, episode = q?.episode, runtime = q?.runtime,
                )
            }.getOrElse { e ->
                timber.log.Timber.w(e, "[Titulky] download selhal id=${cand.id}")
                _state.update { it.copy(subtitlesLoading = false, subtitleError = e.message ?: "Stažení titulků selhalo") }
                return@launch
            }
            val cues = parseSrt(dl.bytes)
            _state.update {
                it.copy(
                    subtitlesLoading = false, selectedSubtitleIndex = index,
                    subtitleCues = cues, subtitleRuntimeOk = dl.runtimeOk,
                )
            }
            timber.log.Timber.i("[Titulky] stopa '${cand.release.ifBlank { cand.title }}' → ${cues.size} cue")
        }
    }

    // ── Styl titulků ─────────────────────────────────────────────────────────
    fun setFontScale(scale: Float) = updateStyle { it.copy(fontScale = scale.coerceIn(0.6f, 2.0f)) }
    fun setColor(argb: Int) = updateStyle { it.copy(colorArgb = argb) }
    fun setBottomPadding(fraction: Float) = updateStyle { it.copy(bottomPaddingFraction = fraction.coerceIn(0.0f, 0.4f)) }

    /** Posun synchronizace. delta v ms (+ = titulky později, − = dříve). Per-source.
     *  Okamžitý — render aplikuje offset live, žádné přepisování .srt ani re-prepare videa. */
    fun nudgeOffset(deltaMs: Long) {
        val newOffset = _state.value.subtitleStyle.offsetMs + deltaMs
        _state.update { it.copy(subtitleStyle = it.subtitleStyle.copy(offsetMs = newOffset)) }
        query?.let { saveSourceOffset(sourceKey(it), newOffset) }
    }

    private fun updateStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        val s = transform(_state.value.subtitleStyle)
        _state.update { it.copy(subtitleStyle = s) }
        saveStyle(s)
    }

    // Globální styl (velikost/barva/pozice) — offset NE, ten je per-source (viz níže).
    private fun loadStyle() = SubtitleStyle(
        fontScale = prefs.getFloat("sub_font_scale", 1.0f),
        colorArgb = prefs.getInt("sub_color_argb", 0xFFFFBF00.toInt()),
        bottomPaddingFraction = prefs.getFloat("sub_bottom_pad", 0.08f),
        offsetMs = 0L,
    )

    private fun saveStyle(s: SubtitleStyle) {
        prefs.edit()
            .putFloat("sub_font_scale", s.fontScale)
            .putInt("sub_color_argb", s.colorArgb)
            .putFloat("sub_bottom_pad", s.bottomPaddingFraction)
            .apply()
    }

    // ── Per-source paměť (offset + vybraná stopa) ────────────────────────────
    // Klíč zdroje = imdb (+ s/e u seriálů). Pamatuje se zpoždění a vybraná stopa
    // jen pro konkrétní film/epizodu; nový zdroj startuje s defaultem (best + 0 s).
    private fun sourceKey(q: SubtitleQuery): String {
        val se = if (q.season != null && q.episode != null) "_s${q.season}e${q.episode}" else ""
        return "${q.imdb}$se"
    }

    /** PICKUP: stabilní klíč pozice přehrávání. Když máme imdb, použij ho (sdílí klíč s budoucí TV
     *  resume, PICKUP F2). Bez imdb fallback na normalizovaný název+rok, ať resume funguje i pro
     *  obsah z Objevit/doporučení (imdb dohledán z TMDB až později). Plné sjednocení napříč VM =
     *  `ResumeStore` v PICKUP F2; tady minimální robustní varianta pro telefon. */
    private fun resumeKeyOf(q: SubtitleQuery): String {
        val base = if (q.imdb.isNotBlank()) q.imdb
        else "t:" + q.origTitle.ifBlank { q.title }.lowercase().filter { it.isLetterOrDigit() }.take(40) + (q.year ?: 0)
        val se = if (q.season != null && q.episode != null) "_s${q.season}e${q.episode}" else ""
        return "$base$se"
    }
    private fun loadSourceOffset(key: String): Long = prefs.getLong("sub_off_$key", 0L)
    private fun saveSourceOffset(key: String, ms: Long) = prefs.edit().putLong("sub_off_$key", ms).apply()
    private fun loadSourceSelectedId(key: String): String? = prefs.getString("sub_sel_$key", null)
    private fun saveSourceSelectedId(key: String, id: String) = prefs.edit().putString("sub_sel_$key", id).apply()

    private val cueTimeRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""",
    )
    private val tagRegex = Regex("<[^>]+>")
    // Pozn.: závěrečná } MUSÍ být escapovaná — ICU regex engine (Android) jinak hodí
    // PatternSyntaxException při kompilaci → pád PlaybackViewModel.<init> (crash při každém přehrání).
    private val assPosRegex = Regex("""\{[^}]*\}""")

    /** Naparsuje .srt (UTF-8) na seznam cue. Offset se NEzapéká — aplikuje se až při renderu. */
    private fun parseSrt(bytes: ByteArray): List<SubtitleCue> {
        val text = bytes.toString(Charsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n")
        val cues = ArrayList<SubtitleCue>()
        for (block in text.split(Regex("\n[ \t]*\n"))) {
            val lines = block.trim('\n', ' ', '\t').split("\n")
            val tIdx = lines.indexOfFirst { it.contains("-->") }
            if (tIdx < 0) continue
            val m = cueTimeRegex.find(lines[tIdx]) ?: continue
            val (h1, m1, s1, ms1, h2, m2, s2, ms2) = m.destructured
            val start = h1.toLong() * 3600000 + m1.toLong() * 60000 + s1.toLong() * 1000 + ms1.padEnd(3, '0').take(3).toLong()
            val end = h2.toLong() * 3600000 + m2.toLong() * 60000 + s2.toLong() * 1000 + ms2.padEnd(3, '0').take(3).toLong()
            val content = lines.drop(tIdx + 1).joinToString("\n")
                .replace(tagRegex, "").replace(assPosRegex, "").trim()
            if (content.isNotEmpty()) cues.add(SubtitleCue(start, end, content))
        }
        return cues
    }

    fun load(itemId: String, positionMs: Long) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val serverUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""

            if (serverUrl.isBlank() || token.isBlank()) {
                _state.update {
                    it.copy(isLoading = false, error = "Jellyfin není nastaven. Přejdi do Jellyfin záložky.")
                }
                return@launch
            }

            try {
                apiClient.update(
                    baseUrl = serverUrl,
                    accessToken = token,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )

                val userUuid = UUID.fromString(userId)
                val item = runCatching {
                    apiClient.userLibraryApi.getItem(userId = userUuid, itemId = UUID.fromString(itemId)).content
                }.getOrNull()

                // Seriál nelze přehrát přímo (vrací HTTP 500) → najdi epizodu (Next Up / první nezhlédnutá).
                var playItemId = itemId
                var title = item?.name ?: itemId
                var playItem: BaseItemDto? = item
                if (item?.type == BaseItemKind.SERIES) {
                    val episode = resolveSeriesEpisode(UUID.fromString(itemId), userUuid)
                    if (episode != null) {
                        playItemId = episode.id.toString()
                        title = listOfNotNull(item.name, episode.name).joinToString(" — ")
                        playItem = episode
                    }
                }

                val userResumeMs = (playItem?.userData?.playbackPositionTicks ?: 0L) / 10_000L
                val resumeMs = if (positionMs > 0L) positionMs else userResumeMs

                val streamUrl = "$serverUrl/Videos/$playItemId/stream?static=true&api_key=$token"

                _state.update {
                    it.copy(
                        isLoading = false,
                        title = title,
                        streamUrl = streamUrl,
                        positionMs = positionMs,
                        resumePositionMs = resumeMs,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba přehrávání") }
            }
        }
    }

    /** Vrátí epizodu k přehrání pro seriál: Next Up → jinak první nezhlédnutá → jinak první. */
    private suspend fun resolveSeriesEpisode(seriesId: UUID, userUuid: UUID): BaseItemDto? {
        val nextUp = runCatching {
            apiClient.tvShowsApi.getNextUp(userId = userUuid, seriesId = seriesId).content.items
        }.getOrNull().orEmpty()
        nextUp.firstOrNull()?.let { return it }
        val episodes = runCatching {
            apiClient.tvShowsApi.getEpisodes(seriesId = seriesId, userId = userUuid).content.items
        }.getOrNull().orEmpty()
        return episodes.firstOrNull { it.userData?.played != true } ?: episodes.firstOrNull()
    }
}
