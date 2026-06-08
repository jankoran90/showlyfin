package com.github.jankoran90.showlyfin.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jankoran90.showlyfin.core.data.ShowlyfinDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "showlyfin_prefs")

/**
 * v3 → v4 (Plan PROFILES): přidá `configJson` + `avatarPath` do tabulky `profile`. Formální migrace,
 * aby update appky NESMAZAL uložené profily (dřív `fallbackToDestructiveMigration`).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profile ADD COLUMN configJson TEXT")
        db.execSQL("ALTER TABLE profile ADD COLUMN avatarPath TEXT")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule {

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): ShowlyfinDatabase = Room.databaseBuilder(
        context,
        ShowlyfinDatabase::class.java,
        "showlyfin.db",
    ).addMigrations(MIGRATION_3_4)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun providesProfileDao(db: ShowlyfinDatabase) = db.profileDao()

    @Provides
    @Singleton
    fun providesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore
}
