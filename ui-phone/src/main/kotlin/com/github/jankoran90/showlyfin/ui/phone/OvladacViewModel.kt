package com.github.jankoran90.showlyfin.ui.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.ui.phone.beacon.OvladacBeaconService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @Named("traktPreferences") private val prefs: SharedPreferences,
    @ApplicationContext private val appContext: Context,
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
        // BEACON — když na TV běží přehrávání a sekce je v popředí, nastartuj lockscreen ovladač
        // (foreground service). Služba se pak udržuje a ukončí sama, až přehrávání skončí.
        if (current?.nowPlayingTitle != null) OvladacBeaconService.start(appContext)
        _state.update {
            it.copy(
                loading = false,
                noCreds = false,
                sessions = sessions,
                current = current,
                selectedId = current?.sessionId,
                coverUrl = cover,
            )
        }
    }

    fun playPause() = command { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "PlayPause") }
    fun stopPlayback() = command { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "Stop") }

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

    fun setVolume(volume: Int) = command { c, id -> naTv.setVolume(c.url, c.token, id, volume) }
    fun toggleMute() = command { c, id -> naTv.toggleMute(c.url, c.token, id) }
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
        const val TICKS_PER_MS = 10_000L
    }
}
