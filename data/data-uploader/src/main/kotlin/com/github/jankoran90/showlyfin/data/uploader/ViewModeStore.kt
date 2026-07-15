package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VANTAGE (SHW-48) — per-sekce volba zobrazení (mřížka / seznam). VLASTNÍ SharedPreferences
 * (`section_view_modes`), reaktivní [modes] (klíč sekce → [GRID]/[LIST]). Stejný princip jako
 * [FavoritesStore] — NE sdílené `trakt_prefs` (ty smaže odhlášení Traktu). Hodnota je prostý
 * řetězec, UI vrstva si ji mapuje na `core.ui.ViewMode` (data vrstva ho nevidí).
 */
@Singleton
class ViewModeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("section_view_modes", Context.MODE_PRIVATE)
    private val _modes = MutableStateFlow(loadAll())
    val modes: StateFlow<Map<String, String>> = _modes.asStateFlow()

    private fun loadAll(): Map<String, String> =
        prefs.all.entries.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun set(sectionKey: String, mode: String) {
        prefs.edit().putString(sectionKey, mode).apply()
        _modes.value = loadAll()
    }

    companion object {
        const val GRID = "grid"
        const val LIST = "list"
        // PANORAMA (SHW-78): široké „Netflix" režimy.
        const val LANDSCAPE = "landscape"
        const val LANDSCAPE_DETAIL = "landscape_detail"

        // Klíče sekcí (per sekce ukládáme zvlášť).
        const val SECTION_DISCOVER = "discover"
        // BESPOKE (SHW-95) F1 — sekce „Pro tebe" (mřížka ↔ immersive řada).
        const val SECTION_FOR_YOU = "for_you"
        const val SECTION_WATCHLIST = "watchlist"
        const val SECTION_HISTORY = "history"
        const val SECTION_RD = "rd"

        // PANORAMA: Knihovna — konfiguruje se z Nastavení (ne z lišty sekce).
        const val LIBRARY_LAYOUT = "library_layout"    // "rows" (default) | "grid"
        const val LIBRARY_CARD_STYLE = "library_style"  // grid|landscape|landscape_detail (globální styl karet)
        const val LIBRARY_LAYOUT_ROWS = "rows"
        const val LIBRARY_LAYOUT_GRID = "grid"

        /** Per-řada styl karet Knihovny (přebíjí globální). Hodnota = ViewMode.storeKey. */
        fun libraryRowStyleKey(libraryId: String) = "libstyle_$libraryId"
    }
}
