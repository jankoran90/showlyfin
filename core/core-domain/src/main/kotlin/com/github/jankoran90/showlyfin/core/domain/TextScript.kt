package com.github.jankoran90.showlyfin.core.domain

/**
 * PASSPORT (SHW-93) A1 — detekce písma pro čitelný název.
 *
 * Niche/asijské tituly u TMDB často nemají český ani anglický překlad → `title` spadne na `original_title`
 * v původním (ne-latinkovém) písmu, které se nedá přečíst ani hledat u zdrojů. Tato heuristika slouží k výběru
 * čitelného kandidáta z řetězce názvů (viz [MediaItem.displayTitle]).
 *
 * `true` = většina písmen je mimo latinku (CJK / hangul / kana / thajština / arabština / hebrejština / azbuka /
 * dévanágarí …). Interpunkce a číslice se nepočítají. Prázdný/bezpísmenný řetězec = `false`.
 */
fun String.looksNonLatin(): Boolean {
    var letters = 0
    var nonLatin = 0
    for (ch in this) {
        if (!ch.isLetter()) continue
        letters++
        val cp = ch.code
        val latin = cp in 0x41..0x5A || cp in 0x61..0x7A ||       // ASCII A-Z a-z
            cp in 0x00C0..0x024F ||                                // Latin-1 Suppl. + Extended-A/B (diakritika)
            cp in 0x1E00..0x1EFF                                   // Latin Extended Additional (vietnamština)
        if (!latin) nonLatin++
    }
    return letters > 0 && nonLatin * 2 >= letters
}
