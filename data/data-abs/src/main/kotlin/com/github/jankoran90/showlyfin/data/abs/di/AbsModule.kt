package com.github.jankoran90.showlyfin.data.abs.di

import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.api.AbsAuthInterceptor
import com.github.jankoran90.showlyfin.data.abs.api.AbsService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AbsModule {

    @Provides
    @Singleton
    @Named("retrofitAbs")
    fun providesAbsRetrofit(
        @Named("okHttpBase") okHttpBase: OkHttpClient,
        prefs: AbsPreferences,
        gson: Gson,
    ): Retrofit {
        val client = okHttpBase.newBuilder()
            .addInterceptor(AbsAuthInterceptor(prefs))
            .build()
        return Retrofit.Builder()
            // baseUrl je jen placeholder — všechny cesty jdou přes @Url plné URL
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun providesAbsService(@Named("retrofitAbs") retrofit: Retrofit): AbsService =
        retrofit.create(AbsService::class.java)
}
