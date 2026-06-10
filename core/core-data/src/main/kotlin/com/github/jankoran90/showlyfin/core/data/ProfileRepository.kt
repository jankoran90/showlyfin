package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.dao.ProfileDao
import com.github.jankoran90.showlyfin.core.data.dao.TemplateDao
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import com.github.jankoran90.showlyfin.core.domain.ProfileMeta
import timber.log.Timber
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
    private val templateDao: TemplateDao,
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val configApplier: ProfileConfigApplier,
    private val configGateway: ProfileConfigGateway,
) {
    /**
     * Klíč profilu pro backend (Plan PROFILES Fáze 4E) = **`jellyfinUserId`** (prefer) → stabilní
     * NAPŘÍČ ZAŘÍZENÍMI: stejný Jellyfin uživatel = stejný klíč na všech telefonech → config se stáhne
     * z backendu kdekoliv (cross-device sync). Každý profil má vlastní JF účet (rozhodnutí usera #33),
     * takže klíče nekolidují. Fallback = stabilní per-profil [ProfileEntity.profileUuid] (profily bez
     * JF loginu — device-local), pak `p<localId>`.
     */
    private fun ProfileEntity.backendKey(): String =
        jellyfinUserId.ifBlank { profileUuid.ifBlank { "p$id" } }

    /**
     * Plan WARDEN W0 — efektivní config profilu = **šablona ⊕ override**. Override = uživatelův
     * [ProfileEntity.configJson]; šablona dle [ProfileEntity.templateUuid] (null / neexistuje =
     * legacy bez šablony → vrací jen override = plná volnost). Zamčená pole vždy diktuje šablona.
     */
    private suspend fun effectiveConfigFor(profile: ProfileEntity): ProfileConfig {
        val override = ProfileConfig.fromJson(profile.configJson)
        val template = profile.templateUuid
            ?.let { templateDao.getByUuid(it) }
            ?.let { ProfileConfig.fromJson(it.configJson) }
        return ProfileConfig.mergeEffective(template, override)
    }

    private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
    val activeProfile: StateFlow<ProfileEntity?> = _activeProfile.asStateFlow()

    /** Config balík aktivního profilu (Plan PROFILES). DEFAULT = bez restrikcí (legacy/odhlášeno). */
    private val _activeConfig = MutableStateFlow(ProfileConfig.DEFAULT)
    val activeConfig: StateFlow<ProfileConfig> = _activeConfig.asStateFlow()

    fun observeAll(): Flow<List<ProfileEntity>> = dao.observeAll()

    suspend fun getAll(): List<ProfileEntity> = dao.getAll()

    /**
     * Plan GATEKEY G-A3 — nasadí lokální `ProfileEntity` **stuby** z backend rosteru (`/api/profiles`),
     * aby profil picker po čisté instalaci ukázal profily bez ručního zadávání. Stub nese jen meta
     * (jméno/admin/jellyfinUserId/šablona/PIN/avatarTag) — creds (serverUrl/token/config) jsou **prázdné**
     * a dotáhnou se až při tapu profilu ([setActive] → [syncConfigFromBackend] + auto JF login = G-A4).
     *
     * Idempotentní: profil se páruje na `backendKey` (= jellyfinUserId/key). Existující se aktualizuje
     * o meta (jméno/admin/šablona/PIN/avatar), creds zůstávají netknuté. Vrací počet **nově přidaných**.
     */
    suspend fun seedFromRoster(metas: List<ProfileMeta>): Int {
        var added = 0
        val existing = dao.getAll()
        for (meta in metas) {
            val key = meta.key.ifBlank { meta.jellyfinUserId }.ifBlank { continue }
            val match = existing.firstOrNull { it.backendKey() == key }
            if (match != null) {
                // Roster je zdroj pravdy pro meta; creds (serverUrl/token/configJson) neměníme.
                val updated = match.copy(
                    name = meta.name.ifBlank { match.name },
                    isAdmin = meta.isAdmin,
                    avatarTag = meta.avatarTag ?: match.avatarTag,
                    templateUuid = meta.templateUuid ?: match.templateUuid,
                    loginPinHash = meta.loginPinHash ?: match.loginPinHash,
                )
                if (updated != match) dao.update(updated)
            } else {
                dao.insert(
                    ProfileEntity(
                        name = meta.name.ifBlank { "Profil" },
                        serverUrl = "",
                        jellyfinUserId = meta.jellyfinUserId.ifBlank { meta.key },
                        jellyfinToken = "",
                        avatarTag = meta.avatarTag,
                        isAdmin = meta.isAdmin,
                        templateUuid = meta.templateUuid,
                        loginPinHash = meta.loginPinHash,
                    )
                )
                added++
            }
        }
        return added
    }

    /** Plan GATEKEY G-A3 — stáhne backend roster a nasadí stuby. Best-effort; vrací počet přidaných. */
    suspend fun seedFromBackendRoster(): Int {
        val metas = configGateway.fetchAllProfiles() ?: return 0
        return seedFromRoster(metas)
    }

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

    /** Plan HELM — nastaví/zruší app-login PIN profilu (hash; null = zrušit) + write-through na backend. */
    suspend fun setLoginPinHash(profileId: Long, hash: String?) {
        val profile = dao.getById(profileId) ?: return
        dao.update(profile.copy(loginPinHash = hash))
        if (_activeProfile.value?.id == profileId) _activeProfile.value = dao.getById(profileId)
        Timber.i("[PUSH] setLoginPinHash → push key='${profile.backendKey()}' pin=${if (hash==null) "clear" else "set"}")
        configGateway.pushLoginPin(profile.backendKey(), profile.name, profile.isAdmin, profile.jellyfinUserId, hash ?: "")
    }

    /** Nastaví/zruší cestu k vlastní fotce profilu (Plan PROFILES 1D). */
    suspend fun setAvatarPath(profileId: Long, path: String?) {
        val profile = dao.getById(profileId) ?: return
        dao.update(profile.copy(avatarPath = path))
        if (_activeProfile.value?.id == profileId) _activeProfile.value = dao.getById(profileId)
    }

    /**
     * Write-through editace **uživatelského override** profilu (configJson). Re-aplikuje *efektivní*
     * config (šablona ⊕ override), je-li profil aktivní — zamčená pole tak zůstanou dle šablony i
     * kdyby override jejich hodnotu nesl (Plan WARDEN W0; tvrdé gating UI přijde ve W2/W4).
     */
    suspend fun updateConfig(profileId: Long, transform: (ProfileConfig) -> ProfileConfig) {
        val profile = dao.getById(profileId) ?: return
        val newOverride = transform(ProfileConfig.fromJson(profile.configJson))
        val newJson = ProfileConfig.toJson(newOverride)
        dao.update(profile.copy(configJson = newJson))
        val updated = dao.getById(profileId)
        if (_activeProfile.value?.id == profileId && updated != null) {
            _activeProfile.value = updated
            val effective = effectiveConfigFor(updated)
            _activeConfig.value = effective
            configApplier.apply(effective)
        }
        // Plan PROFILES Fáze 2: write-through na backend (best-effort, gateway chyby polyká).
        Timber.i("[PUSH] updateConfig → push profile='${profile.name}' key='${profile.backendKey()}'")
        configGateway.pushConfig(profile.backendKey(), newJson, profile.name, profile.isAdmin, profile.jellyfinUserId)
    }

    // --- Šablony (Plan WARDEN W0) ---

    fun observeTemplates(): Flow<List<TemplateEntity>> = templateDao.observeAll()

    suspend fun getTemplates(): List<TemplateEntity> = templateDao.getAll()

    suspend fun getTemplate(uuid: String): TemplateEntity? = templateDao.getByUuid(uuid)

    suspend fun templateCount(): Int = templateDao.count()

    suspend fun upsertTemplate(template: TemplateEntity): Long =
        if (template.id == 0L) templateDao.insert(template)
        else { templateDao.update(template); template.id }

    suspend fun deleteTemplate(template: TemplateEntity) = templateDao.delete(template)

    /**
     * In-app authoring šablony (Plan WARDEN W3c část 2) — lokální upsert + write-through na backend
     * (zdroj pravdy, jako [updateConfig]). Re-aplikuje efektivní config, pokud aktivní profil tuhle
     * šablonu používá (zamčená pole se mohla změnit).
     */
    suspend fun saveTemplateAuthored(template: TemplateEntity): Long {
        val id = upsertTemplate(template)
        Timber.i("[PUSH] saveTemplate → push uuid='${template.templateUuid.ifBlank { "<BLANK!>" }}' name='${template.name}'")
        configGateway.pushTemplate(template.templateUuid, template.name, template.maxAgeRating, template.configJson ?: "{}")
        reapplyIfActiveUsesTemplate(template.templateUuid)
        return id
    }

    /** In-app smazání šablony (Plan WARDEN W3c) — lokál + backend (auto-odpojí profily server-side). */
    suspend fun deleteTemplateAuthored(template: TemplateEntity) {
        templateDao.delete(template)
        configGateway.deleteTemplate(template.templateUuid)
        reapplyIfActiveUsesTemplate(template.templateUuid)
    }

    private suspend fun reapplyIfActiveUsesTemplate(uuid: String) {
        val active = _activeProfile.value ?: return
        if (active.templateUuid != uuid) return
        val effective = effectiveConfigFor(active)
        _activeConfig.value = effective
        configApplier.apply(effective)
    }

    /**
     * Přiřadí (uuid != null) nebo zruší (uuid = null) šablonu profilu (Plan WARDEN W0). Re-aplikuje
     * efektivní config, je-li profil aktivní.
     */
    suspend fun assignTemplate(profileId: Long, templateUuid: String?) {
        val profile = dao.getById(profileId) ?: return
        dao.update(profile.copy(templateUuid = templateUuid))
        val updated = dao.getById(profileId)
        if (_activeProfile.value?.id == profileId && updated != null) {
            _activeProfile.value = updated
            val effective = effectiveConfigFor(updated)
            _activeConfig.value = effective
            configApplier.apply(effective)
        }
        // Plan WARDEN W3c: write-through přiřazení na backend ("" = zrušit → backend uloží "").
        configGateway.pushAssignedTemplate(
            profile.backendKey(), profile.name, profile.isAdmin, profile.jellyfinUserId, templateUuid ?: "",
        )
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

    /** Plan GATEKEY G-A4 — stáhne config balík profilu z backendu (raw JSON; dešifrované creds). */
    suspend fun fetchBackendConfig(profile: ProfileEntity): String? =
        configGateway.fetchConfig(profile.backendKey())

    // Plan HELM — passthrough na gateway pro in-app admin editor (knihovny/žánry/záloha).
    suspend fun fetchJellyfinLibraries(jellyfinUserId: String?) =
        configGateway.fetchJellyfinLibraries(jellyfinUserId)
    suspend fun fetchTmdbGenres(): List<String>? = configGateway.fetchTmdbGenres()
    suspend fun exportProfiles(): String? = configGateway.exportProfiles()
    suspend fun importProfiles(json: String): Boolean = configGateway.importProfiles(json)

    /**
     * Plan GATEKEY G-A4 — zapíše **hydratované** Jellyfin creds (po fetchi balíku / AuthenticateByName)
     * do entity PŘED aktivací, aby [setActive] zapsal kanonické prefs už se správným serverUrl/tokenem
     * (jinak JF/ABS obrazovky naběhnou s prázdným tokenem dřív, než async sync dotáhne creds).
     */
    suspend fun applyHydratedJellyfin(profileId: Long, serverUrl: String, token: String, configJson: String?) {
        val p = dao.getById(profileId) ?: return
        dao.update(p.copy(serverUrl = serverUrl, jellyfinToken = token, configJson = configJson ?: p.configJson))
    }

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
        // Plan PROFILES: aplikuj config balík do kanonických prefs (ABS/Uploader/vzhled…).
        // Plan WARDEN W0: efektivní config = šablona ⊕ override.
        val config = effectiveConfigFor(profile)
        _activeConfig.value = config
        configApplier.apply(config)
        // Plan PROFILES Fáze 2: zkus stáhnout aktuální balík z backendu (cache = lokální configJson).
        // Best-effort + timeout, ať přepnutí profilu nevisí offline. Uploader creds aplikované výše
        // → gateway má URL+cookie. Re-aplikuje jen pokud je profil pořád aktivní a balík přišel.
        syncConfigFromBackend(profile)
    }

    /**
     * Stáhne z backendu šablony + přiřazení + config balík profilu a uloží jako lokální cache
     * (Plan PROFILES Fáze 2 + WARDEN W3c). Best-effort s timeouty; po všech změnách re-aplikuje
     * efektivní config (šablona ⊕ override) jednou, je-li profil aktivní.
     */
    private suspend fun syncConfigFromBackend(profile: ProfileEntity) {
        var changed = false

        // 1. Šablony (globální, web authoring → lokální cache pro mergeEffective i offline).
        withTimeoutOrNull(5000) { configGateway.fetchTemplates() }?.let { payloads ->
            for (p in payloads) {
                val existing = templateDao.getByUuid(p.uuid)
                val entity = (existing ?: TemplateEntity(templateUuid = p.uuid, name = p.name))
                    .copy(name = p.name, maxAgeRating = p.ageRating, configJson = p.configJson)
                templateDao.insert(entity) // REPLACE dle PK (existující nese své id)
            }
        }

        // 2. Přiřazení šablony profilu (cross-device). null = neměnit; "" = zrušit; jinak = uuid.
        val assigned = withTimeoutOrNull(5000) { configGateway.fetchAssignedTemplateUuid(profile.backendKey()) }
        if (assigned != null) {
            val newUuid = assigned.ifBlank { null }
            if (newUuid != profile.templateUuid) {
                dao.update(profile.copy(templateUuid = newUuid))
                changed = true
            }
        }

        // 3. Config balík (uživatelský override).
        val remoteJson = withTimeoutOrNull(5000) {
            configGateway.fetchConfig(profile.backendKey())
        }?.takeIf { it.isNotBlank() }
        if (remoteJson != null) {
            val canonical = ProfileConfig.toJson(ProfileConfig.fromJson(remoteJson))
            val current = dao.getById(profile.id) ?: profile
            if (canonical != current.configJson) {
                dao.update(current.copy(configJson = canonical))
                changed = true
            }
        }

        // 4. Re-aplikuj efektivní config, je-li profil aktivní a něco se změnilo.
        if (changed && _activeProfile.value?.id == profile.id) {
            val updated = dao.getById(profile.id)
            if (updated != null) {
                _activeProfile.value = updated
                val effective = effectiveConfigFor(updated)
                _activeConfig.value = effective
                configApplier.apply(effective)
            }
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
