package com.github.jankoran90.showlyfin.core.ui

/**
 * FOYER (SHW-107, user 2026-07-26) — řazení PLOCHÉ TV sekce (Filmotéka „Vše", Pro tebe, Chci vidět,
 * Oblíbené, Vzácné klenoty, mřížka řady z domova). Na TV je VÝCHOZÍ [ABECEDNE] — user chce vstup do
 * sekce vždycky jako abecední mřížku (telefon si drží svoje, tohle je jen TV větev).
 *
 * Volba se ukládá per sekce (`ViewModeStore.tvSortKey`) → jakmile ji uživatel jednou přepne, drží se
 * (volba „4b"). Zdroj bez daného pořadí (např. hodnocení u položky bez ratingu) řadí položku na konec.
 */
enum class TvSectionSort(val label: String, val storeKey: String) {
    ABECEDNE("Abecedně", "alpha"),
    NEDAVNO("Nedávno přidané", "recent"),
    ROK("Rok (nejnovější)", "year"),
    HODNOCENI("Hodnocení", "rating"),
    VYCHOZI("Výchozí (jak přišlo)", "default");

    companion object {
        /** Z uloženého klíče; neznámé/chybějící = TV default [ABECEDNE]. */
        fun fromKey(key: String?): TvSectionSort =
            entries.firstOrNull { it.storeKey == key } ?: ABECEDNE
    }
}
