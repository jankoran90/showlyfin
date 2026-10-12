package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * PROFIL (2026-08-16, user „nahrávám je já Honza, když budu chtít zobrazit ostatním mám na to
 * fci") — kdo nahrál kterou audioknihu (`GET /api/audiobook/ownership`, backend jellyfin-uploader).
 * ABS sám o vlastníkovi nic neví; appka podle téhle mapy + [com.github.jankoran90.showlyfin.core
 * .domain.ProfileConfig.sharedAudiobookIds] řídí per-profil viditelnost, stejný vzor jako
 * [PodcastSourcesRepository]/[PodcastSource.addedBy].
 */
@Singleton
class AudiobookOwnershipRepository @Inject constructor(
    private val service: UploaderService,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    /**
     * itemId → profileUuid, co audioknihu nahrál. Chybí-li klíč = legacy (před featurou) → viditelné všem.
     * Codex sweep 2026-10-12: start z POSLEDNÍ ÚSPĚŠNÉ mapy (prefs) — dřív prázdná mapa při výpadku
     * uploaderu / offline = „všechno je legacy" = cizí nesdílené knihy viditelné.
     */
    private val _ownership = MutableStateFlow(loadCached())
    val ownership = _ownership.asStateFlow()

    private fun loadCached(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(KEY_CACHE, null) ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun saveCache(map: Map<String, String>) {
        prefs.edit().putString(KEY_CACHE, JSONObject(map).toString()).apply()
    }

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    suspend fun refresh() {
        if (baseUrl.isBlank()) return // bez uploaderu drž poslední známou mapu (ne „všechno legacy")
        runCatching { service.getAudiobookOwnership("${baseUrl.trimEnd('/')}/api/audiobook/ownership", cookie) }
            .onSuccess { map ->
                val m = map.mapNotNull { (id, e) -> e.addedBy?.let { id to it } }.toMap()
                _ownership.value = m
                saveCache(m)
            }
            .onFailure { Timber.w(it, "[PROFIL] načtení vlastnictví audioknih selhalo — držím poslední známou mapu") }
    }

    /** true = audiokniha [itemId] je viditelná profilu [myUuid] s [sharedIds] (nasdílené jemu). */
    fun isVisible(itemId: String, myUuid: String?, sharedIds: Set<String>): Boolean {
        val owner = _ownership.value[itemId] ?: return true // legacy/neznámé = viditelné všem
        return owner == myUuid || itemId in sharedIds
    }

    private companion object {
        const val KEY_CACHE = "audiobook_ownership_cache"
    }
}
