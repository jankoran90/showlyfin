package com.github.jankoran90.showlyfin.core.domain.filmoteka

import kotlinx.serialization.Serializable

/**
 * CINEMATHEQUE (SHW-90) F2 — regionální „kinematografie" pro osu Země Filmotéky. Každý region seskupuje
 * sadu ISO-3166-1 alpha-2 kódů ([iso]). Titul může spadat do více regionů zároveň (viz [regionsOf]).
 * Řazení řad = pořadí deklarace ([entries]); [OSTATNI] je fallback a je definován poslední (vždy dole).
 * Zapíná/vypíná se v Nastavení ([FilmotekaSettingsStore.enabledRegions]); [OSTATNI] se zobrazuje vždy.
 */
@Serializable
enum class CinematographyRegion(val label: String, val iso: Set<String>) {
    ASIJSKA("Asijská", setOf("JP", "KR", "CN", "HK", "TW", "TH", "IN", "ID", "VN", "PH", "MY", "SG")),
    SEVERSKA("Severská", setOf("SE", "NO", "DK", "FI", "IS")),
    BRITSKA("Britská", setOf("GB", "IE")),
    AMERICKA("Americká", setOf("US", "CA")),
    FRANCOUZSKA("Francouzská", setOf("FR", "BE", "LU")),
    NEMECKA_RAKOUSKA("Německá/rakouská", setOf("DE", "AT", "CH")),
    ITALSKA("Italská", setOf("IT")),
    IBEROAMERICKA("Iberoamerická", setOf("ES", "PT", "MX", "AR", "BR", "CL", "CO")),
    VYCHODOEVROPSKA("Východoevropská", setOf("CZ", "SK", "PL", "RU", "UA", "HU", "RO", "BG", "RS", "HR")),
    OSTATNI("Ostatní", emptySet()),
}

/**
 * Regiony, do nichž titul spadá dle jeho zemí původu ([iso] alpha-2). Titul může být ve více regionech.
 * Prázdné / jen neznámé kódy → `listOf(OSTATNI)`. Řazení = pořadí enumu; [CinematographyRegion.OSTATNI]
 * (fallback) je poslední. OSTATNI se nikdy nepřidává vedle konkrétních regionů — jen když nic nepasuje.
 */
fun regionsOf(iso: List<String>?): List<CinematographyRegion> {
    val codes = iso
        ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.uppercase() }
        ?.toSet()
        .orEmpty()
    if (codes.isEmpty()) return listOf(CinematographyRegion.OSTATNI)
    val matched = CinematographyRegion.entries.filter { region ->
        region != CinematographyRegion.OSTATNI && codes.any { it in region.iso }
    }
    return matched.ifEmpty { listOf(CinematographyRegion.OSTATNI) }
}
