package com.github.jankoran90.showlyfin.data.trakt.token

import com.github.jankoran90.showlyfin.data.trakt.model.OAuthResponse

interface TokenProvider {
    fun getToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long, createdAt: Long)
    fun revokeToken()
    suspend fun refreshToken(): OAuthResponse
    fun shouldRefresh(): Boolean

    /**
     * WEATHER (2026-07-16) — ověří, jestli je AKTUÁLNÍ access token u Traktu ještě platný (volání na
     * kanonický autentizovaný endpoint `/users/settings`). Slouží jako pojistka PŘED odhlášením: Trakt
     * během přechodu na nový web (V3, červen–červenec 2026) vrací 401 i na PLATNÝ token u některých
     * endpointů (`/recommendations`, `/users/me/lists` — ověřeno curlem: `/sync` a `/users/settings` = 200,
     * ty dva = 401 se STEJNÝM tokenem). Zároveň jsou po migraci mrtvé staré refresh_tokeny ("session not
     * found") → obnova vždy selže. Bez téhle pojistky by 401 na gated endpointu falešně odhlásil celý účet,
     * i když watchlist/hodnocení/sync fungují. `true` = token žije (nebo síť/nejistota → NEODHLAŠUJ),
     * `false` = token je prokazatelně mrtvý (401 i na `/users/settings`). Default `true` pro ostatní impl.
     */
    suspend fun isAccessTokenLive(): Boolean = true

    /**
     * GLIDE — po DOČASNÉM selhání obnovy (429 rate-limit / síť / 5xx) drž krátký cooldown, ať se
     * Trakt token endpoint nebombarduje paralelními obnovami (root cause „Trakt se odhlásil": 6×
     * refresh za 2 s → 429 → kaskáda). Default false pro ostatní implementace.
     */
    fun isInRefreshCooldown(): Boolean = false
}

/**
 * GLIDE — selhání obnovy Trakt tokenu. [isAuthFailure] = true JEN pro definitivní chybu
 * (HTTP 400/401 = neplatný refresh_token) → teprve to smí odhlásit. 429/5xx/síť = dočasné,
 * token se NESMÍ smazat.
 */
class TokenRefreshException(message: String, val isAuthFailure: Boolean) : Exception(message)
