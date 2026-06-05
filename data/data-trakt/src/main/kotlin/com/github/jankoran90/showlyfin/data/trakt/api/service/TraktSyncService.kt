package com.github.jankoran90.showlyfin.data.trakt.api.service

import com.github.jankoran90.showlyfin.data.trakt.model.*
import com.github.jankoran90.showlyfin.data.trakt.model.request.RatingRequest
import retrofit2.Response
import retrofit2.http.*

interface TraktSyncService {
    @GET("sync/last_activities")
    suspend fun fetchSyncActivity(): SyncActivity

    @GET("sync/history/shows/{showId}")
    suspend fun fetchSyncShowHistory(@Path("showId") showId: Long, @Query("page") page: Int? = null, @Query("limit") limit: Int? = null): Response<List<SyncHistoryItem>>

    @GET("sync/watched/{type}")
    suspend fun fetchSyncWatched(@Path("type") type: String, @Query("extended") extended: String?, @Query("page") page: Int, @Query("limit") limit: Int): List<SyncItem>

    @GET("sync/watchlist/{type}?extended=full")
    suspend fun fetchSyncWatchlist(@Path("type") type: String, @Query("page") page: Int? = null, @Query("limit") limit: Int? = null): Response<List<SyncItem>>

    @POST("sync/watchlist")
    suspend fun postSyncWatchlist(@Body request: SyncExportRequest)

    @POST("sync/history")
    suspend fun postSyncWatched(@Body request: SyncExportRequest)

    @POST("sync/watchlist/remove")
    suspend fun deleteWatchlist(@Body request: SyncExportRequest)

    @POST("sync/history/remove")
    suspend fun deleteHistory(@Body request: SyncExportRequest)

    @POST("sync/ratings")
    suspend fun postRating(@Body request: RatingRequest)

    @POST("sync/ratings/remove")
    suspend fun postRemoveRating(@Body request: RatingRequest)

    @GET("sync/ratings/shows")
    suspend fun fetchShowsRatings(): List<RatingResultShow>

    @GET("sync/ratings/movies")
    suspend fun fetchMoviesRatings(): List<RatingResultMovie>

    @GET("sync/ratings/episodes")
    suspend fun fetchEpisodesRatings(): List<RatingResultEpisode>

    @GET("sync/ratings/seasons")
    suspend fun fetchSeasonsRatings(): List<RatingResultSeason>
}
