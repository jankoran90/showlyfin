package com.github.jankoran90.showlyfin.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Plan WARDEN W0 — pojmenovaná **šablona** = znovupoužitelný config + lock-mapa + věkové pásmo.
 * Admin ji autoruje (in-app W3 / backend W3) a přiřazuje uživatelům přes [ProfileEntity.templateUuid].
 * Efektivní config uživatele = šablona ⊕ override (viz [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.mergeEffective]).
 *
 * [configJson] = serializovaný [com.github.jankoran90.showlyfin.core.domain.ProfileConfig] (baseline
 * + `lockedKeys`). Lock-mapa žije uvnitř configu šablony, aby šel přenášet jeden JSON (backend balík).
 */
@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stabilní klíč šablony (přiřazení + backend). */
    val templateUuid: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    /** Serializovaný [com.github.jankoran90.showlyfin.core.domain.ProfileConfig] (baseline + lockedKeys). */
    val configJson: String? = null,
    /** Věkové pásmo šablony (název [com.github.jankoran90.showlyfin.core.domain.AgeRating]). null = bez omezení. */
    val maxAgeRating: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
