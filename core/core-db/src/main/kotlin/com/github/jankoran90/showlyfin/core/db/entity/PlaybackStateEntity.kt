package com.github.jankoran90.showlyfin.core.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * SUBSTRATE (SHW-99) F3 / EXCISE (SHW-103) Fáze B — doména POZICE PŘEHRÁVÁNÍ direct epizod v [substrate.db].
 *
 * ABS audioknihy/podcasty mají pozici na ABS serveru (cross-device zadarmo); direct epizody (RSS /
 * YouTube / ČT — přímá audio URL, žádný serverový stav) ji dosud držely jen lokálně
 * ([com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore]). Tahle tabulka jim dělá
 * cross-device ekvivalent přes generický delta sync (klíč `slovo-main` u appky Slovo).
 *
 * Sync mixin (vzor [FavoriteEntity]): [profileKey]=profileUuid, [updatedAt]=LWW, [syncVersion], [dirty], [deleted] (tombstone).
 * Identita řádku = [mediaKey] (`yt:<id>` / `rss:<id>` / `ctv:<id>`) — smí obsahovat dvojtečku, proto je celý mediaKey rowId.
 */
@Entity(
    tableName = "playback_state",
    primaryKeys = ["profileKey", "mediaKey"],
    indices = [Index("profileKey")],
)
data class PlaybackStateEntity(
    val profileKey: String,
    val mediaKey: String,
    val posMs: Long,
    val durMs: Long,
    val updatedAt: Long = 0L,
    val syncVersion: Long = 0L,
    val dirty: Int = 0,
    val deleted: Int = 0,
)
