package com.github.jankoran90.showlyfin.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val traktUsername: String? = null,
    val jellyfinServerUrl: String? = null,
)
