package com.github.jankoran90.showlyfin.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stabilní per-profil klíč pro backend (Plan PROFILES Fáze 4). Nezávislý na Jellyfin účtu →
     * dva profily sdílející jeden JF účet se NEpřelévají. Generuje se při vzniku profilu. */
    val profileUuid: String = java.util.UUID.randomUUID().toString(),
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
    /**
     * Plan WARDEN: přiřazená šablona ([com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity.templateUuid]).
     * null = legacy / bez šablony = plná volnost („Dospělý/vše"). Efektivní config = šablona ⊕ override.
     */
    val templateUuid: String? = null,
    /**
     * Plan WARDEN: hash volitelného app-login PINu uživatele. null = bez hesla (rychlý vstup).
     * Admin profil ho má vždy nastavený. Hash, ne plaintext.
     */
    val loginPinHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
