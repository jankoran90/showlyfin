package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * User (2026-09-29 13:12/13:14, „volbu zobrazení ne ve dvou sloupcích ale v jednom, s popisem epizody
 * a časovým údajem do konce; cover samozřejmě velký, ať se vejde vždy název") — rozložení Domů.
 * Nastavení → „Zobrazení Domů" ([com.github.jankoran90.showlyfin.feature.listen.ui.HomeLayoutSettingsSection]).
 */
data class HomeLayout(
    val list: Boolean = false,
    val bigCover: Boolean = true,
    /** Kolik řádků popisu epizody v seznamu (0 = bez popisu). */
    val descLines: Int = 3,
    /** true = „zbývá 1:23:45", false = „0:12:00 / 1:35:45". */
    val remaining: Boolean = true,
)

object HomeLayoutPrefs {
    private const val KEY_LIST = "slovo_home_layout_list"
    private const val KEY_BIG_COVER = "slovo_home_layout_big_cover"
    private const val KEY_DESC_LINES = "slovo_home_layout_desc_lines"
    private const val KEY_REMAINING = "slovo_home_layout_remaining"
    private val KEYS = setOf(KEY_LIST, KEY_BIG_COVER, KEY_DESC_LINES, KEY_REMAINING)

    val DESC_LINE_OPTIONS = listOf(0, 2, 3, 5)

    fun read(prefs: SharedPreferences): HomeLayout = HomeLayout(
        list = prefs.getBoolean(KEY_LIST, false),
        bigCover = prefs.getBoolean(KEY_BIG_COVER, true),
        descLines = prefs.getInt(KEY_DESC_LINES, 3),
        remaining = prefs.getBoolean(KEY_REMAINING, true),
    )

    fun write(prefs: SharedPreferences, layout: HomeLayout) {
        prefs.edit()
            .putBoolean(KEY_LIST, layout.list)
            .putBoolean(KEY_BIG_COVER, layout.bigCover)
            .putInt(KEY_DESC_LINES, layout.descLines)
            .putBoolean(KEY_REMAINING, layout.remaining)
            .apply()
    }

    /** Živě — změna v Nastavení se na Domů projeví hned, bez restartu (jiný VM, stejné prefs). */
    fun observe(prefs: SharedPreferences): Flow<HomeLayout> = callbackFlow {
        trySend(read(prefs))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key in KEYS) trySend(read(p))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
