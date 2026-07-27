package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * EMBER (SHW-108, user 2026-07-27) — hlídač: **čerstvý Trakt token letí i na server**, ne jen do prefs.
 *
 * PROČ: Trakt access token se sám tiše prodlužuje ([TraktTokenProvider.saveTokens] v :data-trakt) a
 * rotuje přitom i refresh_token. Zápis šel dosud POUZE do lokálních prefs (+ per-profil kopie). Na
 * backend se token dostal jen ručním (re)loginem v Nastavení. Mezi loginy tedy serverová kopie stárla
 * a všechno server-side nad Trakt vkusem (zrcadlo Traktu → kurátor „Pro tebe", scrobble) jelo na
 * tokenu, který už Trakt nemusí uznat — potichu, bez jediné stopy v UI.
 *
 * JAK: posloucháme kanonický klíč `TRAKT_ACCESS_TOKEN` v `traktPreferences` (stejný trik, jakým si
 * [TraktTokenProvider] invaliduje vlastní cache). Změna → krátký debounce (jeden zápis prefs sype víc
 * callbacků a `saveTokens` píše access i refresh token) → [ProfileRepository.syncTraktTokenToServer].
 *
 * BEZPEČNÉ vůči přepínání profilů: [ProfileConfigApplier] při přepnutí taky zapisuje do tohoto klíče,
 * ale to je token, který z balíku daného profilu právě přišel → shoda → repository nic nepushne
 * (idempotentní no-op). Odhlášení (prázdný token) neřešíme tady — to je cesta v Nastavení.
 */
@Singleton
class TraktTokenServerSync @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val profileRepository: ProfileRepository,
) {
    private companion object {
        const val KEY_ACCESS_TOKEN = "TRAKT_ACCESS_TOKEN"
        /** Zápis tokenu jde do prefs po částech → počkej, ať pushneme kompletní (access + refresh). */
        const val DEBOUNCE_MS = 1_500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    private var started = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ACCESS_TOKEN) schedulePush()
    }

    /** Idempotentní; volá se z hostitelské aktivity při startu. */
    fun start() {
        if (started) return
        started = true
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Cold start: token se mohl obnovit, když appka neběžela (nebo push posledně selhal offline).
        schedulePush()
    }

    private fun schedulePush() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            val pushed = profileRepository.syncTraktTokenToServer()
            if (pushed) Timber.i("[EMBER] čerstvý Trakt token odeslán na server + kopnut mirror")
        }
    }
}
