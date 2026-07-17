package com.github.jankoran90.showlyfin.core.db.sync

import com.github.jankoran90.showlyfin.core.db.dao.SyncMetaDao
import com.github.jankoran90.showlyfin.core.db.entity.SyncMetaEntity
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SUBSTRATE (SHW-99) F2b — **generický delta sync broker** (nahrazuje F1 full-blob PUT/GET).
 *
 * Jeden [sync] cyklus per (doména, profil):
 *  1. **PUSH** dirty řádků → `POST …/{domain}/delta`. Po ACK ulož serverovou verzi + `dirty=0` ([SyncableDao.clearDirty]).
 *  2. **PULL** `GET …/{domain}/delta?since=lastPullVersion` → [SyncableDao.applyServerRows] (union-safe merge)
 *     → ulož nový `lastPullVersion` do `sync_meta`.
 *
 * Offline/chyba = no-op nad lokálem (data se neztratí): push i pull vrací null → cyklus se přeruší,
 * dirty zůstane a zkusí se příště. `lastPullVersion=0` (první pull) → server vrátí PLNÝ snímek → union do Room.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    private val syncMetaDao: SyncMetaDao,
) {
    /**
     * @param domain serverová doména (`favorites|working-sources|ratings|recommendations|stream-presets`)
     * @param dao adaptér domény nad jejím Room DAO
     * @param profileKey per-profil klíč (= profileUuid)
     * @param baseUrl / [cookie] serverové přístupy (stejné jako full-blob volání; prázdné = jen lokál)
     */
    suspend fun sync(
        domain: String,
        dao: SyncableDao,
        profileKey: String,
        baseUrl: String,
        cookie: String,
    ) {
        if (profileKey.isBlank() || baseUrl.isBlank()) return  // nepřihlášeno / žádný profil → jen lokál

        // ── 1) PUSH dirty ───────────────────────────────────────────────────────
        val dirty = dao.dirtyRows(profileKey)
        if (dirty.isNotEmpty()) {
            val resp = uploaderDs.postProfileDelta(baseUrl, cookie, profileKey, domain, dirty)
            if (resp != null) {
                val versions = resp.rows.associate { it.rowId to it.version }
                dao.clearDirty(profileKey, versions)
                Timber.i("[SUBSTRATE] %s push: %d řádků → verze %d", domain, resp.applied, resp.version)
            } else {
                Timber.w("[SUBSTRATE] %s push selhal (offline?) — dirty zůstává", domain)
                return  // nepokračuj na pull, ať se dirty nepřepíše serverem před úspěšným pushem
            }
        }

        // ── 2) PULL since=lastPullVersion ───────────────────────────────────────
        val since = syncMetaDao.lastPullVersion(profileKey, domain) ?: 0L
        val delta = uploaderDs.getProfileDelta(baseUrl, cookie, profileKey, domain, since) ?: return
        if (delta.rows.isNotEmpty()) {
            dao.applyServerRows(profileKey, delta.rows)
            Timber.i("[SUBSTRATE] %s pull since=%d: %d řádků → verze %d", domain, since, delta.rows.size, delta.version)
        }
        if (delta.version != since) {
            syncMetaDao.upsert(SyncMetaEntity(profileKey, domain, delta.version))
        }
    }
}
