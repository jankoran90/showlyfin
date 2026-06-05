package com.github.jankoran90.showlyfin.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jankoran90.showlyfin.core.data.entity.AppSettingsEntity

@Database(
    entities = [AppSettingsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ShowlyfinDatabase : RoomDatabase()
