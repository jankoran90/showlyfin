package com.github.jankoran90.showlyfin.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sdílený most mezi [AudiobookPlayerService] (feature-listen), MainActivity (app) a phone shellem
 * (ui-phone). Když uživatel klepne na media notifikaci / lock-screen ovládání audioknihy, service
 * spustí MainActivity s extra [EXTRA_OPEN_LISTEN]; MainActivity zavolá [requestOpenListen] a phone
 * shell na změnu [openListen] přepne spodní lištu na sekci „Poslech".
 *
 * Counter (ne boolean) řeší i opakované klepnutí na běžící app (onNewIntent): každý request zvýší
 * hodnotu → LaunchedEffect v shellu se znovu spustí.
 */
object ListenNavSignal {
    /** Boolean extra na launch intentu z notifikace audiopřehrávače. */
    const val EXTRA_OPEN_LISTEN = "showlyfin_open_listen"

    /** Broadcast: AudiobookPlayerService -> app obnovi Poslouchej widget pri zmene prehravani. */
    const val ACTION_LISTEN_STATE_CHANGED = "com.github.jankoran90.showlyfin.LISTEN_STATE_CHANGED"

    /** Plan EVEN: Nastavení -> AudiobookPlayerService přepne úroveň DRC/normalizéru za běhu (live). */
    const val ACTION_LISTEN_DRC_CHANGED = "com.github.jankoran90.showlyfin.LISTEN_DRC_CHANGED"

    private val _openListen = MutableStateFlow(0L)
    val openListen = _openListen.asStateFlow()

    fun requestOpenListen() {
        _openListen.value = _openListen.value + 1
    }

    /** MAESTRO: „Přehrát na TV" → phone shell přepne spodní lištu na sekci „Ovladač". Counter jako výše. */
    private val _openOvladac = MutableStateFlow(0L)
    val openOvladac = _openOvladac.asStateFlow()

    fun requestOpenOvladac() {
        _openOvladac.value = _openOvladac.value + 1
    }

    /**
     * FERRY/BATON: externí stream (RD/Stremio) puštěný na TV NENÍ Jellyfin položka → JF session
     * hlásí `NowPlayingItem=null`, takže Ovladač by ukázal „Nic nehraje". Showlyfin si proto
     * pamatuje, co naposledy castnul na TV, a Ovladač to zobrazí jako běžící titul + ovládá ho.
     * Vyčistí se při Stop z Ovladače nebo po TTL (film může běžet hodiny → velkorysé).
     */
    data class FerryCast(
        val title: String,
        val startedAtMs: Long,
        /** Poster filmu (TMDB) — Ovladač ho ukáže jako cover (jako u knihovny). */
        val posterUrl: String? = null,
        /** TMDb id → klik na cover v Ovladači vrátí na kartu filmu (detail / RD sekce). */
        val tmdbId: Long? = null,
        /** Endpoint, kam box hlásí pozici a odkud ji Ovladač čte (`/api/ferry/state?key=`). */
        val reportUrl: String? = null,
    )

    private val _ferryCast = MutableStateFlow<FerryCast?>(null)
    val ferryCast = _ferryCast.asStateFlow()

    fun setFerryCast(
        title: String,
        posterUrl: String? = null,
        tmdbId: Long? = null,
        reportUrl: String? = null,
    ) {
        _ferryCast.value = FerryCast(title, System.currentTimeMillis(), posterUrl, tmdbId, reportUrl)
    }

    fun clearFerryCast() {
        _ferryCast.value = null
    }

    /** VERDICT (claude-voice doporučovač): proklik `showlyfin://detail?tmdb=` → phone shell otevře
     *  detail filmu podle TMDb id. Payload + seq (retrigger i při stejném tmdb / opakovaném prokliku). */
    data class DetailRequest(val seq: Long, val tmdb: Long, val title: String, val year: Int?)

    private var detailSeq = 0L
    private val _openDetail = MutableStateFlow<DetailRequest?>(null)
    val openDetail = _openDetail.asStateFlow()

    fun requestOpenDetail(tmdb: Long, title: String = "", year: Int? = null) {
        detailSeq += 1
        _openDetail.value = DetailRequest(detailSeq, tmdb, title, year)
    }
}
