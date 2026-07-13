package com.github.jankoran90.showlyfin.core.domain

/**
 * COUCH (SHW-88) — věkový filtr obsahu pro dětský (omezený) profil. Rozhoduje, zda se položka smí
 * zobrazit v OBJEVOVACÍCH plochách (doporučení / trendy / populární / očekávané / Trakt řady / hledání).
 * **Jellyfin knihovna se NEfiltruje** (je pro děti schválená ručně) — volající ji sem prostě neposílá.
 *
 * Primárně rozhoduje reálná TMDB certifikace ([MediaItem.certificationAge], roky). Když je neznámá,
 * padá na žánrovou heuristiku (zjevně nevhodné žánry se skryjí) a dále dle [hideUnrated]:
 * `false` = neznámé (a žánrově neškodné) propustit (ať pásy nezůstanou prázdné), `true` = přísně skrýt.
 */
object ContentAgeGate {

    /** Věk, od kterého už strop nic nefiltruje (dospělý obsah). */
    const val ADULT_AGE = 18

    /** Žánry považované za nevhodné pro dětský strop, když chybí certifikace (CZ i EN varianty). */
    private val MATURE_GENRES = setOf(
        "horror", "horor",
        "thriller",
        "war", "válečný", "válečný film",
        "crime", "krimi", "kriminální",
        "erotic", "erotika", "erotický",
        "adult", "porno", "pro dospělé",
    )

    /**
     * @param capAge věkový strop profilu (roky). null nebo >= [ADULT_AGE] = bez omezení → vše projde.
     * @param hideUnrated true = položky bez certifikace přísně skrýt (i žánrově neškodné).
     * @return true = položku ZOBRAZIT.
     */
    fun isAllowed(capAge: Int?, item: MediaItem, hideUnrated: Boolean = false): Boolean {
        if (capAge == null || capAge >= ADULT_AGE) return true
        item.certificationAge?.let { return it <= capAge }
        // Certifikace neznámá → žánrová pojistka.
        val genres = item.genres.orEmpty().map { it.lowercase().trim() }
        if (genres.any { it in MATURE_GENRES }) return false
        return !hideUnrated
    }

    /** Odfiltruj [items] dle stropu (zachová pořadí). */
    fun filter(capAge: Int?, items: List<MediaItem>, hideUnrated: Boolean = false): List<MediaItem> {
        if (capAge == null || capAge >= ADULT_AGE) return items
        return items.filter { isAllowed(capAge, it, hideUnrated) }
    }
}
