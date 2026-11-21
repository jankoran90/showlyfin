package com.github.jankoran90.showlyfin.feature.listen.service

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AMPHORA (SHW-124) F5 / IN2 — karty Slova v Android Auto (Nastavení › Android Auto). Auto ukáže
 * nahoře nejvýš [MAX_TABS] karet; další se schovají, proto je pořadí důležité.
 */
@Singleton
class AutoTabsPrefs @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    enum class Tab(val label: String) {
        CONTINUE("Pokračovat"),
        NEW("Nové díly"),
        PODCASTS("Podcasty"),
        BOOKS("Knihy"),
        BOOKMARKS("Záložky"),
    }

    /** Zapnuté karty v pořadí. Výchozí: Pokračovat · Nové díly · Podcasty · Knihy (záložky uvnitř Pokračovat). */
    var tabs: List<Tab>
        get() {
            val saved = prefs.getString(KEY_TABS, null) ?: return DEFAULT
            return saved.split(",").mapNotNull { n -> Tab.entries.firstOrNull { it.name == n } }.distinct()
        }
        set(v) = prefs.edit { putString(KEY_TABS, v.distinct().joinToString(",") { it.name }) }

    /** Každá knihovna (Dětské, Dospělý) jako vlastní karta místo jedné karty Knihy. */
    var librariesAsTabs: Boolean
        get() = prefs.getBoolean(KEY_LIB_TABS, false)
        set(v) = prefs.edit { putBoolean(KEY_LIB_TABS, v) }

    companion object {
        const val MAX_TABS = 4
        private const val KEY_TABS = "slovo_auto_tabs"
        private const val KEY_LIB_TABS = "slovo_auto_libraries_as_tabs"
        val DEFAULT = listOf(Tab.CONTINUE, Tab.NEW, Tab.PODCASTS, Tab.BOOKS)
    }
}
