package com.github.jankoran90.showlyfin.core.domain

import kotlinx.serialization.Serializable

/**
 * AMPHORA F7 (DE1 + Z1) — zobrazení dětského profilu, část [ProfileConfig.kidsDisplay].
 * Pole čte Rodičovská appka (web) i telefon; neznámé hodnoty spadnou na výchozí (forward-compat).
 */
@Serializable
data class KidsDisplayPrefs(
    /** Sloupců v poličce Dětí: 2 = velké dlaždice (výchozí), 3 = menší. */
    val columns: Int = DEFAULT_COLUMNS,
    /** Názvy pod obaly (dítě, které nečte, vybírá podle obrázku). */
    val showTitles: Boolean = true,
    /** Kam se appka Dětí otevře: [START_DOMU] (výchozí, jako dosud) nebo [START_POLICKA] (Poslech). */
    val start: String = START_DOMU,
) {
    /** Bezpečný počet sloupců (web může poslat cokoli). */
    val safeColumns: Int get() = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    companion object {
        const val DEFAULT_COLUMNS = 2
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 3
        const val START_POLICKA = "policka"
        const val START_DOMU = "domu"
        val DEFAULT = KidsDisplayPrefs()
    }
}
