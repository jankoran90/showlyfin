package com.github.jankoran90.showlyfin.core.db.sync

import com.github.jankoran90.showlyfin.core.db.dao.CtvWatchedDao
import com.github.jankoran90.showlyfin.core.db.entity.CtvWatchedEntity
import com.github.jankoran90.showlyfin.core.db.model.CtvWatchedItem
import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow
import com.google.gson.Gson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VLTAVA (SHW-110) — adaptér domény `ctv-watched` pro [SyncEngine] nad [CtvWatchedDao].
 *
 * Identita řádku (`rowId`) = **`mediaKey`** (`ctv:<idec>`; smí obsahovat dvojtečku → NEsplítáme,
 * celý je rowId), konzistentní se serverovým `derive_row_id`
 * (`_ID_SPECS["ctv-watched"] = [("mediaKey",)]`). Vzor [PlaybackStateSyncableDao].
 */
@Singleton
class CtvWatchedSyncableDao @Inject constructor(
    private val dao: CtvWatchedDao,
    private val gson: Gson,
) : SyncableDao {

    override suspend fun dirtyRows(profileKey: String): List<DeltaRow> =
        dao.getDirty(profileKey).map { e ->
            DeltaRow(
                rowId = e.mediaKey,
                payload = gson.toJsonTree(CtvWatchedItem(e.mediaKey, e.watchedAt)),
                updatedAt = e.updatedAt,
                version = e.syncVersion,
                deleted = e.deleted,
            )
        }

    override suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>) {
        val toUpsert = mutableListOf<CtvWatchedEntity>()
        for (row in rows) {
            val mediaKey = identity(row) ?: continue
            val local = dao.getRaw(profileKey, mediaKey)
            // Lokální nepushnutá změna, která je novější, má přednost (jinak by ji server přepsal).
            if (local != null && local.dirty == 1 && local.updatedAt >= row.updatedAt) continue
            if (row.deleted == 1) {
                if (local == null) continue
                toUpsert += local.copy(deleted = 1, dirty = 0, updatedAt = row.updatedAt, syncVersion = row.version)
            } else {
                val item = payloadItem(row)
                toUpsert += CtvWatchedEntity(
                    profileKey = profileKey,
                    mediaKey = mediaKey,
                    watchedAt = item?.watchedAt?.takeIf { it > 0L } ?: row.updatedAt,
                    updatedAt = row.updatedAt,
                    syncVersion = row.version,
                    dirty = 0,
                    deleted = 0,
                )
            }
        }
        if (toUpsert.isNotEmpty()) dao.upsertAll(toUpsert)
    }

    override suspend fun clearDirty(profileKey: String, versions: Map<String, Long>) {
        versions.forEach { (rid, version) -> dao.clearDirtyRow(profileKey, rid, version) }
    }

    /** Identita = mediaKey. Přednostně z payloadu, fallback z rowId (celý = mediaKey). */
    private fun identity(row: DeltaRow): String? =
        payloadItem(row)?.mediaKey?.takeIf { it.isNotBlank() } ?: row.rowId.takeIf { it.isNotBlank() }

    private fun payloadItem(row: DeltaRow): CtvWatchedItem? {
        val p = row.payload ?: return null
        if (p.isJsonNull) return null
        return runCatching { gson.fromJson(p, CtvWatchedItem::class.java) }
            .onFailure { Timber.w(it, "[SUBSTRATE] parse ctv-watched delta payload") }.getOrNull()
    }
}
