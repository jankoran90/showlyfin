package com.github.jankoran90.showlyfin.data.tmdb.api

import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.*

internal class TmdbApi(private val service: TmdbService) : TmdbRemoteDataSource {

    override suspend fun fetchMovieDetails(tmdbId: Long) =
        try { if (tmdbId <= 0) null else service.fetchMovieDetails(tmdbId) }
        catch (e: Throwable) { null }

    override suspend fun fetchShowDetails(tmdbId: Long) =
        try { if (tmdbId <= 0) null else service.fetchShowDetails(tmdbId) }
        catch (e: Throwable) { null }

    override suspend fun fetchShowImages(tmdbId: Long) =
        try { if (tmdbId <= 0) TmdbImages.EMPTY else service.fetchShowImages(tmdbId) }
        catch (e: Throwable) { TmdbImages.EMPTY }

    override suspend fun fetchEpisodeImage(showTmdbId: Long?, season: Int?, episode: Int?): TmdbImage? {
        if (showTmdbId == null || showTmdbId <= 0 || season == null || season <= 0 || episode == null || episode <= 0) return null
        return try { service.fetchEpisodeImages(showTmdbId, season, episode).stills?.firstOrNull() } catch (e: Throwable) { null }
    }

    override suspend fun fetchMovieImages(tmdbId: Long) =
        try { if (tmdbId <= 0) TmdbImages.EMPTY else service.fetchMovieImages(tmdbId) }
        catch (e: Throwable) { TmdbImages.EMPTY }

    override suspend fun fetchMoviePeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>> {
        val result = service.fetchMoviePeople(tmdbId)
        return mapOf(TmdbPerson.Type.CAST to (result.cast ?: emptyList()), TmdbPerson.Type.CREW to (result.crew ?: emptyList()))
    }

    override suspend fun fetchShowPeople(tmdbId: Long): Map<TmdbPerson.Type, List<TmdbPerson>> {
        val result = service.fetchShowPeople(tmdbId)
        return mapOf(TmdbPerson.Type.CAST to (result.cast ?: emptyList()), TmdbPerson.Type.CREW to (result.crew ?: emptyList()))
    }

    override suspend fun fetchShowWatchProviders(tmdbId: Long, countryCode: String): TmdbStreamingCountry? {
        val result = service.fetchShowWatchProviders(tmdbId)
        val code = if (countryCode.uppercase() == "UK") "GB" else countryCode.uppercase()
        return result.results[code]
    }

    override suspend fun fetchMovieWatchProviders(tmdbId: Long, countryCode: String): TmdbStreamingCountry? {
        val result = service.fetchMovieWatchProviders(tmdbId)
        val code = if (countryCode.uppercase() == "UK") "GB" else countryCode.uppercase()
        return result.results[code]
    }

    override suspend fun fetchPersonDetails(id: Long): TmdbPerson = service.fetchPersonDetails(id)

    override suspend fun fetchPersonTranslations(id: Long): Map<String, TmdbTranslation.Data> {
        val result = service.fetchPersonTranslation(id).translations ?: emptyList()
        return result
            .filter { if (it.iso_639_1.lowercase() != "zh") true else it.iso_3166_1.lowercase() == "cn" }
            .associateBy(keySelector = { it.iso_639_1.lowercase() }, valueTransform = { it.data ?: TmdbTranslation.Data(null) })
    }

    override suspend fun fetchPersonImages(tmdbId: Long) =
        try { if (tmdbId <= 0) TmdbImages.EMPTY else service.fetchPersonImages(tmdbId) }
        catch (e: Throwable) { TmdbImages.EMPTY }

    override suspend fun fetchMovieTranslation(tmdbId: Long, language: String): TmdbTranslation.Data? =
        try {
            if (tmdbId <= 0) null
            else service.fetchMovieTranslations(tmdbId).translations
                ?.firstOrNull { it.iso_639_1.lowercase() == language.lowercase() }
                ?.data
        } catch (e: Throwable) { null }

    override suspend fun fetchShowTranslation(tmdbId: Long, language: String): TmdbTranslation.Data? =
        try {
            if (tmdbId <= 0) null
            else service.fetchShowTranslations(tmdbId).translations
                ?.firstOrNull { it.iso_639_1.lowercase() == language.lowercase() }
                ?.data
        } catch (e: Throwable) { null }

    override suspend fun fetchCollection(collectionId: Long): TmdbCollection? =
        try {
            if (collectionId <= 0) null
            else service.fetchCollection(collectionId)
        } catch (e: Throwable) { null }

    override suspend fun searchMovies(query: String, language: String): List<TmdbSearchMovieItem> =
        try {
            if (query.isBlank()) emptyList()
            else service.searchMovies(query, language).results
        } catch (e: Throwable) { emptyList() }

    override suspend fun searchShows(query: String, language: String): List<TmdbSearchShowItem> =
        try {
            if (query.isBlank()) emptyList()
            else service.searchShows(query, language).results
        } catch (e: Throwable) { emptyList() }

    override suspend fun searchPeople(query: String, language: String): List<TmdbSearchPersonItem> =
        try {
            if (query.isBlank()) emptyList()
            else service.searchPeople(query, language).results
        } catch (e: Throwable) { emptyList() }

    override suspend fun searchCompanies(query: String): List<TmdbSearchCompanyItem> =
        try {
            if (query.isBlank()) emptyList()
            else service.searchCompanies(query).results
        } catch (e: Throwable) { emptyList() }

    override suspend fun discoverMoviesByPerson(personId: Long, language: String): List<TmdbSearchMovieItem> =
        try {
            if (personId <= 0) emptyList()
            else service.discoverMovies(withPeople = personId.toString(), language = language).results
        } catch (e: Throwable) { emptyList() }

    override suspend fun discoverMoviesByCompany(companyId: Long, language: String): List<TmdbSearchMovieItem> =
        try {
            if (companyId <= 0) emptyList()
            else service.discoverMovies(withCompanies = companyId.toString(), language = language).results
        } catch (e: Throwable) { emptyList() }

    override suspend fun moviesByPersonRole(personId: Long, role: PersonRole, language: String): List<TmdbSearchMovieItem> =
        try {
            when {
                personId <= 0 -> emptyList()
                // Neznámá role → původní chování (cast i crew dohromady přes with_people).
                role == PersonRole.GENERIC ->
                    service.discoverMovies(withPeople = personId.toString(), language = language).results
                else -> {
                    val credits = service.fetchPersonMovieCredits(personId, language)
                    val raw = if (role == PersonRole.ACTING) credits.cast
                    else credits.crew.filter { roleMatches(role, it.department, it.job) }
                    raw.map { it.toMovieItem() }.distinctBy { it.id }
                }
            }
        } catch (e: Throwable) { emptyList() }

    /** Sedí TMDB `job`/`department` kreditu na požadovanou [PersonRole]. */
    private fun roleMatches(role: PersonRole, department: String?, job: String?): Boolean {
        fun jobHas(vararg s: String) = job != null && s.any { job.contains(it, ignoreCase = true) }
        fun dept(d: String) = department.equals(d, ignoreCase = true)
        return when (role) {
            PersonRole.DIRECTING -> jobHas("Director") && !jobHas("of Photography") || dept("Directing")
            PersonRole.WRITING -> dept("Writing") || jobHas("Writer", "Screenplay", "Story", "Author")
            PersonRole.CINEMATOGRAPHY -> jobHas("Director of Photography", "Cinematograph") || dept("Camera")
            PersonRole.PRODUCING -> dept("Production") || jobHas("Producer")
            PersonRole.COMPOSING -> jobHas("Composer", "Music") || dept("Sound") && jobHas("Music")
            PersonRole.ACTING, PersonRole.GENERIC -> false
        }
    }
}
