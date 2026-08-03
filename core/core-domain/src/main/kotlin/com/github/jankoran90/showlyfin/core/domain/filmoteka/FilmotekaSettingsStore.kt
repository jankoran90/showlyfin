package com.github.jankoran90.showlyfin.core.domain.filmoteka

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import com.github.jankoran90.showlyfin.core.domain.FilmotekaPrefs
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
    /** Je aktivní profil dětský? Rozhoduje jen o VÝCHOZÍCH hodnotách (viz [switchProfile]). */
    private var childProfile = false
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

    private val _hybridGenres = MutableStateFlow(loadHybridGenres())
    /** RUBRIC (SHW-104) — hybridní seskupení žánrů (Akční komedie, Sci-fi horor…) na ose Žánr; default ON. */
    val hybridGenres: StateFlow<Boolean> = _hybridGenres.asStateFlow()

    private val _showCollections = MutableStateFlow(loadShowCollections())
    /**
     * FOYER (SHW-107, user 2026-07-26) — karty KOLEKCÍ (Jellyfin BoxSet) ve Filmotéce. Default **VYPNUTO**:
     * Jellyfin vrací kolekce i při dotazu na Movie/Series a Filmotéka je brala jako film (TMDB id kolekce →
     * karta s cizím obsahem a bez čeho přehrát). S vypnutým přepínačem jsou vidět jen filmy zvlášť; se
     * zapnutým přibude řada „Kolekce" s kartami, které OTEVŘOU obsah kolekce (ne fiktivní film).
     */
    val showCollections: StateFlow<Boolean> = _showCollections.asStateFlow()

    private val _onlyWithSource = MutableStateFlow(loadOnlyWithSource())
    /**
     * FOYER (SHW-107, user 2026-07-27 „ve Filmotéce by měly být vidět až po uložení zdroje, tj. i na home"):
     * ukaž JEN tituly, které jdou reálně pustit — mají dohledaný zdroj (working source) NEBO jsou přímo
     * v Jellyfin knihovně (ta hraje ze serveru, žádné dohledávání nepotřebuje). Vypnuto (default) = Filmotéka
     * ukazuje i tituly z „Chci vidět"/Oblíbených, kterým se zdroj teprve shání. Platí i pro řadu na domově.
     */
    val onlyWithSource: StateFlow<Boolean> = _onlyWithSource.asStateFlow()

    /**
     * Přepni na nastavení daného profilu — přenačte všechny toky. Idempotentní (stejný profil = no-op).
     *
     * [childDefaults] = jde o DĚTSKÝ profil → jiné VÝCHOZÍ hodnoty (dnes „jen s dohledaným zdrojem"
     * zapnuto). User 2026-08-02 13:20: *„chci, ať mají ve Filmotéce film až po přehratelném zdroji"* —
     * dítě nemá jak poznat, že se zdroj teprve shání, a karta, co nejde pustit, je pro něj rozbitá věc.
     * 🔴 Mění to jen DEFAULT, ne uloženou volbu: kdo si přepínač už nastavil, o svoje nastavení nepřijde.
     * Příznak dodává feature vrstva (core-domain nesmí vidět ParentalControls).
     * 🔴🔴 `null` = **NEMĚNIT** (ne „dospělý"). Tuhle metodu volají i místa, která o věku profilu nic
     * nevědí (nastavení, sync most) — kdyby jim `false` příznak přebilo, dětský profil by o svůj výchozí
     * stav přišel podle toho, KDO se ozval poslední. *Sdílený stav nesmí resetovat ten, kdo ho nezná.*
     */
    fun switchProfile(id: Long?, childDefaults: Boolean? = null) {
        val child = childDefaults ?: childProfile
        if (id == activeId && switched && child == childProfile) return
        activeId = id
        childProfile = child
        switched = true
        _sources.value = loadSources()
        _defaultAxis.value = loadAxis()
        _allSort.value = loadAllSort()
        _enabledRegions.value = loadRegions()
        _hybridGenres.value = loadHybridGenres()
        _showCollections.value = loadShowCollections()
        _onlyWithSource.value = loadOnlyWithSource()
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

    /** RUBRIC (SHW-104) — zapni/vypni hybridní seskupení žánrů na ose Žánr (per profil). */
    fun setHybridGenresEnabled(enabled: Boolean) {
        _hybridGenres.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_HYBRID), enabled).apply()
    }

    /** FOYER — zapni/vypni karty kolekcí ve Filmotéce (per profil). */
    fun setShowCollections(enabled: Boolean) {
        _showCollections.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_SHOW_COLLECTIONS), enabled).apply()
    }

    private fun loadShowCollections(): Boolean =
        prefs.getBoolean(keyFor(KEY_SHOW_COLLECTIONS), prefs.getBoolean(KEY_SHOW_COLLECTIONS, false))

    // ── SYNC most (FOYER SHW-107) ────────────────────────────────────────────────
    //
    // Běh čte pořád LOKÁLNÍ prefs (rychlé, offline), ale hodnoty se drží v souladu se synchronizovaným
    // profilem: [applySynced] nalije to, co přišlo ze serveru, [snapshot] vrátí aktuální stav k odeslání.
    // Most (feature vrstva) obojí propojí — core-domain nesmí vidět ProfileRepository (obrácená závislost,
    // stejně jako u HomeLayoutStore.switchProfile).

    /** Nalij hodnoty ze synchronizovaného profilu do lokálních prefs. Neznámé/prázdné položky ignoruje. */
    fun applySynced(prefs: FilmotekaPrefs?) {
        if (prefs == null) return
        prefs.sources.mapNotNull { runCatching { FilmotekaSource.valueOf(it) }.getOrNull() }
            .toSet()
            .takeIf { prefs.sources.isNotEmpty() }
            ?.let { set ->
                _sources.value = set
                this.prefs.edit().putString(keyFor(KEY_SOURCES), json.encodeToString(set.map { it.name })).apply()
            }
        prefs.defaultAxis.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { FilmotekaAxis.valueOf(raw) }.getOrNull() }
            ?.let { setDefaultAxis(it) }
        prefs.allSort.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { FilmotekaAllSort.valueOf(raw) }.getOrNull() }
            ?.let { setAllSort(it) }
        prefs.enabledRegions.mapNotNull { runCatching { CinematographyRegion.valueOf(it) }.getOrNull() }
            .toSet()
            .takeIf { prefs.enabledRegions.isNotEmpty() }
            ?.let { set ->
                _enabledRegions.value = set
                this.prefs.edit().putString(keyFor(KEY_REGIONS), json.encodeToString(set.map { it.name })).apply()
            }
        setHybridGenresEnabled(prefs.hybridGenres)
        setShowCollections(prefs.showCollections)
        setOnlyWithSource(prefs.onlyWithSource)
    }

    /** Aktuální stav k odeslání do synchronizovaného profilu. */
    fun snapshot(): FilmotekaPrefs = FilmotekaPrefs(
        sources = _sources.value.map { it.name }.sorted(),
        defaultAxis = _defaultAxis.value.name,
        allSort = _allSort.value.name,
        enabledRegions = _enabledRegions.value.map { it.name }.sorted(),
        hybridGenres = _hybridGenres.value,
        showCollections = _showCollections.value,
        onlyWithSource = _onlyWithSource.value,
    )

    /** FOYER — zapni/vypni „jen tituly s dohledaným zdrojem" (per profil). */
    fun setOnlyWithSource(enabled: Boolean) {
        _onlyWithSource.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_ONLY_WITH_SOURCE), enabled).apply()
    }

    private fun loadOnlyWithSource(): Boolean =
        prefs.getBoolean(keyFor(KEY_ONLY_WITH_SOURCE), prefs.getBoolean(KEY_ONLY_WITH_SOURCE, childProfile))

    private fun loadSources(): Set<FilmotekaSource> {
        val raw = prefs.getString(keyFor(KEY_SOURCES), null) ?: prefs.getString(KEY_SOURCES, null)
            ?: return DEFAULT_SOURCES
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            ?.mapNotNull { name -> runCatching { FilmotekaSource.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: DEFAULT_SOURCES
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

    /** RUBRIC — default ON. Per-profil klíč s fallbackem na globální (migrace) i na výchozí true. */
    private fun loadHybridGenres(): Boolean =
        prefs.getBoolean(keyFor(KEY_HYBRID), prefs.getBoolean(KEY_HYBRID, true))

    private companion object {
        const val KEY_SOURCES = "sources_json"
        const val KEY_AXIS = "default_axis"
        const val KEY_ALL_SORT = "all_sort"
        const val KEY_REGIONS = "regions_json"
        const val KEY_HYBRID = "hybrid_genres"
        /** FOYER (SHW-107) — karty kolekcí (BoxSet) ve Filmotéce; default false. */
        const val KEY_SHOW_COLLECTIONS = "show_collections"
        /** FOYER (SHW-107) — jen tituly s dohledaným zdrojem / z JF knihovny; default false. */
        const val KEY_ONLY_WITH_SOURCE = "only_with_source"
        val ALL_SOURCES: Set<FilmotekaSource> = FilmotekaSource.entries.toSet()

        // Výchozí zapnuté zdroje = vše KROMĚ Jellyfinu (user 07-19: JF do Filmotéky jen když ho vybere v Nastavení,
        // default vypnuto — jinak by JF knihovny „skákaly" do Filmotéky bez souhlasu). Kdo měl JF dřív zapnutý
        // (uložený sources_json), drží se jeho volby.
        val DEFAULT_SOURCES: Set<FilmotekaSource> = ALL_SOURCES - FilmotekaSource.JELLYFIN
        val ALL_REGIONS: Set<CinematographyRegion> = CinematographyRegion.entries.toSet()
    }
}
