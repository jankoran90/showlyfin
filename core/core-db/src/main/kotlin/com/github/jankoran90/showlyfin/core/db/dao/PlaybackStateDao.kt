package com.github.jankoran90.showlyfin.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.jankoran90.showlyfin.core.db.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * SUBSTRATE (SHW-99) F3 — DAO nad `playback_state`. [observe] = reaktivní živé (ne-tombstone) pozice
 * profilu. Mazání (dohráno) = [markDeleted] tombstone, nikdy fyzický delete (sync union nesmí vzkřísit).
 */
@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE profileKey = :profileKey AND deleted = 0")
    fun observe(profileKey: String): Flow<List<PlaybackStateEntity>>

    @Query("SELECT * FROM playback_state WHERE profileKey = :profileKey AND mediaKey = :mediaKey AND deleted = 0 LIMIT 1")
    suspend fun get(profileKey: String, mediaKey: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE profileKey = :profileKey AND mediaKey = :mediaKey LIMIT 1")
    suspend fun getRaw(profileKey: String, mediaKey: String): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PlaybackStateEntity>)

    /** Tombstone: dohráno/smazáno → deleted+dirty, fyzicky nemaže. */
    @Query("UPDATE playback_state SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE profileKey = :profileKey AND mediaKey = :mediaKey")
    suspend fun markDeleted(profileKey: String, mediaKey: String, updatedAt: Long)

    @Query("SELECT * FROM playback_state WHERE profileKey = :profileKey AND dirty = 1")
    suspend fun getDirty(profileKey: String): List<PlaybackStateEntity>

    @Query("UPDATE playback_state SET dirty = 0, syncVersion = :version WHERE profileKey = :profileKey AND mediaKey = :mediaKey")
    suspend fun clearDirtyRow(profileKey: String, mediaKey: String, version: Long)

    @Query("SELECT * FROM playback_state WHERE profileKey = :profileKey")
    suspend fun getAll(profileKey: String): List<PlaybackStateEntity>
}
