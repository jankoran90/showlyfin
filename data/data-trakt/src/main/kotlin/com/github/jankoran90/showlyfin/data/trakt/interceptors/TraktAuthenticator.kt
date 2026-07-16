package com.github.jankoran90.showlyfin.data.trakt.interceptors

import com.github.jankoran90.showlyfin.core.domain.trakt.TraktSessionSignal
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.data.trakt.token.TokenRefreshException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class TraktAuthenticator @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val sessionSignal: TraktSessionSignal,
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        val token = tokenProvider.getToken() ?: return null
        if (isAlreadyRefreshed(response, token)) {
            return response.request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        // GLIDE — po dočasném selhání obnovy (429/síť) nezkoušej hned znovu, jinak vznikne bouře
        // paralelních refresh volání → Trakt vrátí 429 → kaskáda → odhlášení. Počkej na cooldown.
        if (tokenProvider.isInRefreshCooldown()) return null
        return runBlocking(Dispatchers.IO) {
            try {
                Timber.d("Refreshing tokens...")
                val newToken = tokenProvider.refreshToken()
                tokenProvider.saveTokens(newToken.access_token, newToken.refresh_token, newToken.expires_in, newToken.created_at)
                response.request.newBuilder().header("Authorization", "Bearer ${newToken.access_token}").build()
            } catch (error: Throwable) {
                when {
                    error is CancellationException || error.message == "Canceled" -> null
                    // GLIDE — dočasná chyba (429/5xx/síť): token NEMAZAT (cooldown řeší provider),
                    // jen toto volání selže. Dřív se tu volalo revokeToken() = odhlášení z Traktu.
                    error is TokenRefreshException && !error.isAuthFailure -> {
                        Timber.w("Trakt: obnova tokenu dočasně selhala (${error.message}) — token ponechán.")
                        null
                    }
                    // WEATHER v2 (2026-07-16) — refresh_token je po přechodu Traktu na nový web (V3, červen–
                    // červenec 2026) mrtvý ("session not found") → obnova VŽDY selže. Access token přitom dál
                    // funguje pro /sync (watchlist/hodnocení). Trakt zároveň gatuje /recommendations a
                    // /users/me/lists → 401 i pro platný token. Proto: (1) NEODHLAŠUJ (žádný revoke ani dialog),
                    // (2) žádná další síťová validace TADY — authenticator je @Synchronized na hot-path; blokující
                    // volání navíc zablokuje i FUNKČNÍ Trakt requesty (watchlist) = nekonečné načítání (regrese
                    // 1.45.215). Provider si po tomto 400 nastaví cooldown → další 401 padnou rychle (return null
                    // výš přes isInRefreshCooldown). Working endpointy jedou dál, žádný storm/hang.
                    else -> {
                        Timber.w("Trakt: refresh_token neplatný (migrace V3) — token ponechán, bez odhlášení.")
                        null
                    }
                }
            }
        }
    }

    private fun isAlreadyRefreshed(response: Response, token: String?): Boolean {
        val authHeader = response.request.header("Authorization")
        return authHeader != null && !authHeader.contains(token.toString(), true)
    }
}
