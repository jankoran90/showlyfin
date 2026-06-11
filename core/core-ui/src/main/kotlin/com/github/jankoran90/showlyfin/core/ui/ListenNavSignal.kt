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
}
