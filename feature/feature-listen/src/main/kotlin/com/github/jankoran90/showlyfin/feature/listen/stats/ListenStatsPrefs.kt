package com.github.jankoran90.showlyfin.feature.listen.stats

import android.content.Context
import androidx.core.content.edit

/**
 * AMPHORA (SHW-124) F8 / CH2 — Nastavení statistik (Nastavení › Přehrávač › Statistiky poslechu).
 * Lehké SharedPreferences bez VM (čte se při každém tiku přehrávače, Android je drží v paměti).
 */
object ListenStatsPrefs {
    private const val PREFS = "slovo_stats_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_YEAR = "year_card"

    enum class YearCard(val label: String) { DECEMBER("V prosinci"), ALWAYS("Vždy"), NEVER("Nikdy") }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Počítat poslech (výchozí zapnuto). Vypnuto = nic se nezapisuje ani neodesílá. */
    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit { putBoolean(KEY_ENABLED, on) }

    /** Kdy ukázat kartu „Slovo za rok“. */
    fun yearCard(ctx: Context): YearCard =
        YearCard.entries.firstOrNull { it.name == prefs(ctx).getString(KEY_YEAR, null) } ?: YearCard.DECEMBER
    fun setYearCard(ctx: Context, value: YearCard) = prefs(ctx).edit { putString(KEY_YEAR, value.name) }
}
