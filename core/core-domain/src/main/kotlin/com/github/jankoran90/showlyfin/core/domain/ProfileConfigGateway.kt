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
interface ProfileConfigGateway {
    /** true = uploader je nakonfigurován (URL + session cookie) a lze volat backend. */
    suspend fun isAvailable(): Boolean

    /** Stáhne config balík profilu z backendu (raw JSON). null = nedostupné / profil neexistuje. */
    suspend fun fetchConfig(key: String): String?

    /** Uloží metadata + config balík profilu na backend. Selhání se tiše ignoruje. */
    suspend fun pushConfig(key: String, json: String, name: String, isAdmin: Boolean, jellyfinUserId: String)
}
