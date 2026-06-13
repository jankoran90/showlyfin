package com.github.jankoran90.showlyfin.data.trakt.api

import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson
import com.github.jankoran90.showlyfin.data.trakt.TraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.api.service.*
import com.github.jankoran90.showlyfin.data.trakt.model.*
import com.github.jankoran90.showlyfin.data.trakt.model.request.*
import java.lang.System.currentTimeMillis

internal class TraktApi(
    private val showsService: TraktShowsService,
    private val moviesService: TraktMoviesService,
    private val authService: TraktAuthService,
    private val commentsService: TraktCommentsService,
    private val searchService: TraktSearchService,
    private val peopleService: TraktPeopleService,
) : TraktRemoteDataSource {

    override suspend fun fetchShow(traktId: Long) = showsService.fetchShow(traktId)
    override suspend fun fetchShow(traktSlug: String) = showsService.fetchShow(traktSlug)
    override suspend fun fetchMovie(traktId: Long) = moviesService.fetchMovie(traktId)
    override suspend fun fetchMovie(traktSlug: String) = moviesService.fetchMovie(traktSlug)

    override suspend fun fetchPopularShows(genres: String, networks: String, limit: Int, page: Int) =
        showsService.fetchPopularShows(genres, networks, limit, page)

    override suspend fun fetchPopularMovies(genres: String, certifications: String, limit: Int, page: Int) =
        moviesService.fetchPopularMovies(genres, certifications, limit, page)

    override suspend fun fetchTrendingShows(genres: String, networks: String, limit: Int, page: Int): List<Show> =
        showsService.fetchTrendingShows(genres, networks, limit, page).map { it.show!! }

    override suspend fun fetchTrendingMovies(genres: String, certifications: String, limit: Int, page: Int) =
        moviesService.fetchTrendingMovies(genres, certifications, limit, page).map { it.movie!! }

    override suspend fun fetchAnticipatedShows(genres: String, networks: String, limit: Int, page: Int): List<Show> =
        showsService.fetchAnticipatedShows(genres, networks, limit, page).map { it.show!! }

    override suspend fun fetchAnticipatedMovies(genres: String, certifications: String, limit: Int, page: Int): List<Movie> =
        moviesService.fetchAnticipatedMovies(genres, certifications, limit, page).map { it.movie!! }

    override suspend fun fetchRecommendedMovies(genres: String, certifications: String, limit: Int): List<Movie> =
        moviesService.fetchRecommendedMovies(genres, certifications, limit).map { it.movie!! }

    override suspend fun fetchRelatedShows(traktId: Long, addToLimit: Int) =
        showsService.fetchRelatedShows(traktId, Config.TRAKT_RELATED_SHOWS_LIMIT + addToLimit)

    override suspend fun fetchRelatedMovies(traktId: Long, addToLimit: Int) =
        moviesService.fetchRelatedMovies(traktId, Config.TRAKT_RELATED_MOVIES_LIMIT + addToLimit)

    override suspend fun fetchNextEpisode(traktId: Long): Episode? {
        val response = showsService.fetchNextEpisode(traktId)
        if (response.isSuccessful && response.code() == 204) return null
        return response.body()
    }

    override suspend fun fetchSearch(query: String, withMovies: Boolean) =
        if (withMovies) searchService.fetchSearchResultsMovies(query) else searchService.fetchSearchResults(query)

    override suspend fun fetchPersonIds(idType: String, id: String): Ids? {
        val result = searchService.fetchPersonIds(idType, id)
        if (result.isNotEmpty()) return result.first().person?.ids
        return null
    }

    override suspend fun fetchPersonShowsCredits(traktId: Long, type: TmdbPerson.Type): List<PersonCredit> {
        val result = peopleService.fetchPersonCredits(traktId = traktId, "shows")
        val cast = result.cast ?: emptyList()
        val crew = result.crew?.values?.flatten()?.distinctBy { it.show?.ids?.trakt } ?: emptyList()
        return if (type == TmdbPerson.Type.CAST) cast else crew
    }

    override suspend fun fetchPersonMoviesCredits(traktId: Long, type: TmdbPerson.Type): List<PersonCredit> {
        val result = peopleService.fetchPersonCredits(traktId = traktId, "movies")
        val cast = result.cast ?: emptyList()
        val crew = result.crew?.values?.flatten()?.distinctBy { it.movie?.ids?.trakt } ?: emptyList()
        return if (type == TmdbPerson.Type.CAST) cast else crew
    }

    override suspend fun fetchSearchId(idType: String, id: String) = searchService.fetchSearchId(idType, id)

    override suspend fun fetchSeasons(traktId: Long) =
        showsService.fetchSeasons(traktId).sortedByDescending { it.number }

    override suspend fun fetchShowComments(traktId: Long, limit: Int) =
        showsService.fetchShowComments(traktId, limit, currentTimeMillis())

    override suspend fun fetchMovieComments(traktId: Long, limit: Int) =
        moviesService.fetchMovieComments(traktId, limit, currentTimeMillis())

    override suspend fun fetchCommentReplies(commentId: Long) =
        commentsService.fetchCommentReplies(commentId, currentTimeMillis())

    override suspend fun fetchShowTranslations(traktId: Long, code: String) =
        showsService.fetchShowTranslations(traktId, code)

    override suspend fun fetchMovieTranslations(traktId: Long, code: String) =
        moviesService.fetchMovieTranslations(traktId, code)

    override suspend fun fetchSeasonTranslations(showTraktId: Long, seasonNumber: Int, code: String) =
        showsService.fetchSeasonTranslations(showTraktId, seasonNumber, code)

    override suspend fun fetchEpisodeComments(traktId: Long, seasonNumber: Int, episodeNumber: Int): List<Comment> =
        try {
            showsService.fetchEpisodeComments(traktId, seasonNumber, episodeNumber, currentTimeMillis())
        } catch (t: Throwable) {
            emptyList()
        }

    override suspend fun fetchAuthTokens(code: String): OAuthResponse {
        val request = OAuthRequest(code, Config.traktClientId, Config.traktClientSecret, Config.TRAKT_REDIRECT_URL)
        return authService.fetchOAuthToken(request)
    }

    override suspend fun refreshAuthTokens(refreshToken: String): OAuthResponse {
        val request = OAuthRefreshRequest(refreshToken, Config.traktClientId, Config.traktClientSecret, Config.TRAKT_REDIRECT_URL)
        return authService.refreshOAuthToken(request)
    }

    override suspend fun revokeAuthTokens(token: String) {
        val request = OAuthRevokeRequest(token, Config.traktClientId, Config.traktClientSecret)
        authService.revokeOAuthToken(request)
    }

    override suspend fun fetchMovieCollections(traktId: Long): List<MovieCollection> =
        moviesService.fetchMovieCollections(traktId).filter { it.privacy == "public" }

    override suspend fun fetchMovieCollectionItems(collectionId: Long): List<Movie> =
        moviesService.fetchMovieCollectionItems(collectionId).sortedBy { it.rank }.map { it.movie }
}
