package com.github.jankoran90.showlyfin.ui.phone

import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.tmdb.model.PersonRole
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind

/**
 * COMPASS (SHW-44) — sdílené mapování „kategorie Oblíbeného → role + tvorba osoby" mezi sekcí
 * Oblíbení ([OblibeniViewModel]) a univerzálním hledáním ([SearchViewModel]). Jeden zdroj pravdy,
 * ať se obě obrazovky nerozjedou (DRY).
 */
internal fun favoriteKindToRole(kind: FavoriteKind): PersonRole = when (kind) {
    FavoriteKind.ACTOR -> PersonRole.ACTING
    FavoriteKind.DIRECTOR -> PersonRole.DIRECTING
    FavoriteKind.WRITER -> PersonRole.WRITING
    FavoriteKind.PRODUCER -> PersonRole.PRODUCING
    FavoriteKind.COMPOSER -> PersonRole.COMPOSING
    else -> PersonRole.GENERIC
}

/** Filmy z TMDB → kolekce validních karet (nesou tmdbId → otevřou správný detail). */
internal fun moviesToWorksCollection(name: String, movies: List<TmdbSearchMovieItem>): MediaCollection? {
    val parts = movies
        .filter { !it.poster_path.isNullOrBlank() }
        .take(60)
        .map { m ->
            CollectionPart(
                key = "tmdb_${m.id}",
                tmdbId = m.id,
                jellyfinId = null,
                title = m.title ?: "",
                posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                year = m.release_date?.take(4),
                rating = m.vote_average,
                popularity = m.popularity,
                genres = TmdbGenres.names(m.genre_ids, isShow = false),
            )
        }
    return if (parts.isEmpty()) null else MediaCollection(name = name, parts = parts)
}
