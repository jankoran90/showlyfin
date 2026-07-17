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

/**
 * Plan GATEKEY G-A3 — meta jednoho profilu z backend rosteru (`GET /api/profiles`), **bez creds**.
 * Slouží k seedu lokálních `ProfileEntity` stubů po čisté instalaci → profil picker. Creds
 * (Jellyfin/ABS/Uploader) se dotáhnou až při tapu profilu přes [ProfileConfigGateway.fetchConfig].
 * [key] = backendový klíč (= `jellyfinUserId`); [hasConfig] = profil má na backendu uložený balík.
 */
data class ProfileMeta(
    val key: String,
    val name: String,
    val isAdmin: Boolean,
    val jellyfinUserId: String,
    val avatarTag: String?,
    val templateUuid: String?,
    val loginPinHash: String?,
    val hasConfig: Boolean,
)

/**
 * Plan HELM — odkaz na Jellyfin knihovnu (UserView) pro in-app admin editor whitelistu.
 * [collectionType] = movies/tvshows/… (nebo null). [id] se ukládá do
 * [ProfileConfig.jellyfinLibraryWhitelist].
 */
data class JellyfinLibraryRef(
    val id: String,
    val name: String,
    val collectionType: String?,
)

/**
 * SUBSTRATE (SHW-99) F2c KROK 2 — výsledek serverového Trakt mirror refreshe. [tokenStale] = uložený
 * access token na serveru je mrtvý (V3 zeď) → appka musí pushnout čerstvý po re-loginu.
 */
data class MirrorRefreshResult(
    val ok: Boolean,
    val tokenStale: Boolean,
    val watched: Int = 0,
    val watchlist: Int = 0,
)

interface ProfileConfigGateway {
    companion object {
        /**
         * Plan GATEKEY G-A1 — zapečená URL backendu (jellyfin-uploader). Hlavní login se na ni
         * přihlašuje bez nutnosti cokoli zadávat; „pokročilé" v login obrazovce ji umí přepsat.
         */
        const val DEFAULT_BASE_URL = "https://upload.jankoran.cz"

        /**
         * Heslo k backendu (UPLOAD_PASSWORD) napevno z build env — **auto-login po čisté instalaci**,
         * aby se při vývoji nemusely pořád znovu vyplňovat profily/přihlášení (rozhodnutí usera
         * 2026-06-11; release jde jen k němu). Nastavuje `ShowlyfinApp` z `BuildConfig.BACKEND_AUTOLOGIN_PASSWORD`
         * (zdroj = env `SHOWLYFIN_BACKEND_PASSWORD`, **mimo git**). Prázdné = feature vypnutá.
         */
        var autoLoginPassword: String = ""
    }

    /** true = uploader je nakonfigurován (URL + session cookie) a lze volat backend. */
    suspend fun isAvailable(): Boolean

    /**
     * Plan GATEKEY G-A1 — **hlavní login** do Showlyfinu = přihlášení k jellyfin-uploader backendu
     * (heslo = `UPLOAD_PASSWORD`). URL je zapečená ([DEFAULT_BASE_URL]); [baseUrlOverride] (neprázdné)
     * ji přepíše pro „pokročilé". Při úspěchu uloží URL + session cookie + heslo do kanonických prefs
     * (`uploader_base_url`/`uploader_session_cookie`/`uploader_password`) → backend je dál dostupný
     * pro roster i config. Vrací **true = úspěch** (cookie získána), false = selhání (špatné heslo/síť).
     */
    suspend fun login(password: String, baseUrlOverride: String? = null): Boolean

    /** Stáhne config balík profilu z backendu (raw JSON). null = nedostupné / profil neexistuje. */
    suspend fun fetchConfig(key: String): String?

    /**
     * Plan GATEKEY G-A3 — roster všech profilů z backendu (`GET /api/profiles`, jen meta — bez creds).
     * null = nedostupné / nepřihlášeno. Po čisté instalaci se z něj nasadí lokální `ProfileEntity` stuby
     * → profil picker.
     */
    suspend fun fetchAllProfiles(): List<ProfileMeta>?

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

    /**
     * Plan WARDEN W3c (část 2) — write-through in-app authoringu šablony na backend (zdroj pravdy,
     * jako [pushConfig]). [configJson] = serializovaný [ProfileConfig] (baseline + lockedKeys).
     * Best-effort; selhání se tiše ignoruje (lokální `TemplateDao` je už zapsaný).
     */
    suspend fun pushTemplate(uuid: String, name: String, ageRating: String?, configJson: String)

    /** Plan WARDEN W3c — smazání šablony na backendu (auto-odpojí profily server-side). Best-effort. */
    suspend fun deleteTemplate(uuid: String)

    /**
     * Plan WARDEN W3c — write-through přiřazení/zrušení šablony profilu na backend. [templateUuid]
     * prázdný = zrušit (backend uloží "", sync to přečte jako bez šablony). Best-effort.
     */
    suspend fun pushAssignedTemplate(key: String, name: String, isAdmin: Boolean, jellyfinUserId: String, templateUuid: String)

    /**
     * Plan HELM — seznam Jellyfin knihoven (UserView ids) pro in-app admin editor whitelistu.
     * [userId] = `jellyfinUserId` editovaného profilu (prázdné = všechny media folders). Jde přes
     * backend (`/api/jellyfin/libraries`, admin API key), aby admin viděl knihovny i cizího profilu
     * bez vlastního JF tokenu. null = nedostupné.
     */
    suspend fun fetchJellyfinLibraries(userId: String?): List<JellyfinLibraryRef>?

    /** Plan HELM — seznam TMDB žánrů (názvy, cs) pro in-app editor žánrů. null = nedostupné. */
    suspend fun fetchTmdbGenres(): List<String>?

    /** Plan HELM — záloha: export všech profilů + šablon z backendu (raw JSON balík). null = nedostupné. */
    suspend fun exportProfiles(): String?

    /** Plan HELM — obnova: import balíku profilů + šablon na backend. true = úspěch. */
    suspend fun importProfiles(json: String): Boolean

    /**
     * Plan HELM — write-through app-login PINu profilu na backend (cross-device). [pinHash] = SHA-256
     * hash, nebo `""` = zrušit PIN. Best-effort; selhání se tiše ignoruje (lokál je už zapsaný).
     */
    suspend fun pushLoginPin(key: String, name: String, isAdmin: Boolean, jellyfinUserId: String, pinHash: String)

    /**
     * SUBSTRATE F2c KROK 2 — kopne server mirror (`POST /api/profiles/{key}/mirror/refresh`), aby
     * server hned natáhl čerstvý Trakt vkus do serverového mirroru (volá se PO pushi čerstvého tokenu
     * po re-loginu). Best-effort; null = nedostupné / chyba. [MirrorRefreshResult.tokenStale] = token
     * na serveru je mrtvý → potřeba pushnout nový.
     */
    suspend fun refreshTraktMirror(key: String): MirrorRefreshResult?
}
