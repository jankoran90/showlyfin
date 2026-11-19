package com.github.jankoran90.showlyfin.feature.listen.kids

import android.content.Context
import com.github.jankoran90.showlyfin.core.ui.KioskMode

/**
 * Rodičovská správa (TREEHOUSE F5 / R3, 2026-11-19) — nastavení samostahování obsahu Dětí přes Wi-Fi
 * (Nastavení › Dětský telefon). Výchozí „Poznat samo" = jen v dětském režimu telefonu ([KioskMode]),
 * aby se na telefonu dospělého po krátkém přepnutí na Děti nestáhly gigabajty a nic nemazalo.
 *
 * [adopted] = stažené díly, které patří Dětem (stáhlo je samostahování, nebo byly v době běhu schválené)
 * — mazat se smí jen ty, cizí stažené (dospělého) se nikdy nedotkne.
 */
object KidsAutoDownloadPrefs {
    enum class Mode(val key: String, val label: String) {
        AUTO("auto", "V dětském režimu"),
        ON("on", "Vždy"),
        OFF("off", "Vypnuto"),
    }

    /** Kolik nejnovějších dílů stáhnout u CELÉHO pořadu (série a jednotlivé díly vždy celé). */
    val SHOW_LIMITS = listOf(5, 10, 20, 50)
    const val DEFAULT_SHOW_LIMIT = 10

    private const val PREFS = "kids_autodownload"
    private const val KEY_MODE = "mode"
    private const val KEY_DELETE = "delete_removed"
    private const val KEY_LIMIT = "show_limit"
    private const val KEY_ADOPTED = "adopted"
    private const val KEY_LAST = "last_summary"
    private const val KEY_LIBS = "managed_libraries"
    private const val KEY_LAST_AT = "last_at"

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): Mode =
        sp(context).getString(KEY_MODE, null).let { raw -> Mode.entries.firstOrNull { it.key == raw } } ?: Mode.AUTO

    fun setMode(context: Context, mode: Mode) = sp(context).edit().putString(KEY_MODE, mode.key).apply()

    /** Má samostahování na tomhle telefonu běžet? (Dětský profil se kontroluje zvlášť.) */
    fun isEnabled(context: Context): Boolean = when (mode(context)) {
        Mode.ON -> true
        Mode.OFF -> false
        Mode.AUTO -> KioskMode.isActive(context)
    }

    fun deleteRemoved(context: Context): Boolean = sp(context).getBoolean(KEY_DELETE, true)
    fun setDeleteRemoved(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_DELETE, value).apply()

    fun showLimit(context: Context): Int = sp(context).getInt(KEY_LIMIT, DEFAULT_SHOW_LIMIT)
    fun setShowLimit(context: Context, value: Int) = sp(context).edit().putInt(KEY_LIMIT, value).apply()

    /** Klíče: `o:<klíč stažení>` (vlastní zdroje), `a:<id epizody>` (ABS podcast), `b:<id knihy>`. */
    fun adopted(context: Context): Set<String> = sp(context).getStringSet(KEY_ADOPTED, emptySet()).orEmpty().toSet()
    /** Změna evidence jako rozdíl pod zámkem (průchod samostahování a ruční stažení se nepřepíšou). */
    @Synchronized
    fun applyAdoptedDelta(context: Context, added: Set<String>, removed: Set<String>) {
        if (added.isEmpty() && removed.isEmpty()) return
        sp(context).edit().putStringSet(KEY_ADOPTED, (adopted(context) - removed) + added).commit()
    }

    /** Knihovny, které kdy byly dětské (odebere-li rodič celou knihovnu, její adoptované knihy se smažou). */
    fun managedLibraries(context: Context): Set<String> = sp(context).getStringSet(KEY_LIBS, emptySet()).orEmpty().toSet()
    fun setManagedLibraries(context: Context, ids: Set<String>) = sp(context).edit().putStringSet(KEY_LIBS, ids).apply()

    fun lastSummary(context: Context): String? = sp(context).getString(KEY_LAST, null)
    fun lastAt(context: Context): Long = sp(context).getLong(KEY_LAST_AT, 0L)
    fun setLast(context: Context, summary: String) =
        sp(context).edit().putString(KEY_LAST, summary).putLong(KEY_LAST_AT, System.currentTimeMillis()).apply()
}
