package com.github.jankoran90.showlyfin.data.tmdb.api

/**
 * COUCH (SHW-88) — převod TMDB certifikačního řetězce (např. „12", „PG-13", „U", „FSK 16") na číselnou
 * věkovou hranici v letech. Slouží dětskému věkovému filtru. Konzervativní: písmenné kódy mapuje na
 * bezpečnou spodní hranici, jinak vytáhne číslice. Neznámé / neohodnocené → null.
 */
internal object CertificationAge {

    /** Preferované země certifikace (pořadí = priorita pro .cz publikum). */
    val COUNTRY_PRIORITY = listOf("CZ", "SK", "DE", "GB", "US")

    fun toAge(certRaw: String?): Int? {
        val cert = certRaw?.trim()?.uppercase() ?: return null
        if (cert.isEmpty() || cert in UNRATED) return null
        LETTER_AGES[cert]?.let { return it }
        // „12", „12A", „PG-13", „16+", „FSK 18" → vytáhni číslice.
        val digits = cert.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 0..21 }
    }

    private val UNRATED = setOf("NR", "UNRATED", "NP", "N/A", "-", "TBD")

    private val LETTER_AGES = mapOf(
        // Bez omezení / do 6
        "U" to 0, "G" to 0, "AL" to 0, "T" to 0, "TV-Y" to 0, "TV-G" to 0, "0" to 0,
        "TV-Y7" to 7,
        // Rodičovský dohled
        "PG" to 8, "TV-PG" to 8,
        // Teen
        "M" to 15, "MA15+" to 15, "TV-14" to 14,
        // Dospělí
        "R" to 17, "NC-17" to 18, "X" to 18, "R18" to 18, "R18+" to 18,
        "18+" to 18, "TV-MA" to 18,
    )
}
