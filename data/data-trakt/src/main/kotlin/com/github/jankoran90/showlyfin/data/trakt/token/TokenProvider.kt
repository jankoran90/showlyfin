package com.github.jankoran90.showlyfin.data.trakt.token

import com.github.jankoran90.showlyfin.data.trakt.model.OAuthResponse

interface TokenProvider {
    fun getToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long, createdAt: Long)
    fun revokeToken()
    suspend fun refreshToken(): OAuthResponse
    fun shouldRefresh(): Boolean
}
