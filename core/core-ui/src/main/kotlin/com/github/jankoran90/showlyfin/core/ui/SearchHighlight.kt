package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.github.jankoran90.showlyfin.core.domain.foldForSearchIndexed
import com.github.jankoran90.showlyfin.core.domain.normalizeForSearch

/**
 * [text] s podbarvenými výskyty [query] (bez ohledu na diakritiku/velikost) pro zvýraznění shody
 * ve fulltextu Nastavení. Port kánonu z hubme (`core/search/SearchMatch.highlightMatches`).
 *
 * Index-mapping jede přes [foldForSearchIndexed], takže zvýraznění sedí i u č/ř/š — foldnutý text
 * (bez diakritiky) se hledá dotazem a mapa vrátí odpovídající rozsah v ORIGINÁLE (s diakritikou).
 * Prázdný dotaz = text beze změny.
 */
fun highlightMatches(
    text: String,
    query: String,
    highlight: Color,
    onHighlight: Color,
): AnnotatedString {
    val q = query.normalizeForSearch()
    if (q.isEmpty()) return AnnotatedString(text)
    val (folded, map) = foldForSearchIndexed(text)
    if (folded.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var orig = 0 // append kurzor v ORIGINÁLE
        var f = 0    // hledací kurzor ve FOLDU
        while (f < folded.length) {
            val hit = folded.indexOf(q, startIndex = f)
            if (hit < 0) break
            val start = map[hit]
            val end = if (hit + q.length - 1 < map.size) map[hit + q.length - 1] + 1 else text.length
            if (start > orig) append(text.substring(orig, start))
            withStyle(SpanStyle(background = highlight, color = onHighlight)) {
                append(text.substring(start, end))
            }
            orig = end
            f = hit + q.length
        }
        if (orig < text.length) append(text.substring(orig))
    }
}
