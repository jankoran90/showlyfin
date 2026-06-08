package com.github.jankoran90.showlyfin.data.abs.model

/** UI-facing modely poslechové sekce (odstíněné od syrových ABS responses). */

data class Audiobook(
    val id: String,
    val title: String,
    val author: String?,
    val narrator: String?,
    val seriesName: String?,
    val coverUrl: String?,
    val durationSec: Double,
    val progress: Double,        // 0.0 - 1.0
    val currentTimeSec: Double,  // uložená pozice
    val isFinished: Boolean,
)

data class AudiobookDetail(
    val book: Audiobook,
    val description: String?,
    val publishedYear: String?,
    val genres: List<String>,
    val chapters: List<Chapter>,
)

data class Chapter(
    val index: Int,
    val title: String,
    val startSec: Double,
    val endSec: Double,
)

/** Výsledek otevření play session — vše potřebné pro ExoPlayer + sync. */
data class AbsPlayback(
    val sessionId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val streamUrl: String,       // plné URL prvního audio tracku (single-file audiokniha)
    val startPositionSec: Double,// uložená pozice ze serveru
    val durationSec: Double,
    val chapters: List<Chapter>,
)
