package com.github.jankoran90.showlyfin.data.abs.model

/** Stav stažení podcast epizody pro offline poslech (Plan MARCONI Fáze E). */
enum class DownloadStatus { NONE, DOWNLOADING, DOWNLOADED, FAILED }

/** Průběžný stav stažení jedné epizody (pro UI badge). */
data class DownloadState(
    val status: DownloadStatus = DownloadStatus.NONE,
    val progress: Float = 0f,   // 0..1 — vyplněno jen během DOWNLOADING
)

/** Perzistovaný záznam o stažené epizodě (index pro offline přehrání + správu stažení). */
data class EpisodeDownload(
    val episodeId: String,
    val itemId: String,
    val title: String,
    val podcastTitle: String?,
    val coverUrl: String?,
    val filePath: String,
    val durationSec: Double,
    val sizeBytes: Long,
)
