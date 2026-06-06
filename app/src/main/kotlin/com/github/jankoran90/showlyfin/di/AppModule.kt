package com.github.jankoran90.showlyfin.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

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
}
