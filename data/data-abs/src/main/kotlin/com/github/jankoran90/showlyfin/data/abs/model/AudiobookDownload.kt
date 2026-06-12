package com.github.jankoran90.showlyfin.data.abs.model

import android.net.Uri
import java.io.File

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
    /** Lokálně stažený obal (Plan CASTAWAY CA-4) — null u starších stažení / když se nepodařil. */
    val localCoverPath: String? = null,
)

/** Jedna lokálně stažená audio stopa audioknihy (offset v rámci celé knihy zachován). */
data class LocalAudiobookTrack(
    val index: Int,
    val filePath: String,
    val startOffsetSec: Double,
    val durationSec: Double,
)

/**
 * Mapování stažené audioknihy na UI model police [Audiobook] (Plan CASTAWAY CA-2). Offline nemáme
 * server-side progres/pozici (ty drží jen server) → 0/false; obal je stále serverové URL (lokální
 * cache obalů = CASTAWAY CA-4 follow-up).
 */
/** Obal k zobrazení: lokálně stažený (offline) má přednost, jinak serverové URL. */
fun AudiobookDownload.displayCover(): String? =
    localCoverPath?.let { File(it).takeIf(File::exists)?.let { f -> Uri.fromFile(f).toString() } } ?: coverUrl

fun AudiobookDownload.toAudiobook(): Audiobook = Audiobook(
    id = itemId,
    title = title,
    author = author,
    narrator = null,
    seriesName = null,
    coverUrl = displayCover(),
    durationSec = durationSec,
    progress = 0.0,
    currentTimeSec = 0.0,
    isFinished = false,
)

/**
 * Offline detail stažené knihy (Plan CASTAWAY CA-2) — když server není dostupný, postavíme detail
 * z lokálního záznamu, aby šel otevřít a spustit přehrávač. Popis/rok/žánry server nemá offline.
 */
fun AudiobookDownload.toAudiobookDetail(): AudiobookDetail = AudiobookDetail(
    book = toAudiobook(),
    description = null,
    publishedYear = null,
    genres = emptyList(),
    chapters = chapters,
)
