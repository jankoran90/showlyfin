package com.github.jankoran90.showlyfin.data.trakt.model.request

import com.github.jankoran90.showlyfin.data.trakt.model.Episode
import com.github.jankoran90.showlyfin.data.trakt.model.Movie
import com.github.jankoran90.showlyfin.data.trakt.model.Show

data class CommentRequest(
    val show: Show? = null,
    val movie: Movie? = null,
    val episode: Episode? = null,
    val comment: String,
    val spoiler: Boolean,
)
