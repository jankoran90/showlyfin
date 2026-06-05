package com.github.jankoran90.showlyfin.data.trakt.api.service

import com.github.jankoran90.showlyfin.data.trakt.model.*
import com.github.jankoran90.showlyfin.data.trakt.model.request.CreateListRequest
import retrofit2.Response
import retrofit2.http.*

interface TraktUsersService {
    @GET("recommendations/shows?extended=full")
    suspend fun fetchRecommendedShows(@Query("limit") limit: Int = 20): List<Show>

    @GET("recommendations/movies?extended=full")
    suspend fun fetchRecommendedMovies(@Query("limit") limit: Int = 20): List<Movie>

    @GET("users/me")
    suspend fun fetchMyProfile(): User

    @GET("users/hidden/progress_watched?type=show&extended=full")
    suspend fun fetchHiddenShows(@Query("page") page: Int, @Query("limit") pageLimit: Int): Response<List<HiddenItem>>

    @GET("users/hidden/dropped?type=show&extended=full")
    suspend fun fetchDroppedShows(@Query("page") page: Int, @Query("limit") pageLimit: Int): Response<List<HiddenItem>>

    @POST("users/hidden/progress_watched")
    suspend fun postHiddenShows(@Body request: SyncExportRequest)

    @POST("users/hidden/calendar")
    suspend fun postHiddenMovies(@Body request: SyncExportRequest)

    @GET("users/hidden/calendar?type=movie&extended=full")
    suspend fun fetchHiddenMovies(@Query("page") page: Int, @Query("limit") pageLimit: Int): Response<List<HiddenItem>>

    @GET("users/me/lists")
    suspend fun fetchSyncLists(): List<CustomList>

    @GET("users/me/lists/{id}")
    suspend fun fetchSyncList(@Path("id") listId: Long): CustomList

    @GET("users/me/lists/{id}/items/{types}?extended=full")
    suspend fun fetchSyncListItems(@Path("id") listId: Long, @Path("types") types: String, @Query("page") page: Int? = null, @Query("limit") limit: Int? = null): Response<List<SyncItem>>

    @POST("users/me/lists")
    suspend fun postCreateList(@Body request: CreateListRequest): CustomList

    @PUT("users/me/lists/{id}")
    suspend fun postUpdateList(@Path("id") listId: Long, @Body request: CreateListRequest): CustomList

    @DELETE("users/me/lists/{id}")
    suspend fun deleteList(@Path("id") listId: Long): Response<Any>

    @POST("users/me/lists/{id}/items")
    suspend fun postAddListItems(@Path("id") listId: Long, @Body request: SyncExportRequest)

    @POST("users/me/lists/{id}/items/remove")
    suspend fun postRemoveListItems(@Path("id") listId: Long, @Body request: SyncExportRequest)

    @POST("users/hidden/{section}/remove")
    suspend fun deleteHidden(@Path("section") section: String, @Body request: SyncExportRequest)
}
