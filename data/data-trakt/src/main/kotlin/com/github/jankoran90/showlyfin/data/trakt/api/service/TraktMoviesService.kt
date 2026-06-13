package com.github.jankoran90.showlyfin.data.trakt.api.service

import com.github.jankoran90.showlyfin.data.trakt.model.Comment
import com.github.jankoran90.showlyfin.data.trakt.model.Movie
import com.github.jankoran90.showlyfin.data.trakt.model.MovieCollection
import com.github.jankoran90.showlyfin.data.trakt.model.MovieCollectionItem
import com.github.jankoran90.showlyfin.data.trakt.model.MovieResult
import com.github.jankoran90.showlyfin.data.trakt.model.Translation
import retrofit2.http.*

interface TraktMoviesService {
    @GET("movies/{traktId}?extended=full")
    suspend fun fetchMovie(@Path("traktId") traktId: Long): Movie

    @GET("movies/{traktSlug}?extended=full")
    suspend fun fetchMovie(@Path("traktSlug") traktSlug: String): Movie

    @GET("movies/popular?extended=full")
    suspend fun fetchPopularMovies(@Query("genres") genres: String, @Query("certifications") certifications: String, @Query("limit") limit: Int, @Query("page") page: Int): List<Movie>

    @GET("movies/trending?extended=full")
    suspend fun fetchTrendingMovies(@Query("genres") genres: String, @Query("certifications") certifications: String, @Query("limit") limit: Int, @Query("page") page: Int): List<MovieResult>

    @GET("movies/anticipated?extended=full")
    suspend fun fetchAnticipatedMovies(@Query("genres") genres: String, @Query("certifications") certifications: String, @Query("limit") limit: Int, @Query("page") page: Int): List<MovieResult>

    @GET("movies/recommended/weekly?extended=full")
    suspend fun fetchRecommendedMovies(@Query("genres") genres: String, @Query("certifications") certifications: String, @Query("limit") limit: Int): List<MovieResult>

    @GET("movies/{traktId}/related?extended=full")
    suspend fun fetchRelatedMovies(@Path("traktId") traktId: Long, @Query("limit") limit: Int): List<Movie>

    @GET("movies/{traktId}/comments/newest?extended=full")
    suspend fun fetchMovieComments(@Path("traktId") traktId: Long, @Query("limit") limit: Int, @Query("timestamp") timestamp: Long): List<Comment>

    @GET("movies/{traktId}/translations/{code}")
    suspend fun fetchMovieTranslations(@Path("traktId") traktId: Long, @Path("code") countryCode: String): List<Translation>

    @GET("movies/{traktId}/lists/official/popular")
    suspend fun fetchMovieCollections(@Path("traktId") traktId: Long): List<MovieCollection>

    @GET("lists/{collectionId}/items/movie?extended=full")
    suspend fun fetchMovieCollectionItems(@Path("collectionId") collectionId: Long): List<MovieCollectionItem>
}
