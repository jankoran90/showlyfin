package com.github.jankoran90.showlyfin.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jankoran90.showlyfin.core.data.dao.ProfileDao
import com.github.jankoran90.showlyfin.core.data.dao.TemplateDao
import com.github.jankoran90.showlyfin.core.data.entity.AppSettingsEntity
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity

@Database(
    entities = [AppSettingsEntity::class, ProfileEntity::class, TemplateEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class ShowlyfinDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun templateDao(): TemplateDao
}
