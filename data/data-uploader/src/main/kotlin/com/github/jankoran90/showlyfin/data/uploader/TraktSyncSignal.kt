package com.github.jankoran90.showlyfin.data.uploader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COUCH — sdílený „Trakt sync se změnil" signál. Watchlist NENÍ reaktivní (jde přímo přes Trakt API, žádný
 * lokální store s flow), takže po přidání/odebrání z „Chci vidět" v detailu ([DetailViewModel.toggleWatchlist])
 * bumpneme [version]; domov ([TvHomeViewModel]) na to reaguje přenačtením Trakt řad (watchlist/historie), aby
 * čerstvě přidaný titul naskočil i v domovské řadě — ne jen v samostatné sekci Trakt (fresh VM při vstupu).
 */
@Singleton
class TraktSyncSignal @Inject constructor() {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Zavolej po úspěšné změně Trakt watchlistu/historie → konzumenti přenačtou Trakt řady. */
    fun bump() {
        _version.value += 1
    }
}
