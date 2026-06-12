package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.maestro.AvrController
import com.github.jankoran90.showlyfin.data.maestro.BoxController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var pollJob: Job? = null
    private var userSelectedId: String? = null

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
        val current = sessions.firstOrNull { it.sessionId == userSelectedId }
            ?: naTv.pickWatchSession(sessions)
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
                avrEnabled = avrCfg != null,
                avrReachable = avrStatus?.reachable ?: false,
                avrVolume = avrStatus?.volume ?: it.avrVolume,
                avrMuted = if (avrStatus?.reachable == true) avrStatus.muted else it.avrMuted,
                avrVolumeStep = avrVolumeStepPref(),
            )
        }
    }

    /** Vrátí host AVR pokud je ovládání hlasitosti přes AVR povolené a IP vyplněná, jinak null. */
    private fun avrConfig(): String? {
        if (!prefs.getBoolean("avr_enabled", false)) return null
        return prefs.getString("avr_host", "").orEmpty().trim().takeIf { it.isNotBlank() }
    }

    private suspend fun refreshAvrOnly(host: String) {
        val s = avr.status(host)
        _state.update {
            it.copy(
                avrReachable = s.reachable,
                avrVolume = s.volume ?: it.avrVolume,
                avrMuted = if (s.reachable) s.muted else it.avrMuted,
            )
        }
    }

    /** Ručně zapnout celou sestavu (receiver + probudit TV + box + spustit přehrávač). */
    fun powerOnSystem() = sceneAction("Zapínám obývák…") {
        avrConfig()?.let { host ->
            avr.powerOn(host)
            // Respekt k vlastní power-on hlasitosti AVR: hodnotu nastavíme JEN když ji user
            // v appce přímo zadal (override). Prázdné = ponecháme na receiveru (děti ráno).
            avrDefaultVolume()?.let { delay(POWER_ON_VOL_DELAY_MS); avr.setVolume(host, it) }
        }
        tvHost()?.let { box.wake(it) }
        boxMac()?.let { box.wakeViaWol(it) }
        boxHost()?.let { box.wakeAndLaunch(it) }
    }

    /** Ručně vypnout sestavu (receiver do standby + uspat TV i box). */
    fun powerOffSystem() = sceneAction("Vypínám obývák…") {
        avrConfig()?.let { avr.powerOff(it) }
        tvHost()?.let { box.sleep(it) }
        boxHost()?.let { box.sleep(it) }
    }

    private fun sceneAction(status: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(sceneStatus = status) }
            runCatching { block() }
            delay(COMMAND_SETTLE_MS)
            avrConfig()?.let { refreshAvrOnly(it) }
            _state.update { it.copy(sceneStatus = null) }
        }
    }

    private fun boxHost(): String? =
        prefs.getString("avr_box_host", "").orEmpty().trim().takeIf { it.isNotBlank() }

    private fun boxMac(): String? =
        prefs.getString("avr_box_mac", "").orEmpty().trim().takeIf { it.isNotBlank() }

    private fun tvHost(): String? =
        prefs.getString("avr_tv_host", "").orEmpty().trim().takeIf { it.isNotBlank() }

    /** Override výchozí hlasitosti po zapnutí; null = ponechat na receiveru (respekt). */
    private fun avrDefaultVolume(): Int? =
        prefs.getString("avr_default_volume", "").orEmpty().trim().toIntOrNull()?.takeIf { it > 0 }

    /** Krok hlasitosti +/- (jednotky AVR), default 3. */
    private fun avrVolumeStepPref(): Int =
        prefs.getString("avr_volume_step", "").orEmpty().trim().toIntOrNull()?.coerceIn(1, 20) ?: 3

    fun playPause() = command { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "PlayPause") }
    fun stopPlayback() = command { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "Stop") }

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
        const val TICKS_PER_MS = 10_000L
    }
}
