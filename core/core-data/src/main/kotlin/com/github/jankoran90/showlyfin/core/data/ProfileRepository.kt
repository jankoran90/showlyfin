package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.dao.ProfileDao
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val PREF_ACTIVE_PROFILE_ID = "active_profile_id"

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val configApplier: ProfileConfigApplier,
    private val configGateway: ProfileConfigGateway,
) {
    /** Klíč profilu pro backend (Plan PROFILES Fáze 2) = jellyfinUserId, fallback `p<localId>`. */
    private fun ProfileEntity.backendKey(): String = jellyfinUserId.ifBlank { "p$id" }

    private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
    val activeProfile: StateFlow<ProfileEntity?> = _activeProfile.asStateFlow()

    /** Config balík aktivního profilu (Plan PROFILES). DEFAULT = bez restrikcí (legacy/odhlášeno). */
    private val _activeConfig = MutableStateFlow(ProfileConfig.DEFAULT)
    val activeConfig: StateFlow<ProfileConfig> = _activeConfig.asStateFlow()

    fun observeAll(): Flow<List<ProfileEntity>> = dao.observeAll()

    suspend fun getAll(): List<ProfileEntity> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun upsert(profile: ProfileEntity): Long {
        return if (profile.id == 0L) dao.insert(profile)
        else { dao.update(profile); profile.id }
    }

    suspend fun delete(profile: ProfileEntity) {
        dao.delete(profile)
        if (_activeProfile.value?.id == profile.id) {
            _activeProfile.value = null
            _activeConfig.value = ProfileConfig.DEFAULT
            prefs.edit().remove(PREF_ACTIVE_PROFILE_ID).apply()
        }
    }

    /**
     * Odhlášení / přepnutí profilu (Plan PROFILES 1C). Zruší aktivní profil → startovní brána ukáže
     * ProfilePicker. Profil ZŮSTÁVÁ uložený v DB vč. přihlášení (kanonické creds se přepíšou až při
     * příští aktivaci profilu).
     */
    fun clearActive() {
        _activeProfile.value = null
        _activeConfig.value = ProfileConfig.DEFAULT
        prefs.edit().remove(PREF_ACTIVE_PROFILE_ID).apply()
    }

    /** Přejmenování profilu (Plan PROFILES 1D). */
    suspend fun rename(profileId: Long, newName: String) {
        val profile = dao.getById(profileId) ?: return
        dao.update(profile.copy(name = newName))
        if (_activeProfile.value?.id == profileId) _activeProfile.value = dao.getById(profileId)
    }

    /** Nastaví/zruší cestu k vlastní fotce profilu (Plan PROFILES 1D). */
    suspend fun setAvatarPath(profileId: Long, path: String?) {
        val profile = dao.getById(profileId) ?: return
        dao.update(profile.copy(avatarPath = path))
        if (_activeProfile.value?.id == profileId) _activeProfile.value = dao.getById(profileId)
    }

    /** Write-through editace configu profilu (admin in-app). Re-aplikuje, je-li profil aktivní. */
    suspend fun updateConfig(profileId: Long, transform: (ProfileConfig) -> ProfileConfig) {
        val profile = dao.getById(profileId) ?: return
        val newConfig = transform(ProfileConfig.fromJson(profile.configJson))
        val newJson = ProfileConfig.toJson(newConfig)
        dao.update(profile.copy(configJson = newJson))
        if (_activeProfile.value?.id == profileId) {
            _activeProfile.value = dao.getById(profileId)
            _activeConfig.value = newConfig
            configApplier.apply(newConfig)
        }
        // Plan PROFILES Fáze 2: write-through na backend (best-effort, gateway chyby polyká).
        configGateway.pushConfig(profile.backendKey(), newJson, profile.name, profile.isAdmin, profile.jellyfinUserId)
    }

    suspend fun setDefault(profileId: Long) {
        dao.clearDefault()
        dao.setDefault(profileId)
    }

    suspend fun setTvDefault(profileId: Long) {
        dao.clearTvDefault()
        dao.setTvDefault(profileId)
    }

    suspend fun getTvDefault(): ProfileEntity? = dao.getTvDefault()

    suspend fun updateMaxAgeRating(profileId: Long, rating: String?) {
        val profile = dao.getById(profileId) ?: return
        dao.update(profile.copy(maxAgeRating = rating))
        if (_activeProfile.value?.id == profileId) {
            _activeProfile.value = dao.getById(profileId)
        }
    }

    suspend fun getDefault(): ProfileEntity? = dao.getDefault()

    suspend fun setActive(profileId: Long) {
        val profile = dao.getById(profileId) ?: return
        _activeProfile.value = profile
        prefs.edit().putLong(PREF_ACTIVE_PROFILE_ID, profileId).apply()
        // Backward compat: write canonical Jellyfin prefs so existing ViewModels work
        prefs.edit()
            .putString("jellyfin_server_url", profile.serverUrl)
            .putString("jellyfin_token", profile.jellyfinToken)
            .putString("jellyfin_user_id", profile.jellyfinUserId)
            .apply()
        // Plan PROFILES: aplikuj config balík do kanonických prefs (ABS/Uploader/vzhled…)
        val config = ProfileConfig.fromJson(profile.configJson)
        _activeConfig.value = config
        configApplier.apply(config)
        // Plan PROFILES Fáze 2: zkus stáhnout aktuální balík z backendu (cache = lokální configJson).
        // Best-effort + timeout, ať přepnutí profilu nevisí offline. Uploader creds aplikované výše
        // → gateway má URL+cookie. Re-aplikuje jen pokud je profil pořád aktivní a balík přišel.
        syncConfigFromBackend(profile)
    }

    /** Stáhne config balík profilu z backendu (Plan PROFILES Fáze 2) a uloží jako lokální cache. */
    private suspend fun syncConfigFromBackend(profile: ProfileEntity) {
        val remoteJson = withTimeoutOrNull(5000) {
            configGateway.fetchConfig(profile.backendKey())
        }?.takeIf { it.isNotBlank() } ?: return
        val remoteConfig = ProfileConfig.fromJson(remoteJson)
        val canonical = ProfileConfig.toJson(remoteConfig)
        if (canonical == profile.configJson) return // beze změny
        dao.update(profile.copy(configJson = canonical))
        if (_activeProfile.value?.id == profile.id) {
            _activeProfile.value = dao.getById(profile.id)
            _activeConfig.value = remoteConfig
            configApplier.apply(remoteConfig)
        }
    }

    suspend fun restoreActive(preferTv: Boolean = false) {
        val id = prefs.getLong(PREF_ACTIVE_PROFILE_ID, 0L).takeIf { it > 0L }
        val profile = id?.let { dao.getById(it) }
            ?: if (preferTv) dao.getTvDefault() ?: dao.getDefault() else dao.getDefault()
        if (profile != null) setActive(profile.id)
    }

    suspend fun migrateLegacyPrefsIfNeeded(): ProfileEntity? {
        if (dao.count() > 0) return null
        val serverUrl = prefs.getString("jellyfin_server_url", null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString("jellyfin_token", null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs.getString("jellyfin_user_id", null)?.takeIf { it.isNotBlank() } ?: return null
        val username = prefs.getString("jellyfin_user_name", null)?.takeIf { it.isNotBlank() } ?: "Hlavní profil"
        val profile = ProfileEntity(
            name = username,
            serverUrl = serverUrl,
            jellyfinUserId = userId,
            jellyfinToken = token,
            isAdmin = true, // Legacy single-profile = admin
            isDefault = true,
        )
        val id = dao.insert(profile)
        setActive(id)
        return dao.getById(id)
    }
}
