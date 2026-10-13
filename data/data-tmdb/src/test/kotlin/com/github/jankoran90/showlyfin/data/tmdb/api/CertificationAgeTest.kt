package com.github.jankoran90.showlyfin.data.tmdb.api

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificationAgeTest {

    @Test
    fun `odlehle CZ hodnoceni prebije nejprisnejsi referencni zeme`() {
        // Madagaskar 3 (TMDB 80321): CZ 15+, DE 6, GB PG, US PG → PG (8), ne 15.
        val age = CertificationAge.pick(mapOf("CZ" to listOf("15+"), "DE" to listOf("6"), "GB" to listOf("PG"), "US" to listOf("PG", "PG")))
        assertEquals(8, age)
    }

    @Test
    fun `rozdil o jeden stupen zustava ceske hodnoceni`() {
        // Madagaskar 2: CZ 12+, DE 0, GB PG, US PG → 12 (krok 2 vs 1).
        assertEquals(12, CertificationAge.pick(mapOf("CZ" to listOf("12+"), "DE" to listOf("0"), "GB" to listOf("PG"), "US" to listOf("PG"))))
    }

    @Test
    fun `mirnejsi CZ se nezprisnuje`() {
        assertEquals(0, CertificationAge.pick(mapOf("CZ" to listOf("U"), "DE" to listOf("16"), "US" to listOf("R"))))
    }

    @Test
    fun `bez dvou referencnich zemi plati CZ`() {
        assertEquals(15, CertificationAge.pick(mapOf("CZ" to listOf("15+"), "US" to listOf("PG"))))
    }

    @Test
    fun `bez CZ a SK rozhoduje priorita`() {
        assertEquals(6, CertificationAge.pick(mapOf("DE" to listOf("6"), "US" to listOf("PG-13"))))
    }
}
