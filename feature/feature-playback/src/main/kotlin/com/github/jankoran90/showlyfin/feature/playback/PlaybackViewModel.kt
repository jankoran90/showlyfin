package com.github.jankoran90.showlyfin.feature.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleCandidate
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.data.uploader.subtitle.SubtitleTranslationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val translateStore: SubtitleTranslationStore,
    private val videoResumeStore: VideoResumeStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlaybackUiState(
            subtitleStyle = loadStyle(),
            controlsHideSec = prefs.getInt(PlayerPrefs.CONTROLS_HIDE_SEC_KEY, PlayerPrefs.DEFAULT_CONTROLS_HIDE_SEC),
            seekStepSec = prefs.getInt(PlayerPrefs.SEEK_STEP_SEC_KEY, PlayerPrefs.DEFAULT_SEEK_STEP_SEC),
        ),
    )
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var query: SubtitleQuery? = null
    // PICKUP: klíč pozice je oddělený od `query` (titulky), protože resume musí fungovat i BEZ imdb.
    private var resumeKey: String? = null
    // REWIND (SHW-68): klíč lokálního resume VIDEA (JF-item playback). Předaný z volajícího (NaVýbornou
    // video → sdílený klíč s RSS epizodou). Null = neukládáme lokálně (např. film z Detailu = jen server).
    private var videoResumeKey: String? = null
    // LINGUA Fáze 3: klíč běžícího/dřívějšího AI překladu + observer sdíleného store (worker na pozadí).
    private var translateKey: String? = null
    private var translateObserveJob: Job? = null

    /** Play an arbitrary external HTTP(S) URL (e.g. RealDebrid direct link from Stremio). */
    fun loadExternal(url: String, title: String, subtitleQuery: SubtitleQuery? = null, posterUrl: String? = null) {
        // PICKUP: u externích streamů (Stremio/RD) si pozici pamatujeme lokálně (Jellyfin ji řeší přes
        // server). Titulky vyžadují imdb (gate beze změny), ALE resume klíčujeme přes resumeKeyOf, který
        // má fallback na název+rok — obsah z Objevit/doporučení má imdb zatím prázdné (TMDB ho dohledá
        // později), takže gate na imdb by resume tiše vypnul (pozice se neuloží → dialog se nikdy nenabídne).
        // CONDUIT (SHW-58): u dabovaného zdroje (autoSearch=false) NEhledáme titulky, ale resume klíč
        // držíme dál (resume musí fungovat i bez titulků).
        query = subtitleQuery?.takeIf { it.imdb.isNotBlank() && it.autoSearch }
        resumeKey = subtitleQuery?.let { resumeKeyOf(it) }
        val savedResume = resumeKey?.let { prefs.getLong("resume_$it", 0L) } ?: 0L
        timber.log.Timber.i("[PICKUP] loadExternal resumeKey=%s saved=%d autoSearch=%s url=%s", resumeKey, savedResume, subtitleQuery?.autoSearch, url.take(70))
        // Nový stream → VŽDY vyhoď titulky z předchozího filmu. Bez tohohle by film bez vlastních
        // titulků (0 kandidátů / prázdné imdb → loadSubtitles se brzy vrátí) dál promítal cues
        // z minule přehraného filmu = „české titulky na úplně jiný film" (Old Joy). loadSubtitles
        // si vzápětí nastaví subtitlesLoading=true a naplní vlastní kandidáty.
        _state.update {
            it.copy(
                isLoading = false, title = title, streamUrl = url, posterUrl = posterUrl,
                positionMs = 0L, resumePositionMs = savedResume,
                subtitleCues = emptyList(), selectedSubtitleIndex = -1,
                subtitleCandidates = emptyList(), subtitleRuntimeOk = "-", subtitleError = null,
                canTranslateAi = false, aiTranslating = false, aiTranslateError = null,
            )
        }
        // Nový film → zruš observer překladu z minulého (ať jeho Done/Error nepřepíše tenhle film).
        translateObserveJob?.cancel(); translateObserveJob = null; translateKey = null
        if (query != null && uploaderBaseUrl.isNotBlank()) {
            loadSubtitles()
        }
    }

    /**
     * NOMAD (SHW-60): offline přehrání STAŽENÉHO souboru z telefonu (file://) + případně lokální .srt.
     * Žádná síť — resume klíčujeme přes [offlineKey] do stejných `resume_` prefs jako externí streamy
     * (saveExternalPosition se postará o průběžné ukládání), takže „Pokračovat / Od začátku" funguje i offline.
     */
    fun loadLocal(videoPath: String, subtitlePath: String?, title: String, offlineKey: String, posterPath: String? = null) {
        query = null
        resumeKey = offlineKey.takeIf { it.isNotBlank() }
        val savedResume = resumeKey?.let { prefs.getLong("resume_$it", 0L) } ?: 0L
        val videoUri = android.net.Uri.fromFile(java.io.File(videoPath)).toString()
        val posterUri = posterPath?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
        translateObserveJob?.cancel(); translateObserveJob = null; translateKey = null
        _state.update {
            it.copy(
                isLoading = false, title = title, streamUrl = videoUri, posterUrl = posterUri,
                positionMs = 0L, resumePositionMs = savedResume,
                subtitleCues = emptyList(), selectedSubtitleIndex = -1,
                subtitleCandidates = emptyList(), subtitleRuntimeOk = "-", subtitleError = null,
                canTranslateAi = false, aiTranslating = false, aiTranslateError = null,
            )
        }
        if (subtitlePath != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val cues = runCatching { parseSrt(java.io.File(subtitlePath).readBytes()) }.getOrDefault(emptyList())
                if (cues.isNotEmpty()) _state.update { it.copy(subtitleCues = cues, selectedSubtitleIndex = 0) }
            }
        }
    }

    /** REWIND (SHW-68): ulož/aktualizuj pozici JF-item VIDEA lokálně (per [videoResumeKey]). Showlyfin
     *  nehlásí JF progress zpět na server → video resume děláme lokálně (jako external streamy).
     *  No-op bez klíče (film z Detailu → jen serverový resume, beze změny chování). */
    fun saveVideoPosition(positionMs: Long, durationMs: Long) {
        val key = videoResumeKey ?: return
        videoResumeStore.save(key, positionMs, durationMs)
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
            // LINGUA: 0 CZ titulků + máme imdb → AI překlad jako poslední záloha. Fáze 3: dřív
            // přeložené (persistované) nasaď sám = „nezmizí nikdy"; jinak nabídni tlačítko a pozoruj
            // překlad běžící na pozadí (worker přežije odchod z přehrávače).
            if (resp.subtitles.isEmpty()) {
                if (q.imdb.isNotBlank()) setupAiTranslation(q)
                return@launch
            }

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

    /** LINGUA Fáze 3: připrav AI překlad pro film bez CZ titulků. Když už byl jednou přeložen
     *  (persistováno), nasaď češtinu **sám bez ptaní** (auto-nasazení po návratu = „nezmizí nikdy").
     *  Jinak nabídni tlačítko a pozoruj sdílený store — překlad běží ve workeru NA POZADÍ, takže
     *  doběhne i po odchodu z přehrávače a tady ho jen převezmeme, je-li obrazovka ještě otevřená. */
    private fun setupAiTranslation(q: SubtitleQuery) {
        val key = translateStore.keyOf(q.imdb, q.season, q.episode)
        translateKey = key
        val doneId = translateStore.doneSubId(key)
        if (doneId != null) {
            timber.log.Timber.i("[Lingua] film už přeložen ($key) → nasazuji AI češtinu automaticky")
            applyAiSubtitle(doneId)
            return
        }
        _state.update { it.copy(canTranslateAi = true) }
        observeTranslation(key)
    }

    /** Přidá AI českou stopu mezi kandidáty (není-li už) a vybere ji. Idempotentní. */
    private fun applyAiSubtitle(subId: String) {
        val existing = _state.value.subtitleCandidates.indexOfFirst { it.id == subId }
        if (existing >= 0) {
            _state.update { it.copy(aiTranslating = false, canTranslateAi = false) }
            if (_state.value.selectedSubtitleIndex != existing) selectSubtitle(existing)
            return
        }
        val aiCand = SubtitleCandidate(id = subId, title = "AI překlad (čeština)", lang = "cs", imdbMatch = true)
        val newList = _state.value.subtitleCandidates + aiCand
        _state.update { it.copy(aiTranslating = false, canTranslateAi = false, subtitleCandidates = newList) }
        timber.log.Timber.i("[Lingua] AI stopa nasazena $subId")
        selectSubtitle(newList.lastIndex)
    }

    /** Sleduje sdílený store (worker na pozadí ho plní) pro daný film a promítá stav do UI. */
    private fun observeTranslation(key: String) {
        translateObserveJob?.cancel()
        translateObserveJob = viewModelScope.launch {
            translateStore.jobs
                .map { it[key] }
                .distinctUntilChanged()
                .collect { st ->
                    if (translateKey != key) return@collect
                    when (st) {
                        is SubtitleTranslationStore.State.Running ->
                            _state.update { it.copy(aiTranslating = true, aiTranslateError = null) }
                        is SubtitleTranslationStore.State.Done -> applyAiSubtitle(st.subId)
                        is SubtitleTranslationStore.State.Error ->
                            _state.update { it.copy(aiTranslating = false, aiTranslateError = st.message) }
                        null -> Unit
                    }
                }
        }
    }

    /** Uživatel ťukl na „Přeložit do češtiny (AI)" → zařadí překlad NA POZADÍ (přežije odchod
     *  z přehrávače) + dá vědět notifikací, až je hotovo. UI převezme výsledek přes [observeTranslation]. */
    fun translateSubtitlesAi() {
        val q = query ?: return
        if (q.imdb.isBlank() || _state.value.aiTranslating) return
        val base = uploaderBaseUrl
        if (base.isBlank()) {
            _state.update { it.copy(aiTranslateError = "Není nastavený server pro titulky") }
            return
        }
        val key = translateStore.keyOf(q.imdb, q.season, q.episode)
        translateKey = key
        _state.update { it.copy(aiTranslating = true, aiTranslateError = null) }
        translateStore.setRunning(key)
        observeTranslation(key)
        translateStore.enqueueTranslate(appContext, base, uploaderCookie, q.imdb, _state.value.title, q.season, q.episode)
        timber.log.Timber.i("[Lingua] překlad zařazen na pozadí ${q.imdb}")
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

    fun load(itemId: String, positionMs: Long, resumeKey: String? = null, fallbackTitle: String = "") {
        // REWIND: klíč lokálního video resume (NaVýbornou video sdílí klíč s RSS epizodou; null = jen server).
        videoResumeKey = resumeKey
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
                // REWIND: notifikace/název = JF name; když JF lookup nedá použitelný název (např.
                // NaVýbornou video), použij název epizody z volajícího (ne syrové itemId/UUID).
                var title = item?.name?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { itemId }
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
                // REWIND: vezmi pozdější ze serverového (jiný klient / box) a lokálního resume (telefon).
                val localResumeMs = resumeKey?.let { videoResumeStore.get(it)?.posMs } ?: 0L
                val resumeMs = if (positionMs > 0L) positionMs else maxOf(userResumeMs, localResumeMs)

                val streamUrl = "$serverUrl/Videos/$playItemId/stream?static=true&api_key=$token"

                _state.update {
                    it.copy(
                        isLoading = false,
                        title = title,
                        streamUrl = streamUrl,
                        // MARQUEE: plakát filmu/seriálu z Jellyfinu do systémové notifikace.
                        posterUrl = "$serverUrl/Items/$itemId/Images/Primary?api_key=$token",
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
