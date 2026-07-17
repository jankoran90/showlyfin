package com.github.jankoran90.showlyfin.core.db.sync

import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow

/**
 * SUBSTRATE (SHW-99) F2b — kontrakt domény pro generický [SyncEngine]. Každá páteřní doména
 * (favorites, working-sources, …) dodá adaptér nad svým Room DAO, který umí:
 *
 *  - [dirtyRows] — lokální řádky čekající na push (`dirty=1`), převedené na [DeltaRow]
 *    (`rowId` = stabilní identita = serverový `derive_row_id`; `payload` = JSON položky; `deleted` = tombstone).
 *  - [applyServerRows] — aplikovat stažené serverové řádky do Room (deleted→tombstone; jinak upsert,
 *    a to jen když `!localDirty || server.updatedAt novější` — union-safe, nepřepíše rozdělanou lokální změnu).
 *  - [clearDirty] — po ACK pushe shodit `dirty=0` a uložit serverovou verzi (`rowId → version`).
 */
interface SyncableDao {

    suspend fun dirtyRows(profileKey: String): List<DeltaRow>

    suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>)

    suspend fun clearDirty(profileKey: String, versions: Map<String, Long>)
}
