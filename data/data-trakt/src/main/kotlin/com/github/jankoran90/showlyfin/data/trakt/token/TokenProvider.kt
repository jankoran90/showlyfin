package com.github.jankoran90.showlyfin.data.trakt.token

import com.github.jankoran90.showlyfin.data.trakt.model.OAuthResponse

interface TokenProvider {
    fun getToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long, createdAt: Long)
    fun revokeToken()
    suspend fun refreshToken(): OAuthResponse
    fun shouldRefresh(): Boolean

    /**
     * GLIDE — po DOČASNÉM selhání obnovy (429 rate-limit / síť / 5xx) drž krátký cooldown, ať se
     * Trakt token endpoint nebombarduje paralelními obnovami (root cause „Trakt se odhlásil": 6×
     * refresh za 2 s → 429 → kaskáda). Default false pro ostatní implementace.
     */
    fun isInRefreshCooldown(): Boolean = false

    /**
     * ORBIT — isLoggedIn čte ŽIVOST tokenu (platný tvar + ne po expiraci), ne pouhou přítomnost stringu.
     * Default false pro ostatní implementace.
     */
    fun isTokenLive(): Boolean = false

    /**
     * ORBIT — jednotná single-flight obnova: spotřebuje rotující refresh_token PRÁVĚ JEDNOU a atomicky uloží
     * nový pár. Sdílený zámek napříč Authenticator i Interceptor (dřív každý @Synchronized na jiném objektu →
     * double-spend → Trakt replay-detekce → 400 „session not found" → mrtvý chain = denní re-login kruh).
     * Vrací platný access token k použití, nebo null při selhání.
     */
    suspend fun refreshTokenSafely(): String? {
        val r = refreshToken()
        saveTokens(r.access_token, r.refresh_token, r.expires_in, r.created_at)
        return r.access_token
    }
}

/**
 * GLIDE — selhání obnovy Trakt tokenu. [isAuthFailure] = true JEN pro definitivní chybu
 * (HTTP 400/401 = neplatný refresh_token) → teprve to smí odhlásit. 429/5xx/síť = dočasné,
 * token se NESMÍ smazat.
 */
class TokenRefreshException(message: String, val isAuthFailure: Boolean) : Exception(message)
