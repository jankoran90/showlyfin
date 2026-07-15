package com.github.jankoran90.showlyfin.core.domain.trakt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CONVERGE V1 — sdílený signál „Trakt tě definitivně odhlásil, je potřeba re-login". Emituje ho
 * [data.trakt.interceptors.TraktAuthenticator] ve chvíli, kdy 401 nejde samo obnovit (mrtvý refresh_token →
 * `revokeToken()`). Sleduje ho TV shell ([ui.tv.TvShell]) → zobrazí globální re-auth prompt s jednoklikem do
 * Nastavení → Účty, ať uživatel nezůstane tiše odhlášený (dřív jen matoucí „HTTP 401" v detailu). Jedna
 * infra napříč appkou (žádné duplikace device-auth UI).
 */
@Singleton
class TraktSessionSignal @Inject constructor() {
    private val _reauthNeeded = MutableStateFlow(0)
    /** Verze se zvýší při každém definitivním odhlášení; konzument reaguje na změnu (drop iniciální). */
    val reauthNeeded: StateFlow<Int> = _reauthNeeded.asStateFlow()

    /** Zavolej při definitivním odhlášení z Traktu (neplatný refresh_token) → prompt na re-login. */
    fun signalReauthNeeded() {
        _reauthNeeded.value += 1
    }
}
