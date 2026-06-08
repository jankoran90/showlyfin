package com.github.jankoran90.showlyfin.data.abs.model

/** UI-facing modely podcast části poslechové sekce (odstíněné od syrových ABS responses). */

data class Podcast(
    val id: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val numEpisodes: Int,
    val numUnfinished: Int,   // odhad: numEpisodes - dokončené epizody
)

data class PodcastEpisode(
    val id: String,
    val itemId: String,          // id podcast library item (potřeba pro play/progress)
    val title: String,
    val subtitle: String?,
    val description: String?,
    val publishedAt: Long?,      // ms
    val durationSec: Double,
    val progress: Double,        // 0.0 - 1.0
    val currentTimeSec: Double,  // uložená pozice
    val isFinished: Boolean,
    val guest: String? = null,   // best-effort vyparsované jméno hosta (zvýraznit v seznamu)
)

data class PodcastDetail(
    val podcast: Podcast,
    val description: String?,
    val episodes: List<PodcastEpisode>,  // newest-first
)

/**
 * Epizoda dostupná v RSS feedu, kterou ABS server ještě nemá staženou (z `checkfornew`).
 * [raw] = původní JSON objekt z ABS — posílá se beze změny zpět do `download` (ABS kontrakt).
 */
data class FeedEpisode(
    val id: String,                 // klíč pro výběr (guid/enclosure url/index fallback)
    val title: String,
    val publishedAt: Long?,         // ms
    val description: String?,       // čistý text (bez HTML)
    val durationSec: Double?,
    val guest: String? = null,      // best-effort vyparsovaný host (zvýraznit, jako v detailu)
    val raw: com.google.gson.JsonObject,
)

/** Podcast + stav ABS server auto-downloadu — pro správu v Nastavení. */
data class PodcastServerAutoDownload(
    val itemId: String,
    val title: String,
    val coverUrl: String?,
    val autoDownload: Boolean,
)
