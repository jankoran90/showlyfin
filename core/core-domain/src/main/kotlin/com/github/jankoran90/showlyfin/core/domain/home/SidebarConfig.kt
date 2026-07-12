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

/** Položky, které sidebar může zobrazit. `home` = skok fokusu zpět na obsah. */
enum class SidebarItem(val label: String) {
    DOMU("Domů"),
    HLEDAT("Hledat"),
    OBLIBENE("Oblíbené"),
    KNIHOVNA("Knihovna"),
    NASTAVENI("Nastavení"),
    ;

    companion object {
        fun fromName(name: String): SidebarItem? = entries.firstOrNull { it.name == name }

        /** Výchozí sidebar: minimalistický (Domů / Hledat / Nastavení zapnuté, zbytek vypnutý). */
        val DEFAULT: List<SidebarEntry> = listOf(
            SidebarEntry(DOMU.name, enabled = true),
            SidebarEntry(HLEDAT.name, enabled = true),
            SidebarEntry(OBLIBENE.name, enabled = false),
            SidebarEntry(KNIHOVNA.name, enabled = false),
            SidebarEntry(NASTAVENI.name, enabled = true),
        )
    }
}
