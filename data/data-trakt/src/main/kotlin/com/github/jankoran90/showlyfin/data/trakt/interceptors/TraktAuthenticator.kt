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
                    // WEATHER (2026-07-16) — obnova selhala definitivně (neplatný refresh_token), ALE to
                    // NEMUSÍ znamenat mrtvý access token. Trakt během přechodu na nový web (V3, červen–červenec
                    // 2026) vrací 401 i na PLATNÝ token u některých endpointů (/recommendations, /users/me/lists
                    // — ověřeno curlem: /sync a /users/settings = 200, ty dva = 401 se STEJNÝM tokenem) a staré
                    // refresh_tokeny jsou po migraci mrtvé ("session not found") → obnova VŽDY selže. Dřív to
                    // revokovalo CELOU session + dialog i když watchlist/hodnocení/sync fungují. Pojistka: ověř
                    // access token proti /users/settings. Platný → NEODHLAŠUJ (jen tohle volání selže).
                    else -> {
                        if (tokenProvider.isAccessTokenLive()) {
                            Timber.w("Trakt: obnova selhala, ale access token je platný (gated 401 / migrace V3) — token ponechán.")
                            null
                        } else {
                            Timber.w("Trakt: refresh_token neplatný a access token mrtvý → odhlášení.")
                            tokenProvider.revokeToken()
                            sessionSignal.signalReauthNeeded()
                            null
                        }
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
