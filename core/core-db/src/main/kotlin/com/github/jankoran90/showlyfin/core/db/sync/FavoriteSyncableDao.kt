package com.github.jankoran90.showlyfin.core.db.sync

import com.github.jankoran90.showlyfin.core.db.dao.FavoriteDao
import com.github.jankoran90.showlyfin.core.db.entity.FavoriteEntity
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow
import com.google.gson.Gson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SUBSTRATE (SHW-99) F2b — adaptér domény OBLÍBENÉ pro [SyncEngine] nad [FavoriteDao].
 *
 * Identita řádku (`rowId`) = **`kind:id`** — konzistentní se serverovým `derive_row_id`
 * (`_ID_SPECS["favorites"] = [("kind","id")]`). `payload` = JSON [FavoriteItem].
 */
@Singleton
class FavoriteSyncableDao @Inject constructor(
    private val dao: FavoriteDao,
    private val gson: Gson,
) : SyncableDao {

    // ── PUSH: dirty Room řádky → DeltaRow ────────────────────────────────────────
    override suspend fun dirtyRows(profileKey: String): List<DeltaRow> =
        dao.getDirty(profileKey).map { e ->
            DeltaRow(
                rowId = rowId(e.kind, e.refId),
                payload = gson.toJsonTree(e.toItem()),
                updatedAt = e.updatedAt,
                version = e.syncVersion,
                deleted = e.deleted,
            )
        }

    // ── PULL: serverové řádky → Room (union-safe, LWW, tombstone-aware) ───────────
    override suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>) {
        val toUpsert = mutableListOf<FavoriteEntity>()
        for (row in rows) {
            val id = identity(row) ?: continue
            val (kind, refId) = id
            val local = dao.get(profileKey, kind, refId)
            // Neztratit rozdělanou lokální změnu: dirty lokál vyhrává, není-li server prokazatelně novější.
            if (local != null && local.dirty == 1 && local.updatedAt >= row.updatedAt) continue

            if (row.deleted == 1) {
                if (local == null) continue  // nikdy jsme neměli → nic k tombstonování
                toUpsert += local.copy(
                    deleted = 1, dirty = 0, updatedAt = row.updatedAt, syncVersion = row.version,
                )
            } else {
                val item = payloadItem(row) ?: continue  // live řádek bez payloadu nedokážeme materializovat
                toUpsert += item.toEntity(profileKey, dirty = 0, updatedAt = row.updatedAt, version = row.version)
            }
        }
        if (toUpsert.isNotEmpty()) dao.upsertAll(toUpsert)
    }

    // ── po ACK pushe: shodit dirty + uložit serverovou verzi ─────────────────────
    override suspend fun clearDirty(profileKey: String, versions: Map<String, Long>) {
        versions.forEach { (rid, version) ->
            val kind = rid.substringBefore(':')
            val refId = rid.substringAfter(':').toLongOrNull() ?: return@forEach
            dao.clearDirtyRow(profileKey, kind, refId, version)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────
    private fun rowId(kind: String, refId: Long): String = "$kind:$refId"

    /** Identita řádku = (kind.name, refId). Přednostně z payloadu, fallback z rowId `kind:id`. */
    private fun identity(row: DeltaRow): Pair<String, Long>? {
        payloadItem(row)?.let { if (it.id > 0L) return it.kind.name to it.id }
        val rid = row.rowId
        val idx = rid.indexOf(':')
        if (idx <= 0) return null
        val kind = rid.substring(0, idx)
        val refId = rid.substring(idx + 1).toLongOrNull() ?: return null
        return kind to refId
    }

    private fun payloadItem(row: DeltaRow): FavoriteItem? {
        val p = row.payload ?: return null
        if (p.isJsonNull) return null
        return runCatching { gson.fromJson(p, FavoriteItem::class.java) }
            .onFailure { Timber.w(it, "[SUBSTRATE] parse favorite delta payload") }.getOrNull()
    }

    private fun FavoriteEntity.toItem(): FavoriteItem = FavoriteItem(
        kind = runCatching { FavoriteKind.valueOf(kind) }.getOrDefault(FavoriteKind.MOVIE),
        id = refId,
        name = name,
        imageUrl = imageUrl,
        year = year,
        addedAtMs = addedAt,
    )

    private fun FavoriteItem.toEntity(key: String, dirty: Int, updatedAt: Long, version: Long): FavoriteEntity =
        FavoriteEntity(
            profileKey = key,
            kind = kind.name,
            refId = id,
            name = name,
            imageUrl = imageUrl,
            year = year,
            addedAt = addedAtMs,
            updatedAt = updatedAt,
            syncVersion = version,
            dirty = dirty,
            deleted = 0,
        )
}
