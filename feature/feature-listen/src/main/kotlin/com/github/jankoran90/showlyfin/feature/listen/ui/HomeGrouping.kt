package com.github.jankoran90.showlyfin.feature.listen.ui

import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel.ContinueItem

/**
 * User 2026-11-19 16:00: „sdružovat poslechové karty na Domů — Harry Potter, Správná pětka, Trabantem, Po jedné
 * stopě … rozposlouchané sbalené pod jednu hlavní kartu toho pořadu nebo audioknihy, řazené dle nejnovějšího
 * poslechu, pod rozbalením díly označené jako na kartě pořadu". Knihy = stejná série v ABS, díly = stejný pořad.
 * Skupina s jednou položkou se ukáže jako běžná karta s tlačítkem „Celá série“ (rozbalí zbytek série / pořadu).
 */
internal sealed interface HomeEntry {
    val key: String

    data class Single(val item: ContinueItem) : HomeEntry {
        override val key: String = continueItemKey(item)
    }

    data class Group(
        override val key: String,
        val title: String,
        /** Nejnovější poslech první (ten pokračuje tlačítkem ▶ na hlavní kartě). */
        val items: List<ContinueItem>,
        /** Obrázek série / pořadu; jinak obrázek naposled poslouchaného dílu. */
        val imageUrl: String?,
        val isBooks: Boolean,
        val wideImage: Boolean,
    ) : HomeEntry {
        val latest: ContinueItem get() = items.first()
    }
}

/** [items] jsou seřazené od nejnovějšího poslechu → pořadí skupin = jejich nejnovější položka. */
internal fun groupHomeItems(items: List<ContinueItem>, enabled: Boolean): List<HomeEntry> {
    if (!enabled) return items.map { HomeEntry.Single(it) }
    val buckets = LinkedHashMap<String, MutableList<ContinueItem>>()
    items.forEach { buckets.getOrPut(groupKeyOf(it) ?: "single:${continueItemKey(it)}") { mutableListOf() } += it }
    return buckets.map { (key, list) ->
        // User 2026-11-19 17:35: série knih / pořad se rozbalí i s jednou rozposlouchanou položkou (zbytek série).
        if (key.startsWith("single:")) return@map HomeEntry.Single(list.first())
        val first = list.first()
        when (first) {
            is ContinueItem.Book -> HomeEntry.Group(
                key = key,
                title = seriesBase(first.book.seriesName).orEmpty().ifBlank { first.book.title },
                items = list,
                imageUrl = list.firstNotNullOfOrNull { (it as? ContinueItem.Book)?.book?.coverUrl },
                isBooks = true,
                wideImage = false,
            )
            is ContinueItem.Episode -> HomeEntry.Group(
                key = key,
                title = first.sourceTitle.ifBlank { first.episode.title },
                items = list,
                imageUrl = list.firstNotNullOfOrNull { (it as? ContinueItem.Episode)?.episode?.imageUrl?.takeIf { u -> u.isNotBlank() } },
                isBooks = false,
                wideImage = first.sourceType == "youtube" || first.sourceType == "ctv",
            )
        }
    }
}

private fun groupKeyOf(item: ContinueItem): String? = when (item) {
    // Revize: stejný název série v jiné knihovně = jiná série (nesloučit).
    is ContinueItem.Book -> seriesBase(item.book.seriesName)?.lowercase()?.let { "series:${item.book.libraryId}:$it" }
    // Starší YouTube díl bez zdroje (sourceRef "") nesdružovat — nevím, ke kterému pořadu patří.
    is ContinueItem.Episode -> item.sourceRef.takeIf { it.isNotBlank() }?.let { "show:${item.sourceType}:$it" }
}

/** „Správná pětka #4" → „Správná pětka"; prázdná série = null. */
private fun seriesBase(raw: String?): String? =
    raw?.substringBefore(" #")?.trim()?.takeIf { it.isNotBlank() }

internal fun continueItemKey(item: ContinueItem): String = when (item) {
    is ContinueItem.Book -> "book:${item.book.id}"
    is ContinueItem.Episode -> "ep:${item.sourceType}:${item.sourceRef}:${item.episode.id}"
}

/**
 * Tester 2026-11-19 („po rozbalení zůstane sousední karta sama v řádku“): rozbalená skupina je pruh přes celou
 * šířku → kdyby začínal uprostřed řady mřížky, zbytek řady by zůstal prázdný. Pruh proto odsuneme až za konec
 * rozpracované řady (doplní ji následující karty). [columns] = počet sloupců mřížky (1 = seznam, beze změny).
 */
internal fun orderForGrid(entries: List<HomeEntry>, expanded: Set<String>, columns: Int): List<HomeEntry> {
    if (columns <= 1) return entries
    val out = ArrayList<HomeEntry>(entries.size)
    val pending = ArrayList<HomeEntry>()
    var col = 0
    for (e in entries) {
        if (e is HomeEntry.Group && e.key in expanded) {
            if (col == 0) out += e else pending += e
            continue
        }
        out += e
        col = (col + 1) % columns
        if (col == 0 && pending.isNotEmpty()) { out += pending; pending.clear() }
    }
    out += pending
    return out
}
