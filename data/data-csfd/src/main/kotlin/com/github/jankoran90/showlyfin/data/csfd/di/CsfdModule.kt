package com.github.jankoran90.showlyfin.data.csfd.di

import com.github.jankoran90.showlyfin.data.csfd.CsfdScraper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CsfdModule {
    @Provides
    @Singleton
    fun providesCsfdScraper(): CsfdScraper = CsfdScraper()
}
