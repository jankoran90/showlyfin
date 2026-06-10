package com.github.jankoran90.showlyfin.core.domain

/**
 * Most mezi [ProfileConfig] a backend web adminem (Plan PROFILES Fáze 2). Implementaci drží
 * `data-uploader` (`UploaderProfileConfigGateway`) — `core-data` na ní nesmí přímo záviset, proto
 * je rozhraní zde v `core-domain` a injektuje se přes Hilt (dependency inversion).
 *
 * Přenáší se **raw JSON string** balíku ([ProfileConfig.toJson]/[ProfileConfig.fromJson]) — žádná
 * duplikace modelů. Klíč profilu = `jellyfinUserId` (stabilní napříč zařízeními).
 *
 * Veškeré operace jsou **best-effort**: offline / nepřihlášený uploader → fetch vrací null, push je
 * no-op. Lokální `configJson` v Room je cache backendu (offline funguje z posledního staženého).
 */
/**
 * Plochá data šablony z backendu (Plan WARDEN W3) pro lokální cache. [configJson] = serializovaný
 * [ProfileConfig] (baseline + `lockedKeys`); mapuje se na `TemplateEntity` v core-data.
 */
data class TemplatePayload(
    val uuid: String,
    val name: String,
    val ageRating: String?,
    val configJson: String,
)

interface ProfileConfigGateway {
    /** true = uploader je nakonfigurován (URL + session cookie) a lze volat backend. */
    suspend fun isAvailable(): Boolean

    /** Stáhne config balík profilu z backendu (raw JSON). null = nedostupné / profil neexistuje. */
    suspend fun fetchConfig(key: String): String?

    /** Uloží metadata + config balík profilu na backend. Selhání se tiše ignoruje. */
    suspend fun pushConfig(key: String, json: String, name: String, isAdmin: Boolean, jellyfinUserId: String)

    /**
     * Plan WARDEN W3c — stáhne všechny šablony autorované na backendu (web admin). null = nedostupné.
     * App si je cachuje lokálně (`TemplateDao`), aby `mergeEffective` fungoval i offline.
     */
    suspend fun fetchTemplates(): List<TemplatePayload>?

    /**
     * Plan WARDEN W3c — templateUuid přiřazený profilu na backendu (cross-device propsání přiřazení).
     * Kontrakt: **null** = nepodařilo se stáhnout / profil na backendu není (NEMĚNIT lokální stav);
     * **prázdný string** = profil existuje, ale bez šablony (lokálně zrušit přiřazení); jinak = uuid.
     */
    suspend fun fetchAssignedTemplateUuid(key: String): String?
}
