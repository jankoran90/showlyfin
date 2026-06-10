package com.github.jankoran90.showlyfin.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM template ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM template ORDER BY createdAt ASC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM template WHERE templateUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM template")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)
}
