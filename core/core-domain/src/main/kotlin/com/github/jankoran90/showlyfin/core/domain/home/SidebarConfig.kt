package com.github.jankoran90.showlyfin.core.domain.home

import kotlinx.serialization.Serializable

/**
 * TENFOOT — TV DOMOV REDESIGN. Konfigurovatelný levý sidebar. Default = minimalistický
 * (Domů / Hledat / Nastavení); user si v Nastavení může zapnout bohatší menu (Knihovna, Oblíbené).
 * Ukládá [HomeLayoutStore] jako JSON seznam (pořadí + enabled), forward-compat merge s [DEFAULT].
 */
@Serializable
data class SidebarEntry(
    /** [SidebarItem] name (stringově kvůli forward-compat). */
    val item: String,
    val enabled: Boolean,
)

/**
 * PŮDORYS (SHW-112, user 2026-07-31) — jedna položka MENU TELEFONU (drawer). Obdoba [SidebarEntry] pro
 * telefonní shell: [item] = `FilmySection.name` (stringově kvůli forward-compat — core-domain nevidí do
 * `ui-filmy-phone`, a přejmenování/přidání sekce nesmí zahodit uložené pořadí).
 *
 * Výchozí sadu drží telefonní shell (zná svoje sekce); [HomeLayoutStore] ji jen ukládá + merguje.
 * Nastavení a Profil se sem ZÁMĚRNĚ neukládají — jsou připnuté dole, aby si uživatel nemohl schovat
 * jedinou cestu zpátky do nastavení.
 */
@Serializable
data class PhoneMenuEntry(
    /** `FilmySection` name (stringově kvůli forward-compat). */
    val item: String,
    val enabled: Boolean,
)

/** Položky, které sidebar může zobrazit. Každá = přepnutí sekce shellu (kromě Hledat = push destinace). */
enum class SidebarItem(val label: String) {
    DOMU("Domů"),
    // BESPOKE (SHW-95) F1 — sekce „Pro tebe" (kurátor) nahradila Objevovat. Interní název `OBJEVOVAT`
    // ZÁMĚRNĚ ponechán (uložené sidebar layouty profilů drží položky stringově dle name → přejmenování
    // by ztratilo pozici/enabled); mění se jen label a cíl (dispatch → TvSection.FOR_YOU).
    OBJEVOVAT("Pro tebe"),
    FILMOTEKA("Filmotéka"),
    KLENOTY("Vzácné klenoty"),
    TRAKT("Trakt"),
    CHCI_VIDET("Chci vidět"),
    KNIHOVNA("Knihovna"),
    OBLIBENE("Oblíbené"),
    HLEDAT("Hledat"),
    NASTAVENI("Nastavení"),
    ;

    companion object {
        fun fromName(name: String): SidebarItem? = entries.firstOrNull { it.name == name }

        /** Výchozí sidebar: navigace vrácena po redesignu 293 — Domů / Objevovat / Knihovna / Hledat /
         *  Nastavení zapnuté; Oblíbené volitelné (má i kartu srdce jinde). */
        val DEFAULT: List<SidebarEntry> = listOf(
            SidebarEntry(DOMU.name, enabled = true),
            SidebarEntry(OBJEVOVAT.name, enabled = true),
            SidebarEntry(FILMOTEKA.name, enabled = true),
            SidebarEntry(KLENOTY.name, enabled = true),
            SidebarEntry(TRAKT.name, enabled = true),
            SidebarEntry(CHCI_VIDET.name, enabled = true),
            SidebarEntry(KNIHOVNA.name, enabled = true),
            SidebarEntry(OBLIBENE.name, enabled = false),
            SidebarEntry(HLEDAT.name, enabled = true),
            SidebarEntry(NASTAVENI.name, enabled = true),
        )
    }
}
