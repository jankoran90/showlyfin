package com.github.jankoran90.showlyfin.feature.detail

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw
import com.github.jankoran90.showlyfin.data.jellyfin.BoxSetInfo
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbCollection
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbMovieDetails
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbShowDetails

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
    val mergedCollection: MediaCollection? = null,
)
