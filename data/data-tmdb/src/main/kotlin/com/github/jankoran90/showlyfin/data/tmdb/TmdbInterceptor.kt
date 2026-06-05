package com.github.jankoran90.showlyfin.data.tmdb

import com.github.jankoran90.showlyfin.core.network.Config
import okhttp3.Interceptor
import okhttp3.Response

class TmdbInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("api_key", Config.tmdbApiKey)
            .build()
        val request = original.newBuilder().url(url).header("Content-Type", "application/json").build()
        return chain.proceed(request)
    }
}
