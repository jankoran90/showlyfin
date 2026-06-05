package com.github.jankoran90.showlyfin.feature.detail

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
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
)
