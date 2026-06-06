package com.github.jankoran90.showlyfin.data.tmdb

import com.github.jankoran90.showlyfin.data.tmdb.model.*

interface TmdbRemoteDataSource {
    suspend fun fetchMovieDetails(tmdbId: Long): TmdbMovieDetails?
    suspend fun fetchShowDetails(tmdbId: Long): TmdbShowDetails?
    suspend fun fetchShowImages(tmdbId: Long): TmdbImages
    suspend fun fetchEpisodeImage(showTmdbId: Long?, season: Int?, episode: Int?): TmdbImage?
    suspend fun fetchMovieImages(tmdbId: Long): TmdbImages
    suspend fun fetchMoviePeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>>
    suspend fun fetchShowPeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>>
    suspend fun fetchShowWatchProviders(tmdbId: Long, countryCode: String): TmdbStreamingCountry?
    suspend fun fetchMovieWatchProviders(tmdbId: Long, countryCode: String): TmdbStreamingCountry?
    suspend fun fetchPersonDetails(id: Long): TmdbPerson
    suspend fun fetchPersonTranslations(id: Long): Map<String, TmdbTranslation.Data>
    suspend fun fetchPersonImages(tmdbId: Long): TmdbImages
    suspend fun fetchMovieTranslation(tmdbId: Long, language: String = "cs"): TmdbTranslation.Data?
    suspend fun fetchShowTranslation(tmdbId: Long, language: String = "cs"): TmdbTranslation.Data?
    suspend fun fetchCollection(collectionId: Long): TmdbCollection?
}
