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
    /** Povolené žánry (lowercase). Prázdné = bez allow-listu (vše kromě blacklistu). */
    val allowedGenres: Set<String> = emptySet(),
    /** Zakázané žánry (lowercase) — blacklist. */
    val blockedGenres: Set<String> = emptySet(),
    /** Preferovaný věkový rating pro Discover — název [AgeRating]. null = bez omezení z profilu. */
    val preferredAgeRating: String? = null,
    /** Předvyplněné přihlašovací údaje sub-appek. */
    val credentials: CredentialBundle = CredentialBundle(),
    /** Ostatní vzhledové/chování toggly (volné klíče → string hodnoty). */
    val appearance: Map<String, String> = emptyMap(),
) {
    fun isSectionVisible(key: String): Boolean =
        visibleSections.isEmpty() || visibleSections.contains(key)

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
