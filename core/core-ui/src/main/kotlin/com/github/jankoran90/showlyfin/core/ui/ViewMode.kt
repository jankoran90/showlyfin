package com.github.jankoran90.showlyfin.core.ui

/**
 * VANTAGE (SHW-48) — způsob zobrazení obsahu sekce: [GRID] (mřížka poster karet jako Objevit)
 * nebo [LIST] (řádky cover + titulek/rok/popis jako Chci vidět). Přepíná ikona v liště filtrů
 * ([SectionBar]); volba se drží per sekce (ViewModeStore). „Na RD" má jen grid (přepínač skrytý).
 */
enum class ViewMode { GRID, LIST }
