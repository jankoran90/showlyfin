package com.github.jankoran90.showlyfin.core.db.di

import android.content.Context
import androidx.room.Room
import com.github.jankoran90.showlyfin.core.db.SubstrateDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SUBSTRATE (SHW-99) F1 — Hilt provider páteřní DB. VĚDOMĚ BEZ `fallbackToDestructiveMigration`
 * (na rozdíl od core-data showlyfin.db) — páteř nese uživatelská data, destrukce zakázána.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreDbModule {

    @Provides
    @Singleton
    fun providesSubstrateDatabase(
        @ApplicationContext context: Context,
    ): SubstrateDatabase = Room.databaseBuilder(
        context,
        SubstrateDatabase::class.java,
        "substrate.db",
    ).build()

    @Provides
    fun providesFavoriteDao(db: SubstrateDatabase) = db.favoriteDao()
}
