package com.github.jankoran90.showlyfin.core.domain

/**
 * MERIDIAN (SHW-119) — jediný zdroj pravdy pro zápis STOPÁŽE. Používá ho seznamový řádek (za
 * režisérem), bublina rychlého posuvníku i cokoli dalšího, ať se zápis nikde nerozejde.
 *
 * Formát: `1 h 47 m` · `47 m` · `2 h` (celé hodiny bez nulových minut). Nekladná/neznámá = null,
 * volající pak nekreslí nic (radši prázdno než „0 m").
 */
fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "$mins m"
        mins == 0 -> "$hours h"
        else -> "$hours h $mins m"
    }
}
