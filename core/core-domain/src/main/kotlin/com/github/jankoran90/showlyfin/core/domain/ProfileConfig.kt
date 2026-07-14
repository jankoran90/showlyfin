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
    /**
     * Plan STRATA (SHW-33) — **skryté sekce (telefon)**, blocklist. Prázdné = nic skryté = vše
     * viditelné (default). Ukládají se JEN výslovně skryté sekce → nová sekce (Ovladač i budoucí) je
     * vždy viditelná, dokud ji admin sám neskryje. Nahrazuje křehký allow-list [visibleSections].
     * [Sections.NASTAVENI] se nikdy neskrývá (jediná cesta k odhlášení).
     */
    val hiddenSections: Set<String> = emptySet(),
    /**
     * Skryté sekce na TV (per-form-factor, např. „Knihovnu skrýt na telefonu, nechat na TV").
     * **null = zrcadlí [hiddenSections]** (telefon); prázdné = nic skryté na TV (nezávisle na telefonu).
     */
    val hiddenSectionsTv: Set<String>? = null,
    /**
     * Pořadí top-level nav sekcí (Plan STRATA Fáze E) — klíče ze [Sections] (spodní lišta). Prázdné =
     * kanonické pořadí. Neznámé/chybějící klíče se doplní kanonicky na konec (robustní vůči novým sekcím).
     */
    val sectionOrder: List<String> = emptyList(),
    /** Pořadí podsekcí „Sleduj" (Knihovna/Chci vidět/Objevit/Na RD). Prázdné = kanonické. */
    val subsectionOrder: List<String> = emptyList(),
    /**
     * Pořadí Jellyfin knihovních řádků v podsekci „Knihovna" (Plan STRATA Fáze E) — list library ids.
     * Prázdné = pořadí ze serveru. Neznámé id zahodit, chybějící doplnit na konec.
     */
    val libraryOrder: List<String> = emptyList(),
    // LEGACY (Plan STRATA migrace): allow-list model před blocklistem. Deserializují se JEN kvůli
    // [migrateLegacySections]; nový kód je needituje ani neukládá (po migraci se nulují).
    val visibleSections: Set<String> = emptySet(),
    val visibleSectionsTv: Set<String>? = null,
    /** Povolené Jellyfin library ids. null = všechny. Prázdný seznam = žádná (záměrně). */
    val jellyfinLibraryWhitelist: List<String>? = null,
    /**
     * Povolené Audiobookshelf knihovny (police) v sekci Poslech (Plan PROFILES Fáze 4E). Ids platí pro
     * audioknihy i podcasty. null = všechny. Prázdný seznam = žádná (skryje vše v Poslechu).
     */
    val absLibraryWhitelist: List<String>? = null,
    /**
     * Skryté podcasty (ABS library-item ids) pro tento profil — autoruje admin ve Správě.
     * Jemnější než [absLibraryWhitelist] (skrývá jednotlivé pořady, ne celou polici).
     * Prázdné = nic skryté. Filtruje seznam podcastů v sekci Poslech.
     */
    val hiddenPodcastIds: Set<String> = emptySet(),
    /**
     * WEFT (SHW-75/W5): per-profil skrytí POŘADŮ na **časové ose** Poslechu (Timeline). Klíč zdroje =
     * `type:ref` (shoda s [com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore.key]),
     * pro ABS pořad `abs:<id>`. Každý profil si skrývá sám ve svém účtu. Prázdné = nic skryté.
     */
    val hiddenTimelineSourceKeys: Set<String> = emptySet(),
    /** WEFT (SHW-75/W5): per-profil skrytí POŘADŮ ve **Sledovaných** (knihovna). Klíč jako výše. */
    val hiddenFollowingSourceKeys: Set<String> = emptySet(),
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
     * SUBWEAVE (SHW-89) Fáze C — styl titulků (velikost/barva/pozice) per-profil, synchronizovaný
     * TV↔telefon. null = profil zatím nemá vlastní styl → čte se lokální fallback (migrace z prefs).
     */
    val subtitleStyle: SubtitleStylePrefs? = null,
    /**
     * Per-source výběr titulkové stopy: klíč = imdb(+s/e), hodnota = id stopy (`osf_`/`os_`/`ai_`/
     * titulky id) nebo `"OFF"`. Durabilita: id nese odkaz do server cache (stažené/AI titulky trvalé),
     * takže výběr přežije i přeházení výsledků hledání. **Cap ~300 (LRU)** — hlídá velikost synced JSON.
     */
    val subtitleSelections: Map<String, String> = emptyMap(),
    /** Per-source posun synchronizace titulků (ms). Klíč jako [subtitleSelections]. Cap ~300 (LRU). */
    val subtitleOffsets: Map<String, Long> = emptyMap(),
    /**
     * Lock-mapa (Plan WARDEN W0): logické klíče ([LockKeys]), které jsou **admin-zamčené** =
     * uživatel je nesmí editovat a do efektivního configu se vždy berou ze **šablony**, ne z
     * uživatelského override. Smysl má jen na **šabloně**; na uživatelském override se ignoruje.
     * Prázdné = nic zamčené (legacy/bez šablony = plná volnost).
     */
    val lockedKeys: Set<String> = emptySet(),
) {
    /** Plan STRATA — blocklist: sekce je viditelná, dokud není ve [hiddenSections]. */
    fun isSectionVisible(key: String): Boolean = key !in hiddenSections

    /** Skryté sekce pro daný form factor: TV bere [hiddenSectionsTv], null = zrcadlí telefon. */
    fun hiddenSectionsFor(tv: Boolean): Set<String> =
        if (tv) hiddenSectionsTv ?: hiddenSections else hiddenSections

    /** Plan STRATA Fáze E — top-level nav sekce v efektivním pořadí (neznámé/chybějící kanonicky na konec). */
    fun orderedSections(): List<String> = applyOrder(sectionOrder, Sections.CANONICAL_NAV)

    /** Podsekce „Sleduj" v efektivním pořadí. */
    fun orderedSubsections(): List<String> = applyOrder(subsectionOrder, Sections.CANONICAL_SUBSECTIONS)

    /** Jellyfin knihovny v efektivním pořadí (dynamická sada [available] = co server vrátil). */
    fun orderedLibraryIds(available: List<String>): List<String> {
        if (libraryOrder.isEmpty()) return available
        val known = libraryOrder.filter { it in available }
        return known + available.filterNot { it in known }
    }

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

    /** true = podcast (ABS id) se má profilu zobrazit (není ve [hiddenPodcastIds]). */
    fun isPodcastVisible(podcastId: String): Boolean = podcastId !in hiddenPodcastIds

    /** WEFT (SHW-75/W5): true = pořad (klíč `type:ref` / `abs:id`) se má profilu ukázat na časové ose. */
    fun isTimelineSourceVisible(sourceKey: String): Boolean = sourceKey !in hiddenTimelineSourceKeys

    /** WEFT (SHW-75/W5): true = pořad se má profilu ukázat ve Sledovaných. */
    fun isFollowingSourceVisible(sourceKey: String): Boolean = sourceKey !in hiddenFollowingSourceKeys

    /** true = klíč je touto šablonou zamčený (uživatel needituje, bere se hodnota šablony). */
    fun isLocked(lockKey: String): Boolean = lockedKeys.contains(lockKey)

    /**
     * Plan STRATA migrace: pokud config nese legacy allow-list ([visibleSections]/[visibleSectionsTv]),
     * přepočítá ho na blocklist [hiddenSections]/[hiddenSectionsTv] a legacy pole vynuluje. Idempotentní
     * (po prvním uložení už legacy pole nejsou). Ovladač se po migraci VŽDY zviditelní (řešený root
     * cause — v starých allow-listech chyběl). Prázdný allow-list = vše viditelné = nic skryté.
     */
    fun migrateLegacySections(): ProfileConfig {
        if (visibleSections.isEmpty() && visibleSectionsTv == null) return this
        val toggleable = Sections.TOGGLEABLE
        fun hiddenFrom(allow: Set<String>): Set<String> =
            if (allow.isEmpty()) emptySet() else (toggleable - allow - Sections.OVLADAC)
        return copy(
            hiddenSections = hiddenFrom(visibleSections),
            hiddenSectionsTv = visibleSectionsTv?.let { hiddenFrom(it) },
            visibleSections = emptySet(),
            visibleSectionsTv = null,
        )
    }

    /** Seřadí [canonical] dle [order]; neznámé klíče z order zahodí, chybějící kanonicky doplní na konec. */
    private fun applyOrder(order: List<String>, canonical: List<String>): List<String> {
        if (order.isEmpty()) return canonical
        val known = order.filter { it in canonical }
        return known + canonical.filterNot { it in known }
    }

    companion object {
        /** Default config — bez jakýchkoli restrikcí. */
        val DEFAULT = ProfileConfig()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String?): ProfileConfig =
            if (raw.isNullOrBlank()) DEFAULT
            else runCatching { json.decodeFromString<ProfileConfig>(raw).migrateLegacySections() }
                .getOrDefault(DEFAULT)

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
                hiddenSections = if (template.hiddenSections.isNotEmpty()) template.hiddenSections else override.hiddenSections,
                hiddenSectionsTv = template.hiddenSectionsTv ?: override.hiddenSectionsTv,
                sectionOrder = template.sectionOrder.ifEmpty { override.sectionOrder },
                subsectionOrder = template.subsectionOrder.ifEmpty { override.subsectionOrder },
                libraryOrder = template.libraryOrder.ifEmpty { override.libraryOrder },
                jellyfinLibraryWhitelist = template.jellyfinLibraryWhitelist ?: override.jellyfinLibraryWhitelist,
                absLibraryWhitelist = template.absLibraryWhitelist ?: override.absLibraryWhitelist,
                hiddenPodcastIds = template.hiddenPodcastIds.ifEmpty { override.hiddenPodcastIds },
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
        const val ORDER = "order" // Plan STRATA Fáze E — pořadí sekcí/podsekcí (zamčeno = jen admin)

        /** Všechny zamykatelné klíče (pro authoring UI ve W3). */
        val ALL = setOf(
            VISIBLE_SECTIONS, JELLYFIN_LIBRARIES, ABS_LIBRARIES, GENRES,
            AGE_RATING, DEFAULT_SECTION, APPEARANCE, CREDENTIALS, ORDER,
        )
    }

    /** Klíče sekcí pro [hiddenSections] / pořadí. */
    object Sections {
        // Spodní lišta
        const val SLEDUJ = "sleduj"
        const val OVLADAC = "ovladac" // RELAY/Ovladač — dálkové ovládání běžící TV session
        const val POSLECH = "poslech"
        const val NASTAVENI = "nastaveni" // vždy viditelné (jediná cesta k odhlášení)
        // Podsekce „Sleduj" pageru
        const val KNIHOVNA = "knihovna"
        const val CHCI_VIDET = "chciVidet"
        const val OBJEVIT = "objevit"
        const val HISTORIE = "historie" // Plan STRATA B5 — Trakt watched (vzor yeshowly)
        const val NA_RD = "naRd"

        /** Plan STRATA Fáze E — kanonické pořadí top-level nav (bez Nastavení; to je vždy poslední/fixní). */
        val CANONICAL_NAV = listOf(SLEDUJ, OVLADAC, POSLECH)
        /** Kanonické pořadí podsekcí „Sleduj". */
        val CANONICAL_SUBSECTIONS = listOf(KNIHOVNA, CHCI_VIDET, OBJEVIT, HISTORIE, NA_RD)
        /** Přepínatelné (skrývatelné) sekce — vše krom [NASTAVENI]. */
        val TOGGLEABLE = setOf(SLEDUJ, OVLADAC, POSLECH, KNIHOVNA, CHCI_VIDET, OBJEVIT, HISTORIE, NA_RD)
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

/**
 * SUBWEAVE Fáze C — persistovaný styl titulků (bez offsetu; ten je per-source v
 * [ProfileConfig.subtitleOffsets]). Zrcadlí runtime `SubtitleStyle` v feature-playback.
 */
@Serializable
data class SubtitleStylePrefs(
    val fontScale: Float = 1.0f,
    val colorArgb: Int = 0xFFFFBF00.toInt(),
    val bottomPaddingFraction: Float = 0.08f,
    /** Vzhled pozadí/okraje titulku (obrys/stín/podklad/bez). Default = lehký obrys. */
    val edge: SubtitleEdgePref = SubtitleEdgePref.OUTLINE,
    /** Síla/intenzita okraje (obrys tloušťka / stín rozostření / podklad krytí). 1.0 = default. */
    val edgeStrength: Float = 1.0f,
)

/** Vzhled okraje titulku — jak se text odděluje od obrazu. Zrcadlí runtime `SubtitleEdge`. */
@Serializable
enum class SubtitleEdgePref { OUTLINE, SHADOW, BOX, NONE }

/**
 * Vloží [key]→[value] a udrží mapu v LRU pořadí s tvrdým stropem [max] (nejstarší klíč vypadne).
 * Slouží per-source titulkovým mapám v [ProfileConfig], aby synced JSON nerostl bez omezení.
 */
fun <V> Map<String, V>.putCappedLru(key: String, value: V, max: Int = 300): Map<String, V> {
    val m = LinkedHashMap<String, V>(this)
    m.remove(key) // re-insert → přesun na konec (nejnovější)
    m[key] = value
    while (m.size > max) m.remove(m.keys.first())
    return m
}
