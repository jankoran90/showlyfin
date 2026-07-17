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
 *  - [enabledRegions] — zapnuté „kinematografie" pro osu Země (default všechny). Vypnutý region se v ose
 *    Země nezobrazí; [CinematographyRegion.OSTATNI] (fallback) je vždy viditelný, netogluje se.
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

    private val _allSort = MutableStateFlow(loadAllSort())
    /** CONVERGE V1 — řazení plochého výpisu osy „Vše" (default ABECEDNĚ od CELLULOID; přepínatelné na „nedávno přidané"). */
    val allSort: StateFlow<FilmotekaAllSort> = _allSort.asStateFlow()

    private val _enabledRegions = MutableStateFlow(loadRegions())
    /** F2 — zapnuté regiony pro osu Země (default všechny). [CinematographyRegion.OSTATNI] je vždy viditelný. */
    val enabledRegions: StateFlow<Set<CinematographyRegion>> = _enabledRegions.asStateFlow()

    /** Přepni na nastavení daného profilu — přenačte všechny toky. Idempotentní (stejný profil = no-op). */
    fun switchProfile(id: Long?) {
        if (id == activeId && switched) return
        activeId = id
        switched = true
        _sources.value = loadSources()
        _defaultAxis.value = loadAxis()
        _allSort.value = loadAllSort()
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

    fun setAllSort(sort: FilmotekaAllSort) {
        _allSort.value = sort
        prefs.edit().putString(keyFor(KEY_ALL_SORT), sort.name).apply()
    }

    /** F2 — zapni/vypni region pro osu Země. Ukládá se jako seznam jmen enumu (per profil). */
    fun setRegionEnabled(region: CinematographyRegion, enabled: Boolean) {
        val next = _enabledRegions.value.toMutableSet().apply { if (enabled) add(region) else remove(region) }
        _enabledRegions.value = next
        prefs.edit().putString(keyFor(KEY_REGIONS), json.encodeToString(next.map { it.name })).apply()
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
        // CONVERGE V1 — „Vše" je nově výchozí osa (přehledný plochý vstup); kdo si dřív zvolil Žánr/Země, drží.
        return raw?.let { runCatching { FilmotekaAxis.valueOf(it) }.getOrNull() } ?: FilmotekaAxis.ALL
    }

    private fun loadAllSort(): FilmotekaAllSort {
        val raw = prefs.getString(keyFor(KEY_ALL_SORT), null) ?: prefs.getString(KEY_ALL_SORT, null)
        // CELLULOID (user 2026-07-17) — Filmotéka výchozí ABECEDNĚ (katalog). Uložená volba má přednost;
        // „Nedávno přidané" je jen přepínač. Sdílené s TV Filmotékou (konzistentní, jen počáteční hodnota).
        return raw?.let { runCatching { FilmotekaAllSort.valueOf(it) }.getOrNull() } ?: FilmotekaAllSort.ALPHABETICAL
    }

    private fun loadRegions(): Set<CinematographyRegion> {
        val raw = prefs.getString(keyFor(KEY_REGIONS), null) ?: prefs.getString(KEY_REGIONS, null)
            ?: return ALL_REGIONS
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            ?.mapNotNull { name -> runCatching { CinematographyRegion.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: ALL_REGIONS
    }

    private companion object {
        const val KEY_SOURCES = "sources_json"
        const val KEY_AXIS = "default_axis"
        const val KEY_ALL_SORT = "all_sort"
        const val KEY_REGIONS = "regions_json"
        val ALL_SOURCES: Set<FilmotekaSource> = FilmotekaSource.entries.toSet()
        val ALL_REGIONS: Set<CinematographyRegion> = CinematographyRegion.entries.toSet()
    }
}
