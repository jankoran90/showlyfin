package com.github.jankoran90.showlyfin.core.ui.tokens

import androidx.compose.ui.unit.dp

/**
 * AMPHORA (SHW-124) DS1 — tokeny mezer a velikostí pro sjednocení UI (soupis fúze: „stovky čísel
 * natvrdo"). Tvary NEjsou tady: berou se z `MaterialTheme.shapes` (extraSmall 4 · small 8 · medium 12 ·
 * large 16 · extraLarge 28) — karta = medium, náhled v řádku = small, list/sheet = extraLarge.
 * Nový a upravovaný kód používá tyhle hodnoty místo syrových `N.dp`.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Rezerva pod posledním prvkem seznamu, aby ho nepřekryl mini přehrávač. */
    val miniPlayerReserve = 96.dp
}

/** Velikosti obalů (DS1 jedna karta): karta v řadě, náhled v řádku seznamu, malý odznak. */
object CoverSize {
    val card = 132.dp
    val row = 52.dp
    val thumb = 40.dp
    /** DS2 obal jako hrdina — velký obal v hlavičce detailu. */
    val hero = 176.dp
}
