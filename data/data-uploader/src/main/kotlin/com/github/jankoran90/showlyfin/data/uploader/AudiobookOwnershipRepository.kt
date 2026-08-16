package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** itemId → profileUuid, co audioknihu nahrál. Chybí-li klíč = legacy (před featurou) → viditelné všem. */
    private val _ownership = MutableStateFlow<Map<String, String>>(emptyMap())
    val ownership = _ownership.asStateFlow()

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    suspend fun refresh() {
        if (baseUrl.isBlank()) { _ownership.value = emptyMap(); return }
        runCatching { service.getAudiobookOwnership("${baseUrl.trimEnd('/')}/api/audiobook/ownership", cookie) }
            .onSuccess { map -> _ownership.value = map.mapNotNull { (id, e) -> e.addedBy?.let { id to it } }.toMap() }
            .onFailure { Timber.w(it, "[PROFIL] načtení vlastnictví audioknih selhalo") }
    }

    /** true = audiokniha [itemId] je viditelná profilu [myUuid] s [sharedIds] (nasdílené jemu). */
    fun isVisible(itemId: String, myUuid: String?, sharedIds: Set<String>): Boolean {
        val owner = _ownership.value[itemId] ?: return true // legacy/neznámé = viditelné všem
        return owner == myUuid || itemId in sharedIds
    }
}
