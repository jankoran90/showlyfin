package com.github.jankoran90.showlyfin.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jankoran90.showlyfin.core.db.dao.FavoriteDao
import com.github.jankoran90.showlyfin.core.db.dao.PlaybackStateDao
import com.github.jankoran90.showlyfin.core.db.dao.SavedShowDao
import com.github.jankoran90.showlyfin.core.db.dao.SyncMetaDao
import com.github.jankoran90.showlyfin.core.db.entity.FavoriteEntity
import com.github.jankoran90.showlyfin.core.db.entity.PlaybackStateEntity
import com.github.jankoran90.showlyfin.core.db.entity.SavedShowEntity
import com.github.jankoran90.showlyfin.core.db.entity.SyncMetaEntity

/**
 * SUBSTRATE (SHW-99) — reaktivní datová páteř. SAMOSTATNÝ DB soubor `substrate.db` (odděleně od
 * `showlyfin.db` s profily — tu NESAHÁME). BEZ `fallbackToDestructiveMigration`: páteř nese uživatelská
 * data (oblíbené, později Trakt/JF/pozice), destrukce = ztráta dat → vždy explicitní migrace.
 *
 * F1 = jen doména `favorite`. F2b přidal `sync_meta` (delta kurzor per profil+doména, aditivní migrace
 * v1→v2). F3 (EXCISE Fáze B) přidal poslechové domény `playback_state` (pozice ČT/YouTube/RSS) +
 * `saved_show` (oblíbené pořady) — aditivní migrace v2→v3. Další domény (user_rating, trakt_*, jf_*…) později.
 */
@Database(
    entities = [
        FavoriteEntity::class,
        SyncMetaEntity::class,
        PlaybackStateEntity::class,
        SavedShowEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class SubstrateDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun savedShowDao(): SavedShowDao
}
