package com.github.jankoran90.slovo.di

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
 * EXCISE (SHW-103) — klon `com.github.jankoran90.filmy.di.AppModule`. Sdílené moduly (feature-listen,
 * core-theme VM aj.) očekávají tyto `@Named` SharedPreferences ze SingletonComponentu; modul žije v `:app`,
 * takže Slovo si ho musí poskytnout sám. Stejné názvy úložišť = stejné chování napříč appkami fleetu.
 *
 * 🔴 **`Gson` a `@Named("okHttpBase")` OkHttpClient si Slovo NEPOSKYTUJE** (a nesmí). Původní komentář
 * tvrdil „Slovo Trakt netáhne", jenže **táhne** — přes `:feature:feature-playback` (WatchedReporter),
 * takže `TraktModule` je v jeho grafu taky a obojí bylo bindnuté DVAKRÁT:
 * `[Dagger/DuplicateBindings]` → `:app-slovo:hiltJavaCompileDebug` padal a s ním **celé CI (39 běhů
 * v řadě červených)**. Nikdo si toho nevšiml, protože ostrá cesta appek jde přes dellhome, ne přes CI.
 * Kdyby Slovo někdy `feature-playback` (a tím data-trakt) ztratilo, providery se sem musí vrátit.
 * Opraveno 2026-08-01.
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
