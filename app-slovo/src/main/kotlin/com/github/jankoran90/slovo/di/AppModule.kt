package com.github.jankoran90.slovo.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.time.Duration
import javax.inject.Named
import javax.inject.Singleton

/**
 * EXCISE (SHW-103) — klon `com.github.jankoran90.filmy.di.AppModule`. Sdílené moduly (feature-listen,
 * core-theme VM aj.) očekávají tyto `@Named` SharedPreferences ze SingletonComponentu; modul žije v `:app`,
 * takže Slovo si ho musí poskytnout sám. Stejné názvy úložišť = stejné chování napříč appkami fleetu.
 *
 * Navíc: `@Named("okHttpBase")` OkHttpClient + `Gson` — poslechové moduly (data-abs/uploader) je konzumují,
 * ale v showlyfinu je poskytoval `TraktModule` (film). Slovo Trakt netáhne → poskytne si je sám (stejná konfigurace).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providesGson(): Gson = Gson()

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
    @Named("traktPreferences")
    fun providesTraktPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @Named("csfdPreferences")
    fun providesCsfdPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences("csfd_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @Named("subtitlePreferences")
    fun providesSubtitlePreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences("subtitle_prefs", Context.MODE_PRIVATE)
}
