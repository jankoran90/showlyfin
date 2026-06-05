package com.github.jankoran90.showlyfin.data.trakt.interceptors

import android.os.Build
import com.github.jankoran90.showlyfin.core.network.Config
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktHeadersInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Content-Type", "application/json")
            .header("User-Agent", Config.traktUserAgent("1.0", 1, Build.VERSION.SDK_INT))
            .header("trakt-api-key", Config.traktClientId)
            .header("trakt-api-version", Config.TRAKT_VERSION)
            .build()
        return chain.proceed(request)
    }
}
