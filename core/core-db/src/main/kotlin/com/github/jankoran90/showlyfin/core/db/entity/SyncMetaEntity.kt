package com.github.jankoran90.showlyfin.core.db.entity

import androidx.room.Entity

/**
 * SUBSTRATE (SHW-99) F2b — kurzor delta sync per (profil, doména). Drží poslední verzi, kterou klient
 * úspěšně stáhl (`GET …/delta?since=lastPullVersion`), aby příště tahal jen změny. Monotónní (server-side).
 *
 * PK = (profileKey, domain). `lastPullVersion=0` → první pull vrátí PLNÝ snímek (union do Room).
 */
@Entity(tableName = "sync_meta", primaryKeys = ["profileKey", "domain"])
data class SyncMetaEntity(
    val profileKey: String,
    /** Sync doména na serveru: `favorites|working-sources|ratings|recommendations|stream-presets`. */
    val domain: String,
    val lastPullVersion: Long = 0L,
)
