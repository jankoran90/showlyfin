package com.github.jankoran90.showlyfin.core.ui

import android.content.Context
import android.content.Intent
import java.net.URLEncoder

/**
 * BEAM (SHW-63): sdílení Poslech položek ostatním showlyfin uživatelům. Vyrobí `showlyfin://listen…`
 * deep-link (příjemce s appkou si ho otevře PŘÍMO v Poslechu — viz [ListenNavSignal.openListenItem]
 * + handler v MainActivity/phone shellu) a otevře systémové sdílení (ACTION_SEND, text+odkaz).
 *
 * Pozn.: některé messengery custom scheme `showlyfin://` automaticky nepodtrhnou — odkaz funguje po
 * ťuknutí / zkopírování; případný https redirect je možné rozšíření (known gap).
 */
object ShareLinks {
    private const val BASE = "showlyfin://listen"

    fun podcast(itemId: String) = "$BASE?type=podcast&id=${enc(itemId)}"
    fun audiobook(itemId: String) = "$BASE?type=audiobook&id=${enc(itemId)}"
    fun episode(itemId: String, episodeId: String) =
        "$BASE?type=episode&id=${enc(itemId)}&ep=${enc(episodeId)}"

    /** [handle] = YouTube handle kanálu (např. "@hovoryzezeme"), [v] = volitelně videoId epizody. */
    fun youtube(handle: String, channelTitle: String, v: String? = null) =
        "$BASE?type=yt&channel=${enc(handle)}&title=${enc(channelTitle)}" +
            (v?.let { "&v=${enc(it)}" } ?: "")

    /** Otevře systémové sdílení s názvem (čitelné) + deep-linkem. */
    fun share(context: Context, title: String, link: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$link")
        }
        context.startActivity(
            Intent.createChooser(send, "Sdílet").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
