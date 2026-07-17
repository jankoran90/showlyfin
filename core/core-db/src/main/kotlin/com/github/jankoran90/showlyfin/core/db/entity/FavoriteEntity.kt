package com.github.jankoran90.showlyfin.core.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * SUBSTRATE (SHW-99) F1 — doména OBLÍBENÉ v [substrate.db]. Room = jediný zdroj pravdy pro UI.
 *
 * Sync mixin (společný vzor všech páteřních tabulek):
 *  - [profileKey] = **profileUuid** (NE jellyfin_user_id — dva profily sdílející jeden JF účet by
 *    kolidovaly, viz F0 kritický nález). Per-profil izolace + cross-device.
 *  - [updatedAt] = čas poslední lokální změny (LWW při konfliktu).
 *  - [syncVersion] = verze přiřazená po úspěšném pushi (F1 zatím jen inkrement; delta v F2).
 *  - [dirty] = 1 → čeká na push na server.
 *  - [deleted] = 1 → **tombstone** (fyzicky nemažeme, aby UNION se serverem smazané nevzkřísil).
 *
 * PK = (profileKey, kind, refId). Index (profileKey) pro rychlý per-profil dotaz.
 */
@Entity(
    tableName = "favorite",
    primaryKeys = ["profileKey", "kind", "refId"],
    indices = [Index("profileKey")],
)
data class FavoriteEntity(
    val profileKey: String,
    /** [com.github.jankoran90.showlyfin.data.uploader.FavoriteKind].name (MOVIE/ACTOR/DIRECTOR/…). */
    val kind: String,
    /** Přirozený identifikátor položky = tmdbId (film / osoba / vydavatelství). */
    val refId: Long,
    val name: String = "",
    val imageUrl: String? = null,
    val year: Int? = null,
    /** Čas přidání do oblíbených (ms). */
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncVersion: Long = 0L,
    val dirty: Int = 0,
    val deleted: Int = 0,
)
