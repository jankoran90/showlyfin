package com.github.jankoran90.showlyfin.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serverUrl: String,
    val jellyfinUserId: String,
    val jellyfinToken: String,
    val avatarTag: String? = null,
    val isAdmin: Boolean = false,
    val isDefault: Boolean = false,
    val tvDefault: Boolean = false,
    val maxAgeRating: String? = null,
    /** Serializovaný [com.github.jankoran90.showlyfin.core.domain.ProfileConfig] (Plan PROFILES). */
    val configJson: String? = null,
    /** Cesta k vlastní lokální fotce profilu (filesDir/avatars/<id>.jpg). null = Jellyfin avatar/iniciála. */
    val avatarPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
