package com.github.jankoran90.showlyfin.data.tmdb.api

/**
 * COUCH (SHW-88) — převod TMDB certifikačního řetězce (např. „12", „PG-13", „U", „FSK 16") na číselnou
 * věkovou hranici v letech. Slouží dětskému věkovému filtru. Konzervativní: písmenné kódy mapuje na
 * bezpečnou spodní hranici, jinak vytáhne číslice. Neznámé / neohodnocené → null.
 */
internal object CertificationAge {

    /** Preferované země certifikace (pořadí = priorita pro .cz publikum). */
    val COUNTRY_PRIORITY = listOf("CZ", "SK", "DE", "GB", "US")

    /** Země, se kterými se české/slovenské hodnocení porovnává (TMDB je u nich spolehlivé). */
    val REFERENCE_COUNTRIES = listOf("DE", "GB", "US")

    /** Věkový stupeň: 0 (do 5) · 1 (6–8) · 2 (9–12) · 3 (13–15) · 4 (16+). */
    fun step(age: Int): Int = when {
        age <= 5 -> 0
        age <= 8 -> 1
        age <= 12 -> 2
        age <= 15 -> 3
        else -> 4
    }

    /**
     * Vyber věk dle [COUNTRY_PRIORITY]. V rámci země NEJNIŽŠÍ platná hranice (víc release_dates).
     *
     * 🔒 Odlehlá hodnota (user 2026-10-13, Madagaskar 3: TMDB CZ „15+", přitom US/GB PG a DE 6 → dětem
     * zmizel z Filmotéky i kolekce): když se vybraná země liší od NEJPŘÍSNĚJŠÍ z referenčních zemí
     * ([REFERENCE_COUNTRIES], aspoň dvě s údajem) o 2 a víc stupňů ([step]), platí ta referenční —
     * tedy stále nejpřísnější z nich, jen bez chyby v jedné databázi. Rozdíl o 1 stupeň zůstává.
     */
    fun pick(byCountry: Map<String, List<String>>): Int? {
        fun ageOf(certs: List<String>?): Int? = certs.orEmpty().mapNotNull { toAge(it) }.minOrNull()
        val chosen = COUNTRY_PRIORITY.firstNotNullOfOrNull { c -> ageOf(byCountry[c])?.let { c to it } }
            ?: return ageOf(byCountry.values.flatten())
        val (country, age) = chosen
        if (country in REFERENCE_COUNTRIES) return age
        val refs = REFERENCE_COUNTRIES.mapNotNull { ageOf(byCountry[it]) }
        if (refs.size < 2) return age
        val ref = refs.max()
        return if (step(age) - step(ref) >= 2) ref else age
    }

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
