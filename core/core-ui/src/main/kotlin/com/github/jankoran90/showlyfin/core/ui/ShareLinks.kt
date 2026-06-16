package com.github.jankoran90.showlyfin.core.ui

import android.content.Context
import android.content.Intent
import java.net.URLEncoder

/**
 * BEAM (SHW-63) + CLASP (SHW-66): sdílení Poslech položek ostatním showlyfin uživatelům.
 * Vyrobí **https App Link** `https://upload.jankoran.cz/s/listen?…` — messengery (WhatsApp/Messenger/SMS)
 * ho udělají KLIKACÍ (custom scheme `showlyfin://` nepodtrhnou), příjemce s appkou ho přes Android
 * App Links otevře PŘÍMO v Poslechu (ověření přes /.well-known/assetlinks.json na serveru); bez appky
 * / bez ověření landing stránka přesměruje na `showlyfin://` (fallback). Handler = MainActivity (parsuje
 * https i starší showlyfin:// kvůli zpětné kompatibilitě). [ListenNavSignal.openListenItem].
 */
object ShareLinks {
    private const val BASE = "https://upload.jankoran.cz/s/listen"

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
