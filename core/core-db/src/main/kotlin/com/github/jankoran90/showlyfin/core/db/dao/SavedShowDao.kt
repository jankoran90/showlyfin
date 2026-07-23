package com.github.jankoran90.showlyfin.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.jankoran90.showlyfin.core.db.entity.SavedShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * SUBSTRATE (SHW-99) F3 — DAO nad `saved_show` (oblíbené pořady / srdíčka). [observe] = reaktivní živé,
 * nejnovější srdíčko první. Mazání = [markDeleted] tombstone.
 */
@Dao
interface SavedShowDao {

    @Query("SELECT * FROM saved_show WHERE profileKey = :profileKey AND deleted = 0 ORDER BY addedAt DESC")
    fun observe(profileKey: String): Flow<List<SavedShowEntity>>

    @Query("SELECT * FROM saved_show WHERE profileKey = :profileKey AND type = :type AND ref = :ref LIMIT 1")
    suspend fun get(profileKey: String, type: String, ref: String): SavedShowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedShowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SavedShowEntity>)

    @Query("UPDATE saved_show SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE profileKey = :profileKey AND type = :type AND ref = :ref")
    suspend fun markDeleted(profileKey: String, type: String, ref: String, updatedAt: Long)

    @Query("SELECT * FROM saved_show WHERE profileKey = :profileKey AND dirty = 1")
    suspend fun getDirty(profileKey: String): List<SavedShowEntity>

    @Query("UPDATE saved_show SET dirty = 0, syncVersion = :version WHERE profileKey = :profileKey AND type = :type AND ref = :ref")
    suspend fun clearDirtyRow(profileKey: String, type: String, ref: String, version: Long)

    @Query("SELECT * FROM saved_show WHERE profileKey = :profileKey")
    suspend fun getAll(profileKey: String): List<SavedShowEntity>
}
