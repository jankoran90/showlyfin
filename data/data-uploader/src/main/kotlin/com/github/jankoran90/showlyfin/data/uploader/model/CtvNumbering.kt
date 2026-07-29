package com.github.jankoran90.showlyfin.data.uploader.model

/**
 * VLTAVA (SHW-110) F5 — čísla SÉRIE a DÍLU pro pořad z ČT iVysílání.
 *
 * ČT v datech epizod čísla dílů ani sezón NEPOSÍLÁ (`episodesPreviewFind` vrací jen id/název/datum/
 * délku). Odvozujeme je proto ze dvou zdrojů, oba ověřené živě na produkčním API 2026-07-29:
 *
 * **1) Název dílu nese „N/M " u seriálů, které ČT čísluje** (autoritativní, má přednost):
 * `Vyprávěj` → „1/26 Od začátku", „2/26 Je to ten pravý?"; `Nemocnice na kraji města` → „1/20 Výročí".
 * Když číslování nese většina dílů, je to JEDNA řada o M dílech a rozpad podle idec (níž) se NEDĚLÁ —
 * Nemocnice má totiž 20 dílů rozprostřených přes DVA idec prefixy, ale ČT je počítá jako jednu řadu.
 *
 * **2) Jinak z `idec`:** posledních [ORDINAL_LEN] číslic = pořadí dílu, zbytek (prefix) = identita
 * série. Ověřeno: `Magické hlubiny` = `31629838010`+`0001..0006` a `32029838010`+`0001..0007`
 * (dvě řady po 6 a 7 dílech), `Vyprávěj` = `20852216140`+`0001..`.
 *
 * 🔴 **Datum vysílání jako pořadí NEPOUŽÍVÁME.** `date` je poslední REPRÍZA, ne premiéra: u Magických
 * hlubin má „Jezera a bažiny" (2. díl) datum 2026-07-28, zatímco 1. díl „Řeky a potoky" 2026-07-27 a
 * 4. díl „Rybníky a přehrady" 2024-10-07. Právě proto appka nabízela jako „další díl" Jezera a bažiny
 * (user 2026-07-28: „vybrala se jezera a bažiny, a ta určitě není první díl"). Řadíme podle idec.
 */
object CtvNumbering {

    /** Kolik posledních číslic `idec` nese pořadí dílu (zbytek = identita série). */
    private const val ORDINAL_LEN = 4

    /** Díl s dopočítanými čísly. [seasonKey] = identita série (prefix idec / „" u číslovaných seriálů). */
    data class Numbered(
        val episode: CtvEpisode,
        val seasonKey: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        /** Název bez „N/M " prefixu — číslo kreslíme zvlášť, ať se neopakuje. */
        val cleanTitle: String,
    )

    private val NUMBER_PREFIX = Regex("""^\s*(\d{1,3})\s*/\s*(\d{1,4})\s+(.*)$""")

    /**
     * Seřaď díly (od prvního) a přidej jim čísla série a dílu. Vstup smí přijít v jakémkoli pořadí —
     * jediné, na co se spoléháme, je `idec` (a případné „N/M " v názvu).
     */
    fun number(episodes: List<CtvEpisode>): List<Numbered> {
        if (episodes.isEmpty()) return emptyList()
        val sorted = episodes.sortedWith(compareBy({ seasonKeyOf(it.id) }, { ordinalOf(it.id) }, { it.id }))
        // Čísluje ČT sama? (většina dílů nese „N/M") → jedna řada, čísla bereme od ní.
        val labelled = sorted.count { NUMBER_PREFIX.matches(it.title) }
        if (labelled * 2 > sorted.size) {
            val numbered = sorted.mapIndexed { idx, ep ->
                val m = NUMBER_PREFIX.find(ep.title)
                Numbered(
                    episode = ep,
                    seasonKey = "",
                    seasonNumber = 1,
                    // Díl bez „N/M" (ČT občas přidá bonus) dostane pořadí v seřazeném seznamu.
                    episodeNumber = m?.groupValues?.get(1)?.toIntOrNull() ?: (idx + 1),
                    cleanTitle = m?.groupValues?.get(3)?.trim()?.takeIf { it.isNotBlank() } ?: ep.title,
                )
            }
            // Číslo z názvu je autoritativní → seřaď podle něj (idec u přečíslovaných řad nesedí).
            return numbered.sortedBy { it.episodeNumber }
        }
        // Jinak série = prefix idec, díl = pořadí v ní.
        val seasonKeys = sorted.map { seasonKeyOf(it.id) }.distinct()
        val counters = HashMap<String, Int>()
        return sorted.map { ep ->
            val key = seasonKeyOf(ep.id)
            val n = (counters[key] ?: 0) + 1
            counters[key] = n
            Numbered(
                episode = ep,
                seasonKey = key,
                seasonNumber = seasonKeys.indexOf(key) + 1,
                episodeNumber = n,
                cleanTitle = ep.title,
            )
        }
    }

    /** „S01E04" — tvar, ve kterém to píšeme u seriálů z Jellyfinu (user 2026-07-28: „vezmi to 1:1"). */
    fun label(seasonNumber: Int, episodeNumber: Int): String =
        "S%02dE%02d".format(seasonNumber, episodeNumber)

    /** Prefix idec bez posledních [ORDINAL_LEN] číslic = identita série; krátké/nečíselné id → celé. */
    private fun seasonKeyOf(idec: String): String =
        if (idec.length > ORDINAL_LEN) idec.dropLast(ORDINAL_LEN) else idec

    /** Posledních [ORDINAL_LEN] číslic idec jako číslo (pořadí dílu v sérii); nečíselné → 0. */
    private fun ordinalOf(idec: String): Int =
        idec.takeLast(ORDINAL_LEN).toIntOrNull() ?: 0
}
