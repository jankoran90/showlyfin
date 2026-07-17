package com.github.jankoran90.showlyfin.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.jankoran90.showlyfin.core.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * SUBSTRATE (SHW-99) F1 — DAO nad tabulkou `favorite`. [observe] vrací reaktivní [Flow] (UI čte JEN
 * odsud → okamžité překreslení). Mazání = [markDeleted] tombstone, NIKDY fyzický delete.
 */
@Dao
interface FavoriteDao {

    /** Reaktivní seznam živých (ne-tombstone) oblíbených profilu. */
    @Query("SELECT * FROM favorite WHERE profileKey = :profileKey AND deleted = 0 ORDER BY addedAt DESC")
    fun observe(profileKey: String): Flow<List<FavoriteEntity>>

    /** Vše včetně tombstones (pro sync/merge/push). */
    @Query("SELECT * FROM favorite WHERE profileKey = :profileKey")
    suspend fun getAll(profileKey: String): List<FavoriteEntity>

    @Query("SELECT * FROM favorite WHERE profileKey = :profileKey AND kind = :kind AND refId = :refId LIMIT 1")
    suspend fun get(profileKey: String, kind: String, refId: Long): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FavoriteEntity>)

    /** Tombstone: označí jako smazané + dirty (čeká na push), fyzicky NEmaže. */
    @Query("UPDATE favorite SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE profileKey = :profileKey AND kind = :kind AND refId = :refId")
    suspend fun markDeleted(profileKey: String, kind: String, refId: Long, updatedAt: Long)

    /** Řádky čekající na push. */
    @Query("SELECT * FROM favorite WHERE profileKey = :profileKey AND dirty = 1")
    suspend fun getDirty(profileKey: String): List<FavoriteEntity>

    /** Po úspěšném pushi shodí dirty a povýší verzi. */
    @Query("UPDATE favorite SET dirty = 0, syncVersion = :version WHERE profileKey = :profileKey AND dirty = 1")
    suspend fun clearDirty(profileKey: String, version: Long)

    @Query("SELECT COUNT(*) FROM favorite WHERE profileKey = :profileKey")
    suspend fun count(profileKey: String): Int
}
