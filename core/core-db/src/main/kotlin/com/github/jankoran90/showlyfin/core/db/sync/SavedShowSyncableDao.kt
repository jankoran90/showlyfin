package com.github.jankoran90.showlyfin.core.db.sync

import com.github.jankoran90.showlyfin.core.db.dao.SavedShowDao
import com.github.jankoran90.showlyfin.core.db.entity.SavedShowEntity
import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import com.google.gson.Gson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SUBSTRATE (SHW-99) F3 — adaptér domény `saved-shows` pro [SyncEngine] nad [SavedShowDao].
 *
 * Identita řádku (`rowId`) = **`type:ref`** (server `_ID_SPECS["saved-shows"] = [("type","ref")]`).
 * `type` je bez dvojtečky, `ref` ji smí obsahovat (RSS feed URL) → fallback split na PRVNÍ dvojtečce;
 * přednostně bereme identitu z payloadu ([SourceSearchResult]). `payload` = celá karta (offline render).
 */
@Singleton
class SavedShowSyncableDao @Inject constructor(
    private val dao: SavedShowDao,
    private val gson: Gson,
) : SyncableDao {

    override suspend fun dirtyRows(profileKey: String): List<DeltaRow> =
        dao.getDirty(profileKey).map { e ->
            DeltaRow(
                rowId = "${e.type}:${e.ref}",
                payload = gson.toJsonTree(e.toCard()),
                updatedAt = e.updatedAt,
                version = e.syncVersion,
                deleted = e.deleted,
            )
        }

    override suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>) {
        val toUpsert = mutableListOf<SavedShowEntity>()
        for (row in rows) {
            val id = identity(row) ?: continue
            val (type, ref) = id
            val local = dao.get(profileKey, type, ref)
            if (local != null && local.dirty == 1 && local.updatedAt >= row.updatedAt) continue
            if (row.deleted == 1) {
                if (local == null) continue
                toUpsert += local.copy(deleted = 1, dirty = 0, updatedAt = row.updatedAt, syncVersion = row.version)
            } else {
                val card = payloadCard(row) ?: continue
                toUpsert += card.toEntity(
                    profileKey = profileKey,
                    // Zachovej lokální addedAt pořadí, jinak čas serverové verze (nové srdíčko z jiného zařízení).
                    addedAt = local?.addedAt ?: row.updatedAt,
                    updatedAt = row.updatedAt,
                    version = row.version,
                    dirty = 0,
                )
            }
        }
        if (toUpsert.isNotEmpty()) dao.upsertAll(toUpsert)
    }

    override suspend fun clearDirty(profileKey: String, versions: Map<String, Long>) {
        versions.forEach { (rid, version) ->
            val idx = rid.indexOf(':')
            if (idx <= 0) return@forEach
            dao.clearDirtyRow(profileKey, rid.substring(0, idx), rid.substring(idx + 1), version)
        }
    }

    /** Identita = (type, ref). Přednostně z payloadu, fallback split rowId na PRVNÍ dvojtečce. */
    private fun identity(row: DeltaRow): Pair<String, String>? {
        payloadCard(row)?.let { if (it.type.isNotBlank() && it.ref.isNotBlank()) return it.type to it.ref }
        val rid = row.rowId
        val idx = rid.indexOf(':')
        if (idx <= 0) return null
        return rid.substring(0, idx) to rid.substring(idx + 1)
    }

    private fun payloadCard(row: DeltaRow): SourceSearchResult? {
        val p = row.payload ?: return null
        if (p.isJsonNull) return null
        return runCatching { gson.fromJson(p, SourceSearchResult::class.java) }
            .onFailure { Timber.w(it, "[SUBSTRATE] parse saved-shows delta payload") }.getOrNull()
    }

    private fun SavedShowEntity.toCard(): SourceSearchResult = SourceSearchResult(
        type = type, ref = ref, title = title, subtitle = subtitle,
        thumbnail = thumbnail, summary = summary, episodeCount = episodeCount, category = category,
    )

    private fun SourceSearchResult.toEntity(
        profileKey: String, addedAt: Long, updatedAt: Long, version: Long, dirty: Int,
    ): SavedShowEntity = SavedShowEntity(
        profileKey = profileKey, type = type, ref = ref, title = title, subtitle = subtitle,
        thumbnail = thumbnail, summary = summary, episodeCount = episodeCount, category = category,
        addedAt = addedAt, updatedAt = updatedAt, syncVersion = version, dirty = dirty, deleted = 0,
    )
}
