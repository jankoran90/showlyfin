package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.maestro.AvrController
import com.github.jankoran90.showlyfin.data.maestro.BoxController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrBoxHostOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrBoxMacOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrEnabledOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrHostOrDefault
import com.github.jankoran90.showlyfin.ui.phone.HomeSystemDefaults.avrTvHostOrDefault
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class NaTvCoordinator @Inject constructor(
    private val naTvService: NaTvService,
    private val avr: AvrController,
    private val box: BoxController,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _messages = Channel<String>(capacity = Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun playOnTv(item: MediaItem, knownJellyfinId: String? = null) {
        // Hned přepni appku na sekci „Ovladač" (uvidíš průběh scény + ovládání). MAESTRO.
        ListenNavSignal.requestOpenOvladac()
        viewModelScope.launch {
            val url = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            if (url.isBlank() || token.isBlank()) {
                _messages.send("Jellyfin není nastaven")
                return@launch
            }
            // Preferuj už vyřešené Jellyfin id z detailu (pokrývá filmy v kolekcích,
            // kde findJellyfinItemId přes recursive dotaz vrací BoxSet a film nenajde).
            val itemId = knownJellyfinId
                ?: naTvService.findJellyfinItemId(url, token, item.imdbId, item.tmdbId)
            if (itemId == null) {
                _messages.send("Film není v Jellyfin knihovně")
                return@launch
            }

            // Rychlá cesta: běží už Yellyfin session na TV? Hned přehraj.
            var target = pickYellyfin(naTvService.getSessions(url, token))

            // Jinak spusť scénu MAESTRO: probuď receiver + box, spusť Yellyfin, počkej na session.
            if (target == null && isSceneConfigured()) {
                target = runWakeScene(url, token)
            }

            if (target == null) {
                _messages.send(
                    if (isSceneConfigured()) "Nepodařilo se probudit TV"
                    else "Žádná aktivní Jellyfin session",
                )
                return@launch
            }
            val ok = naTvService.sendPlayCommand(url, token, target.sessionId, itemId)
            if (ok) _messages.send("Spuštěno na ${target.deviceName}")
            else _messages.send("Odeslání selhalo")
        }
    }

    /** Scéna „spustit z vypnuté TV": AVR power → box WoL + ADB launch Yellyfin → poll na session. */
    private suspend fun runWakeScene(url: String, token: String): JellyfinSessionSummary? {
        _messages.send("Zapínám obývák…")
        // 1) Receiver ze standby (vstup STRM BOX si AVR přepne sám přes CEC). Výchozí hlasitost
        // nastavíme JEN když ji user v appce zadal (jinak respekt k power-on hlasitosti AVR).
        avrConfig()?.let { host ->
            avr.powerOn(host)
            avrDefaultVolume()?.let { delay(800); avr.setVolume(host, it) }
        }
        // 2) Televize napřímo (CEC kaskáda nemusí stačit — viz device test).
        tvHost()?.let { box.wake(it) }
        // 3) Box z hlubokého spánku (Wake-on-LAN; ADB ho pak probudí z lehkého).
        boxMac()?.let { box.wakeViaWol(it) }
        val boxHost = boxHost()

        // 3) Poll na Yellyfin session; mezitím (po)spouštěj Yellyfin na boxu, jak naběhne síť.
        repeat(SCENE_MAX_POLLS) { i ->
            val found = pickYellyfin(naTvService.getSessions(url, token))
            if (found != null) return found
            if (boxHost != null && (i == 0 || i == LAUNCH_RETRY_POLL)) {
                if (i == LAUNCH_RETRY_POLL) _messages.send("Spouštím přehrávač na TV…")
                box.wakeAndLaunch(boxHost)
            }
            delay(SCENE_POLL_MS)
        }
        return null
    }

    private fun pickYellyfin(sessions: List<JellyfinSessionSummary>): JellyfinSessionSummary? =
        sessions.firstOrNull {
            val hay = "${it.client.orEmpty()} ${it.deviceName}".lowercase()
            hay.contains("yellyfin") || hay.contains("wolphin") || hay.contains("wholphin")
        }

    private fun isSceneConfigured(): Boolean =
        prefs.avrEnabledOrDefault() && (avrConfig() != null || boxHost() != null)

    private fun avrConfig(): String? = prefs.avrHostOrDefault().takeIf { it.isNotBlank() }

    private fun boxHost(): String? = prefs.avrBoxHostOrDefault().takeIf { it.isNotBlank() }

    private fun boxMac(): String? = prefs.avrBoxMacOrDefault().takeIf { it.isNotBlank() }

    private fun tvHost(): String? = prefs.avrTvHostOrDefault().takeIf { it.isNotBlank() }

    private fun avrDefaultVolume(): Int? =
        prefs.getString("avr_default_volume", "").orEmpty().trim().toIntOrNull()?.takeIf { it > 0 }

    private companion object {
        const val SCENE_MAX_POLLS = 16      // ~32 s
        const val SCENE_POLL_MS = 2_000L
        const val LAUNCH_RETRY_POLL = 6     // ~12 s — po doběhnutí WoL zkus spustit Yellyfin znovu
    }
}
