package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * Implementace [ProfileConfigGateway] přes jellyfin-uploader backend (Plan PROFILES Fáze 2).
 * Čte uploader URL + session cookie z kanonických prefs (`uploader_base_url`/`uploader_session_cookie`,
 * stejné klíče jako ostatní uploader konzumenti). Best-effort — chyby se logují a polykají, aby
 * přepnutí profilu fungovalo i offline (lokální `configJson` cache).
 */
internal class UploaderProfileConfigGateway @Inject constructor(
    private val remote: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ProfileConfigGateway {

    private fun baseUrl(): String = prefs.getString("uploader_base_url", "")?.trim().orEmpty()
    private fun cookie(): String = prefs.getString("uploader_session_cookie", "")?.trim().orEmpty()

    override suspend fun isAvailable(): Boolean = baseUrl().isNotBlank() && cookie().isNotBlank()

    override suspend fun fetchConfig(key: String): String? {
        if (!isAvailable() || key.isBlank()) return null
        return runCatching { remote.getProfileConfig(baseUrl(), cookie(), key) }
            .onFailure { Timber.w(it, "[Profiles] fetchConfig($key) selhal") }
            .getOrNull()
    }

    override suspend fun pushConfig(key: String, json: String, name: String, isAdmin: Boolean, jellyfinUserId: String) {
        if (!isAvailable() || key.isBlank()) return
        runCatching {
            remote.putProfile(baseUrl(), cookie(), key, name, isAdmin, jellyfinUserId)
            remote.putProfileConfig(baseUrl(), cookie(), key, json)
        }.onFailure { Timber.w(it, "[Profiles] pushConfig($key) selhal") }
    }
}
