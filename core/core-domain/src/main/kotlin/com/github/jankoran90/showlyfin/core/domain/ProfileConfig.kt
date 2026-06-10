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
         * Plan WARDEN W0 — efektivní config = **šablona ⊕ uživatelský override**.
         *
         * Model granularity (rozhodnutí W0): override drží *kompletní* uživatelskou konfiguraci
         * (při přiřazení šablony se inicializuje jejím snapshotem). Merge proto vychází z [override]
         * a **přepíše jen zamčená pole** hodnotou ze [template] ([ProfileConfig.lockedKeys] šablony).
         * Tím odpadá nejednoznačnost „prázdná množina = nenastaveno vs. záměrně prázdné": odemčená
         * pole vlastní uživatel, zamčená vždy živě diktuje šablona (admin změní zámek → propíše se
         * všem). [template] == null (legacy profil bez šablony) → override beze změny (plná volnost).
         */
        fun mergeEffective(template: ProfileConfig?, override: ProfileConfig): ProfileConfig {
            if (template == null) return override
            val locked = template.lockedKeys
            // Efektivní config nese zámky šablony → UI (Nastavení) ví, co smí uživatel editovat (W2).
            var r = override.copy(lockedKeys = locked)
            if (locked.isEmpty()) return r
            if (LockKeys.VISIBLE_SECTIONS in locked) r = r.copy(visibleSections = template.visibleSections)
            if (LockKeys.JELLYFIN_LIBRARIES in locked) r = r.copy(jellyfinLibraryWhitelist = template.jellyfinLibraryWhitelist)
            if (LockKeys.ABS_LIBRARIES in locked) r = r.copy(absLibraryWhitelist = template.absLibraryWhitelist)
            if (LockKeys.GENRES in locked) r = r.copy(allowedGenres = template.allowedGenres, blockedGenres = template.blockedGenres)
            if (LockKeys.AGE_RATING in locked) r = r.copy(preferredAgeRating = template.preferredAgeRating)
            if (LockKeys.DEFAULT_SECTION in locked) r = r.copy(defaultSection = template.defaultSection)
            if (LockKeys.APPEARANCE in locked) r = r.copy(appearance = template.appearance)
            if (LockKeys.CREDENTIALS in locked) r = r.copy(credentials = template.credentials)
            return r
        }
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
    /** Serializovaný Stremio/Comet StreamFilterPrefs (server-side), volitelný. */
    val streamFilterJson: String? = null,
)

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
