package com.github.jankoran90.showlyfin.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kompletní „config balík" navázaný na app-profil (Plan PROFILES). Je nadřazen chování celé
 * aplikace: které sekce jsou viditelné, které Jellyfin knihovny se zobrazí, žánrové allow/block
 * listy, preferovaný věkový rating pro Discover a předvyplněné přihlašovací údaje sub-appek.
 *
 * Ve Fázi 1 ho autoruje admin in-app; ve Fázi 2 se autorování přesune na backend web (stejný JSON).
 * Lokálně se serializuje do [com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity.configJson].
 *
 * Default = „vše viditelné, žádné restrikce" (zpětně kompatibilní s profily bez configu).
 */
@Serializable
data class ProfileConfig(
    /** Viditelné sekce. Prázdné = vše viditelné (admin/legacy). Viz [Sections]. */
    val visibleSections: Set<String> = emptySet(),
    /** Povolené Jellyfin library ids. null = všechny. Prázdný seznam = žádná (záměrně). */
    val jellyfinLibraryWhitelist: List<String>? = null,
    /**
     * Povolené Audiobookshelf knihovny (police) v sekci Poslech (Plan PROFILES Fáze 4E). Ids platí pro
     * audioknihy i podcasty. null = všechny. Prázdný seznam = žádná (skryje vše v Poslechu).
     */
    val absLibraryWhitelist: List<String>? = null,
    /** Povolené žánry (lowercase). Prázdné = bez allow-listu (vše kromě blacklistu). */
    val allowedGenres: Set<String> = emptySet(),
    /** Zakázané žánry (lowercase) — blacklist. */
    val blockedGenres: Set<String> = emptySet(),
    /** Preferovaný věkový rating pro Discover — název [AgeRating]. null = bez omezení z profilu. */
    val preferredAgeRating: String? = null,
    /**
     * „Hlavní" sekce — která sekce/podsekce se profilu otevře po vstupu (Plan PROFILES Fáze 4).
     * Hodnota = klíč ze [Sections] (spodní lišta: [Sections.SLEDUJ]/[Sections.POSLECH], nebo podsekce
     * Sleduj: [Sections.KNIHOVNA]/[Sections.CHCI_VIDET]/[Sections.OBJEVIT]/[Sections.NA_RD]).
     * null = výchozí (Sleduj / první podsekce).
     */
    val defaultSection: String? = null,
    /** Předvyplněné přihlašovací údaje sub-appek. */
    val credentials: CredentialBundle = CredentialBundle(),
    /** Ostatní vzhledové/chování toggly (volné klíče → string hodnoty). */
    val appearance: Map<String, String> = emptyMap(),
    /**
     * Lock-mapa (Plan WARDEN W0): logické klíče ([LockKeys]), které jsou **admin-zamčené** =
     * uživatel je nesmí editovat a do efektivního configu se vždy berou ze **šablony**, ne z
     * uživatelského override. Smysl má jen na **šabloně**; na uživatelském override se ignoruje.
     * Prázdné = nic zamčené (legacy/bez šablony = plná volnost).
     */
    val lockedKeys: Set<String> = emptySet(),
) {
    fun isSectionVisible(key: String): Boolean =
        visibleSections.isEmpty() || visibleSections.contains(key)

    /**
     * Žánrový filtr profilu (Plan PROFILES 1E). Vrací true = položku zobrazit.
     * Blacklist má přednost; je-li allow-list neprázdný, projdou jen položky s aspoň jedním povoleným
     * žánrem. [allowedGenres]/[blockedGenres] se očekávají lowercase.
     */
    fun isGenreAllowed(itemGenres: List<String>?): Boolean {
        if (allowedGenres.isEmpty() && blockedGenres.isEmpty()) return true
        val g = itemGenres.orEmpty().map { it.lowercase() }
        if (blockedGenres.isNotEmpty() && g.any { it in blockedGenres }) return false
        if (allowedGenres.isNotEmpty() && g.none { it in allowedGenres }) return false
        return true
    }

    /** true = klíč je touto šablonou zamčený (uživatel needituje, bere se hodnota šablony). */
    fun isLocked(lockKey: String): Boolean = lockedKeys.contains(lockKey)

    companion object {
        /** Default config — bez jakýchkoli restrikcí. */
        val DEFAULT = ProfileConfig()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String?): ProfileConfig =
            if (raw.isNullOrBlank()) DEFAULT
            else runCatching { json.decodeFromString<ProfileConfig>(raw) }.getOrDefault(DEFAULT)

        fun toJson(config: ProfileConfig): String = json.encodeToString(config)

        /**
         * Plan WARDEN W0 / finální sémantika Plan VAULT V9 (#42, 2026-06-11):
         *
         * **Per-profil override (autoruje VÝHRADNĚ admin ve Správě) vždy vyhrává.** Šablona dává:
         * 1. [lockedKeys] = lock-mapa pro **gating ne-admin Nastavení** (UI; hodnoty nevynucuje),
         * 2. **defaulty při PŘIŘAZENÍ** — snapshot hodnot šablony do override dělá
         *    `ProfileRepository.assignTemplate` (jednorázově), ne merge,
         * 3. chybějící creds domény (null = „chybí", u creds nikdy „všechny") se doplní živě.
         *
         * Proč ne vynucování hodnot šablonou: V8 ukázala, že zamčená šablona s prázdnými hodnotami
         * nuluje plný per-profil config („ABS se nechová jako maty", b130) a vynucování dělá adminův
         * per-profil editor mrtvým („skrývání sekcí nereaguje", b131). U sekcí/whitelistů má navíc
         * prázdno/null význam „vše" → nejde rozlišit od „nenastaveno" → živá dědičnost hodnot je
         * inherentně nejednoznačná. [template] == null (legacy bez šablony) → override beze změny.
         */
        fun mergeEffective(template: ProfileConfig?, override: ProfileConfig): ProfileConfig {
            if (template == null) return override
            return override.copy(
                // Efektivní config nese zámky šablony → UI (Nastavení) ví, co smí uživatel editovat (W2).
                lockedKeys = template.lockedKeys,
                credentials = override.credentials.mergeMissingFrom(template.credentials),
            )
        }

        /**
         * Plan VAULT V9 — jednorázový **snapshot hodnot šablony do override při přiřazení**
         * ([ProfileRepository.assignTemplate]). Přenese jen pole, která šablona reálně nese
         * (non-null / non-empty); zbytek override drží. Creds chybějící domény doplní.
         */
        fun snapshotFromTemplate(template: ProfileConfig, override: ProfileConfig): ProfileConfig =
            override.copy(
                visibleSections = if (template.visibleSections.isNotEmpty()) template.visibleSections else override.visibleSections,
                jellyfinLibraryWhitelist = template.jellyfinLibraryWhitelist ?: override.jellyfinLibraryWhitelist,
                absLibraryWhitelist = template.absLibraryWhitelist ?: override.absLibraryWhitelist,
                allowedGenres = template.allowedGenres.ifEmpty { override.allowedGenres },
                blockedGenres = template.blockedGenres.ifEmpty { override.blockedGenres },
                preferredAgeRating = template.preferredAgeRating ?: override.preferredAgeRating,
                defaultSection = template.defaultSection ?: override.defaultSection,
                appearance = template.appearance.ifEmpty { override.appearance },
                credentials = override.credentials.mergeMissingFrom(template.credentials),
            )
    }

    /**
     * Logické klíče pro [lockedKeys] (Plan WARDEN). Hrubá granularita (per-doména, ne per-field) —
     * dostatečná pro WARDEN a jednoduchá na UI. Případné zjemnění je otevřené rozhodnutí plánu.
     */
    object LockKeys {
        const val VISIBLE_SECTIONS = "visibleSections"
        const val JELLYFIN_LIBRARIES = "jellyfinLibraries"
        const val ABS_LIBRARIES = "absLibraries"
        const val GENRES = "genres"
        const val AGE_RATING = "ageRating"
        const val DEFAULT_SECTION = "defaultSection"
        const val APPEARANCE = "appearance"
        const val CREDENTIALS = "credentials"

        /** Všechny zamykatelné klíče (pro authoring UI ve W3). */
        val ALL = setOf(
            VISIBLE_SECTIONS, JELLYFIN_LIBRARIES, ABS_LIBRARIES, GENRES,
            AGE_RATING, DEFAULT_SECTION, APPEARANCE, CREDENTIALS,
        )
    }

    /** Klíče sekcí pro [visibleSections]. */
    object Sections {
        // Spodní lišta
        const val SLEDUJ = "sleduj"
        const val POSLECH = "poslech"
        const val NASTAVENI = "nastaveni" // vždy viditelné (jediná cesta k odhlášení)
        // Podsekce „Sleduj" pageru
        const val KNIHOVNA = "knihovna"
        const val CHCI_VIDET = "chciVidet"
        const val OBJEVIT = "objevit"
        const val NA_RD = "naRd"
    }
}

/**
 * Přihlašovací údaje per profil. Hesla se ukládají kvůli předvyplnění / auto-reloginu (stejně jako
 * dnes [com.github.jankoran90.showlyfin.data.abs.AbsPreferences] ukládá heslo). Ve Fázi 2 je drží
 * backend (šifrovaně) a app je dostává v balíku.
 */
@Serializable
data class CredentialBundle(
    val jellyfin: JellyfinCreds? = null,
    val abs: AbsCreds? = null,
    val uploader: UploaderCreds? = null,
    /** Trakt OAuth tokeny per profil (Plan VAULT) — Trakt se stal 4. creds doménou pod adminem. */
    val trakt: TraktCreds? = null,
    /** Serializovaný Stremio/Comet StreamFilterPrefs (server-side), volitelný. */
    val streamFilterJson: String? = null,
) {
    /** Chybějící (null) creds domény doplní z [other] — defaults šablony v [ProfileConfig.mergeEffective]. */
    fun mergeMissingFrom(other: CredentialBundle): CredentialBundle = CredentialBundle(
        jellyfin = jellyfin ?: other.jellyfin,
        abs = abs ?: other.abs,
        uploader = uploader ?: other.uploader,
        trakt = trakt ?: other.trakt,
        streamFilterJson = streamFilterJson ?: other.streamFilterJson,
    )
}

@Serializable
data class JellyfinCreds(
    val url: String = "",
    val userId: String = "",
    val token: String = "",
    val username: String = "",
    val password: String? = null,
)

@Serializable
data class AbsCreds(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val token: String? = null,
)

@Serializable
data class UploaderCreds(
    val url: String = "",
    val password: String = "",
)

/**
 * Trakt OAuth tokeny per profil (Plan VAULT). Klíče v prefs zrcadlí
 * [com.github.jankoran90.showlyfin.data.trakt.token.TraktTokenProvider] (`TRAKT_ACCESS_TOKEN`…),
 * aby applier zapsal token tam, odkud `TokenProvider`/`isLoggedIn` čte. Časy jsou **absolutní
 * epoch millis** (cross-device): `createdAtMillis` + `expiresAtMillis` řídí `shouldRefresh`.
 */
@Serializable
data class TraktCreds(
    val accessToken: String = "",
    val refreshToken: String = "",
    val createdAtMillis: Long = 0,
    val expiresAtMillis: Long = 0,
    val username: String? = null,
)
