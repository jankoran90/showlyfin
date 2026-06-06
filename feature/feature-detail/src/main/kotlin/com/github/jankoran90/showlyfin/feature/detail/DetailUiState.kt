package com.github.jankoran90.showlyfin.feature.detail

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw
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
)
