package com.github.jankoran90.showlyfin.core.domain.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TENFOOT — TV DOMOV REDESIGN. Uživatelské rozvržení domova: seznam [HomeRowConfig] řad + [SidebarEntry]
 * sidebar. Dedikovaný prefs soubor (`tv_home_layout`) mimo `traktPreferences` → izolace + snadný reset,
 * nepodléhá odhlášení Traktu. Vzor: [core.domain.resume.VideoResumeStore] (kotlinx JSON → String,
 * reaktivní [StateFlow], tolerantní `load`).
 *
 * **Forward-compat merge:** uložené (validní) řady zůstávají v uživatelově pořadí, nové default řady
 * z novější OTA se doplní na konec. „Skrýt" = `enabled=false` (řada zůstává, aby se dala vrátit);
 * hard [removeRow] jen pro vlastní (custom_*) řady. Neznámá enum hodnota → default (`coerceInputValues`);
 * poškozená jednotlivá řada se přeskočí (per-element decode), zbytek přežije.
 */
@Singleton
class HomeLayoutStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tv_home_layout", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * COUCH per-profil — domov (řady/sidebar/immersive/seen) je klíčovaný per AKTIVNÍ profil. Klíč nese
     * prefix `p<id>_`; při chybějícím per-profil klíči padá read na GLOBÁLNÍ klíč (bezešvá migrace — dospělý
     * zdědí stávající layout, deti začne od stejného defaultu). core-domain nesmí vidět ProfileRepository
     * (obrácená závislost), proto profil přepíná [switchProfile] volané z TvHomeViewModel (feature vrstva).
     */
    private var activeId: Long? = null

    private fun keyFor(base: String): String = activeId?.let { "p${it}_$base" } ?: base

    /** Přepni na layout daného profilu — přenačte všechny toky. Idempotentní (stejný profil = no-op). */
    fun switchProfile(id: Long?) {
        if (id == activeId && switched) return
        activeId = id
        switched = true
        _rows.value = loadRows()
        _sidebar.value = loadSidebar()
        _phoneMenu.value = loadPhoneMenu()
        _immersiveBackground.value = prefs.getBoolean(keyFor(KEY_IMMERSIVE), prefs.getBoolean(KEY_IMMERSIVE, true))
        _immersiveHeader.value = prefs.getBoolean(keyFor(KEY_IMMERSIVE_HEADER), prefs.getBoolean(KEY_IMMERSIVE_HEADER, true))
        _immersiveHeaderLines.value = prefs.getInt(keyFor(KEY_IMMERSIVE_HEADER_LINES), prefs.getInt(KEY_IMMERSIVE_HEADER_LINES, 0))
    }
    private var switched = false

    private val _rows = MutableStateFlow(loadRows())
    /** Všechny řady (i vypnuté) v pořadí; konzument si vyfiltruje `enabled`. */
    val rows: StateFlow<List<HomeRowConfig>> = _rows.asStateFlow()

    private val _sidebar = MutableStateFlow(loadSidebar())
    val sidebar: StateFlow<List<SidebarEntry>> = _sidebar.asStateFlow()

    private val _phoneMenu = MutableStateFlow(loadPhoneMenu())
    /**
     * PŮDORYS (SHW-112) — menu telefonu (pořadí + zapnutí sekcí). **Prázdné = uživatel si ho ještě
     * nenastavil** → telefonní shell použije svoje kanonické pořadí. Výchozí sadu tu ZÁMĚRNĚ nedržíme:
     * core-domain nezná `FilmySection` (to je `ui-filmy-phone`), merge s novými sekcemi dělá shell.
     */
    val phoneMenu: StateFlow<List<PhoneMenuEntry>> = _phoneMenu.asStateFlow()

    private val _immersiveBackground = MutableStateFlow(prefs.getBoolean(KEY_IMMERSIVE, true))
    /** Netflix-like immersive pozadí (fokusovaná karta řídí fanart) na Domů/Objevovat/Knihovna. */
    val immersiveBackground: StateFlow<Boolean> = _immersiveBackground.asStateFlow()

    fun setImmersiveBackground(enabled: Boolean) {
        _immersiveBackground.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_IMMERSIVE), enabled).apply()
    }

    private val _immersiveHeader = MutableStateFlow(prefs.getBoolean(KEY_IMMERSIVE_HEADER, true))
    /** OTA 299: immersive hlavička nahoře (název + rok + popis fokusované karty = „netflix styl") — oddělená
     * od pozadí, aby šla zapnout/vypnout zvlášť. */
    val immersiveHeader: StateFlow<Boolean> = _immersiveHeader.asStateFlow()

    fun setImmersiveHeader(enabled: Boolean) {
        _immersiveHeader.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_IMMERSIVE_HEADER), enabled).apply()
    }

    private val _immersiveHeaderLines = MutableStateFlow(prefs.getInt(KEY_IMMERSIVE_HEADER_LINES, 0))
    /** CONVERGE (SHW-97): počet řádků popisu v immersive hlavičce. 0 = AUTO (dopočítá se z dostupné výšky,
     * ať se nic neuřízne pod obsahem ani při jiné velikosti UI/písma); 1..N = pevný počet řádků. */
    val immersiveHeaderLines: StateFlow<Int> = _immersiveHeaderLines.asStateFlow()

    fun setImmersiveHeaderLines(lines: Int) {
        _immersiveHeaderLines.value = lines
        prefs.edit().putInt(keyFor(KEY_IMMERSIVE_HEADER_LINES), lines).apply()
    }

    // ── Řady ──────────────────────────────────────────────────────────────────

    /** Posun řady o jedno místo (nahoru = dřív). No-op na kraji. */
    fun move(id: String, up: Boolean) {
        _rows.update { list ->
            // Přesun mezi VIDITELNÝMI (zapnutými) řadami — cíl je sousední ZAPNUTÁ řada, ne libovolná
            // sousední v plném seznamu (jinak by se řada „prohodila" se skrytou = uživateli se nic nezmění).
            val enabled = list.filter { it.enabled }
            val ei = enabled.indexOfFirst { it.id == id }
            if (ei < 0) return@update list
            val ej = if (up) ei - 1 else ei + 1
            if (ej < 0 || ej >= enabled.size) return@update list
            val i = list.indexOfFirst { it.id == enabled[ei].id }
            val j = list.indexOfFirst { it.id == enabled[ej].id }
            if (i < 0 || j < 0) return@update list
            list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        }
        persistRows()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _rows.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        persistRows()
    }

    /** Přepiš konfiguraci řady (styl/řazení/filtr/titulek). */
    fun updateRow(config: HomeRowConfig) {
        _rows.update { list -> list.map { if (it.id == config.id) config else it } }
        persistRows()
    }

    /** Přidej vlastní řadu na konec (id musí být unikátní). */
    fun addRow(config: HomeRowConfig) {
        _rows.update { list -> if (list.any { it.id == config.id }) list else list + config }
        persistRows()
    }

    /**
     * Hromadně přidej řady (import z Jellyfin serveru). Řady s již existujícím id se PŘESKOČÍ (uživatelovo
     * nastavení má přednost), nové se doplní na konec v pořadí importu. Idempotentní — opakovaný import nepřidá duplikáty.
     */
    fun addRows(configs: List<HomeRowConfig>) {
        if (configs.isEmpty()) return
        _rows.update { list ->
            val existing = list.map { it.id }.toMutableSet()
            list + configs.filter { existing.add(it.id) }
        }
        persistRows()
    }

    /** Odeber řadu. Default řadu to jen skryje sémanticky ne — vrátí se při dalším merge; pro
     *  default používej [setEnabled]. Míněno pro vlastní `custom_*` řady. */
    fun removeRow(id: String) {
        _rows.update { list -> list.filterNot { it.id == id } }
        persistRows()
    }

    /** Obnovit výchozí sadu řad. Vyčistí i „seen" knihovny → při dalším [syncLibraries] se naseedují znovu. */
    fun resetRows() {
        _rows.value = DEFAULT_ROWS
        // DEFAULT_ROWS už je ve FOYER pořadí → označ verzi, ať migrace nešahá na čerstvý reset.
        prefs.edit().remove(keyFor(KEY_SEEN_LIBS)).putInt(keyFor(KEY_LAYOUT_VERSION), LAYOUT_VERSION_FOYER).apply()
        persistRows()
    }

    /**
     * Seed-once per Jellyfin knihovna. Pro každou knihovnu, kterou jsme ještě NEVIDĚLI, přidá první-třídní
     * [HomeRowSourceType.JELLYFIN_LIBRARY] řadu (enabled, default styl dle collectionType) a označí ji jako
     * viděnou. Existující řady zachovají uživatelovo nastavení (styl/pořadí/enabled); knihovny, které user
     * skryl nebo smazal, se NEVRACÍ (jsou v „seen"). Voláno z UI po načtení seznamu knihoven profilu.
     */
    fun syncLibraries(libraries: List<LibrarySummary>) {
        if (libraries.isEmpty()) return
        val seen = prefs.getStringSet(keyFor(KEY_SEEN_LIBS), emptySet()).orEmpty()
        val existingLibIds = _rows.value.mapNotNull { it.params[HomeRowParams.LIBRARY_ID] }.toSet()
        val toAdd = libraries
            .filter { it.id !in seen && it.id !in existingLibIds }
            .map { lib ->
                HomeRowConfig(
                    id = "lib_${lib.id}",
                    source = HomeRowSourceType.JELLYFIN_LIBRARY,
                    title = lib.name,
                    cardStyle = defaultLibraryStyle(lib.collectionType),
                    params = mapOf(
                        HomeRowParams.LIBRARY_ID to lib.id,
                        HomeRowParams.COLLECTION_TYPE to (lib.collectionType ?: ""),
                    ),
                )
            }
        // Vždy označ VŠECHNY aktuální knihovny jako viděné (i ty už přítomné) → idempotentní.
        prefs.edit().putStringSet(keyFor(KEY_SEEN_LIBS), seen + libraries.map { it.id }).apply()
        if (toAdd.isEmpty()) return
        _rows.update { list -> list + toAdd }
        // Seed knihoven je AUTOMATICKÝ (ne volba uživatele) → NEznačí řady jako „sáhl na ně",
        // jinak by čerstvě nainstalované zařízení vystrčilo své výchozí řady přes cizí přeskládané.
        persistRowsList(_rows.value)
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    fun moveSidebar(item: String, up: Boolean) {
        _sidebar.update { list ->
            val i = list.indexOfFirst { it.item == item }
            if (i < 0) return@update list
            val j = if (up) i - 1 else i + 1
            if (j < 0 || j >= list.size) return@update list
            list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        }
        persistSidebar()
    }

    fun setSidebarEnabled(item: String, enabled: Boolean) {
        _sidebar.update { list -> list.map { if (it.item == item) it.copy(enabled = enabled) else it } }
        persistSidebar()
    }

    // ── Menu telefonu (PŮDORYS SHW-112) ───────────────────────────────────────

    /**
     * Ulož CELÉ menu telefonu. Přesun i skrytí posílá shell vždycky jako kompletní seznam — zná svoje
     * sekce a umí do něj rovnou zamergovat ty, které přibyly novou OTA (core-domain `FilmySection` nevidí).
     * Prázdný seznam ignoruj — nemá čím přepsat existující volbu.
     */
    fun setPhoneMenu(entries: List<PhoneMenuEntry>) {
        if (entries.isEmpty()) return
        if (entries == _phoneMenu.value) return
        _phoneMenu.value = entries
        persistPhoneMenu()
    }

    /** Zpět na kanonické pořadí telefonu — smaž volbu, shell si příště naseeduje default. */
    fun resetPhoneMenu() {
        _phoneMenu.value = emptyList()
        prefs.edit().remove(keyFor(KEY_PHONE_MENU)).apply()
    }

    // ── SYNC most (PŮDORYS SHW-112) ───────────────────────────────────────────
    //
    // Běh čte pořád LOKÁLNÍ prefs (rychle, i offline), ale hodnoty se drží v souladu se synchronizovaným
    // profilem: [applySynced] nalije to, co přišlo ze serveru, [snapshot] vrátí stav k odeslání. Most
    // (feature vrstva, `HomeLayoutSync`) obojí propojí — core-domain nesmí vidět ProfileRepository.

    /**
     * Nalij rozvržení ze synchronizovaného profilu do lokálních prefs. **Prázdný seznam = „druhá strana
     * nic nemá"** → NEpřepisuj jím lokální stav (jinak by první zařízení bez layoutu vygumovalo domov
     * tomu druhému). Řady se berou tak, jak přišly (jsou už po merge z druhého zařízení); chybějící
     * default řady doplní [loadRows] merge při příštím startu.
     */
    fun applySynced(remote: com.github.jankoran90.showlyfin.core.domain.HomeLayoutPrefs?) {
        if (remote == null) return
        if (remote.rows.isNotEmpty()) {
            _rows.value = remote.rows
            persistRowsList(remote.rows)
            // Přišlé pořadí je uživatelovo (už po FOYER migraci na druhém zařízení) → nemigruj ho znovu.
            prefs.edit().putInt(keyFor(KEY_LAYOUT_VERSION), LAYOUT_VERSION_FOYER).apply()
        }
        if (remote.sidebar.isNotEmpty()) {
            _sidebar.value = remote.sidebar
            persistSidebar()
        }
        if (remote.phoneMenu.isNotEmpty()) {
            _phoneMenu.value = remote.phoneMenu
            persistPhoneMenu()
        }
        setImmersiveBackground(remote.immersiveBackground)
        setImmersiveHeader(remote.immersiveHeader)
        setImmersiveHeaderLines(remote.immersiveHeaderLines)
    }

    /** Aktuální rozvržení k odeslání do synchronizovaného profilu. */
    fun snapshot(): com.github.jankoran90.showlyfin.core.domain.HomeLayoutPrefs =
        com.github.jankoran90.showlyfin.core.domain.HomeLayoutPrefs(
            rows = _rows.value,
            sidebar = _sidebar.value,
            phoneMenu = _phoneMenu.value,
            immersiveBackground = _immersiveBackground.value,
            immersiveHeader = _immersiveHeader.value,
            immersiveHeaderLines = _immersiveHeaderLines.value,
        )

    // ── Perzistence ─────────────────────────────────────────────────────────────

    // ── „Sáhl na to uživatel?" (PŮDORYS fix, user 2026-08-01) ─────────────────
    //
    // 🔴 Bez tohoto sync PŘEPISOVAL cizí nastavení VÝCHOZÍMI hodnotami: telefon TV sidebar needituje,
    // takže má pořád výchozí (všechno zapnuté) — a při první synchronizaci ho vystrčil na server a
    // přebil tím sidebar, který si uživatel na TV skoro celý vypnul („zase je tam sidebar plný").
    // Pravidlo: zařízení vystrčí doménu, jen když na ni SAMO sáhlo (nebo když na serveru ještě žádná není).

    fun rowsTouched(): Boolean = touched(KEY_TOUCHED_ROWS)
    fun sidebarTouched(): Boolean = touched(KEY_TOUCHED_SIDEBAR)
    fun phoneMenuTouched(): Boolean = touched(KEY_TOUCHED_PHONE_MENU)

    private fun touched(key: String): Boolean = prefs.getBoolean(keyFor(key), false)
    private fun markTouched(key: String) = prefs.edit().putBoolean(keyFor(key), true).apply()

    private fun persistRows() = persistRowsList(_rows.value).also { markTouched(KEY_TOUCHED_ROWS) }

    /** Zápis konkrétního seznamu (migrace persistuje výsledek dřív, než ho dostane [_rows]). */
    private fun persistRowsList(rows: List<HomeRowConfig>) {
        prefs.edit().putString(keyFor(KEY_ROWS), json.encodeToString(rows)).apply()
    }

    private fun persistSidebar() {
        markTouched(KEY_TOUCHED_SIDEBAR)
        prefs.edit().putString(keyFor(KEY_SIDEBAR), json.encodeToString(_sidebar.value)).apply()
    }

    private fun persistPhoneMenu() {
        markTouched(KEY_TOUCHED_PHONE_MENU)
        prefs.edit().putString(keyFor(KEY_PHONE_MENU), json.encodeToString(_phoneMenu.value)).apply()
    }

    /** Menu telefonu — bez uložené volby vrací PRÁZDNO (default zná až shell, viz [phoneMenu]). */
    private fun loadPhoneMenu(): List<PhoneMenuEntry> {
        val raw = prefs.getString(keyFor(KEY_PHONE_MENU), null) ?: prefs.getString(KEY_PHONE_MENU, null)
        return decodeList(raw) { el -> json.decodeFromJsonElement<PhoneMenuEntry>(el) }
    }

    private fun loadRows(): List<HomeRowConfig> {
        // Per-profil klíč; fallback na GLOBÁLNÍ (bezešvá migrace stávajícího layoutu na první profil).
        val raw = prefs.getString(keyFor(KEY_ROWS), null) ?: prefs.getString(KEY_ROWS, null)
        val stored = decodeList(raw) { el ->
            json.decodeFromJsonElement<HomeRowConfig>(el)
        }
            // Migrace ≤293: starý meta zdroj zahoď — nahradí ho seed JELLYFIN_LIBRARY řad ([syncLibraries]).
            .filterNot { it.source == HomeRowSourceType.JELLYFIN_LIBRARIES }
            // WEATHER (user 2026-07-16): odstraň Trakt DOPORUČENÍ/OBJEVOVÁNÍ řady i ze STARÝCH uložených
            // layoutů (rozbité migrací Traktu na V3 + dětem nevhodné; user chce jen NAŠE + watchlist/historii).
            .filterNot { it.id in DEPRECATED_ROW_IDS }
        if (stored.isEmpty()) return DEFAULT_ROWS
        // Merge: uložené v pořadí + nové default řady (podle id) na konec.
        val storedIds = stored.map { it.id }.toSet()
        val merged = stored + DEFAULT_ROWS.filter { it.id !in storedIds }
        // FOYER (SHW-107) — jednorázové přeskládání na nový výchozí začátek domova (user 2026-07-26 volba „a").
        // Běží JEDNOU per profil (`layout_version`), pak si user pořadí přehazuje sám v editoru řady.
        val version = prefs.getInt(keyFor(KEY_LAYOUT_VERSION), 0)
        if (version < LAYOUT_VERSION_FOYER) {
            prefs.edit().putInt(keyFor(KEY_LAYOUT_VERSION), LAYOUT_VERSION_RAMPA).apply()
            return migrateQueueUnderNextUp(migrateToFoyerOrder(merged))
        }
        // RAMPA (SHW-121): kdo už FOYER migraci má, dostane jen zařazení fronty pod „Další díly" —
        // bez tohohle by nová řada spadla merge-em na KONEC domova, kam ji user nechtěl.
        if (version < LAYOUT_VERSION_RAMPA) {
            prefs.edit().putInt(keyFor(KEY_LAYOUT_VERSION), LAYOUT_VERSION_RAMPA).apply()
            return migrateQueueUnderNextUp(merged)
        }
        return merged
    }

    /**
     * RAMPA (SHW-121) — postav řadu „K přehrání" HNED ZA „Další díly" (user 2026-08-28: *„taky na web
     * nebo tv app pod dalsi dily jako pruh"*). Uživatelovo pořadí zbytku zůstává; skrytou řadu přesun
     * NEodkrývá. Idempotentní.
     */
    private fun migrateQueueUnderNextUp(rows: List<HomeRowConfig>): List<HomeRowConfig> {
        val queue = rows.firstOrNull { it.id == PLAY_QUEUE_ROW_ID }
            ?: DEFAULT_ROWS.firstOrNull { it.id == PLAY_QUEUE_ROW_ID }
            ?: return rows
        val rest = rows.filterNot { it.id == PLAY_QUEUE_ROW_ID }
        val at = rest.indexOfFirst { it.id == "next_up" }
        val result = if (at < 0) listOf(queue) + rest else rest.take(at + 1) + queue + rest.drop(at + 1)
        persistRowsList(result)
        return result
    }

    /**
     * FOYER (SHW-107) — vynes „Další díly" a „Filmotéka — nedávno přidané" na začátek domova; zbytek si
     * DRŽÍ uživatelovo pořadí. Chybějící Filmotéka řada se doplní z [DEFAULT_ROWS]; skryté („enabled=false")
     * řady se přesunem NEodkrývají — jen se posunou (uživatel je schoval schválně). Idempotentní.
     */
    private fun migrateToFoyerOrder(rows: List<HomeRowConfig>): List<HomeRowConfig> {
        val head = FOYER_HEAD_ROW_IDS.mapNotNull { id ->
            rows.firstOrNull { it.id == id } ?: DEFAULT_ROWS.firstOrNull { it.id == id }
        }
        val headIds = head.map { it.id }.toSet()
        val result = head + rows.filterNot { it.id in headIds }
        persistRowsList(result)
        return result
    }

    private fun loadSidebar(): List<SidebarEntry> {
        val raw = prefs.getString(keyFor(KEY_SIDEBAR), null) ?: prefs.getString(KEY_SIDEBAR, null)
        val stored = decodeList(raw) { el ->
            json.decodeFromJsonElement<SidebarEntry>(el)
        }.filter { SidebarItem.fromName(it.item) != null }
        if (stored.isEmpty()) return SidebarItem.DEFAULT
        val storedItems = stored.map { it.item }.toSet()
        return stored + SidebarItem.DEFAULT.filter { it.item !in storedItems }
    }

    /** Per-element tolerantní decode: poškozená položka se přeskočí, ne celý seznam. */
    private fun <T> decodeList(raw: String?, decode: (kotlinx.serialization.json.JsonElement) -> T): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { runCatching { decode(it) }.getOrNull() }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val KEY_ROWS = "rows_json"
        private const val KEY_SIDEBAR = "sidebar_json"
        /** PŮDORYS (SHW-112) — menu telefonu (pořadí + zapnutí sekcí drawer). */
        private const val KEY_PHONE_MENU = "phone_menu_json"
        // „Sáhl na tuhle doménu uživatel NA TOMTO zařízení?" — brání tomu, aby výchozí hodnoty přepsaly
        // cizí nastavení (user 2026-08-01: telefon vystrčil svůj výchozí sidebar a přebil vypnutý TV sidebar).
        private const val KEY_TOUCHED_ROWS = "touched_rows"
        private const val KEY_TOUCHED_SIDEBAR = "touched_sidebar"
        private const val KEY_TOUCHED_PHONE_MENU = "touched_phone_menu"
        private const val KEY_IMMERSIVE = "immersive_bg"
        private const val KEY_IMMERSIVE_HEADER = "immersive_header"
        private const val KEY_IMMERSIVE_HEADER_LINES = "immersive_header_lines"
        private const val KEY_SEEN_LIBS = "seen_library_ids"
        /** FOYER (SHW-107) — verze rozvržení; < [LAYOUT_VERSION_FOYER] = přeskládat začátek domova jednou. */
        private const val KEY_LAYOUT_VERSION = "layout_version"
        private const val LAYOUT_VERSION_FOYER = 2

        /** RAMPA (SHW-121) — verze rozvržení, která zná řadu „K přehrání" a její místo pod „Další díly". */
        private const val LAYOUT_VERSION_RAMPA = 3
        const val PLAY_QUEUE_ROW_ID = "play_queue"

        /** Id řady „Filmotéka — nedávno přidané" (drill „Celá filmotéka" ji pozná i z UI). */
        const val FILMOTEKA_RECENT_ROW_ID = "filmoteka_recent"

        /** FOYER — řady, které patří na začátek domova (v tomto pořadí). */
        private val FOYER_HEAD_ROW_IDS = listOf("next_up", FILMOTEKA_RECENT_ROW_ID)

        // WEATHER (user 2026-07-16): Trakt doporučovací/objevovací řady vyřazené z domova — strippnou se
        // i z uložených layoutů ([loadRows]). Trakt je migrací na V3 rozbil (401) + ukazovaly dětem
        // nevhodný obsah. Zůstává jen NÁŠ kurátor (brain_for_you) + Chci vidět + Historie.
        private val DEPRECATED_ROW_IDS = setOf(
            "couchmonkey_reco", "trakt_reco_movies", "trakt_reco_shows",
            "weighted_reco", "trending_movies", "popular_shows",
        )

        /** Default styl karty pro řadu knihovny dle collectionType. Konzistentní = plakát (žádné
         *  nahodilé landscape jako v ≤293); user přepíše v editoru. */
        fun defaultLibraryStyle(collectionType: String?): HomeCardStyle = HomeCardStyle.POSTER

        /** Výchozí domov: vzdušná Kodi-like sada. Obsah hned nahoře (Pokračovat), pak Trakt.
         *  JF knihovny se přidávají dynamicky per knihovna ([syncLibraries]) — ne natvrdo zde. */
        val DEFAULT_ROWS: List<HomeRowConfig> = listOf(
            // FOYER (SHW-107, user 2026-07-26): domov začíná „Další díly", hned pod ním „Filmotéka —
            // nedávno přidané". Teprve pak zbytek. Pořadí platí i pro STÁVAJÍCÍ uložené layouty — přeskládá
            // je jednorázová migrace ([migrateToFoyerOrder], `layout_version` → 2, user volba „a").
            HomeRowConfig(
                id = "next_up",
                source = HomeRowSourceType.NEXT_UP,
                title = "Další díly",
                cardStyle = HomeCardStyle.LANDSCAPE,
                // KOLO2 (M): z výroby jen první řada má immersive hlavičku zapnutou.
                immersiveHeader = true,
            ),
            // RAMPA (SHW-121, user 2026-08-28: „taky na web nebo tv app pod dalsi dily jako pruh") —
            // proto HNED pod „Další díly". Prázdná se řada nevykreslí (render bere jen neprázdné),
            // takže dokud si nic nepřidá, nic nepřekáží („pokud bude prázdný tak autonezobrazuj").
            HomeRowConfig(
                id = PLAY_QUEUE_ROW_ID,
                source = HomeRowSourceType.PLAY_QUEUE,
                title = "K přehrání",
                cardStyle = HomeCardStyle.POSTER,
            ),
            HomeRowConfig(
                id = FILMOTEKA_RECENT_ROW_ID,
                source = HomeRowSourceType.FILMOTEKA_RECENT,
                title = "Filmotéka — nedávno přidané",
                cardStyle = HomeCardStyle.POSTER,
            ),
            HomeRowConfig(
                id = "continue",
                source = HomeRowSourceType.CONTINUE_WATCHING,
                title = "Pokračovat ve sledování",
                cardStyle = HomeCardStyle.LANDSCAPE,
            ),
            // AUTEUR (SHW-91): kurátorský mozek „Pro tebe" (LLM z vkusu Trakt+Favorites → TMDB) = NAŠE
            // doporučení. Zapnuto z výroby; merge v loadRows doplní i stávajícím uživatelům (nový id → append).
            HomeRowConfig(
                id = "brain_for_you",
                source = HomeRowSourceType.BRAIN_FOR_YOU,
                title = "Pro tebe (kurátor)",
                cardStyle = HomeCardStyle.POSTER,
            ),
            // WEATHER (user 2026-07-16): z Traktu na Domů si necháváme JEN watchlist („Chci vidět") a historii.
            // VŠECHNA Trakt DOPORUČENÍ/OBJEVOVÁNÍ pryč — jednak je Trakt migrací na V3 rozbil (401), jednak
            // ukazovala dětem nevhodný obsah (Trendy: horory) a user chce jen NAŠE (kurátor). Odstraněné řady
            // (couchmonkey_reco/trakt_reco_movies/trakt_reco_shows/weighted_reco/trending_movies/popular_shows)
            // se navíc strippnou i ze STARÝCH uložených layoutů — viz DEPRECATED_ROW_IDS ve [loadRows].
            HomeRowConfig(
                id = "trakt_watchlist",
                source = HomeRowSourceType.TRAKT_WATCHLIST,
                title = "Chci vidět",
                cardStyle = HomeCardStyle.POSTER,
                params = mapOf(HomeRowParams.WATCHLIST_KIND to "all"),
            ),
            HomeRowConfig(
                id = "trakt_history",
                source = HomeRowSourceType.TRAKT_HISTORY,
                title = "Historie",
                cardStyle = HomeCardStyle.POSTER,
                params = mapOf(HomeRowParams.WATCHLIST_KIND to "all"),
            ),
            HomeRowConfig(
                id = "favorites",
                source = HomeRowSourceType.FAVORITES,
                title = "Oblíbené",
                cardStyle = HomeCardStyle.POSTER,
            ),
            // Zapamatované zdroje — když prázdné, render řadu vynechá (buildList jen neprázdné).
            HomeRowConfig(
                id = "saved_for_playback",
                source = HomeRowSourceType.SAVED_FOR_PLAYBACK,
                title = "Uloženo k přehrání",
                cardStyle = HomeCardStyle.POSTER,
            ),
        )
    }
}

/** Lehký souhrn Jellyfin knihovny pro [HomeLayoutStore.syncLibraries] (bez závislosti na feature vrstvě). */
data class LibrarySummary(
    val id: String,
    val name: String,
    val collectionType: String?,
)
