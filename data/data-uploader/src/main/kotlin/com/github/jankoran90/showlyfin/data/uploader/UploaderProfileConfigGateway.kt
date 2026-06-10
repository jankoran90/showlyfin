package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
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
}
