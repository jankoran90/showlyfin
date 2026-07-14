package com.github.jankoran90.showlyfin.data.tmdb.di

import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.data.tmdb.CachedTmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.TmdbInterceptor
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.api.TmdbApi
import com.github.jankoran90.showlyfin.data.tmdb.api.TmdbService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TmdbModule {

    @Provides
    @Singleton
    fun providesTmdbInterceptor(): TmdbInterceptor = TmdbInterceptor()

    @Provides
    @Singleton
    @Named("okHttpTmdb")
    fun providesTmdbOkHttp(logging: HttpLoggingInterceptor, tmdbInterceptor: TmdbInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .writeTimeout(Duration.ofSeconds(60)).readTimeout(Duration.ofSeconds(60)).callTimeout(Duration.ofSeconds(60))
            .addInterceptor(tmdbInterceptor).addInterceptor(logging)
            .build()

    @Provides
    @Singleton
    @Named("retrofitTmdb")
    fun providesTmdbRetrofit(@Named("okHttpTmdb") okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder().client(okHttpClient).addConverterFactory(GsonConverterFactory.create(gson)).baseUrl(Config.TMDB_BASE_URL).build()

    // CINEMATHEQUE (SHW-90): konzumenti dostávají cache dekorátor (in-memory details/translations/cert).
    // Jeden binding pro TmdbRemoteDataSource → žádná Hilt kolize; sdílené napříč Home/Discover/Trakt/Filmotéka.
    @Provides
    @Singleton
    fun providesTmdbApi(@Named("retrofitTmdb") retrofit: Retrofit): TmdbRemoteDataSource =
        CachedTmdbRemoteDataSource(TmdbApi(retrofit.create(TmdbService::class.java)))
}
