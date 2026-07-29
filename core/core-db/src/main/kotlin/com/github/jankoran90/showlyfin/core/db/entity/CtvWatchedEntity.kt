package com.github.jankoran90.showlyfin.core.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * VLTAVA (SHW-110) — doména DOKOUKANÝCH dílů ČT v `substrate.db`.
 *
 * Díl ČT nemá Jellyfin id ani imdb, takže „zhlédnuto" nemá kam nahlásit (Jellyfin/Trakt reportér ho
 * nevezme) a z pozice přehrávání to poznat NEJDE — dokoukaný díl má pozici smazanou stejně jako díl,
 * který se nikdy nespustil. Dosud to držel lokální `SharedPreferences` → stav neznal druhý přístroj
 * a navíc byl SPOLEČNÝ VŠEM PROFILŮM (dětský viděl dokoukané dospělého). Tahle tabulka to dělá
 * per profil a cross-device přes generický delta sync (doména `ctv-watched`).
 *
 * Sync mixin (vzor [PlaybackStateEntity]): [profileKey]=profileUuid, [updatedAt]=LWW, [syncVersion],
 * [dirty], [deleted] (tombstone = „přece jen nezhlédnuto", nikdy fyzický delete).
 * Identita řádku = [mediaKey] (`ctv:<idec>`) — smí obsahovat dvojtečku, proto je celý mediaKey rowId.
 */
@Entity(
    tableName = "ctv_watched",
    primaryKeys = ["profileKey", "mediaKey"],
    indices = [Index("profileKey")],
)
data class CtvWatchedEntity(
    val profileKey: String,
    val mediaKey: String,
    /** Kdy byl díl označen za dokoukaný (epoch ms). */
    val watchedAt: Long,
    val updatedAt: Long = 0L,
    val syncVersion: Long = 0L,
    val dirty: Int = 0,
    val deleted: Int = 0,
)
