package com.github.jankoran90.showlyfin.data.trakt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktAuthManager @Inject constructor(
    private val remoteDataSource: TraktRemoteDataSource,
    private val tokenProvider: TokenProvider,
) {
    private val _authCodeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authCodeFlow: SharedFlow<String> = _authCodeFlow.asSharedFlow()

    fun onAuthCode(code: String) {
        _authCodeFlow.tryEmit(code)
    }

    suspend fun authorize(code: String) {
        val tokens = remoteDataSource.fetchAuthTokens(code)
        tokenProvider.saveTokens(
            accessToken = tokens.access_token,
            refreshToken = tokens.refresh_token,
            expiresIn = tokens.expires_in,
            createdAt = tokens.created_at,
        )
    }

    fun isLoggedIn(): Boolean = tokenProvider.getToken() != null

    fun logout() = tokenProvider.revokeToken()
}
