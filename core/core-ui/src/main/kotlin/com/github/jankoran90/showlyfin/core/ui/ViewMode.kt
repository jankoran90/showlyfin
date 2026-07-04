package com.github.jankoran90.showlyfin.core.ui

/**
 * VANTAGE (SHW-48) / PANORAMA (SHW-78) — způsob zobrazení obsahu sekce:
 * - [GRID] mřížka poster karet (2:3),
 * - [LIST] řádky cover + titulek/rok/popis,
 * - [LANDSCAPE] široké „Netflix" karty (16:9 backdrop),
 * - [LANDSCAPE_DETAIL] široké karty s vždy viditelným krátkým popisem.
 *
 * Přepíná rozbalovací menu v liště filtrů ([SectionBar]); volba se drží per sekce (ViewModeStore).
 */
enum class ViewMode {
    GRID, LIST, LANDSCAPE, LANDSCAPE_DETAIL;

    /** Řetězcová hodnota pro ViewModeStore (data vrstva nezná enum). */
    val storeKey: String
        get() = when (this) {
            GRID -> "grid"
            LIST -> "list"
            LANDSCAPE -> "landscape"
            LANDSCAPE_DETAIL -> "landscape_detail"
        }

    /** Label do rozbalovacího přepínače. */
    val label: String
        get() = when (this) {
            GRID -> "Mřížka"
            LIST -> "Seznam"
            LANDSCAPE -> "Na šířku"
            LANDSCAPE_DETAIL -> "Na šířku + popis"
        }

    companion object {
        fun fromKey(key: String?): ViewMode = when (key) {
            "list" -> LIST
            "landscape" -> LANDSCAPE
            "landscape_detail" -> LANDSCAPE_DETAIL
            else -> GRID
        }
    }
}
