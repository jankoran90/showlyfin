package com.github.jankoran90.showlyfin.feature.listen.ui

import android.content.Context
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import androidx.compose.ui.unit.dp
import java.text.Collator
import java.util.Locale

/**
 * AMPHORA (SHW-124) F2 / KN1 — volby Knihovny: výchozí záložka (Knihy | Pořady | Stažené), filtr stavu,
 * řazení knih a mřížka / seznam. Lehké SharedPreferences bez VM (vzor AudiotekaPrefs); volby z lišty
 * Knihovny se pamatují a stejné jdou nastavit v Nastavení › Knihovna.
 */
object LibraryPrefs {
    private const val PREFS = "slovo_library"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // User 2026-11-21 21:09: „má jít nastavit per profil, zda jsou první knihy, nebo pořady“ → klíče per profil.
    // Aktivní profil = `trakt_prefs.active_profile_id` (zapisuje ProfileRepository.setActive).
    private fun profileId(ctx: Context): Long =
        ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE).getLong("active_profile_id", 0L)
    private fun profileKey(ctx: Context, base: String): String = profileId(ctx).takeIf { it > 0L }?.let { "${base}_p$it" } ?: base

    // User 2026-11-23: „vše v jednom pod knihovnou… přejmenuj knihovnu na domov“ + „pořadí si každý změní a výchozí
    // je první“ → Domů = záložky [LibraryTab]; pořadí a skrytí per profil, appka se otevře na první viditelné.
    /** Všechny záložky v pořadí profilu + jestli jsou vidět (editor v Nastavení). */
    fun tabsWithVisibility(ctx: Context): List<Pair<LibraryTab, Boolean>> {
        val pr = p(ctx)
        val saved = pr.getString(profileKey(ctx, "tab_order"), null)
        val order = if (saved != null) {
            val list = saved.split(",").mapNotNull { n -> LibraryTab.entries.firstOrNull { it.name == n } }.distinct()
            list + LibraryTab.entries.filter { it !in list }
        } else legacyOrder(ctx)
        val hidden = pr.getStringSet(profileKey(ctx, "tab_hidden"), emptySet()).orEmpty()
        return order.map { it to (it.name !in hidden) }
    }

    /** Viditelné záložky; nikdy prázdné (všechno skryté = Přehled). */
    fun visibleTabs(ctx: Context): List<LibraryTab> =
        tabsWithVisibility(ctx).filter { it.second }.map { it.first }.ifEmpty { listOf(LibraryTab.OVERVIEW) }

    fun setTabOrder(ctx: Context, order: List<LibraryTab>) =
        p(ctx).edit().putString(profileKey(ctx, "tab_order"), order.joinToString(",") { it.name }).apply()

    fun setTabHidden(ctx: Context, tab: LibraryTab, hidden: Boolean) {
        val key = profileKey(ctx, "tab_hidden")
        val cur = p(ctx).getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (hidden) cur.add(tab.name) else cur.remove(tab.name)
        p(ctx).edit().putStringSet(key, cur).apply()
    }

    /** Bez uloženého pořadí: Přehled první, za ním dřívější „výchozí záložka Knihovny“ (0 Knihy, 1 Pořady, 2 Stažené). */
    private fun legacyOrder(ctx: Context): List<LibraryTab> {
        val pr = p(ctx)
        val pk = profileKey(ctx, "default_tab")
        val old = if (pr.contains(pk)) pr.getInt(pk, 0) else pr.getInt("default_tab", 0)
        val second = when (old) { 1 -> LibraryTab.SHOWS; 2 -> LibraryTab.DOWNLOADS; else -> LibraryTab.BOOKS }
        return listOf(LibraryTab.OVERVIEW, second) + LibraryTab.entries.filter { it != LibraryTab.OVERVIEW && it != second }
    }

    fun sort(ctx: Context): BookSort = BookSort.entries.getOrElse(p(ctx).getInt("book_sort", 0)) { BookSort.RECENT }
    fun setSort(ctx: Context, v: BookSort) = p(ctx).edit().putInt("book_sort", v.ordinal).apply()

    fun filter(ctx: Context): BookFilter = BookFilter.entries.getOrElse(p(ctx).getInt("book_filter", 0)) { BookFilter.ALL }
    fun setFilter(ctx: Context, v: BookFilter) = p(ctx).edit().putInt("book_filter", v.ordinal).apply()

    fun listMode(ctx: Context): Boolean = p(ctx).getBoolean("book_list", false)
    fun setListMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("book_list", v).apply()

    fun rowSize(ctx: Context): RowSize = RowSize.entries.getOrElse(p(ctx).getInt("book_row", 1)) { RowSize.MEDIUM }
    fun setRowSize(ctx: Context, v: RowSize) = p(ctx).edit().putInt("book_row", v.ordinal).apply()

    // Pořady: stejná lišta jako knihy (user 2026-11-23 „proč pořady nemají lištu filtrů a řazení“).
    fun showSort(ctx: Context): ShowSort = ShowSort.entries.getOrElse(p(ctx).getInt("show_sort", 0)) { ShowSort.TITLE }
    fun setShowSort(ctx: Context, v: ShowSort) = p(ctx).edit().putInt("show_sort", v.ordinal).apply()
    fun showFilter(ctx: Context): ShowFilter = ShowFilter.entries.getOrElse(p(ctx).getInt("show_filter", 0)) { ShowFilter.ALL }
    fun setShowFilter(ctx: Context, v: ShowFilter) = p(ctx).edit().putInt("show_filter", v.ordinal).apply()
    fun showListMode(ctx: Context): Boolean = p(ctx).getBoolean("show_list", false)
    fun setShowListMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("show_list", v).apply()
    fun showRowSize(ctx: Context): RowSize = RowSize.entries.getOrElse(p(ctx).getInt("show_row", 1)) { RowSize.MEDIUM }
    fun setShowRowSize(ctx: Context, v: RowSize) = p(ctx).edit().putInt("show_row", v.ordinal).apply()
}

/** Záložky Domů (dřív Domů + Knihovna + Pořady zvlášť). */
enum class LibraryTab(val label: String) {
    OVERVIEW("Přehled"),
    BOOKS("Knihy"),
    SHOWS("Pořady"),
    NEW("Nové díly"),
    DOWNLOADS("Stažené"),
}

/** Velikost řádku seznamu (náhled + písmo). */
enum class RowSize(val label: String, val cover: androidx.compose.ui.unit.Dp) {
    SMALL("Malé řádky", 40.dp),
    MEDIUM("Střední řádky", 56.dp),
    LARGE("Velké řádky", 88.dp),
}

enum class ShowSort(val label: String) {
    TITLE("Název"),
    NEWEST("Nejnovější díl"),
    ADDED("Naposledy přidané"),
}

/** Filtr typu pořadu; [type] = `PodcastSource.type` (ABS podcasty patří k Podcastům). */
enum class ShowFilter(val label: String, val type: String) {
    ALL("Vše", "all"),
    PODCAST("Podcasty", "rss"),
    YOUTUBE("YouTube", "youtube"),
    CTV("ČT", "ctv"),
}

enum class BookSort(val label: String) {
    RECENT("Naposledy poslouchané"),
    TITLE("Název"),
    ADDED("Naposledy přidané"),
    AUTHOR("Autor"),
    LONGEST("Nejdelší"),
    SHORTEST("Nejkratší"),
}

enum class BookFilter(val label: String) {
    ALL("Vše"),
    IN_PROGRESS("Rozposlouchané"),
    NOT_STARTED("Nezačaté"),
    FINISHED("Dohrané"),
}

// Rozposlouchaná až od 15 s poslechu (user 2026-11-21: Baron Prášil s uloženými 0,7 s visel v Rozposlouchaných).
private fun Audiobook.started(): Boolean = !isFinished && currentTimeSec >= STARTED_MIN_SEC
/** Sdílená hranice „rozposlouchané“ — Knihovna, Domů Pokračovat i Nově v knihovně. */
internal const val STARTED_MIN_SEC = 15.0

fun List<Audiobook>.filteredBy(f: BookFilter): List<Audiobook> = when (f) {
    BookFilter.ALL -> this
    BookFilter.IN_PROGRESS -> filter { it.started() }
    BookFilter.NOT_STARTED -> filter { !it.isFinished && !it.started() }
    BookFilter.FINISHED -> filter { it.isFinished }
}

/** Seřazený seznam; pořadí sérií v mřížce určí jejich nejlépe umístěný díl (viz [orderShelf]). */
fun List<Audiobook>.sortedForShelf(sort: BookSort): List<Audiobook> {
    val collator = Collator.getInstance(Locale("cs"))
    return when (sort) {
        BookSort.RECENT -> sortedWith(compareByDescending<Audiobook> { it.lastUpdate ?: 0L }.thenBy(collator) { it.title })
        BookSort.TITLE -> sortedWith(compareBy(collator) { it.title })
        BookSort.ADDED -> sortedWith(compareByDescending<Audiobook> { it.addedAt ?: 0L }.thenBy(collator) { it.title })
        BookSort.AUTHOR -> sortedWith(compareBy<Audiobook, String>(collator) { it.author.orEmpty() }.thenBy(collator) { it.title })
        BookSort.LONGEST -> sortedByDescending { it.durationSec }
        BookSort.SHORTEST -> sortedBy { it.durationSec }
    }
}

/**
 * Police (knihy + série jako jedna karta) v pořadí podle [sorted]: série stojí tam, kde je její první
 * díl v seřazeném seznamu. Název řadí abecedně i série (jako dřív).
 */
fun orderShelf(shelf: List<BookShelfItem>, sorted: List<Audiobook>, sort: BookSort): List<BookShelfItem> {
    if (sort == BookSort.TITLE) return shelf
    val rank = sorted.withIndex().associate { (i, b) -> b.id to i }
    return shelf.sortedBy { item ->
        when (item) {
            is BookShelfItem.Standalone -> rank[item.book.id] ?: Int.MAX_VALUE
            is BookShelfItem.SeriesGroup -> item.books.minOfOrNull { rank[it.id] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
        }
    }
}
