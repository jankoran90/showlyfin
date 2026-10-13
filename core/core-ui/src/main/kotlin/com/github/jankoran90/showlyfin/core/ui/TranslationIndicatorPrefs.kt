package com.github.jankoran90.showlyfin.core.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** AMFITEÁTR (FLM-05) F3 — vypínač stále viditelného ukazatele AI překladů (TV i telefon, `trakt_prefs`). */
object TranslationIndicatorPrefs {
    const val KEY = "translation_indicator_enabled"
    fun enabled(p: SharedPreferences): Boolean = p.getBoolean(KEY, true)
    fun set(p: SharedPreferences, on: Boolean) = p.edit().putBoolean(KEY, on).apply()

    /** Pozorovatelný stav vypínače — změna v Nastavení se projeví hned (Codex review: prefs Compose nesleduje). */
    @Composable
    fun observe(p: SharedPreferences): State<Boolean> {
        val state = remember(p) { mutableStateOf(enabled(p)) }
        DisposableEffect(p) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                if (key == KEY) state.value = enabled(prefs)
            }
            p.registerOnSharedPreferenceChangeListener(listener)
            onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}
