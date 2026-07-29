package com.github.jankoran90.showlyfin.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.jankoran90.showlyfin.core.db.entity.CtvWatchedEntity
import kotlinx.coroutines.flow.Flow

/**
 * VLTAVA (SHW-110) — DAO nad `ctv_watched`. [observe] = reaktivní živé (ne-tombstone) dokoukané díly
 * profilu. Odznačení = [markDeleted] tombstone, nikdy fyzický delete (sync union by ho vzkřísil).
 */
@Dao
interface CtvWatchedDao {

    @Query("SELECT * FROM ctv_watched WHERE profileKey = :profileKey AND deleted = 0")
    fun observe(profileKey: String): Flow<List<CtvWatchedEntity>>

    @Query("SELECT * FROM ctv_watched WHERE profileKey = :profileKey AND mediaKey = :mediaKey LIMIT 1")
    suspend fun getRaw(profileKey: String, mediaKey: String): CtvWatchedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CtvWatchedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CtvWatchedEntity>)

    /** Tombstone: „přece jen nezhlédnuto" → deleted+dirty, fyzicky nemaže. */
    @Query("UPDATE ctv_watched SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE profileKey = :profileKey AND mediaKey = :mediaKey")
    suspend fun markDeleted(profileKey: String, mediaKey: String, updatedAt: Long)

    @Query("SELECT * FROM ctv_watched WHERE profileKey = :profileKey AND dirty = 1")
    suspend fun getDirty(profileKey: String): List<CtvWatchedEntity>

    @Query("UPDATE ctv_watched SET dirty = 0, syncVersion = :version WHERE profileKey = :profileKey AND mediaKey = :mediaKey")
    suspend fun clearDirtyRow(profileKey: String, mediaKey: String, version: Long)

    @Query("SELECT * FROM ctv_watched WHERE profileKey = :profileKey")
    suspend fun getAll(profileKey: String): List<CtvWatchedEntity>
}
