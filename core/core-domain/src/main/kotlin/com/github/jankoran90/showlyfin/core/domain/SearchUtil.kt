package com.github.jankoran90.showlyfin.core.domain

import java.text.Normalizer

/**
 * Normalizace pro vyhledávání nezávislé na diakritice a velikosti písmen.
 * "Příšerný" → "priserny", "CHUŤ ČAJE" → "chut caje" (parita yeshowly removeDiacritics).
 */
fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITIC_MARKS, "")
        .lowercase()
        .trim()

private val DIACRITIC_MARKS = Regex("\\p{Mn}+")

/**
 * Fold pro zvýraznění shody: vrátí normalizovaný text (bez diakritiky, lowercase) SPOLU s mapou
 * `index_ve_foldu -> index_v_originále`. Skládá se PO ZNACÍCH (jako hubme `foldSearch`), takže
 * zachovává vztah pozic i u víceznakové diakritiky (č/ř/š) — NFD dekomponuje precomponovaný znak
 * na základ + combining mark, mark se zahodí, zůstane 1 znak mapovaný zpět na původní index.
 * NEtrimuje (na rozdíl od [normalizeForSearch]) — zvýraznění musí zachovat pozice mezer.
 */
fun foldForSearchIndexed(text: String): Pair<String, IntArray> {
    val sb = StringBuilder(text.length)
    val map = ArrayList<Int>(text.length)
    for (i in text.indices) {
        val folded = Normalizer.normalize(text[i].toString(), Normalizer.Form.NFD)
            .replace(DIACRITIC_MARKS, "")
            .lowercase()
        for (c in folded) {
            sb.append(c)
            map.add(i)
        }
    }
    return sb.toString() to map.toIntArray()
}

/**
 * True, když normalizovaný dotaz je obsažen v některém z normalizovaných polí
 * (např. český, anglický nebo originální název). Prázdný dotaz = vždy true.
 */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val q = query.normalizeForSearch()
    if (q.isBlank()) return true
    return fields.any { !it.isNullOrBlank() && it.normalizeForSearch().contains(q) }
}

/** Vyhledávací match MediaItemu — porovná český i originální/anglický název. */
fun MediaItem.matchesQuery(query: String): Boolean = matchesQuery(query, titleCz, title)
