package com.github.jankoran90.showlyfin.widget

import android.content.Context
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService

/**
 * RELAY — dálkové ovládání běžící Jellyfin session pro „Sleduj" widget.
 * NE lokální přehrávač: posílá příkazy na aktivní JF session (typicky Wolphin/Yellyfin na TV)
 * přes [NaTvService] / Jellyfin Sessions API. Creds bere z aktivního profilu (`traktPreferences`).
 */
object WatchRemote {

    private const val TICKS_PER_MS = 10_000L
    private const val SEEK_BACK_MS = 10_000L
    private const val SEEK_FWD_MS = 30_000L

    data class State(
        val noCreds: Boolean,
        val deviceName: String?,
        val playing: Boolean,
        val title: String?,
        val subtitle: String?,
        val sessionId: String?,
    ) {
        val hasSession: Boolean get() = sessionId != null
        val hasNowPlaying: Boolean get() = !title.isNullOrBlank()

        companion object {
            val NO_CREDS = State(true, null, false, null, null, null)
            val NO_SESSION = State(false, null, false, null, null, null)
        }
    }

    suspend fun load(context: Context): State {
        val creds = creds(context) ?: return State.NO_CREDS
        val svc = context.widgetEntryPoint().naTvService()
        val target = svc.pickWatchSession(svc.getSessions(creds.url, creds.token))
            ?: return State.NO_SESSION
        return State(
            noCreds = false,
            deviceName = target.deviceName,
            playing = target.isPlaying,
            title = target.nowPlayingTitle,
            subtitle = target.nowPlayingSubtitle,
            sessionId = target.sessionId,
        )
    }

    suspend fun playPause(context: Context) =
        command(context) { svc, c, id -> svc.sendPlaystateCommand(c.url, c.token, id, "PlayPause") }

    suspend fun stop(context: Context) =
        command(context) { svc, c, id -> svc.sendPlaystateCommand(c.url, c.token, id, "Stop") }

    suspend fun rewind(context: Context) = seekBy(context, -SEEK_BACK_MS)

    suspend fun forward(context: Context) = seekBy(context, SEEK_FWD_MS)

    private suspend fun seekBy(context: Context, deltaMs: Long) {
        val creds = creds(context) ?: return
        val svc = context.widgetEntryPoint().naTvService()
        val target = svc.pickWatchSession(svc.getSessions(creds.url, creds.token)) ?: return
        val newPos = (target.positionTicks + deltaMs * TICKS_PER_MS).coerceAtLeast(0L)
        svc.sendSeek(creds.url, creds.token, target.sessionId, newPos)
    }

    private suspend fun command(
        context: Context,
        block: suspend (NaTvService, Creds, String) -> Boolean,
    ) {
        val creds = creds(context) ?: return
        val svc = context.widgetEntryPoint().naTvService()
        val target = svc.pickWatchSession(svc.getSessions(creds.url, creds.token)) ?: return
        block(svc, creds, target.sessionId)
    }

    private data class Creds(val url: String, val token: String)

    private fun creds(context: Context): Creds? {
        val prefs = context.widgetEntryPoint().traktPreferences()
        val url = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        return if (url.isBlank() || token.isBlank()) null else Creds(url, token)
    }
}
