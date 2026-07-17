package com.github.jankoran90.showlyfin.core.db.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    /**
     * SUBSTRATE F2b — aditivní migrace v1→v2: přidá `sync_meta` (delta kurzor). NEdestruktivní,
     * `favorite` tabulka beze změny (dirty/deleted/updatedAt/syncVersion už z v1). Chrání data usera z v1.0.6.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_meta` (" +
                    "`profileKey` TEXT NOT NULL, `domain` TEXT NOT NULL, " +
                    "`lastPullVersion` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profileKey`, `domain`))",
            )
        }
    }

    @Provides
    @Singleton
    fun providesSubstrateDatabase(
        @ApplicationContext context: Context,
    ): SubstrateDatabase = Room.databaseBuilder(
        context,
        SubstrateDatabase::class.java,
        "substrate.db",
    ).addMigrations(MIGRATION_1_2).build()

    @Provides
    fun providesFavoriteDao(db: SubstrateDatabase) = db.favoriteDao()

    @Provides
    fun providesSyncMetaDao(db: SubstrateDatabase) = db.syncMetaDao()
}
