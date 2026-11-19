package com.github.jankoran90.showlyfin.core.ui

/** „1 díl / 2 díly / 5 dílů" — česká shoda čísla a podstatného jména (Slovo, test 2026-11-19 „3 dílů"). */
fun dilyCz(count: Int): String = when {
    count == 1 -> "1 díl"
    count in 2..4 -> "$count díly"
    else -> "$count dílů"
}
