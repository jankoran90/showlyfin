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
