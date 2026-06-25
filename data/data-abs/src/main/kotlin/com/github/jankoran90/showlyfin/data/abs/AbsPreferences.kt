package com.github.jankoran90.showlyfin.data.abs

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Uložené ABS přihlašovací údaje + token. Sdílí stejné SharedPreferences jako zbytek appky
 * (`@Named("traktPreferences")`), jen s vlastními klíči `abs_*`. Heslo se ukládá kvůli
 * auto-reloginu na 401 (token ABS expiruje), stejně jako u Uploaderu.
 */
@Singleton
class AbsPreferences @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    var baseUrl: String
        get() = prefs.getString(KEY_URL, "")?.trimEnd('/').orEmpty()
        set(value) = prefs.edit { putString(KEY_URL, value.trim().trimEnd('/')) }

    var username: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_USER, value) }

    var password: String
        get() = prefs.getString(KEY_PASS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PASS, value) }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    /** Skrývat již přehrané (dokončené) podcast epizody v detailu. */
    var hideFinishedEpisodes: Boolean
        get() = prefs.getBoolean(KEY_HIDE_FINISHED, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_FINISHED, value) }

    // ──────────────── Přehrávání ────────────────

    /** Velikost přeskoku ◀▶ v sekundách (in-app i Android Auto). */
    var skipSeconds: Int
        get() = prefs.getInt(KEY_SKIP_SECONDS, 30)
        set(value) = prefs.edit { putInt(KEY_SKIP_SECONDS, value.coerceIn(5, 120)) }

    /** Zapamatovat naposledy použitou rychlost (zvlášť audiokniha / podcast). */
    var rememberSpeed: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_SPEED, true)
        set(value) = prefs.edit { putBoolean(KEY_REMEMBER_SPEED, value) }

    /** Výchozí rychlost přehrávání (použije se, když [rememberSpeed] == false). */
    var defaultSpeed: Float
        get() = prefs.getFloat(KEY_DEFAULT_SPEED, 1f)
        set(value) = prefs.edit { putFloat(KEY_DEFAULT_SPEED, value.coerceIn(0.5f, 3.5f)) }

    /** Zapamatovaná rychlost audioknih (interní, nastaví se za běhu). */
    var lastBookSpeed: Float
        get() = prefs.getFloat(KEY_LAST_BOOK_SPEED, defaultSpeed)
        set(value) = prefs.edit { putFloat(KEY_LAST_BOOK_SPEED, value.coerceIn(0.5f, 3.5f)) }

    /** Zapamatovaná rychlost podcastů (interní). */
    var lastPodcastSpeed: Float
        get() = prefs.getFloat(KEY_LAST_PODCAST_SPEED, defaultSpeed)
        set(value) = prefs.edit { putFloat(KEY_LAST_PODCAST_SPEED, value.coerceIn(0.5f, 3.5f)) }

    /** Po dokončení epizody automaticky přehrát další z fronty. */
    var autoAdvanceQueue: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ADVANCE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_ADVANCE, value) }

    /**
     * Plan EVEN — hlasitostní vyrovnání / DRC normalizér poslechu.
     * 0 = Vyp, 1 = Mírná, 2 = Střední (default), 3 = Silná, 4 = Noční.
     * Vyrovná tiché vs. hlasité epizody/kapitoly (DynamicsProcessing kompresor + limiter + boost).
     * Default ON (úroveň Střední) — explicitní přání usera, tiché kapitoly v poslechu.
     */
    var listenDrcLevel: Int
        get() = prefs.getInt(KEY_DRC_LEVEL, 2)
        set(value) = prefs.edit { putInt(KEY_DRC_LEVEL, value.coerceIn(0, 4)) }

    /** Na konci epizody ji automaticky označit jako dokončenou na serveru. */
    var autoMarkFinished: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MARK_FINISHED, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_MARK_FINISHED, value) }

    // ──────────────── Fronta ────────────────

    /** Po vyprázdnění fronty pokračovat dalšími nepřehranými epizodami téhož podcastu. */
    var continuePodcastAfterQueue: Boolean
        get() = prefs.getBoolean(KEY_CONTINUE_PODCAST, false)
        set(value) = prefs.edit { putBoolean(KEY_CONTINUE_PODCAST, value) }

    /** Pamatovat frontu po restartu aplikace. */
    var persistQueue: Boolean
        get() = prefs.getBoolean(KEY_PERSIST_QUEUE, true)
        set(value) = prefs.edit { putBoolean(KEY_PERSIST_QUEUE, value) }

    /** Serializovaná fronta (JSON) — perzistence napříč restarty. */
    var queueJson: String
        get() = prefs.getString(KEY_QUEUE_JSON, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_QUEUE_JSON, value) }

    // ──────────────── Stahování ────────────────

    /** Stahovat epizody jen přes Wi-Fi. */
    var downloadWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit { putBoolean(KEY_WIFI_ONLY, value) }

    /** Smazat stažení po dokončení přehrání epizody. */
    var deleteDownloadAfterFinish: Boolean
        get() = prefs.getBoolean(KEY_DELETE_AFTER_FINISH, false)
        set(value) = prefs.edit { putBoolean(KEY_DELETE_AFTER_FINISH, value) }

    /** Maximální počet souběžných stahování. */
    var maxConcurrentDownloads: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENT, 2)
        set(value) = prefs.edit { putInt(KEY_MAX_CONCURRENT, value.coerceIn(1, 5)) }

    /** Automaticky stáhnout N nejnovějších nepřehraných epizod při otevření podcastu (0 = vyp). */
    var autoDownloadNewest: Int
        get() = prefs.getInt(KEY_AUTO_DOWNLOAD, 0)
        set(value) = prefs.edit { putInt(KEY_AUTO_DOWNLOAD, value.coerceIn(0, 10)) }

    /** Rozsah auto-stahování: 0 = všechny podcasty, 1 = jen vybrané (whitelist). */
    var autoDownloadScope: Int
        get() = prefs.getInt(KEY_AUTO_DOWNLOAD_SCOPE, 0)
        set(value) = prefs.edit { putInt(KEY_AUTO_DOWNLOAD_SCOPE, value.coerceIn(0, 1)) }

    /** Whitelist podcastů (itemId) pro auto-stahování, když [autoDownloadScope] == 1. */
    var autoDownloadPodcastIds: Set<String>
        get() = prefs.getStringSet(KEY_AUTO_DOWNLOAD_IDS, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_AUTO_DOWNLOAD_IDS, value) }

    fun isAutoDownloadPodcast(itemId: String): Boolean = itemId in autoDownloadPodcastIds

    fun setAutoDownloadPodcast(itemId: String, enabled: Boolean) {
        autoDownloadPodcastIds = autoDownloadPodcastIds.toMutableSet().apply {
            if (enabled) add(itemId) else remove(itemId)
        }
    }

    // ──────────────── Zobrazení ────────────────

    /** Řadit epizody od nejnovějších (true) nebo nejstarších (false). */
    var episodeSortNewestFirst: Boolean
        get() = prefs.getBoolean(KEY_EPISODE_SORT, true)
        set(value) = prefs.edit { putBoolean(KEY_EPISODE_SORT, value) }

    /** Počet epizod zobrazených v detailu podcastu (0 = všechny). */
    var episodeListLimit: Int
        get() = prefs.getInt(KEY_EPISODE_LIMIT, 0)
        set(value) = prefs.edit { putInt(KEY_EPISODE_LIMIT, value.coerceAtLeast(0)) }

    /** Počet řádků názvu epizody v seznamu (1–99; 99 = bez ořezu). Platí pro detail i RSS sheet. */
    var episodeTitleLines: Int
        get() = prefs.getInt(KEY_EPISODE_TITLE_LINES, 2)
        set(value) = prefs.edit { putInt(KEY_EPISODE_TITLE_LINES, value.coerceIn(1, 99)) }

    /** Zvýrazňovat vyparsovaného hosta jako poutač nad titulkem epizody. */
    var highlightGuest: Boolean
        get() = prefs.getBoolean(KEY_HIGHLIGHT_GUEST, true)
        set(value) = prefs.edit { putBoolean(KEY_HIGHLIGHT_GUEST, value) }

    /** Měřítko písma v seznamech epizod: 0.9 = kompakt, 1.0 = normál, 1.15 = velký. */
    var episodeFontScale: Float
        get() = prefs.getFloat(KEY_EPISODE_FONT_SCALE, 1f)
        set(value) = prefs.edit { putFloat(KEY_EPISODE_FONT_SCALE, value.coerceIn(0.8f, 1.4f)) }

    /** V „Prohledat epizody" skrývat epizody, které ABS server už má stažené. */
    var rssHideDownloaded: Boolean
        get() = prefs.getBoolean(KEY_RSS_HIDE_DOWNLOADED, false)
        set(value) = prefs.edit { putBoolean(KEY_RSS_HIDE_DOWNLOADED, value) }

    /**
     * Počet řádků popisku epizody v seznamu (detail i RSS sheet):
     * 0 = popis skrýt, N = N řádků, 99 = celý popis (bez ořezu).
     */
    var episodeDescriptionLines: Int
        get() = prefs.getInt(KEY_EPISODE_DESC_LINES, 3)
        set(value) = prefs.edit { putInt(KEY_EPISODE_DESC_LINES, value.coerceIn(0, 99)) }

    /**
     * Akce trailing tlačítka u epizody v detailu podcastu:
     * 0 = přidat do fronty (konec), 1 = přidat do fronty (další), 2 = stáhnout.
     */
    var episodeQuickAction: Int
        get() = prefs.getInt(KEY_EPISODE_ACTION, 0)
        set(value) = prefs.edit { putInt(KEY_EPISODE_ACTION, value.coerceIn(0, 2)) }

    /** V přehrávači zobrazovat zbývající čas místo celkové délky. */
    var showRemainingTime: Boolean
        get() = prefs.getBoolean(KEY_SHOW_REMAINING, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_REMAINING, value) }

    /**
     * Akce při swipu doprava na položce fronty v přehrávači:
     * 0 = stáhnout do telefonu, 1 = přehrát hned, 2 = na začátek fronty. (Doleva = odebrat.)
     */
    var queueSwipeAction: Int
        get() = prefs.getInt(KEY_QUEUE_SWIPE, 0)
        set(value) = prefs.edit { putInt(KEY_QUEUE_SWIPE, value.coerceIn(0, 2)) }

    /** Zobrazit tlačítko rychlosti v přehrávači. */
    var showSpeedButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SPEED_BTN, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_SPEED_BTN, value) }

    /** Zobrazit tlačítko časovače spánku v přehrávači. */
    var showSleepButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SLEEP_BTN, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_SLEEP_BTN, value) }

    // ──────────────── Pořadí v Poslechu (PRESET SHW-65, společné pro zařízení) ────────────────

    /** Zobrazit v Poslechu nejdřív Audioknihy (true) nebo Podcasty (false). */
    var listenBooksFirst: Boolean
        get() = prefs.getBoolean(KEY_BOOKS_FIRST, true)
        set(value) = prefs.edit { putBoolean(KEY_BOOKS_FIRST, value) }

    /** Ruční pořadí knihoven audioknih (ID knihoven). Prázdné = pořadí ze serveru. */
    var audiobookLibraryOrder: List<String>
        get() = prefs.getString(KEY_BOOK_LIB_ORDER, "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_BOOK_LIB_ORDER, value.joinToString("\n")) }

    /** Ruční pořadí knihoven podcastů (ID knihoven). Prázdné = pořadí ze serveru. */
    var podcastLibraryOrder: List<String>
        get() = prefs.getString(KEY_PODCAST_LIB_ORDER, "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_PODCAST_LIB_ORDER, value.joinToString("\n")) }

    // ──────────────── Objevování podcastů (AGORA) ────────────────

    /** Výchozí země objevovací obrazovky (kód: cz|us|gb|au). */
    var discoveryCountry: String
        get() = prefs.getString(KEY_DISC_COUNTRY, "cz").orEmpty().ifBlank { "cz" }
        set(value) = prefs.edit { putString(KEY_DISC_COUNTRY, value) }

    /** Výchozí režim objevovací obrazovky (apiValue: popular|active|new|az). */
    var discoveryMode: String
        get() = prefs.getString(KEY_DISC_MODE, "popular").orEmpty().ifBlank { "popular" }
        set(value) = prefs.edit { putString(KEY_DISC_MODE, value) }

    /** Trvale skryté CZ kategorie (ID) — předvyplní se do `excluded` při otevření Objevit. */
    var discoveryHiddenCategories: Set<String>
        get() = prefs.getStringSet(KEY_DISC_HIDDEN_CATS, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_DISC_HIDDEN_CATS, value) }

    /** Min. počet epizod — karty pod prahem se v objevování skryjí (0 = bez filtru). */
    var discoveryMinEpisodes: Int
        get() = prefs.getInt(KEY_DISC_MIN_EP, 0)
        set(value) = prefs.edit { putInt(KEY_DISC_MIN_EP, value.coerceIn(0, 500)) }

    /** Počet karet na stránku (donačítání). */
    var discoveryPageSize: Int
        get() = prefs.getInt(KEY_DISC_PAGE_SIZE, 30)
        set(value) = prefs.edit { putInt(KEY_DISC_PAGE_SIZE, value.coerceIn(10, 100)) }

    /** Zobrazovat popis v náhledech objevovacích karet. */
    var discoveryShowSummary: Boolean
        get() = prefs.getBoolean(KEY_DISC_SHOW_SUMMARY, true)
        set(value) = prefs.edit { putBoolean(KEY_DISC_SHOW_SUMMARY, value) }

    /** Zobrazovat počet epizod v náhledech objevovacích karet. */
    var discoveryShowEpisodeCount: Boolean
        get() = prefs.getBoolean(KEY_DISC_SHOW_EP_COUNT, true)
        set(value) = prefs.edit { putBoolean(KEY_DISC_SHOW_EP_COUNT, value) }

    // ──────────────── Sekce Podcasty — taby + Timeline + filtr (AGORA-TABS) ────────────────

    /**
     * Výchozí záložka po otevření sekce Podcasty: timeline|following|discover. Default = timeline
     * (chronologický feed nových epizod ze všech sledovaných zdrojů).
     */
    var podcastDefaultTab: String
        get() = prefs.getString(KEY_PODCAST_TAB, "timeline").orEmpty().ifBlank { "timeline" }
        set(value) = prefs.edit { putString(KEY_PODCAST_TAB, value) }

    /** Výchozí časový rozsah Timeline ve dnech (7|30|90). Epizody starší než rozsah se odfiltrují. */
    var podcastTimelineRangeDays: Int
        get() = prefs.getInt(KEY_PODCAST_TIMELINE_RANGE, 90)
        set(value) = prefs.edit { putInt(KEY_PODCAST_TIMELINE_RANGE, value.coerceIn(1, 365)) }

    /** Výchozí typ zdroje pro filtr sekce Podcasty: all|rss|youtube. */
    var podcastSourceTypeFilter: String
        get() = prefs.getString(KEY_PODCAST_SOURCE_TYPE, "all").orEmpty().ifBlank { "all" }
        set(value) = prefs.edit { putString(KEY_PODCAST_SOURCE_TYPE, value) }

    /** AGORA Timeline: zobrazit u epizody popis „o čem to je" (RSS/YouTube description). Default ON. */
    var podcastTimelineShowDescription: Boolean
        get() = prefs.getBoolean(KEY_TL_SHOW_DESC, true)
        set(value) = prefs.edit { putBoolean(KEY_TL_SHOW_DESC, value) }

    /** AGORA Timeline: počet řádků popisu v sbaleném stavu (3|4|5), klik na popis rozbalí celý. Default 3. */
    var podcastTimelineDescriptionLines: Int
        get() = prefs.getInt(KEY_TL_DESC_LINES, 3).coerceIn(3, 5)
        set(value) = prefs.edit { putInt(KEY_TL_DESC_LINES, value.coerceIn(3, 5)) }

    /** AGORA Timeline: zobrazit u epizody datum vydání. Default ON. */
    var podcastTimelineShowDate: Boolean
        get() = prefs.getBoolean(KEY_TL_SHOW_DATE, true)
        set(value) = prefs.edit { putBoolean(KEY_TL_SHOW_DATE, value) }

    // ──────────────── Synchronizace ────────────────

    /** Interval syncu pozice na ABS server v sekundách. */
    var syncIntervalSeconds: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL, 15)
        set(value) = prefs.edit { putInt(KEY_SYNC_INTERVAL, value.coerceIn(5, 120)) }

    /** Stabilní device id pro ABS play session. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE, it) }
        }

    // Plan VAULT — „configured" stačí URL + (token NEBO heslo). Když profil z admin Správy přinese
    // jen url/user/heslo (žádný token, protože admin token nemintuje), s původním `&& token` byl
    // `isConfigured=false` → app ABS request vůbec neposlala → AbsAuthInterceptor neměl 401 co
    // re-přihlásit = deadlock (Poslech „Nenastaveno"). Stejný vzor jako POLISH fix u Uploaderu.
    // S heslem request projde → 401 → interceptor relogin heslem → token se mintne a zahojí.
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && (token.isNotBlank() || (username.isNotBlank() && password.isNotBlank()))

    fun saveCredentials(url: String, user: String, pass: String, token: String) {
        prefs.edit {
            putString(KEY_URL, url.trim().trimEnd('/'))
            putString(KEY_USER, user)
            putString(KEY_PASS, pass)
            putString(KEY_TOKEN, token)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_URL); remove(KEY_USER); remove(KEY_PASS); remove(KEY_TOKEN)
        }
    }

    companion object {
        private const val KEY_URL = "abs_base_url"
        private const val KEY_USER = "abs_username"
        private const val KEY_PASS = "abs_password"
        private const val KEY_TOKEN = "abs_token"
        private const val KEY_DEVICE = "abs_device_id"
        private const val KEY_HIDE_FINISHED = "listen_hide_finished"
        private const val KEY_BOOKS_FIRST = "listen_books_first"
        private const val KEY_BOOK_LIB_ORDER = "listen_book_lib_order"
        private const val KEY_PODCAST_LIB_ORDER = "listen_podcast_lib_order"
        private const val KEY_SKIP_SECONDS = "listen_skip_seconds"
        private const val KEY_REMEMBER_SPEED = "listen_remember_speed"
        private const val KEY_DEFAULT_SPEED = "listen_default_speed"
        private const val KEY_LAST_BOOK_SPEED = "listen_last_book_speed"
        private const val KEY_LAST_PODCAST_SPEED = "listen_last_podcast_speed"
        private const val KEY_AUTO_ADVANCE = "listen_auto_advance"
        private const val KEY_DRC_LEVEL = "listen_drc_level"
        private const val KEY_AUTO_MARK_FINISHED = "listen_auto_mark_finished"
        private const val KEY_CONTINUE_PODCAST = "listen_continue_podcast"
        private const val KEY_PERSIST_QUEUE = "listen_persist_queue"
        private const val KEY_QUEUE_JSON = "listen_queue_json"
        private const val KEY_WIFI_ONLY = "listen_wifi_only"
        private const val KEY_DELETE_AFTER_FINISH = "listen_delete_after_finish"
        private const val KEY_MAX_CONCURRENT = "listen_max_concurrent"
        private const val KEY_AUTO_DOWNLOAD = "listen_auto_download_newest"
        private const val KEY_AUTO_DOWNLOAD_SCOPE = "listen_auto_download_scope"
        private const val KEY_AUTO_DOWNLOAD_IDS = "listen_auto_download_ids"
        private const val KEY_EPISODE_SORT = "listen_episode_sort_newest"
        private const val KEY_EPISODE_LIMIT = "listen_episode_limit"
        private const val KEY_EPISODE_TITLE_LINES = "listen_episode_title_lines"
        private const val KEY_EPISODE_DESC_LINES = "listen_episode_desc_lines"
        private const val KEY_HIGHLIGHT_GUEST = "listen_highlight_guest"
        private const val KEY_EPISODE_FONT_SCALE = "listen_episode_font_scale"
        private const val KEY_RSS_HIDE_DOWNLOADED = "listen_rss_hide_downloaded"
        private const val KEY_EPISODE_ACTION = "listen_episode_action"
        private const val KEY_SHOW_REMAINING = "listen_show_remaining"
        private const val KEY_QUEUE_SWIPE = "listen_queue_swipe_action"
        private const val KEY_SHOW_SPEED_BTN = "listen_show_speed_btn"
        private const val KEY_SHOW_SLEEP_BTN = "listen_show_sleep_btn"
        private const val KEY_SYNC_INTERVAL = "listen_sync_interval"
        private const val KEY_DISC_COUNTRY = "podcast_discovery_country"
        private const val KEY_DISC_MODE = "podcast_discovery_mode"
        private const val KEY_DISC_HIDDEN_CATS = "podcast_discovery_hidden_categories"
        private const val KEY_DISC_MIN_EP = "podcast_discovery_min_episodes"
        private const val KEY_DISC_PAGE_SIZE = "podcast_discovery_page_size"
        private const val KEY_DISC_SHOW_SUMMARY = "podcast_discovery_show_summary"
        private const val KEY_DISC_SHOW_EP_COUNT = "podcast_discovery_show_episode_count"
        private const val KEY_PODCAST_TAB = "podcast_default_tab"
        private const val KEY_PODCAST_TIMELINE_RANGE = "podcast_timeline_range_days"
        private const val KEY_PODCAST_SOURCE_TYPE = "podcast_source_type_filter"
        private const val KEY_TL_SHOW_DESC = "podcast_timeline_show_description"
        private const val KEY_TL_DESC_LINES = "podcast_timeline_description_lines"
        private const val KEY_TL_SHOW_DATE = "podcast_timeline_show_date"
    }
}
