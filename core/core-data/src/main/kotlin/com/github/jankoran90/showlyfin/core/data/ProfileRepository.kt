package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.dao.ProfileDao
import com.github.jankoran90.showlyfin.core.data.dao.TemplateDao
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity
import com.github.jankoran90.showlyfin.core.domain.MirrorRefreshResult
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import com.github.jankoran90.showlyfin.core.domain.ProfileMeta
import com.github.jankoran90.showlyfin.core.domain.TraktCreds
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
        val merged = ProfileConfig.mergeEffective(template, override)
        // VAULT V7: applier per-profil creds MAŽE (null = odhlásit) — ale legacy/lokální profily
        // drží JF přihlášení jen v entitě (serverUrl+token), ne v balíku. Syntetizuj ho do configu,
        // ať applier funkční entity login nesmaže.
        if (merged.credentials.jellyfin == null && profile.serverUrl.isNotBlank() && profile.jellyfinToken.isNotBlank()) {
            return merged.copy(
                credentials = merged.credentials.copy(
                    jellyfin = com.github.jankoran90.showlyfin.core.domain.JellyfinCreds(
                        url = profile.serverUrl,
                        userId = profile.jellyfinUserId,
                        token = profile.jellyfinToken,
                        username = profile.name,
                    ),
                ),
            )
        }
        return merged
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

    /**
     * Plan GATEKEY G-A3 — stáhne backend roster a nasadí stuby. Best-effort; vrací počet přidaných.
     * COUCH: [requireJellyfinUser] = seeduj JEN profily s Jellyfin identitou (sledování = JF uživatel).
     * Na TV tím odfiltrujeme čistě poslechové/device-local profily; profily se stejným `jellyfinUserId`
     * (honza + neli = JF „dospělý") navíc [seedFromRoster] deduplikuje na jeden (párování na backendKey).
     */
    suspend fun seedFromBackendRoster(requireJellyfinUser: Boolean = false): Int {
        val metas = configGateway.fetchAllProfiles() ?: return 0
        val filtered = if (requireJellyfinUser) metas.filter { it.jellyfinUserId.isNotBlank() } else metas
        return seedFromRoster(filtered)
    }

    /**
     * COUCH — TV bootstrap: sjednoť profily se serverem jako telefon. Zajisti dostupnost backendu
     * (auto-login z build env, kdyby chyběla cookie po čisté instalaci) a naseeduj JF-user profily.
     * Best-effort — TV bez sítě/backendu prostě ponechá stávající lokální profily.
     */
    suspend fun seedTvRosterBestEffort() {
        runCatching {
            if (!configGateway.isAvailable() && ProfileConfigGateway.autoLoginPassword.isNotBlank()) {
                configGateway.login(ProfileConfigGateway.autoLoginPassword)
            }
            seedFromBackendRoster(requireJellyfinUser = true)
        }
    }

    /**
     * CATALOGUE (SHW-98) — JEN uploader auto-login z build env (BEZ roster seedu). Filmy TV klon záměrně
     * nevolá [seedTvRosterBestEffort] (natáhl by showlyfin roster honza/neli/deti), tím ale přišel i o login →
     * `uploader_base_url` na TV zůstal prázdný → „Uploader není nastaven", žádné zdroje/odznaky/Přehrát. Tohle
     * zapíše base_url+cookie do prefs jako telefon (idempotentní: `isAvailable()` guard = na už přihlášeném no-op).
     */
    suspend fun ensureUploaderLogin() {
        runCatching {
            if (!configGateway.isAvailable() && ProfileConfigGateway.autoLoginPassword.isNotBlank()) {
                configGateway.login(ProfileConfigGateway.autoLoginPassword)
            }
        }
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
            // Applier PŘED publikací do _activeConfig — observeři (ListenViewModel…) čtou kanonické
            // prefs, takže je musí applier stihnout zapsat dřív, než je emise configu probudí.
            configApplier.apply(effective, updated.id)
            _activeConfig.value = effective
        }
        // Plan PROFILES Fáze 2: write-through na backend (best-effort, gateway chyby polyká).
        Timber.i("[PUSH] updateConfig → push profile='${profile.name}' key='${profile.backendKey()}'")
        configGateway.pushConfig(profile.backendKey(), newJson, profile.name, profile.isAdmin, profile.jellyfinUserId)
    }

    /**
     * SUBSTRATE F2c KROK 2 — po Trakt (re)loginu appka pushne čerstvý token (přes [updateConfig]) a hned
     * kopne server mirror, aby server natáhl aktuální Trakt vkus do serverového mirroru (kurátor „Pro
     * tebe" z něj staví cold-start). Volej AŽ PO [updateConfig] (server musí mít čerstvý token). Best-effort.
     */
    suspend fun refreshTraktMirror(profileId: Long): MirrorRefreshResult? {
        val profile = dao.getById(profileId) ?: return null
        return runCatching { configGateway.refreshTraktMirror(profile.backendKey()) }
            .onFailure { Timber.w(it, "[SUBSTRATE] refreshTraktMirror selhal key='${profile.backendKey()}'") }
            .getOrNull()
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
        configApplier.apply(effective, active.id)
        _activeConfig.value = effective
    }

    /**
     * Přiřadí (uuid != null) nebo zruší (uuid = null) šablonu profilu (Plan WARDEN W0). Re-aplikuje
     * efektivní config, je-li profil aktivní. VAULT V9: při přiřazení se hodnoty šablony jednorázově
     * **snapshotnou do override** ([ProfileConfig.snapshotFromTemplate]) — merge už hodnoty šablony
     * nevynucuje (zámky jen gateují editaci), takže default profilu vzniká tady.
     */
    suspend fun assignTemplate(profileId: Long, templateUuid: String?) {
        val profile = dao.getById(profileId) ?: return
        val templateCfg = templateUuid
            ?.let { templateDao.getByUuid(it) }
            ?.let { ProfileConfig.fromJson(it.configJson) }
        val newJson = if (templateCfg != null) {
            val snapped = ProfileConfig.snapshotFromTemplate(templateCfg, ProfileConfig.fromJson(profile.configJson))
            ProfileConfig.toJson(snapped)
        } else {
            profile.configJson
        }
        dao.update(profile.copy(templateUuid = templateUuid, configJson = newJson))
        val updated = dao.getById(profileId)
        if (_activeProfile.value?.id == profileId && updated != null) {
            _activeProfile.value = updated
            val effective = effectiveConfigFor(updated)
            configApplier.apply(effective, updated.id)
            _activeConfig.value = effective
        }
        // Plan WARDEN W3c: write-through přiřazení na backend ("" = zrušit → backend uloží "").
        configGateway.pushAssignedTemplate(
            profile.backendKey(), profile.name, profile.isAdmin, profile.jellyfinUserId, templateUuid ?: "",
        )
        // VAULT V9: snapshot změnil override → propsat i config (jinak by ho příští sync vrátil).
        if (newJson != profile.configJson) {
            configGateway.pushConfig(profile.backendKey(), newJson ?: "{}", profile.name, profile.isAdmin, profile.jellyfinUserId)
        }
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

    /**
     * COUCH per-profil — zrcadli AKTUÁLNÍ Trakt token z kanonických `trakt_prefs` do balíku daného
     * profilu (typicky POSLEDNÍHO aktivního, PŘED přepnutím). Trakt refresh **rotuje refresh_token**
     * a ukládá ho jen do prefs, ne do profilu; bez tohoto write-backu by [ProfileConfigApplier] při
     * návratu na profil přepsal prefs STARÝM (rotací zneplatněným) tokenem z balíku → 401 → odhlášení.
     * Zároveň zrcadlí legacy token (jen v prefs, bez per-profil balíku) do balíku, aby ho gate uviděl.
     * Idempotentní: shodný token = no-op. Best-effort push na backend (cross-device).
     */
    private suspend fun captureCurrentTraktIntoProfile(profileId: Long) {
        val access = prefs.getString("TRAKT_ACCESS_TOKEN", null)?.takeIf { it.isNotBlank() } ?: return
        val profile = dao.getById(profileId) ?: return
        val override = ProfileConfig.fromJson(profile.configJson)
        if (override.credentials.trakt?.accessToken == access) return // shoda → nic
        timber.log.Timber.i("[TRAKT-GUARD] capture prefs token→config profil=%d accessLen=%d", profileId, access.length)
        val newTrakt = TraktCreds(
            accessToken = access,
            refreshToken = prefs.getString("TRAKT_REFRESH_TOKEN", null).orEmpty(),
            createdAtMillis = prefs.getLong("TRAKT_ACCESS_TOKEN_TIMESTAMP", 0L),
            expiresAtMillis = prefs.getLong("TRAKT_ACCESS_TOKEN_EXPIRES_TIMESTAMP", 0L),
            username = override.credentials.trakt?.username,
        )
        val newOverride = override.copy(credentials = override.credentials.copy(trakt = newTrakt))
        val newJson = ProfileConfig.toJson(newOverride)
        dao.update(profile.copy(configJson = newJson))
        // Best-effort write-through (gateway polyká chyby); NEaplikujeme — prefs už ten token drží.
        configGateway.pushConfig(profile.backendKey(), newJson, profile.name, profile.isAdmin, profile.jellyfinUserId)
    }

    suspend fun setActive(profileId: Long) {
        val profile = dao.getById(profileId) ?: return
        // COUCH per-profil: rotovaný/legacy Trakt token patří POSLEDNÍMU aktivnímu profilu — zrcadli ho
        // do jeho balíku DŘÍV, než applier níže přepíše prefs tokenem z balíku aktivovaného profilu.
        prefs.getLong(PREF_ACTIVE_PROFILE_ID, 0L).takeIf { it > 0L }?.let { captureCurrentTraktIntoProfile(it) }
        // COUCH R2 fix: kanonické Jellyfin prefs zapiš PŘED emisí _activeProfile. Konzumenti (TvHomeViewModel
        // reloadAllRows na activeProfile) čtou tyto prefs při reloadu — kdyby se emise stala dřív, reload by
        // sáhl na STARÉ creds a obsah by se nezměnil (device 306: „přepnutí profilu nic nedělá").
        // userId VŽDY s pomlčkami — konzumenti parsují UUID.fromString (VAULT V7, viz dashUuid).
        prefs.edit()
            .putLong(PREF_ACTIVE_PROFILE_ID, profileId)
            .putString("jellyfin_server_url", profile.serverUrl)
            .putString("jellyfin_token", profile.jellyfinToken)
            .putString("jellyfin_user_id", dashUuid(profile.jellyfinUserId))
            .apply()
        // Plan PROFILES: aplikuj config balík do kanonických prefs (ABS/Uploader/vzhled…).
        // Plan WARDEN W0: efektivní config = šablona ⊕ override. Applier PŘED publikací (viz updateConfig).
        // ORBIT — captureCurrentTraktIntoProfile výše mohl JUST-NOW zapsat čerstvý Trakt token do balíku
        // tohoto profilu; načti ho znovu z DAO, ať effectiveConfigFor nestaví config ze STALE snapshotu.
        val profileForConfig = dao.getById(profileId) ?: profile
        val config = effectiveConfigFor(profileForConfig)
        configApplier.apply(config, profile.id)
        _activeConfig.value = config
        // Emise profilu AŽ po zápisu prefs + configu → observeři reloadují s korektními creds.
        _activeProfile.value = profile
        // Plan PROFILES Fáze 2: zkus stáhnout aktuální balík z backendu (cache = lokální configJson).
        // Best-effort + timeout, ať přepnutí profilu nevisí offline. Uploader creds aplikované výše
        // → gateway má URL+cookie. Re-aplikuje jen pokud je profil pořád aktivní a balík přišel.
        syncConfigFromBackend(profile)
    }

    /**
     * Plan RIPPLE (SHW-34) — aktivní re-pull configu AKTIVNÍHO profilu z backendu, aby se admin změny
     * provedené z JINÉHO zařízení propsaly i bez přepnutí profilu / cold-startu. Volá se při návratu
     * appky do popředí (ON_RESUME). Lehké: žádný re-login, jen [syncConfigFromBackend] (best-effort +
     * timeouty), re-aplikuje efektivní config jen když se reálně něco změnilo.
     */
    suspend fun refreshActiveConfig() {
        val profile = _activeProfile.value ?: return
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
            val current = dao.getById(profile.id) ?: profile
            // TRVALÝ FIX (2026-07-15): Trakt token je per-PROFIL device/účet-specific — NIKDY ho nepřebírej
            // z cross-device backend slotu. Profily se sdíleným JF účtem (honza+neli) mají stejný backendKey
            // → jeden backend config slot → jinak si Trakt token navzájem přepisují (viz incident). Zachovej
            // lokální `trakt` a přebírej z remote jen ostatní creds/nastavení.
            val localCreds = ProfileConfig.fromJson(current.configJson).credentials
            val localTraktRaw = localCreds.trakt
            val remoteCfg = ProfileConfig.fromJson(remoteJson)
            // CONVERGE (handoff t0 2026-07-15): lokální trakt DÁL vyhrává (ochrana z hotfixu 332 proti záměně
            // mezi profily se sdíleným backendKey), ALE prázdný lokál (null) adoptuje token z backendu →
            // bootstrap nového zařízení (TV nemá prohlížeč pro login; token přihlášený na telefonu se sem
            // převezme). Bezpečné, dokud mají profily různé jellyfinUserId (kořen: slot per profileUuid — otevřeno).
            // FIX A (2026-07-16, emulátor): i EXPIROVANÝ lokální token ber jako null → adoptuj ČERSTVÝ z backendu.
            // Mrtvý (past-expiry) lokál jinak přebíjel čerstvý backend token pushnutý z telefonu → TV/emulátor
            // zůstal odhlášený i po re-loginu (ověřeno živě: 401 na sync/*, dialog „Trakt tě odhlásil").
            val nowMs = System.currentTimeMillis()
            // 🔒 FIX (user 2026-07-20 „Trakt zlobí každý den"): „live" vyžaduje i SPRÁVNÝ TVAR tokenu (plný 64-hex),
            // ne jen neprázdnost. Dřív se useknutý (32-znakový) remote token adoptoval a uložil do DB balíku →
            // odtud ho přepnutí profilu vytáhlo do prefs → Trakt 401. Teď: malformovaný token není „live" →
            // neadoptuje se z backendu → lokální dobrý token přežije. (Zrcadlí [looksLikeTraktToken] guard z applieru.)
            fun TraktCreds?.isLive() =
                this != null && looksLikeTraktToken(accessToken) && (expiresAtMillis <= 0L || expiresAtMillis > nowMs)
            // Živý lokál vyhrává; jinak adoptuj živý backend; jinak ponech (i mrtvý) lokál kvůli refresh_tokenu.
            val mergedTrakt = localTraktRaw.takeIf { it.isLive() }
                ?: remoteCfg.credentials.trakt?.takeIf { it.isLive() }
                ?: localTraktRaw
            // WEATHER (user 2026-07-16): JF/ABS/uploader creds jsou často LOKÁLNÍ (device login) a NEmusí
            // být v cross-device backend bundlu. Honza měl backend bundle BEZ JF → sync jinak vynuloval jeho
            // device JF (applyConfig remove → „JF nenastaven"). Backend vyhrává jen když creds SKUTEČNĚ nese
            // (nová TV se z něj bootstrapuje); jinak zachovej lokál — stejný princip jako u traktu. Per-profil
            // izolace (b129) drží: preservuje se JEN vlastní config TOHOTO profilu, ne cizí.
            val merged = remoteCfg.copy(
                credentials = remoteCfg.credentials.copy(
                    trakt = mergedTrakt,
                    jellyfin = remoteCfg.credentials.jellyfin ?: localCreds.jellyfin,
                    abs = remoteCfg.credentials.abs ?: localCreds.abs,
                    uploader = remoteCfg.credentials.uploader ?: localCreds.uploader,
                ),
            )
            val canonical = ProfileConfig.toJson(merged)
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
                configApplier.apply(effective, updated.id)
                _activeConfig.value = effective
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
