package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.maestro.AvrController
import com.github.jankoran90.showlyfin.data.maestro.BoxController
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrBoxHostOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrBoxMacOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrEnabledOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrHostOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrTvHostOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrVolumeStepOrDefault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * RELAY / sekce „Ovladač" — real-time dálkové ovládání běžící Jellyfin TV session.
 * Pollne `/Sessions` co ~2 s, dokud je sekce viditelná (start/stop z obrazovky). Ovládání jde
 * přes [NaTvService] (Playstate + GeneralCommand) creds aktivního profilu (`traktPreferences`).
 */
@HiltViewModel
class OvladacViewModel @Inject constructor(
    private val naTv: NaTvService,
    private val avr: AvrController,
    private val box: BoxController,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val noCreds: Boolean = false,
        /** Všechny remote-control schopné session (= dostupné TV pro přepínač). */
        val sessions: List<JellyfinSessionSummary> = emptyList(),
        /** Ručně zvolené zařízení; null = auto-pick (Wolphin/běžící). */
        val selectedId: String? = null,
        /** Vyřešená aktivní session, kterou ovládáme. */
        val current: JellyfinSessionSummary? = null,
        val coverUrl: String? = null,
        /** AVR (Pioneer) ovládání hlasitosti zapnuté v Nastavení + IP vyplněná. */
        val avrEnabled: Boolean = false,
        /** AVR odpovídá na síti (jinak fallback na hlasitost JF session). */
        val avrReachable: Boolean = false,
        /** Poslední známá absolutní hlasitost AVR (0..[AvrController.MAX_VOLUME]). */
        val avrVolume: Int? = null,
        val avrMuted: Boolean = false,
        /** Krok hlasitosti +/- (jednotky AVR), z Nastavení; default 3. */
        val avrVolumeStep: Int = 3,
        /** Probíhající akce napájení sestavy (zapínám/vypínám…), null = nic. */
        val sceneStatus: String? = null,
        /** FERRY/BATON: titul externího streamu běžícího na TV (když JF NowPlaying chybí). null = běžný JF obsah / nic. */
        val externalTitle: String? = null,
        /** FERRY/BATON: poster castnutého filmu (cover v Ovladači jako u knihovny). */
        val externalPosterUrl: String? = null,
        /** FERRY/BATON: TMDb id castnutého filmu → klik na cover vrátí na kartu filmu. */
        val externalTmdb: Long? = null,
        /** CONSOLE: běží externí stream (FERRY) → ukázat panel nastavení obrazu/titulků. */
        val isExternal: Boolean = false,
        /** CONSOLE: lokální stav panelu nastavení (poslané na box). */
        val displayResizeMode: String = "fit",
        val subFontSizeSp: Int = 28,
        val subBottomMarginPct: Int = 5,
        val subColorArgb: Int? = null,
        /** CONSOLE: 4 uživatelsky uložitelné barevné pozice titulků (ARGB). */
        val subColorSlots: List<Int> = DEFAULT_COLOR_SLOTS,
        /** CONSOLE: časový posun titulků na TV v ms (− = dřív, + = později). */
        val subOffsetMs: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var pollJob: Job? = null
    private var userSelectedId: String? = null

    init {
        // CONSOLE: načti uložené barevné pozice titulků (přežijí restart; po fixu revokeToken i Trakt logout).
        val slots = (0 until 4).map { i ->
            prefs.getInt("sub_color_slot_$i", DEFAULT_COLOR_SLOTS[i])
        }
        _state.update { it.copy(subColorSlots = slots) }
    }

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stop()
    }

    /** Uživatel přepnul zařízení nahoře v přepínači (null = zpět na auto). */
    fun selectDevice(sessionId: String?) {
        userSelectedId = sessionId
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val c = creds() ?: run {
            _state.update { it.copy(loading = false, noCreds = true, sessions = emptyList(), current = null, coverUrl = null) }
            return
        }
        val sessions = naTv.getSessions(c.url, c.token)
        var current = sessions.firstOrNull { it.sessionId == userSelectedId }
            ?: naTv.pickWatchSession(sessions)
        // BATON: externí stream (FERRY) — box nehlásí JF NowPlaying. Když na boxu běží náš cast,
        // dotáhni pozici/délku/stav z `/api/ferry/state` a vlož do `current` → posuvník + scrub fungují.
        val fc = activeFerryCastFor(current)
        val ferrySt = fc?.reportUrl?.let { naTv.getFerryState(it) }
        if (ferrySt != null && current != null) {
            current = current.copy(
                positionTicks = ferrySt.positionMs * TICKS_PER_MS,
                runtimeTicks = ferrySt.durationMs * TICKS_PER_MS,
                isPlaying = !ferrySt.paused,
                isPaused = ferrySt.paused,
                canSeek = ferrySt.durationMs > 0L,
                // CONSOLE: externí stream nemá JF MediaStreams → stopy bere z FERRY reportu boxu,
                // ať Ovladač umí přepínat titulky/audio i u RD/Stremio streamů (jako u knihovny).
                subtitleTracks = ferrySt.subtitleTracks,
                audioTracks = ferrySt.audioTracks,
                currentSubtitleIndex = ferrySt.currentSubtitleIndex,
                currentAudioIndex = ferrySt.currentAudioIndex,
            )
        }
        val cover = current?.itemId?.let { naTv.imageUrl(c.url, c.token, it, current.imageTag) }
        val avrCfg = avrConfig()
        val avrStatus = avrCfg?.let { avr.status(it) }
        _state.update {
            it.copy(
                loading = false,
                noCreds = false,
                sessions = sessions,
                current = current,
                selectedId = current?.sessionId,
                coverUrl = cover,
                externalTitle = if (fc != null) (ferrySt?.title?.takeIf { t -> t.isNotBlank() } ?: fc.title) else null,
                externalPosterUrl = fc?.posterUrl,
                externalTmdb = fc?.tmdbId,
                isExternal = fc != null,
                avrEnabled = avrCfg != null,
                avrReachable = avrStatus?.reachable ?: false,
                // Priorita: živé čtení AVR → poslední známá → výchozí z Nastavení (`avr_default_volume`).
                // eISCP MVLQSTN je nespolehlivá → bez defaultu by bar v externím režimu visel na „—".
                avrVolume = avrStatus?.volume ?: it.avrVolume ?: avrDefaultVolume(),
                avrMuted = if (avrStatus?.reachable == true) avrStatus.muted else it.avrMuted,
                avrVolumeStep = avrVolumeStepPref(),
            )
        }
    }

    /**
     * FERRY/BATON: když na boxu (Yellyfin) běží náš externí stream, JF nehlásí NowPlaying → vrátíme
     * zapamatovaný cast (titul/cover/tmdb/report URL), ať Ovladač neukáže „Nic nehraje" a může číst
     * pozici. Jen pro Yellyfin session bez NowPlaying a po dobu TTL (film může běžet hodiny).
     */
    private fun activeFerryCastFor(current: JellyfinSessionSummary?): ListenNavSignal.FerryCast? {
        if (current == null || current.nowPlayingTitle != null) return null
        val isYellyfin = "${current.client.orEmpty()} ${current.deviceName}".lowercase().contains("yellyfin")
        if (!isYellyfin) return null
        val fc = ListenNavSignal.ferryCast.value ?: return null
        return if (System.currentTimeMillis() - fc.startedAtMs < EXTERNAL_TTL_MS) fc else null
    }

    /** Klik na cover externího streamu → vrať se na kartu filmu (detail / RD sekce). */
    fun openCastDetail() {
        val fc = ListenNavSignal.ferryCast.value ?: return
        fc.tmdbId?.let { ListenNavSignal.requestOpenDetail(it, fc.title) }
    }

    /** Vrátí host AVR pokud je ovládání hlasitosti přes AVR povolené (default zapnuto + předvyplněná IP). */
    private fun avrConfig(): String? {
        if (!prefs.avrEnabledOrDefault()) return null
        return prefs.avrHostOrDefault().takeIf { it.isNotBlank() }
    }

    private suspend fun refreshAvrOnly(host: String) {
        val s = avr.status(host)
        _state.update {
            it.copy(
                avrReachable = s.reachable,
                avrVolume = s.volume ?: it.avrVolume ?: avrDefaultVolume(),
                avrMuted = if (s.reachable) s.muted else it.avrMuted,
            )
        }
    }

    /** Ručně zapnout celou sestavu (receiver + probudit TV + box + spustit přehrávač). */
    fun powerOnSystem() = sceneAction("Zapínám obývák…") {
        // Prvky sestavy probouzíme paralelně — když jeden vázne (timeout boxu), nezdrží ostatní.
        coroutineScope {
            avrConfig()?.let { host ->
                launch {
                    avr.powerOn(host)
                    // Respekt k vlastní power-on hlasitosti AVR: hodnotu nastavíme JEN když ji user
                    // v appce přímo zadal (override). Prázdné = ponecháme na receiveru (děti ráno).
                    avrDefaultVolume()?.let { delay(POWER_ON_VOL_DELAY_MS); avr.setVolume(host, it) }
                }
            }
            tvHost()?.let { launch { box.wake(it) } }
            boxMac()?.let { launch { box.wakeViaWol(it) } }
            boxHost()?.let { launch { box.wakeAndLaunch(it) } }
        }
        null
    }

    /** Ručně vypnout sestavu (receiver do standby + uspat TV i box). */
    fun powerOffSystem() = sceneAction("Vypínám obývák…") {
        // Vše paralelně + ohraničeno timeoutem v BoxController → dialog nikdy nevisí.
        val boxOk: Boolean? = coroutineScope {
            avrConfig()?.let { launch { avr.powerOff(it) } }
            val tv = tvHost()?.let { async { box.sleep(it) } }
            val bx = boxHost()?.let { async { box.sleep(it) } }
            val results = listOfNotNull(tv, bx).map { it.await() }
            if (results.isEmpty()) null else results.any { it }
        }
        // Box nakonfigurován, ale ani TV ani box se nepodařilo uspat → typicky chybí ADB autorizace.
        if (boxOk == false) "Box se nepodařilo uspat — autorizuj ADB na boxu (dialog na TV)." else null
    }

    /**
     * Spustí scénu (zapnout/vypnout sestavu). [block] vrátí volitelnou hlášku, která se krátce ukáže
     * (např. nutnost ADB autorizace), pak se dialog vždy schová — i kdyby ADB pod tím ještě doznívalo.
     */
    private fun sceneAction(status: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(sceneStatus = status) }
            val result = runCatching { block() }.getOrNull()
            delay(COMMAND_SETTLE_MS)
            avrConfig()?.let { refreshAvrOnly(it) }
            if (result != null) {
                _state.update { it.copy(sceneStatus = result) }
                delay(SCENE_RESULT_MS)
            }
            _state.update { it.copy(sceneStatus = null) }
        }
    }

    private fun boxHost(): String? = prefs.avrBoxHostOrDefault().takeIf { it.isNotBlank() }

    private fun boxMac(): String? = prefs.avrBoxMacOrDefault().takeIf { it.isNotBlank() }

    private fun tvHost(): String? = prefs.avrTvHostOrDefault().takeIf { it.isNotBlank() }

    /** Override výchozí hlasitosti po zapnutí; null = ponechat na receiveru (respekt). */
    private fun avrDefaultVolume(): Int? =
        prefs.getString("avr_default_volume", "").orEmpty().trim().toIntOrNull()?.takeIf { it > 0 }

    /** Krok hlasitosti +/- (jednotky AVR), default 3. */
    private fun avrVolumeStepPref(): Int = prefs.avrVolumeStepOrDefault()

    fun playPause() = command { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "PlayPause") }
    fun stopPlayback() {
        ListenNavSignal.clearFerryCast()
        command { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "Stop") }
    }

    // --- PILOT: virtuální D-pad — navigace nativním UI na TV přes Jellyfin GeneralCommand.
    // Yellyfin na boxu je přeloží na injektnuté D-pad klávesy (viz RemoteControlReceiver).
    fun navUp() = nav("MoveUp")
    fun navDown() = nav("MoveDown")
    fun navLeft() = nav("MoveLeft")
    fun navRight() = nav("MoveRight")
    fun navSelect() = nav("Select")
    fun navBack() = nav("Back")
    fun navHome() = nav("GoHome")
    private fun nav(name: String) = command { c, id -> naTv.sendGeneralCommand(c.url, c.token, id, name) }

    /** True, když je dostupná ovladatelná TV session (= box/Yellyfin běží) → power tlačítko zelené. */
    fun isTvOn(): Boolean = _state.value.sessions.isNotEmpty()

    /** Power tlačítko D-padu: dle stavu TV zapne/vypne celou sestavu (MAESTRO). */
    fun togglePower() {
        if (isTvOn()) powerOffSystem() else powerOnSystem()
    }

    fun seekBy(deltaMs: Long) = command { c, id ->
        val cur = _state.value.current ?: return@command false
        val newPos = (cur.positionTicks + deltaMs * TICKS_PER_MS).coerceAtLeast(0L)
        naTv.sendSeek(c.url, c.token, id, newPos)
    }

    /** Absolutní seek z posuvníku (0f..1f frakce runtime). */
    fun seekToFraction(fraction: Float) = command { c, id ->
        val cur = _state.value.current ?: return@command false
        if (cur.runtimeTicks <= 0L) return@command false
        val target = (cur.runtimeTicks * fraction.coerceIn(0f, 1f)).toLong()
        naTv.sendSeek(c.url, c.token, id, target)
    }

    /** JF fallback (bez AVR): absolutní hlasitost na session. AVR jede přes [avrVolumeStep]. */
    fun applyVolume(volume: Int) =
        command { c, id -> naTv.setVolume(c.url, c.token, id, volume) }

    /**
     * RELATIVNÍ změna hlasitosti AVR o [steps] jednotek (`MVLUP`/`MVLDOWN`). Mění od reálné úrovně
     * AVR → nikdy neskočí na 0/ticho, i když se aktuální hodnota zrovna nepřečetla. Optimisticky
     * posuneme bar, poll pak potvrdí reálnou hodnotu z AVR.
     */
    fun avrVolumeStep(steps: Int) {
        val host = avrConfig() ?: return
        viewModelScope.launch {
            _state.update { st -> st.copy(avrVolume = st.avrVolume?.let { (it + steps).coerceIn(0, AvrController.MAX_VOLUME) }) }
            avr.volumeStep(host, steps)
            delay(COMMAND_SETTLE_MS)
            refreshAvrOnly(host)
        }
    }

    /** Ztlumení: AVR `AMT`, jinak JF session. */
    fun toggleVolumeMute() {
        val host = avrConfig()
        if (host == null) {
            command { c, id -> naTv.toggleMute(c.url, c.token, id) }
            return
        }
        viewModelScope.launch {
            val newMute = !_state.value.avrMuted
            _state.update { it.copy(avrMuted = newMute) }
            avr.setMute(host, newMute)
            delay(COMMAND_SETTLE_MS)
            refreshAvrOnly(host)
        }
    }
    fun setSubtitle(index: Int) = command { c, id -> naTv.setSubtitleIndex(c.url, c.token, id, index) }
    fun setAudio(index: Int) = command { c, id -> naTv.setAudioIndex(c.url, c.token, id, index) }

    // --- CONSOLE (SHW-39): nastavení obrazu/titulků externího přehrávače z Ovladače.
    /** Poměr obrazu na TV: "fit" | "zoom" | "fill". */
    fun setResizeMode(mode: String) {
        _state.update { it.copy(displayResizeMode = mode) }
        sendDisplayConfig(resizeMode = mode)
    }
    /** Změní velikost titulků o [delta] sp (rozsah 12..60) a pošle na box. */
    fun nudgeSubFontSize(delta: Int) {
        val v = (_state.value.subFontSizeSp + delta).coerceIn(12, 60)
        _state.update { it.copy(subFontSizeSp = v) }
        sendDisplayConfig(subFontSizeSp = v)
    }
    /** Posune titulky nahoru/dolů o [delta] % výšky (rozsah 0..40) a pošle na box. */
    fun nudgeSubMargin(delta: Int) {
        val v = (_state.value.subBottomMarginPct + delta).coerceIn(0, 40)
        _state.update { it.copy(subBottomMarginPct = v) }
        sendDisplayConfig(subBottomMarginPct = v)
    }
    /** Nastaví barvu titulků (ARGB) a pošle na box. */
    fun setSubColor(argb: Int) {
        _state.update { it.copy(subColorArgb = argb) }
        sendDisplayConfig(subColorArgb = argb)
    }
    /** Uloží vybranou barvu na pozici [slot] (0..3), aplikuje ji a zapamatuje napříč restarty. */
    fun saveColorToSlot(slot: Int, argb: Int) {
        if (slot !in 0..3) return
        val slots = _state.value.subColorSlots.toMutableList().also { it[slot] = argb }
        prefs.edit().putInt("sub_color_slot_$slot", argb).apply()
        _state.update { it.copy(subColorSlots = slots, subColorArgb = argb) }
        sendDisplayConfig(subColorArgb = argb)
    }
    /** Posun titulků v čase o [deltaMs] (− dřív / + později), rozsah ±10 s, krok obvykle ±100 ms. */
    fun nudgeSubOffset(deltaMs: Int) {
        val v = (_state.value.subOffsetMs + deltaMs).coerceIn(-10_000, 10_000)
        _state.update { it.copy(subOffsetMs = v) }
        sendDisplayConfig(subOffsetMs = v)
    }
    fun resetSubOffset() {
        _state.update { it.copy(subOffsetMs = 0) }
        sendDisplayConfig(subOffsetMs = 0)
    }

    private fun sendDisplayConfig(
        resizeMode: String? = null,
        subFontSizeSp: Int? = null,
        subColorArgb: Int? = null,
        subBottomMarginPct: Int? = null,
        subOffsetMs: Int? = null,
    ) {
        viewModelScope.launch {
            val c = creds() ?: return@launch
            naTv.castFerryConfig(c.url, c.token, resizeMode, subFontSizeSp, subColorArgb, subBottomMarginPct, subOffsetMs)
        }
    }

    private fun command(block: suspend (Creds, String) -> Boolean) {
        viewModelScope.launch {
            val c = creds() ?: return@launch
            val id = _state.value.current?.sessionId ?: return@launch
            block(c, id)
            delay(COMMAND_SETTLE_MS)
            refresh()
        }
    }

    private data class Creds(val url: String, val token: String)

    private fun creds(): Creds? {
        val url = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        return if (url.isBlank() || token.isBlank()) null else Creds(url, token)
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val COMMAND_SETTLE_MS = 300L
        const val POWER_ON_VOL_DELAY_MS = 800L
        const val SCENE_RESULT_MS = 3_500L
        const val TICKS_PER_MS = 10_000L
        const val EXTERNAL_TTL_MS = 6 * 60 * 60 * 1000L // 6 h — externí stream může běžet dlouho
        val DEFAULT_COLOR_SLOTS = listOf(
            0xFFFFFFFF.toInt(), // bílá
            0xFFFFEB3B.toInt(), // žlutá
            0xFF00E5FF.toInt(), // azurová
            0xFF76FF03.toInt(), // zelená
        )
    }
}
