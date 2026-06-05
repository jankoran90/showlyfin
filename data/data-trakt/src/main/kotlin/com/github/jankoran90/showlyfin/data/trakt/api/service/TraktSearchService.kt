package com.github.jankoran90.showlyfin.data.trakt.api.service

import com.github.jankoran90.showlyfin.data.trakt.model.SearchResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TraktSearchService {
    @GET("search/{idType}/{id}?type=person")
    suspend fun fetchPersonIds(@Path("idType") idType: String, @Path("id") id: String): List<SearchResult>

    @GET("search/{idType}/{id}?extended=full")
    suspend fun fetchSearchId(@Path("idType") idType: String, @Path("id") id: String): List<SearchResult>

    @GET("search/show?extended=full&limit=50")
    suspend fun fetchSearchResults(@Query("query") queryText: String): List<SearchResult>

    @GET("search/show,movie?extended=full&limit=50")
    suspend fun fetchSearchResultsMovies(@Query("query") queryText: String): List<SearchResult>
}
