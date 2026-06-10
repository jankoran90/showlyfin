package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.JellyfinLibraryRef
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import com.github.jankoran90.showlyfin.core.domain.ProfileMeta
import com.github.jankoran90.showlyfin.core.domain.TemplatePayload
import com.google.gson.JsonParser
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

    /** Doplní scheme, když chybí (uživatel zadal jen host), a odřízne koncové „/". */
    private fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/').let {
        if (it.isEmpty() || it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }

    override suspend fun isAvailable(): Boolean = baseUrl().isNotBlank() && cookie().isNotBlank()

    override suspend fun login(password: String, baseUrlOverride: String?): Boolean {
        // URL: override (pokročilé) → uložená → zapečená default. Pak normalizace scheme.
        val resolved = baseUrlOverride?.trim().orEmpty()
            .ifBlank { baseUrl() }
            .ifBlank { ProfileConfigGateway.DEFAULT_BASE_URL }
        val url = normalizeUrl(resolved)
        return runCatching {
            val sessionCookie = remote.login(url, password)
            if (sessionCookie.isBlank()) return@runCatching false
            // Ulož URL + cookie + heslo (heslo → interceptor umí auto-relogin po 401), jako UploaderViewModel.
            prefs.edit()
                .putString("uploader_base_url", url)
                .putString("uploader_session_cookie", sessionCookie)
                .putString("uploader_password", password)
                .apply()
            true
        }.onFailure { Timber.w(it, "[GATEKEY] hlavní login selhal") }.getOrDefault(false)
    }

    override suspend fun fetchConfig(key: String): String? {
        if (!isAvailable() || key.isBlank()) return null
        return runCatching { remote.getProfileConfig(baseUrl(), cookie(), key) }
            .onFailure { Timber.w(it, "[Profiles] fetchConfig($key) selhal") }
            .getOrNull()
    }

    override suspend fun fetchAllProfiles(): List<ProfileMeta>? {
        if (!isAvailable()) return null
        return runCatching {
            val raw = remote.getProfilesMeta(baseUrl(), cookie()) ?: return@runCatching emptyList()
            JsonParser.parseString(raw).asJsonArray.mapNotNull { el ->
                val o = el.asJsonObject
                fun str(field: String): String? = o.get(field)?.takeIf { !it.isJsonNull }?.asString
                val jfUserId = str("jellyfinUserId").orEmpty()
                val key = str("key") ?: jfUserId.ifBlank { return@mapNotNull null }
                ProfileMeta(
                    key = key,
                    name = str("name").orEmpty(),
                    isAdmin = o.get("isAdmin")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    jellyfinUserId = jfUserId,
                    avatarTag = str("avatarTag"),
                    templateUuid = str("templateUuid"),
                    loginPinHash = str("loginPinHash"),
                    hasConfig = o.get("hasConfig")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                )
            }
        }.onFailure { Timber.w(it, "[GATEKEY] fetchAllProfiles selhal") }.getOrNull()
    }

    override suspend fun pushConfig(key: String, json: String, name: String, isAdmin: Boolean, jellyfinUserId: String) {
        if (!isAvailable() || key.isBlank()) return
        runCatching {
            remote.putProfile(baseUrl(), cookie(), key, name, isAdmin, jellyfinUserId)
            remote.putProfileConfig(baseUrl(), cookie(), key, json)
        }.onFailure { Timber.w(it, "[Profiles] pushConfig($key) selhal") }
    }

    override suspend fun fetchTemplates(): List<TemplatePayload>? {
        if (!isAvailable()) return null
        return runCatching {
            val raw = remote.getTemplates(baseUrl(), cookie()) ?: return@runCatching emptyList()
            JsonParser.parseString(raw).asJsonArray.mapNotNull { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                TemplatePayload(
                    uuid = uuid,
                    name = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    ageRating = o.get("ageRating")?.takeIf { !it.isJsonNull }?.asString,
                    configJson = o.get("config")?.takeIf { !it.isJsonNull }?.toString() ?: "{}",
                )
            }
        }.onFailure { Timber.w(it, "[Profiles] fetchTemplates selhal") }.getOrNull()
    }

    override suspend fun fetchAssignedTemplateUuid(key: String): String? {
        if (!isAvailable() || key.isBlank()) return null
        return runCatching {
            val raw = remote.getProfilesMeta(baseUrl(), cookie()) ?: return@runCatching null
            for (el in JsonParser.parseString(raw).asJsonArray) {
                val o = el.asJsonObject
                if (o.get("key")?.takeIf { !it.isJsonNull }?.asString == key) {
                    // Profil nalezen: vrať uuid, nebo "" = bez šablony (kontrakt v rozhraní).
                    return@runCatching o.get("templateUuid")?.takeIf { !it.isJsonNull }?.asString ?: ""
                }
            }
            null // profil na backendu není → neměnit lokální stav
        }.onFailure { Timber.w(it, "[Profiles] fetchAssignedTemplateUuid($key) selhal") }.getOrNull()
    }

    override suspend fun pushTemplate(uuid: String, name: String, ageRating: String?, configJson: String) {
        if (!isAvailable() || uuid.isBlank()) return
        runCatching { remote.putTemplate(baseUrl(), cookie(), uuid, name, ageRating, configJson) }
            .onFailure { Timber.w(it, "[Profiles] pushTemplate($uuid) selhal") }
    }

    override suspend fun deleteTemplate(uuid: String) {
        if (!isAvailable() || uuid.isBlank()) return
        runCatching { remote.deleteTemplate(baseUrl(), cookie(), uuid) }
            .onFailure { Timber.w(it, "[Profiles] deleteTemplate($uuid) selhal") }
    }

    override suspend fun pushAssignedTemplate(key: String, name: String, isAdmin: Boolean, jellyfinUserId: String, templateUuid: String) {
        if (!isAvailable() || key.isBlank()) return
        runCatching { remote.putProfile(baseUrl(), cookie(), key, name, isAdmin, jellyfinUserId, templateUuid) }
            .onFailure { Timber.w(it, "[Profiles] pushAssignedTemplate($key) selhal") }
    }

    // Plan HELM — admin parity (in-app administrace profilů)

    override suspend fun fetchJellyfinLibraries(userId: String?): List<JellyfinLibraryRef>? {
        if (!isAvailable()) return null
        return runCatching {
            val raw = remote.getJellyfinLibraries(baseUrl(), cookie(), userId.orEmpty()) ?: return@runCatching emptyList()
            JsonParser.parseString(raw).asJsonArray.mapNotNull { el ->
                val o = el.asJsonObject
                val id = o.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                JellyfinLibraryRef(
                    id = id,
                    name = o.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    collectionType = o.get("collectionType")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        }.onFailure { Timber.w(it, "[HELM] fetchJellyfinLibraries selhal") }.getOrNull()
    }

    override suspend fun fetchTmdbGenres(): List<String>? {
        if (!isAvailable()) return null
        return runCatching {
            // Backend vrací mapu {id: název}; pro editor stačí seřazené názvy.
            val raw = remote.getTmdbGenres(baseUrl(), cookie()) ?: return@runCatching emptyList()
            JsonParser.parseString(raw).asJsonObject.entrySet()
                .mapNotNull { it.value?.takeIf { v -> !v.isJsonNull }?.asString }
                .sorted()
        }.onFailure { Timber.w(it, "[HELM] fetchTmdbGenres selhal") }.getOrNull()
    }

    override suspend fun exportProfiles(): String? {
        if (!isAvailable()) return null
        return runCatching { remote.exportProfiles(baseUrl(), cookie()) }
            .onFailure { Timber.w(it, "[HELM] exportProfiles selhal") }
            .getOrNull()
    }

    override suspend fun importProfiles(json: String): Boolean {
        if (!isAvailable() || json.isBlank()) return false
        return runCatching { remote.importProfiles(baseUrl(), cookie(), json) }
            .onFailure { Timber.w(it, "[HELM] importProfiles selhal") }
            .getOrDefault(false)
    }
}
