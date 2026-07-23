package com.github.jankoran90.showlyfin.core.db.sync

import com.github.jankoran90.showlyfin.core.db.dao.PlaybackStateDao
import com.github.jankoran90.showlyfin.core.db.entity.PlaybackStateEntity
import com.github.jankoran90.showlyfin.core.db.model.PlaybackStateItem
import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow
import com.google.gson.Gson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SUBSTRATE (SHW-99) F3 — adaptér domény `playback-state` pro [SyncEngine] nad [PlaybackStateDao].
 *
 * Identita řádku (`rowId`) = **`mediaKey`** (smí obsahovat dvojtečku → NEsplítáme; celý = rowId),
 * konzistentní se serverovým `derive_row_id` (`_ID_SPECS["playback-state"] = [("mediaKey",)]`).
 */
@Singleton
class PlaybackStateSyncableDao @Inject constructor(
    private val dao: PlaybackStateDao,
    private val gson: Gson,
) : SyncableDao {

    override suspend fun dirtyRows(profileKey: String): List<DeltaRow> =
        dao.getDirty(profileKey).map { e ->
            DeltaRow(
                rowId = e.mediaKey,
                payload = gson.toJsonTree(PlaybackStateItem(e.mediaKey, e.posMs, e.durMs, e.updatedAt)),
                updatedAt = e.updatedAt,
                version = e.syncVersion,
                deleted = e.deleted,
            )
        }

    override suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>) {
        val toUpsert = mutableListOf<PlaybackStateEntity>()
        for (row in rows) {
            val mediaKey = identity(row) ?: continue
            val local = dao.getRaw(profileKey, mediaKey)
            if (local != null && local.dirty == 1 && local.updatedAt >= row.updatedAt) continue
            if (row.deleted == 1) {
                if (local == null) continue
                toUpsert += local.copy(deleted = 1, dirty = 0, updatedAt = row.updatedAt, syncVersion = row.version)
            } else {
                val item = payloadItem(row) ?: continue
                toUpsert += PlaybackStateEntity(
                    profileKey = profileKey,
                    mediaKey = mediaKey,
                    posMs = item.posMs,
                    durMs = item.durMs,
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

    private fun payloadItem(row: DeltaRow): PlaybackStateItem? {
        val p = row.payload ?: return null
        if (p.isJsonNull) return null
        return runCatching { gson.fromJson(p, PlaybackStateItem::class.java) }
            .onFailure { Timber.w(it, "[SUBSTRATE] parse playback-state delta payload") }.getOrNull()
    }
}
