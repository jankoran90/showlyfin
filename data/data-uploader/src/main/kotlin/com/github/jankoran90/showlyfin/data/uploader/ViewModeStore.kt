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

        // Klíče sekcí (per sekce ukládáme zvlášť).
        const val SECTION_DISCOVER = "discover"
        const val SECTION_WATCHLIST = "watchlist"
        const val SECTION_HISTORY = "history"
    }
}
