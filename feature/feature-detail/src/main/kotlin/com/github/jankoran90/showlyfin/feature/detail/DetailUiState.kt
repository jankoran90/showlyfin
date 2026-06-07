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
    val csfdPlot: String? = null,
    val csfdReviews: List<CsfdReviewRaw> = emptyList(),
    val isCsfdLoading: Boolean = false,
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
    val showStreamPicker: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val streams: List<com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream> = emptyList(),
    val streamError: String? = null,
    val isResolvingStream: Boolean = false,
    val streamStrict: Boolean = true,   // "Přesné hledání" vs "Vše" (per-search)
    val showDownloadMenu: Boolean = false,
    val showSdilejPicker: Boolean = false,
    val isLoadingSdilej: Boolean = false,
    val sdilejStreams: List<com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream> = emptyList(),
    val sdilejError: String? = null,
    val captureMessage: String? = null,
    val pendingPlaybackUrl: String? = null,
    val pendingPlaybackTitle: String = "",
    val pendingSubtitleQuery: com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery? = null,
    val requestStremioFallback: Boolean = false,
    // RD caching progress (Fáze F) — necachovaný torrent se nahrává na RealDebrid
    val rdDownload: RdDownloadState? = null,
    // Bottom sections (universal — in-library i mimo)
    val directorName: String? = null,
    val directorMovies: MediaCollection? = null,
    val studioName: String? = null,
    val studioMovies: MediaCollection? = null,
    // Volitelné sekce (Nastavení → Detail z knihovny)
    val showCollections: Boolean = true,
    val showDirector: Boolean = true,
    val showStudio: Boolean = true,
)
