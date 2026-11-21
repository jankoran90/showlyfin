package com.github.jankoran90.showlyfin.feature.listen.ui

import android.content.Context
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
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

    val tabs = listOf("Knihy", "Pořady", "Stažené")

    // User 2026-11-21 21:09: „má jít nastavit per profil, zda jsou první knihy, nebo pořady“ → klíč per profil,
    // bez vlastní volby profilu platí dřívější společná hodnota. Aktivní profil = `trakt_prefs.active_profile_id`
    // (zapisuje ProfileRepository.setActive, čte i DebugCaptureManager).
    private fun profileKey(ctx: Context): String? =
        ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE).getLong("active_profile_id", 0L)
            .takeIf { it > 0L }?.let { "default_tab_p$it" }

    fun defaultTab(ctx: Context): Int {
        val pr = p(ctx)
        val v = profileKey(ctx)?.takeIf { pr.contains(it) }?.let { pr.getInt(it, 0) } ?: pr.getInt("default_tab", 0)
        return v.coerceIn(0, tabs.lastIndex)
    }
    fun setDefaultTab(ctx: Context, v: Int) = p(ctx).edit().putInt(profileKey(ctx) ?: "default_tab", v).apply()

    fun sort(ctx: Context): BookSort = BookSort.entries.getOrElse(p(ctx).getInt("book_sort", 0)) { BookSort.RECENT }
    fun setSort(ctx: Context, v: BookSort) = p(ctx).edit().putInt("book_sort", v.ordinal).apply()

    fun filter(ctx: Context): BookFilter = BookFilter.entries.getOrElse(p(ctx).getInt("book_filter", 0)) { BookFilter.ALL }
    fun setFilter(ctx: Context, v: BookFilter) = p(ctx).edit().putInt("book_filter", v.ordinal).apply()

    fun listMode(ctx: Context): Boolean = p(ctx).getBoolean("book_list", false)
    fun setListMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("book_list", v).apply()
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
