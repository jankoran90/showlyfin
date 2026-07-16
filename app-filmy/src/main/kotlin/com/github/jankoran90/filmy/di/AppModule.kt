package com.github.jankoran90.filmy.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * CELLULOID (SHW-98) — klon `com.github.jankoran90.showlyfin.di.AppModule`. Sdílené moduly
 * (data-trakt/csfd + AI titulky) očekávají tyto `@Named` SharedPreferences ze SingletonComponentu;
 * modul žije v `:app`, takže Filmy si ho musí poskytnout sám (jinak chybí Hilt binding). Stejné názvy
 * úložišť (`trakt_prefs`/`csfd_prefs`/`subtitle_prefs`) = stejné chování jako showlyfin.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
