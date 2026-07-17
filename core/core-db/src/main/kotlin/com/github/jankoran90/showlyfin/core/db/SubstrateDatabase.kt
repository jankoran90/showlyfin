package com.github.jankoran90.showlyfin.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jankoran90.showlyfin.core.db.dao.FavoriteDao
import com.github.jankoran90.showlyfin.core.db.entity.FavoriteEntity

/**
 * SUBSTRATE (SHW-99) — reaktivní datová páteř. SAMOSTATNÝ DB soubor `substrate.db` (odděleně od
 * `showlyfin.db` s profily — tu NESAHÁME). BEZ `fallbackToDestructiveMigration`: páteř nese uživatelská
 * data (oblíbené, později Trakt/JF/pozice), destrukce = ztráta dat → vždy explicitní migrace.
 *
 * F1 = jen doména `favorite`. Další domény (saved_source, user_rating, trakt_*, jf_*, playback_state…)
 * přibudou přes řádnou Room migraci ve F3.
 */
@Database(
    entities = [FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SubstrateDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
