package com.github.jankoran90.showlyfin.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jankoran90.showlyfin.core.db.dao.FavoriteDao
import com.github.jankoran90.showlyfin.core.db.dao.SyncMetaDao
import com.github.jankoran90.showlyfin.core.db.entity.FavoriteEntity
import com.github.jankoran90.showlyfin.core.db.entity.SyncMetaEntity

/**
 * SUBSTRATE (SHW-99) — reaktivní datová páteř. SAMOSTATNÝ DB soubor `substrate.db` (odděleně od
 * `showlyfin.db` s profily — tu NESAHÁME). BEZ `fallbackToDestructiveMigration`: páteř nese uživatelská
 * data (oblíbené, později Trakt/JF/pozice), destrukce = ztráta dat → vždy explicitní migrace.
 *
 * F1 = jen doména `favorite`. F2b přidal `sync_meta` (delta kurzor per profil+doména, aditivní migrace
 * v1→v2). Další domény (saved_source, user_rating, trakt_*, jf_*, playback_state…) přibudou ve F3.
 */
@Database(
    entities = [FavoriteEntity::class, SyncMetaEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SubstrateDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun syncMetaDao(): SyncMetaDao
}
