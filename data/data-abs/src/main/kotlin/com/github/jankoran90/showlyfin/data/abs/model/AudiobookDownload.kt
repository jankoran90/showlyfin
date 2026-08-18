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
    /**
     * User (2026-08-16 17:38, „dětský telefon je trvale offline, Domů nic nezaznamenává") — ABS
     * knihovna, ze které kniha pochází ([Audiobook.libraryId]). Nullable (ne `= ""` jako u
     * [Audiobook]) — Gson při deserializaci starších záznamů BEZ tohohle pole objekt nekonstruuje
     * přes konstruktor (reflexe/Unsafe), takže Kotliní default `= ""` se u chybějícího klíče
     * NEPOUŽIJE a pole by skončilo na runtime `null` i přes staticky non-null typ — nullable typ
     * tohle riziko odstraňuje. Dopočítáno zpětně v `HomeViewModel.doRefresh()` ze síťového seznamu.
     */
    val libraryId: String? = null,
    /**
     * User (2026-08-16 17:56, „série karty se offline nezobrazují, Harry Potter není pod kartou
     * série ale zvlášť") — [Audiobook.seriesName] se dřív do staženého záznamu vůbec neukládal,
     * `groupBooksBySeries()` proto offline knihu vždy zařadil jako samostatnou položku.
     */
    val seriesName: String? = null,
    /** Poslední lokálně uložená pozice přehrávání OFFLINE (sekundy) — zapisuje přehrávač periodicky + při dohrání. */
    val localPositionSec: Double = 0.0,
    /** Epoch ms poslední lokální aktualizace pozice — řadí Domů stejně jako `Audiobook.lastUpdate`. */
    val localUpdatedAt: Long = 0L,
    /** Kniha dohrána OFFLINE (server o tom neví). */
    val localIsFinished: Boolean = false,
)

/** Jedna lokálně stažená audio stopa audioknihy (offset v rámci celé knihy zachován). */
data class LocalAudiobookTrack(
    val index: Int,
    val filePath: String,
    val startOffsetSec: Double,
    val durationSec: Double,
)

/**
 * Mapování stažené audioknihy na UI model police [Audiobook] (Plan CASTAWAY CA-2). User (2026-08-16
 * 17:38) — server-side progres se dřív ukazoval natvrdo 0/false (appka na trvale offline zařízení
 * nikdy neviděla progres) → teď se čte z lokálně persistovaného [localPositionSec]/[localIsFinished]
 * (zapisuje přehrávač, viz `AudiobookPlayerConnection`).
 */
/** Obal k zobrazení: lokálně stažený (offline) má přednost, jinak serverové URL. */
fun AudiobookDownload.displayCover(): String? =
    localCoverPath?.let { File(it).takeIf(File::exists)?.let { f -> Uri.fromFile(f).toString() } } ?: coverUrl

fun AudiobookDownload.toAudiobook(): Audiobook = Audiobook(
    id = itemId,
    title = title,
    author = author,
    narrator = null,
    coverUrl = displayCover(),
    durationSec = durationSec,
    progress = if (durationSec > 0.0) (localPositionSec / durationSec).coerceIn(0.0, 1.0) else 0.0,
    currentTimeSec = localPositionSec,
    isFinished = localIsFinished,
    lastUpdate = localUpdatedAt.takeIf { it > 0L },
    libraryId = libraryId ?: "",
    seriesName = seriesName,
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
