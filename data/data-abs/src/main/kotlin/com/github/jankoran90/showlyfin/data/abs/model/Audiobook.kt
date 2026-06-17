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
    /** CRUISE (SHW-70): čas posledního poslechu (epoch ms z ABS mediaProgress) → řazení AA „Pokračovat". */
    val lastUpdate: Long? = null,
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

/** Jeden audio soubor audioknihy (ABS vrací víc souborů poskládaných za sebou). */
data class AbsTrack(
    val index: Int,
    val url: String,             // plné URL souboru (+token)
    val startOffsetSec: Double,  // začátek tohoto souboru v čase CELÉ knihy
    val durationSec: Double,
)

/** Výsledek otevření play session — vše potřebné pro ExoPlayer + sync. */
data class AbsPlayback(
    val sessionId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val tracks: List<AbsTrack>,  // všechny soubory; přehrávač je poskládá za sebe
    val startPositionSec: Double,// uložená pozice ze serveru (čas celé knihy)
    val durationSec: Double,     // délka celé knihy
    val chapters: List<Chapter>, // kapitoly v čase celé knihy
)
