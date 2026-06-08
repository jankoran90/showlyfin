package com.github.jankoran90.showlyfin.data.abs.api

import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrariesResponse
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibraryItem
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibraryItemsResponse
import com.github.jankoran90.showlyfin.data.abs.model.AbsLoginRequest
import com.github.jankoran90.showlyfin.data.abs.model.AbsLoginResponse
import com.github.jankoran90.showlyfin.data.abs.model.AbsMediaUpdate
import com.github.jankoran90.showlyfin.data.abs.model.AbsMeResponse
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayRequest
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlaySession
import com.github.jankoran90.showlyfin.data.abs.model.AbsProgressUpdate
import com.github.jankoran90.showlyfin.data.abs.model.AbsSyncRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Audiobookshelf REST API. Plné URL se předávají přes `@Url` (host = uložená ABS instance),
 * autorizace přes `@Header("Authorization") Bearer <token>`. 401 → AbsAuthInterceptor relogin.
 */
interface AbsService {

    @POST
    @Headers("x-return-tokens: true")
    suspend fun login(@Url url: String, @Body request: AbsLoginRequest): AbsLoginResponse

    @GET
    suspend fun getLibraries(@Url url: String, @Header("Authorization") bearer: String): AbsLibrariesResponse

    @GET
    suspend fun getLibraryItems(@Url url: String, @Header("Authorization") bearer: String): AbsLibraryItemsResponse

    @GET
    suspend fun getItem(@Url url: String, @Header("Authorization") bearer: String): AbsLibraryItem

    @GET
    suspend fun getMe(@Url url: String, @Header("Authorization") bearer: String): AbsMeResponse

    @POST
    suspend fun play(@Url url: String, @Header("Authorization") bearer: String, @Body request: AbsPlayRequest): AbsPlaySession

    @POST
    suspend fun syncProgress(@Url url: String, @Header("Authorization") bearer: String, @Body request: AbsSyncRequest): Response<ResponseBody>

    @POST
    suspend fun closeSession(@Url url: String, @Header("Authorization") bearer: String, @Body request: AbsSyncRequest): Response<ResponseBody>

    /** Označení epizody/položky přehráno/nepřehráno: PATCH /api/me/progress/{itemId}[/{episodeId}]. */
    @PATCH
    suspend fun patchProgress(@Url url: String, @Header("Authorization") bearer: String, @Body body: AbsProgressUpdate): Response<ResponseBody>

    /** Úprava media položky (ABS server auto-download): PATCH /api/items/{itemId}/media. */
    @PATCH
    suspend fun patchMedia(@Url url: String, @Header("Authorization") bearer: String, @Body body: AbsMediaUpdate): Response<ResponseBody>
}
