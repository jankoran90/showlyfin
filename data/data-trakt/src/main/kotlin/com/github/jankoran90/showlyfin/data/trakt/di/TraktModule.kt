package com.github.jankoran90.showlyfin.data.trakt.di

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.TraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.api.AuthorizedTraktApi
import com.github.jankoran90.showlyfin.data.trakt.api.TraktApi
import com.github.jankoran90.showlyfin.data.trakt.api.service.*
import com.github.jankoran90.showlyfin.data.trakt.interceptors.*
import com.github.jankoran90.showlyfin.data.trakt.token.TraktTokenProvider
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
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
object TraktModule {

    @Provides
    @Singleton
    fun providesGson(): Gson = Gson()

    @Provides
    @Singleton
    fun providesHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides
    @Singleton
    @Named("okHttpBase")
    fun providesBaseOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .writeTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(60))
            .build()

    @Provides
    @Singleton
    @Named("okHttpTrakt")
    fun providesTraktOkHttp(
        logging: HttpLoggingInterceptor,
        headers: TraktHeadersInterceptor,
        retry: TraktRetryInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .writeTimeout(Duration.ofSeconds(60)).readTimeout(Duration.ofSeconds(60)).callTimeout(Duration.ofSeconds(60))
        .addInterceptor(headers).addInterceptor(retry).addInterceptor(logging)
        .build()

    @Provides
    @Singleton
    @Named("okHttpAuthorizedTrakt")
    fun providesAuthorizedTraktOkHttp(
        logging: HttpLoggingInterceptor,
        authorization: TraktAuthorizationInterceptor,
        headers: TraktHeadersInterceptor,
        refreshToken: TraktRefreshTokenInterceptor,
        retry: TraktRetryInterceptor,
        authenticator: TraktAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .writeTimeout(Duration.ofSeconds(60)).readTimeout(Duration.ofSeconds(60)).callTimeout(Duration.ofSeconds(60))
        .addInterceptor(headers).addInterceptor(refreshToken).addInterceptor(authorization)
        .addInterceptor(retry).addInterceptor(logging).authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    @Named("retrofitTrakt")
    fun providesTraktRetrofit(
        @Named("okHttpTrakt") okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .baseUrl(Config.TRAKT_BASE_URL)
        .build()

    @Provides
    @Singleton
    @Named("retrofitAuthorizedTrakt")
    fun providesAuthorizedTraktRetrofit(
        @Named("okHttpAuthorizedTrakt") okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .baseUrl(Config.TRAKT_BASE_URL)
        .build()

    @Provides
    @Singleton
    fun providesTraktApi(@Named("retrofitTrakt") retrofit: Retrofit): TraktRemoteDataSource =
        TraktApi(
            showsService = retrofit.create(TraktShowsService::class.java),
            moviesService = retrofit.create(TraktMoviesService::class.java),
            authService = retrofit.create(TraktAuthService::class.java),
            commentsService = retrofit.create(TraktCommentsService::class.java),
            searchService = retrofit.create(TraktSearchService::class.java),
            peopleService = retrofit.create(TraktPeopleService::class.java),
        )

    @Provides
    @Singleton
    fun providesAuthorizedTraktApi(@Named("retrofitAuthorizedTrakt") retrofit: Retrofit): AuthorizedTraktRemoteDataSource =
        AuthorizedTraktApi(
            usersService = retrofit.create(TraktUsersService::class.java),
            syncService = retrofit.create(TraktSyncService::class.java),
            commentsService = retrofit.create(TraktCommentsService::class.java),
        )

    @Provides
    @Singleton
    fun providesTraktTokenProvider(
        @Named("traktPreferences") sharedPreferences: SharedPreferences,
        @Named("okHttpBase") okHttpClient: OkHttpClient,
        gson: Gson,
    ): TokenProvider = TraktTokenProvider(sharedPreferences, gson, okHttpClient)
}
