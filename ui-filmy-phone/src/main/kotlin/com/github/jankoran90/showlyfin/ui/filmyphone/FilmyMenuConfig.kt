package com.github.jankoran90.showlyfin.ui.filmyphone

import com.github.jankoran90.showlyfin.core.domain.home.PhoneMenuEntry

/**
 * PŮDORYS (SHW-112, user 2026-07-31 „v telefonu ani nemam moznost zobrazovat co ma byt v sidebaru") —
 * překlad mezi uloženým menu telefonu (`PhoneMenuEntry`, stringová jména sekcí v core-domain) a
 * [FilmySection]. Kanonické pořadí zná JEN telefonní shell; store ho jen ukládá.
 *
 * Nastavení a Profil se sem ZÁMĚRNĚ nepočítají — jsou připnuté dole v menu, aby si uživatel nemohl
 * schovat jedinou cestu zpátky do nastavení (a k přepnutí profilu).
 */
object FilmyMenuConfig {

    /** Sekce, které si uživatel může přeskládat a schovat (bez připnutých Nastavení/Profil). */
    fun canonical(filmotekaFirst: Boolean): List<FilmySection> =
        FilmyShellPrefs.discoverOrder(filmotekaFirst) + FilmySection.LIBRARY

    /**
     * Uložené menu → kompletní seznam k zobrazení/editaci: uživatelovo pořadí zůstává, sekce přidané
     * novou OTA se doplní na konec (zapnuté), neznámá jména se zahodí. Prázdné uložené = kanonické.
     * Idempotentní: merge(merge(x)) == merge(x).
     */
    fun merge(stored: List<PhoneMenuEntry>, filmotekaFirst: Boolean): List<PhoneMenuEntry> {
        val all = canonical(filmotekaFirst)
        val known = all.map { it.name }.toSet()
        val kept = stored.filter { it.item in known }.distinctBy { it.item }
        val keptItems = kept.map { it.item }.toSet()
        return kept + all.filter { it.name !in keptItems }.map { PhoneMenuEntry(it.name, enabled = true) }
    }

    /**
     * Sekce k vykreslení v menu (jen zapnuté, v uživatelově pořadí). Pojistka: kdyby v uloženém menu
     * nezbyla ANI JEDNA zapnutá (poškozený/cizí config), vrať kanonickou sadu — prázdné menu by
     * uživatele zavřelo v jedné obrazovce.
     */
    fun visibleSections(stored: List<PhoneMenuEntry>, filmotekaFirst: Boolean): List<FilmySection> {
        val visible = merge(stored, filmotekaFirst)
            .filter { it.enabled }
            .mapNotNull { entry -> sectionOf(entry.item) }
        return visible.ifEmpty { canonical(filmotekaFirst) }
    }

    /** [FilmySection] podle uloženého jména; null = sekce z novější/starší verze, kterou neznáme. */
    fun sectionOf(item: String): FilmySection? = FilmySection.entries.firstOrNull { it.name == item }
}
