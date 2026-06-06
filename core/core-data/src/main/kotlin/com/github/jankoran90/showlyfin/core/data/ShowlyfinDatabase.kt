package com.github.jankoran90.showlyfin.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jankoran90.showlyfin.core.data.dao.ProfileDao
import com.github.jankoran90.showlyfin.core.data.entity.AppSettingsEntity
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity

@Database(
    entities = [AppSettingsEntity::class, ProfileEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ShowlyfinDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
