package com.github.jankoran90.showlyfin.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.jankoran90.showlyfin.core.db.entity.SyncMetaEntity

/** SUBSTRATE (SHW-99) F2b — DAO nad [SyncMetaEntity] (poslední stažená delta verze per profil+doména). */
@Dao
interface SyncMetaDao {

    @Query("SELECT lastPullVersion FROM sync_meta WHERE profileKey = :profileKey AND domain = :domain LIMIT 1")
    suspend fun lastPullVersion(profileKey: String, domain: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: SyncMetaEntity)
}
