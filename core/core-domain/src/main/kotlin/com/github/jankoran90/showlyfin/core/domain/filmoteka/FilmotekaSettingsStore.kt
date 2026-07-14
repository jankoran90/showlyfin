package com.github.jankoran90.showlyfin.core.domain.filmoteka

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CINEMATHEQUE (SHW-90) — per-profil nastavení Filmotéky. Mirror [core.domain.home.HomeLayoutStore]:
 * dedikovaný prefs soubor (`tv_filmoteka`) mimo `traktPreferences` (izolace + reset, nepodléhá odhlášení
 * Traktu), kotlinx JSON, reaktivní [StateFlow], per-profil klíč (`p<id>_`) s fallbackem na globální klíč
 * (bezešvá migrace). Profil přepíná [switchProfile] volané z feature vrstvy (core-domain nesmí vidět
 * ProfileRepository).
 *
 * Pole:
 *  - [sources] — zapnuté zdroje (default všechny 4).
 *  - [defaultAxis] — výchozí osa (default GENRE).
 *  - [enabledRegions] — vybrané „kinematografie" pro osu Země (default prázdné = vše). **F2** — pole je
 *    připravené, ale ve F1 se nepoužívá (osa Země zatím prázdná).
 */
@Singleton
class FilmotekaSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tv_filmoteka", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    private var activeId: Long? = null
    private var switched = false
    private fun keyFor(base: String): String = activeId?.let { "p${it}_$base" } ?: base

    private val _sources = MutableStateFlow(loadSources())
    val sources: StateFlow<Set<FilmotekaSource>> = _sources.asStateFlow()

    private val _defaultAxis = MutableStateFlow(loadAxis())
    val defaultAxis: StateFlow<FilmotekaAxis> = _defaultAxis.asStateFlow()

    private val _enabledRegions = MutableStateFlow(loadRegions())
    /** F2 — vybrané regiony pro osu Země. Prázdné = vše. Ve F1 se nečte. */
    val enabledRegions: StateFlow<Set<String>> = _enabledRegions.asStateFlow()

    /** Přepni na nastavení daného profilu — přenačte všechny toky. Idempotentní (stejný profil = no-op). */
    fun switchProfile(id: Long?) {
        if (id == activeId && switched) return
        activeId = id
        switched = true
        _sources.value = loadSources()
        _defaultAxis.value = loadAxis()
        _enabledRegions.value = loadRegions()
    }

    fun setSourceEnabled(source: FilmotekaSource, enabled: Boolean) {
        val next = _sources.value.toMutableSet().apply { if (enabled) add(source) else remove(source) }
        _sources.value = next
        prefs.edit().putString(keyFor(KEY_SOURCES), json.encodeToString(next.map { it.name })).apply()
    }

    fun setDefaultAxis(axis: FilmotekaAxis) {
        _defaultAxis.value = axis
        prefs.edit().putString(keyFor(KEY_AXIS), axis.name).apply()
    }

    /** F2 — zapni/vypni region pro osu Země. Ve F1 nevoláno. */
    fun setRegionEnabled(region: String, enabled: Boolean) {
        val next = _enabledRegions.value.toMutableSet().apply { if (enabled) add(region) else remove(region) }
        _enabledRegions.value = next
        prefs.edit().putString(keyFor(KEY_REGIONS), json.encodeToString(next.toList())).apply()
    }

    private fun loadSources(): Set<FilmotekaSource> {
        val raw = prefs.getString(keyFor(KEY_SOURCES), null) ?: prefs.getString(KEY_SOURCES, null)
            ?: return ALL_SOURCES
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            ?.mapNotNull { name -> runCatching { FilmotekaSource.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: ALL_SOURCES
    }

    private fun loadAxis(): FilmotekaAxis {
        val raw = prefs.getString(keyFor(KEY_AXIS), null) ?: prefs.getString(KEY_AXIS, null)
        return raw?.let { runCatching { FilmotekaAxis.valueOf(it) }.getOrNull() } ?: FilmotekaAxis.GENRE
    }

    private fun loadRegions(): Set<String> {
        val raw = prefs.getString(keyFor(KEY_REGIONS), null) ?: prefs.getString(KEY_REGIONS, null)
            ?: return emptySet()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()?.toSet() ?: emptySet()
    }

    private companion object {
        const val KEY_SOURCES = "sources_json"
        const val KEY_AXIS = "default_axis"
        const val KEY_REGIONS = "regions_json"
        val ALL_SOURCES: Set<FilmotekaSource> = FilmotekaSource.entries.toSet()
    }
}
