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
