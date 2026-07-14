package com.github.jankoran90.showlyfin.data.tmdb

import com.github.jankoran90.showlyfin.data.tmdb.model.*
import java.util.concurrent.ConcurrentHashMap

/**
 * CINEMATHEQUE (SHW-90) — in-memory cache dekorátor nad [TmdbRemoteDataSource]. TMDB vrstva dřív neměla
 * ŽÁDNOU cache: Home/Discover/Trakt/Filmotéka tahaly `movie|tv/{id}` details opakovaně (Filmotéka se
 * stovkami titulů to násobí). Details/translations/certifikace jsou v rámci session stabilní → cachujeme
 * je bez TTL, keyed `(id, lang)`. Ostatní volání delegujeme beze změny.
 *
 * [Holder] obaluje i null výsledek (miss/chyba) → opakovaný dotaz na neexistující id netahá znovu.
 * ConcurrentHashMap toleruje souběžné čtení; ojedinělý dvojitý fetch při závodu je neškodný.
 */
internal class CachedTmdbRemoteDataSource(
    private val delegate: TmdbRemoteDataSource,
) : TmdbRemoteDataSource by delegate {

    private class Holder<T>(val value: T)

    private val movieDetails = ConcurrentHashMap<String, Holder<TmdbMovieDetails?>>()
    private val showDetails = ConcurrentHashMap<String, Holder<TmdbShowDetails?>>()
    private val movieTranslation = ConcurrentHashMap<String, Holder<TmdbTranslation.Data?>>()
    private val showTranslation = ConcurrentHashMap<String, Holder<TmdbTranslation.Data?>>()
    private val movieCertAge = ConcurrentHashMap<Long, Holder<Int?>>()
    private val showCertAge = ConcurrentHashMap<Long, Holder<Int?>>()

    override suspend fun fetchMovieDetails(tmdbId: Long, language: String?): TmdbMovieDetails? {
        val key = "$tmdbId|${language ?: ""}"
        movieDetails[key]?.let { return it.value }
        return delegate.fetchMovieDetails(tmdbId, language).also { movieDetails[key] = Holder(it) }
    }

    override suspend fun fetchShowDetails(tmdbId: Long, language: String?): TmdbShowDetails? {
        val key = "$tmdbId|${language ?: ""}"
        showDetails[key]?.let { return it.value }
        return delegate.fetchShowDetails(tmdbId, language).also { showDetails[key] = Holder(it) }
    }

    override suspend fun fetchMovieTranslation(tmdbId: Long, language: String): TmdbTranslation.Data? {
        val key = "$tmdbId|$language"
        movieTranslation[key]?.let { return it.value }
        return delegate.fetchMovieTranslation(tmdbId, language).also { movieTranslation[key] = Holder(it) }
    }

    override suspend fun fetchShowTranslation(tmdbId: Long, language: String): TmdbTranslation.Data? {
        val key = "$tmdbId|$language"
        showTranslation[key]?.let { return it.value }
        return delegate.fetchShowTranslation(tmdbId, language).also { showTranslation[key] = Holder(it) }
    }

    override suspend fun fetchMovieCertificationAge(tmdbId: Long): Int? {
        movieCertAge[tmdbId]?.let { return it.value }
        return delegate.fetchMovieCertificationAge(tmdbId).also { movieCertAge[tmdbId] = Holder(it) }
    }

    override suspend fun fetchShowCertificationAge(tmdbId: Long): Int? {
        showCertAge[tmdbId]?.let { return it.value }
        return delegate.fetchShowCertificationAge(tmdbId).also { showCertAge[tmdbId] = Holder(it) }
    }
}
