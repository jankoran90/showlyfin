package com.github.jankoran90.showlyfin.data.abs.model

/**
 * Perzistovaný záznam o stažené CELÉ audioknize (Plan CADENCE Fáze D). Na rozdíl od epizody má
 * audiokniha víc audio souborů (stop) poskládaných za sebou + kapitoly — proto vlastní model
 * (index keyed by itemId). Offline přehrání skládá [tracks] do [AbsPlayback] s lokálními URI.
 */
data class AudiobookDownload(
    val itemId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val durationSec: Double,
    val chapters: List<Chapter>,
    val tracks: List<LocalAudiobookTrack>,
    val sizeBytes: Long,
)

/** Jedna lokálně stažená audio stopa audioknihy (offset v rámci celé knihy zachován). */
data class LocalAudiobookTrack(
    val index: Int,
    val filePath: String,
    val startOffsetSec: Double,
    val durationSec: Double,
)
