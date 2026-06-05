package com.github.jankoran90.showlyfin.data.trakt.interceptors

import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class TraktRefreshTokenInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (tokenProvider.shouldRefresh()) {
            runBlocking(Dispatchers.IO) {
                try {
                    val refreshedTokens = tokenProvider.refreshToken()
                    tokenProvider.saveTokens(refreshedTokens.access_token, refreshedTokens.refresh_token, refreshedTokens.expires_in, refreshedTokens.created_at)
                } catch (error: Throwable) {
                    if (error !is CancellationException && error.message != "Canceled") {
                        Timber.e(error)
                    }
                }
            }
        }
        return chain.proceed(request)
    }
}
