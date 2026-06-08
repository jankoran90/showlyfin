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
)

data class PodcastDetail(
    val podcast: Podcast,
    val description: String?,
    val episodes: List<PodcastEpisode>,  // newest-first
)
