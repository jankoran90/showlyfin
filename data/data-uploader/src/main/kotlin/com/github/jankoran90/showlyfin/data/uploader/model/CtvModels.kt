package com.github.jankoran90.showlyfin.data.uploader.model

/**
 * KAVKA (SHW-76): ČT iVysílání jako 3. typ zdroje podcastů (`ctv`), symetricky k YouTube ([YtModels]).
 * Backend `/api/ctv/feed?show=<sidp>` vrací díly pořadu (GraphQL `episodesPreviewFind`). Přehrání jde
 * přes DASH manifest `/api/ctv/manifest/{idec}.mpd` — o2tv CDN je IP-locked na server, takže manifest i
 * segmenty byte-proxujeme přes uploader (jako CLARITY HLS, jen DASH). `?audio=1` = poslechová varianta.
 */
data class CtvShowFeed(
    val title: String? = null,
    val description: String? = null,
    val show: String? = null,
    val total: Int? = null,
    val episodes: List<CtvEpisode> = emptyList(),
)

data class CtvEpisode(
    val id: String,                   // idec (15 číslic) — klíč pro resolve/manifest
    val title: String = "",
    val description: String? = null,
    val duration: Double? = null,     // sekundy
    val date: String? = null,         // ISO datetime ("2026-06-17T22:00:00.000Z")
    val label: String? = null,        // "43 min" (cardLabels.bottomRight)
    val image: String? = null,        // URL karty dílu
)
