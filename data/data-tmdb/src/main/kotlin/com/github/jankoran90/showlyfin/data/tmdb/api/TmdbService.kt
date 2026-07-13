package com.github.jankoran90.showlyfin.data.tmdb.api

import com.github.jankoran90.showlyfin.data.tmdb.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbService {

    // COUCH (SHW-88): volitelný `language` (null → TMDB default en, zpětně kompatibilní). Enrich posílá
    // cs-CZ → `title`/`overview` v details je pak česky-nebo-originál (robustní CZ fallback vůči prázdné
    // `cs` translations u niche titulů).
    @GET("movie/{tmdbId}")
    suspend fun fetchMovieDetails(
        @Path("tmdbId") tmdbId: Long,
        @Query("language") language: String? = null,
    ): TmdbMovieDetails

    @GET("tv/{tmdbId}")
    suspend fun fetchShowDetails(
        @Path("tmdbId") tmdbId: Long,
        @Query("language") language: String? = null,
    ): TmdbShowDetails

    // COUCH (SHW-88): certifikace (věkové hranice) pro dětský filtr.
    @GET("movie/{tmdbId}/release_dates")
    suspend fun fetchMovieReleaseDates(@Path("tmdbId") tmdbId: Long): TmdbReleaseDatesResponse

    @GET("tv/{tmdbId}/content_ratings")
    suspend fun fetchShowContentRatings(@Path("tmdbId") tmdbId: Long): TmdbContentRatingsResponse

    @GET("tv/{tmdbId}/images")
    suspend fun fetchShowImages(@Path("tmdbId") tmdbId: Long): TmdbImages

    // TENFOOT WS-C (SHW-87): detail sezóny (seznam epizod). Jazyk cs-CZ (fallback orig na prázdné pole v UI).
    @GET("tv/{tmdbId}/season/{seasonNumber}")
    suspend fun fetchSeason(
        @Path("tmdbId") tmdbId: Long,
        @Path("seasonNumber") seasonNumber: Int,
        @Query("language") language: String = "cs-CZ",
    ): TmdbSeasonDetails

    @GET("tv/{tmdbId}/season/{season}/episode/{episode}/images")
    suspend fun fetchEpisodeImages(@Path("tmdbId") tmdbId: Long?, @Path("season") seasonNumber: Int?, @Path("episode") episodeNumber: Int?): TmdbImages

    @GET("movie/{tmdbId}/images")
    suspend fun fetchMovieImages(@Path("tmdbId") tmdbId: Long): TmdbImages

    @GET("person/{tmdbId}/images")
    suspend fun fetchPersonImages(@Path("tmdbId") tmdbId: Long): TmdbImages

    @GET("person/{tmdbId}")
    suspend fun fetchPersonDetails(@Path("tmdbId") tmdbId: Long): TmdbPerson

    @GET("person/{tmdbId}/movie_credits")
    suspend fun fetchPersonMovieCredits(
        @Path("tmdbId") tmdbId: Long,
        @Query("language") language: String = "cs-CZ",
    ): TmdbPersonMovieCredits

    @GET("person/{tmdbId}/translations")
    suspend fun fetchPersonTranslation(@Path("tmdbId") tmdbId: Long): TmdbTranslationResponse

    @GET("movie/{tmdbId}/translations")
    suspend fun fetchMovieTranslations(@Path("tmdbId") tmdbId: Long): TmdbTranslationResponse

    @GET("tv/{tmdbId}/translations")
    suspend fun fetchShowTranslations(@Path("tmdbId") tmdbId: Long): TmdbTranslationResponse

    @GET("collection/{collectionId}")
    suspend fun fetchCollection(@Path("collectionId") collectionId: Long): TmdbCollection

    @GET("movie/{tmdbId}/credits")
    suspend fun fetchMoviePeople(@Path("tmdbId") tmdbId: Long): TmdbPeople

    @GET("tv/{tmdbId}/aggregate_credits")
    suspend fun fetchShowPeople(@Path("tmdbId") tmdbId: Long): TmdbPeople

    @GET("movie/{tmdbId}/watch/providers")
    suspend fun fetchMovieWatchProviders(@Path("tmdbId") tmdbId: Long): TmdbStreamings

    @GET("tv/{tmdbId}/watch/providers")
    suspend fun fetchShowWatchProviders(@Path("tmdbId") tmdbId: Long): TmdbStreamings

    @GET("search/movie?include_adult=false")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("language") language: String = "cs-CZ",
    ): TmdbSearchMovieResponse

    @GET("search/tv?include_adult=false")
    suspend fun searchShows(
        @Query("query") query: String,
        @Query("language") language: String = "cs-CZ",
    ): TmdbSearchShowResponse

    // COMPASS C3 (SHW-44) — hledání lidí a vydavatelství.
    @GET("search/person?include_adult=false")
    suspend fun searchPeople(
        @Query("query") query: String,
        @Query("language") language: String = "cs-CZ",
    ): TmdbSearchPersonResponse

    @GET("search/company")
    suspend fun searchCompanies(
        @Query("query") query: String,
    ): TmdbSearchCompanyResponse

    // COUCH (SHW-88): personalizovaná TMDB doporučení k danému filmu (kvalitnější než /similar) —
    // seed pro play-count vážený engine.
    @GET("movie/{tmdbId}/recommendations")
    suspend fun fetchMovieRecommendations(
        @Path("tmdbId") tmdbId: Long,
        @Query("language") language: String = "cs-CZ",
    ): TmdbSearchMovieResponse

    @GET("discover/movie?include_adult=false&sort_by=popularity.desc")
    suspend fun discoverMovies(
        @Query("with_people") withPeople: String? = null,
        @Query("with_companies") withCompanies: String? = null,
        @Query("language") language: String = "cs-CZ",
    ): TmdbSearchMovieResponse
}
