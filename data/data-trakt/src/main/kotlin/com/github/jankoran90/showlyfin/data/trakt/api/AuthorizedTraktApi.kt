package com.github.jankoran90.showlyfin.data.trakt.api

import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.api.service.TraktCommentsService
import com.github.jankoran90.showlyfin.data.trakt.api.service.TraktSyncService
import com.github.jankoran90.showlyfin.data.trakt.api.service.TraktUsersService
import com.github.jankoran90.showlyfin.data.trakt.model.*
import com.github.jankoran90.showlyfin.data.trakt.model.request.*
import okhttp3.Headers

private const val TRAKT_SYNC_PAGE_LIMIT = 250

internal class AuthorizedTraktApi(
    private val usersService: TraktUsersService,
    private val syncService: TraktSyncService,
    private val commentsService: TraktCommentsService,
) : AuthorizedTraktRemoteDataSource {

    override suspend fun postComment(commentRequest: CommentRequest) = commentsService.postComment(commentRequest)
    override suspend fun postCommentReply(commentId: Long, commentRequest: CommentRequest) =
        commentsService.postCommentReply(commentId, commentRequest)
    override suspend fun deleteComment(commentId: Long) = commentsService.deleteComment(commentId)
    override suspend fun fetchMyProfile() = usersService.fetchMyProfile()
    override suspend fun fetchRecommendedShows(limit: Int) = usersService.fetchRecommendedShows(limit)
    override suspend fun fetchRecommendedMovies(limit: Int) = usersService.fetchRecommendedMovies(limit)

    override suspend fun fetchHiddenShows(): List<HiddenItem> {
        var page = 1
        val results = mutableListOf<HiddenItem>()
        do {
            val response = usersService.fetchHiddenShows(page, TRAKT_SYNC_PAGE_LIMIT)
            results.addAll(response.body().orEmpty())
            page += 1
        } while (page <= response.headers().getPaginationPageCount())
        return results
    }

    override suspend fun fetchDroppedShows(): List<HiddenItem> {
        var page = 1
        val results = mutableListOf<HiddenItem>()
        do {
            val response = usersService.fetchDroppedShows(page, TRAKT_SYNC_PAGE_LIMIT)
            results.addAll(response.body().orEmpty())
            page += 1
        } while (page <= response.headers().getPaginationPageCount())
        return results
    }

    override suspend fun postHiddenShows(shows: List<SyncExportItem>) {
        usersService.postHiddenShows(SyncExportRequest(shows = shows))
    }

    override suspend fun postHiddenMovies(movies: List<SyncExportItem>) {
        usersService.postHiddenMovies(SyncExportRequest(movies = movies))
    }

    override suspend fun fetchHiddenMovies(): List<HiddenItem> {
        var page = 1
        val results = mutableListOf<HiddenItem>()
        do {
            val response = usersService.fetchHiddenMovies(page, TRAKT_SYNC_PAGE_LIMIT)
            results.addAll(response.body().orEmpty())
            page += 1
        } while (page <= response.headers().getPaginationPageCount())
        return results
    }

    override suspend fun fetchSyncActivity(): SyncActivity = syncService.fetchSyncActivity()

    override suspend fun fetchSyncShowHistory(showId: Long): List<SyncHistoryItem> {
        var page = 1
        val results = mutableListOf<SyncHistoryItem>()
        do {
            val response = syncService.fetchSyncShowHistory(showId, page, TRAKT_SYNC_PAGE_LIMIT)
            results.addAll(response.body().orEmpty())
            page += 1
        } while (page <= response.headers().getPaginationPageCount())
        return results
    }

    override suspend fun fetchSyncWatchedShows(extended: String?): List<SyncItem> {
        var page = 1
        val results = mutableListOf<SyncItem>()
        do {
            val response = syncService.fetchSyncWatched("shows", extended, page, TRAKT_SYNC_PAGE_LIMIT).filter { it.show != null }
            results.addAll(response)
            page += 1
        } while (response.isNotEmpty())
        return results
    }

    override suspend fun fetchSyncWatchedMovies(extended: String?): List<SyncItem> {
        var page = 1
        val results = mutableListOf<SyncItem>()
        do {
            val response = syncService.fetchSyncWatched("movies", extended, page, TRAKT_SYNC_PAGE_LIMIT).filter { it.movie != null }
            results.addAll(response)
            page += 1
        } while (response.isNotEmpty())
        return results
    }

    override suspend fun fetchSyncShowsWatchlist() = fetchSyncWatchlist("shows")
    override suspend fun fetchSyncMoviesWatchlist() = fetchSyncWatchlist("movies")

    override suspend fun fetchSyncWatchlist(type: String): List<SyncItem> {
        var page = 1
        val results = mutableListOf<SyncItem>()
        do {
            val response = syncService.fetchSyncWatchlist(type, page, TRAKT_SYNC_PAGE_LIMIT)
            results.addAll(response.body().orEmpty())
            page += 1
        } while (page <= response.headers().getPaginationPageCount())
        return results
    }

    override suspend fun fetchSyncLists() = usersService.fetchSyncLists()
    override suspend fun fetchSyncList(listId: Long) = usersService.fetchSyncList(listId)

    override suspend fun fetchSyncListItems(listId: Long, withMovies: Boolean): List<SyncItem> {
        var page = 1
        val results = mutableListOf<SyncItem>()
        val types = arrayListOf("show").apply { if (withMovies) add("movie") }.joinToString(",")
        do {
            val response = usersService.fetchSyncListItems(listId, types, page, TRAKT_SYNC_PAGE_LIMIT)
            results.addAll(response.body().orEmpty())
            page += 1
        } while (page <= response.headers().getPaginationPageCount())
        return results
    }

    override suspend fun postCreateList(name: String, description: String?): CustomList =
        usersService.postCreateList(CreateListRequest(name, description))

    override suspend fun postUpdateList(customList: CustomList): CustomList =
        usersService.postUpdateList(customList.ids.trakt, CreateListRequest(customList.name, customList.description))

    override suspend fun deleteList(listId: Long) { usersService.deleteList(listId) }

    override suspend fun postAddListItems(listTraktId: Long, showsIds: List<Long>, moviesIds: List<Long>) {
        val body = SyncExportRequest(
            shows = showsIds.map { SyncExportItem.create(it, null) },
            movies = moviesIds.map { SyncExportItem.create(it, null) },
        )
        usersService.postAddListItems(listTraktId, body)
    }

    override suspend fun postRemoveListItems(listTraktId: Long, showsIds: List<Long>, moviesIds: List<Long>) {
        val body = SyncExportRequest(
            shows = showsIds.map { SyncExportItem.create(it, null) },
            movies = moviesIds.map { SyncExportItem.create(it, null) },
        )
        usersService.postRemoveListItems(listTraktId, body)
    }

    override suspend fun postSyncWatchlist(request: SyncExportRequest) = syncService.postSyncWatchlist(request)
    override suspend fun postSyncWatched(request: SyncExportRequest) = syncService.postSyncWatched(request)
    override suspend fun postDeleteProgress(request: SyncExportRequest) = syncService.deleteHistory(request)
    override suspend fun postDeleteWatchlist(request: SyncExportRequest) { syncService.deleteWatchlist(request) }
    override suspend fun deleteHiddenShow(request: SyncExportRequest) = usersService.deleteHidden("progress_watched", request)
    override suspend fun deleteDroppedShow(request: SyncExportRequest) = usersService.deleteHidden("dropped", request)
    override suspend fun deleteHiddenMovie(request: SyncExportRequest) = usersService.deleteHidden("calendar", request)

    override suspend fun deleteRating(show: Show) {
        syncService.postRemoveRating(RatingRequest(shows = listOf(RatingRequestValue(0, "", RatingRequestIds(show.ids?.trakt ?: -1)))))
    }
    override suspend fun deleteRating(movie: Movie) {
        syncService.postRemoveRating(RatingRequest(movies = listOf(RatingRequestValue(0, "", RatingRequestIds(movie.ids?.trakt ?: -1)))))
    }
    override suspend fun deleteRating(episode: Episode) {
        syncService.postRemoveRating(RatingRequest(episodes = listOf(RatingRequestValue(0, "", RatingRequestIds(episode.ids?.trakt ?: -1)))))
    }
    override suspend fun deleteRating(season: Season) {
        syncService.postRemoveRating(RatingRequest(seasons = listOf(RatingRequestValue(0, "", RatingRequestIds(season.ids?.trakt ?: -1)))))
    }

    override suspend fun postRatings(request: RatingRequest) = syncService.postRating(request)

    override suspend fun postRemoveRatings(request: RatingRequest) = syncService.postRemoveRating(request)

    override suspend fun postRating(movie: Movie, rating: Int, ratedAt: String) {
        syncService.postRating(RatingRequest(movies = listOf(RatingRequestValue(rating, ratedAt, RatingRequestIds(movie.ids?.trakt ?: -1)))))
    }
    override suspend fun postRating(show: Show, rating: Int, ratedAt: String) {
        syncService.postRating(RatingRequest(shows = listOf(RatingRequestValue(rating, ratedAt, RatingRequestIds(show.ids?.trakt ?: -1)))))
    }
    override suspend fun postRating(episode: Episode, rating: Int, ratedAt: String) {
        syncService.postRating(RatingRequest(episodes = listOf(RatingRequestValue(rating, ratedAt, RatingRequestIds(episode.ids?.trakt ?: -1)))))
    }
    override suspend fun postRating(season: Season, rating: Int, ratedAt: String) {
        syncService.postRating(RatingRequest(seasons = listOf(RatingRequestValue(rating, ratedAt, RatingRequestIds(season.ids?.trakt ?: -1)))))
    }

    override suspend fun fetchShowsRatings() = syncService.fetchShowsRatings()
    override suspend fun fetchMoviesRatings() = syncService.fetchMoviesRatings()
    override suspend fun fetchEpisodesRatings() = syncService.fetchEpisodesRatings()
    override suspend fun fetchSeasonsRatings() = syncService.fetchSeasonsRatings()
}

private fun Headers.getPaginationPageCount(): Int = this["x-pagination-page-count"]?.toInt() ?: 0
