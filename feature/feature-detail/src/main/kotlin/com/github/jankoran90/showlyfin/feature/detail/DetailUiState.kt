package com.github.jankoran90.showlyfin.feature.detail

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw
import com.github.jankoran90.showlyfin.data.jellyfin.BoxSetInfo
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbCollection
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbMovieDetails
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbShowDetails

/** Stav nahrávání necachovaného torrentu na RealDebrid (Fáze F). */
data class RdDownloadState(
    val torrentId: String = "",
    val fileIdx: Int = 0,
    val status: String = "magnet_conversion",
    val progress: Double = 0.0,       // 0–100
    val speedBytesPerSec: Long = 0,
    val seeders: Int = 0,
    val title: String = "",
)

/** CONDUIT (SHW-58): cesta výběru zvuku v rozcestníku „Přehrát" — český dabing vs původní znění + CZ titulky. */
enum class StreamAudioPath { CZ_DUB, ORIGINAL }

data class DetailArgs(
    val traktId: Long,
    val tmdbId: Long?,
    val type: MediaType,
    val title: String,
)

data class DetailUiState(
    val item: MediaItem? = null,
    val movieDetails: TmdbMovieDetails? = null,
    val showDetails: TmdbShowDetails? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val csfdId: Long? = null,
    val csfdRating: Int? = null,
    val csfdTitle: String? = null,
    val csfdPlot: String? = null,
    val csfdReviews: List<CsfdReviewRaw> = emptyList(),
    val isCsfdLoading: Boolean = false,
    // ČSFD galerie fotek (F3) — lazy load po kliku na fanart / button Galerie.
    val csfdGallery: List<String> = emptyList(),
    val isGalleryLoading: Boolean = false,
    val showGallery: Boolean = false,
    val tmdbCzOverview: String? = null,
    val tmdbCzTitle: String? = null,
    val collection: TmdbCollection? = null,
    val ownedImdbToJellyfin: Map<String, String> = emptyMap(),
    val ownedTmdbToJellyfin: Map<Long, String> = emptyMap(),
    val watchedImdbIds: Set<String> = emptySet(),
    val watchedTmdbIds: Set<Long> = emptySet(),
    val isOwnedInLibrary: Boolean = false,
    val ownedJellyfinId: String? = null,
    val isWatched: Boolean = false,
    val boxSets: List<BoxSetInfo> = emptyList(),
    val boxSetByTmdbCollection: Map<Long, String> = emptyMap(),
    val boxSetByNormalizedName: Map<String, String> = emptyMap(),
    val matchingBoxSetId: String? = null,
    val jellyfinCollection: MediaCollection? = null,
    val mergedCollection: MediaCollection? = null,
    val isTraktLoggedIn: Boolean = false,
    val isInWatchlist: Boolean = false,
    val isTogglingWatchlist: Boolean = false,
    val cast: List<TmdbPerson> = emptyList(),
    // Stream / Stáhnout hub
    val uploaderConfigured: Boolean = false,
    // CONDUIT (SHW-58): rozcestník „Přehrát" → CZ dabing / Originál, pak filtrovaný stream picker.
    val showStreamPathChooser: Boolean = false,
    val streamAudioPath: StreamAudioPath? = null,
    val showStreamPicker: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val isProbingStreams: Boolean = false,   // Plan CASCADE Fáze 3: probe dalších zdrojů běží na pozadí
    val streams: List<com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream> = emptyList(),
    val streamError: String? = null,
    val isResolvingStream: Boolean = false,
    // Plan FERRY (SHW-37): odesílání zvoleného streamu na TV (yellyfin) + výsledná hláška.
    val isCastingToTv: Boolean = false,
    val castToTvResult: com.github.jankoran90.showlyfin.data.jellyfin.CastResult? = null,
    // PROJECTOR (HUB-74): hláška hlasového castu (odmítnutí — žádný zdroj / cíl nedostupný).
    val autoCastMessage: String? = null,
    val streamStrict: Boolean = true,   // "Přesné hledání" vs "Vše" (per-search)
    // Plan SIEVE (SHW-38): paměť fungujícího zdroje.
    // rememberedSource = uložený „naposledy fungovalo" pro tento film (pin v pickeru, S3).
    // pendingWorkingConfirm = stream, který se právě přehrál a nabízíme „Tohle sedí? 👍" (S2).
    val rememberedSource: com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream? = null,
    val pendingWorkingConfirm: com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream? = null,
    val showDownloadMenu: Boolean = false,
    val showSdilejPicker: Boolean = false,
    val isLoadingSdilej: Boolean = false,
    val sdilejStreams: List<com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream> = emptyList(),
    val sdilejError: String? = null,
    // QUARRY (SHW-79): předvyplnění pro ruční úpravu hledaného textu na Sdílej.cz (název + rok).
    val sdilejDefaultTitle: String = "",
    val sdilejDefaultYear: Int? = null,
    val captureMessage: String? = null,
    val pendingPlaybackUrl: String? = null,
    val pendingPlaybackTitle: String = "",
    val pendingSubtitleQuery: com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery? = null,
    val requestStremioFallback: Boolean = false,
    // Plan WINNOW (SHW-41): zdroj je na RealDebridu blokovaný (DMCA, HTTP 451) — místo tichého skoku
    // do externí Stremio appky ukážeme jasný dialog s vysvětlením + volbou „otevřít ve Stremiu".
    val blockedDmcaMessage: String? = null,
    // REPRISE (SHW-54): přehrávač (Media3) nezvládl KONTEJNER/KODEK souboru (např. Criterion MKV se
    // zlib-komprimovanou stopou — `ContentCompAlgo 0`). Místo tichého skoku do externí Stremio appky
    // ukážeme jasný dialog: zkus jiný release (hraje i na TV + s našimi titulky) / otevři ve Stremiu.
    val incompatibleFormatMessage: String? = null,
    // CASCADE Fáze 4: krátká info hláška při auto-advance po chybě přehrávání ("zkouším další zdroj…")
    val autoAdvanceInfo: String? = null,
    // RD caching progress (Fáze F) — necachovaný torrent se nahrává na RealDebrid
    val rdDownload: RdDownloadState? = null,
    // Bottom sections (universal — in-library i mimo)
    val directorName: String? = null,
    val directorMovies: MediaCollection? = null,
    val studioName: String? = null,
    val studioMovies: MediaCollection? = null,
    // Plan ENSEMBLE (SHW-45): sekce „Tvůrci" — pás herců + Režie/Scénář/Kamera. Klik na osobu →
    // její tvorba (filmografie) jako validní karty (`personFilmography`, nese tmdbId).
    val directors: List<TmdbPerson> = emptyList(),
    val writers: List<TmdbPerson> = emptyList(),
    val cinematographers: List<TmdbPerson> = emptyList(),
    val showPersonSheet: Boolean = false,
    val personSheetName: String? = null,
    val personSheetLoading: Boolean = false,
    val personFilmography: MediaCollection? = null,
    // COMPASS C2 (SHW-44): otevřená osoba v sheetu + její kategorie Oblíbených (null = nelze přidat,
    // např. scénárista/kameraman bez vlastní kategorie) + zda je už oblíbená.
    val personSheetPerson: TmdbPerson? = null,
    val personSheetKind: com.github.jankoran90.showlyfin.data.uploader.FavoriteKind? = null,
    // VANTAGE (SHW-48): český titulek dle role (Herecká tvorba / Režie / Hudba …) do listu tvorby.
    val personSheetRoleLabel: String? = null,
    val isPersonFavorite: Boolean = false,
    // COMPASS C2 (SHW-44): tento film je v Oblíbených (hvězda v app-baru).
    val isFavorite: Boolean = false,
    // Volitelné sekce (Nastavení → Detail z knihovny)
    val showCollections: Boolean = true,
    val showDirector: Boolean = true,
    val showStudio: Boolean = true,
    val showCreators: Boolean = true,
    // Počet řádků popisu ve sbaleném stavu (Nastavení). 0 = bez omezení.
    val plotCollapsedLines: Int = 5,
    // CANVAS (SHW-47) A: pořadí akčních tlačítek na detailu (konfigurovatelné v Nastavení).
    val actionOrder: List<String> = DETAIL_ACTION_KEYS,
    // NOMAD (SHW-60): stav offline stažení TOHOTO titulu do telefonu (jen filmy z knihovny — slice).
    val offlineState: com.github.jankoran90.showlyfin.data.offline.OfflineState = com.github.jankoran90.showlyfin.data.offline.OfflineState(),
)

/** CANVAS (SHW-47) A: klíče akčních tlačítek na detailu (kulatá lišta nahoře) + výchozí pořadí. */
val DETAIL_ACTION_KEYS = listOf("favorite", "play", "tv", "stremio", "download", "watchlist")

/** Parsuj uložené pořadí (CSV) → doplň chybějící klíče na konec, zahoď neznámé. */
fun parseActionOrder(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return DETAIL_ACTION_KEYS
    val saved = raw.split(",").map { it.trim() }.filter { it in DETAIL_ACTION_KEYS }
    return (saved + DETAIL_ACTION_KEYS.filter { it !in saved }).distinct()
}
