package com.github.jankoran90.showlyfin.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [],
    version = 1,
    exportSchema = true,
)
abstract class ShowlyfinDatabase : RoomDatabase()
