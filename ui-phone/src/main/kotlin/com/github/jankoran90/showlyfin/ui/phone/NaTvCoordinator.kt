package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class NaTvCoordinator @Inject constructor(
    private val naTvService: NaTvService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _messages = Channel<String>(capacity = Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun playOnTv(item: MediaItem, knownJellyfinId: String? = null) {
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
            val sessions = naTvService.getSessions(url, token)
            if (sessions.isEmpty()) {
                _messages.send("Žádná aktivní Jellyfin session")
                return@launch
            }
            val target = sessions.firstOrNull { it.isActive } ?: sessions.first()
            val ok = naTvService.sendPlayCommand(url, token, target.sessionId, itemId)
            if (ok) _messages.send("Spuštěno na ${target.deviceName}")
            else _messages.send("Odeslání selhalo")
        }
    }
}
