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

/**
 * v4 → v5 (Plan PROFILES Fáze 4): přidá `profileUuid` = stabilní per-profil backend klíč nezávislý
 * na Jellyfin účtu (fix „prolévání" configu mezi profily, které sdílejí jeden JF účet). Existující
 * řádky se zpětně doplní náhodným uuid (`randomblob`), aby každý měl unikátní stabilní klíč.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profile ADD COLUMN profileUuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE profile SET profileUuid = lower(hex(randomblob(16))) WHERE profileUuid = '' OR profileUuid IS NULL")
    }
}

/**
 * v5 → v6 (Plan WARDEN W0): profilová architektura admin/uživatel.
 * - `profile` += `templateUuid` (přiřazená šablona; null = legacy/bez šablony = plná volnost) +
 *   `loginPinHash` (volitelný app-login PIN, hash; null = bez hesla).
 * - nová tabulka `template` = pojmenovaná znovupoužitelná šablona (config + lock-mapa + věk).
 * Stávající profily zůstanou bez šablony (NULL) → efektivní config = jejich override (beze změny).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profile ADD COLUMN templateUuid TEXT")
        db.execSQL("ALTER TABLE profile ADD COLUMN loginPinHash TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `template` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`templateUuid` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`configJson` TEXT, " +
                "`maxAgeRating` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
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
    ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun providesProfileDao(db: ShowlyfinDatabase) = db.profileDao()

    @Provides
    fun providesTemplateDao(db: ShowlyfinDatabase) = db.templateDao()

    @Provides
    @Singleton
    fun providesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore
}
