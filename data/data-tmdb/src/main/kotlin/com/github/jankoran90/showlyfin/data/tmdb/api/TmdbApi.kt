package com.github.jankoran90.showlyfin.data.tmdb.api

import com.github.jankoran90.showlyfin.core.domain.looksNonLatin
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.*

internal class TmdbApi(private val service: TmdbService) : TmdbRemoteDataSource {

    override suspend fun fetchMovieDetails(tmdbId: Long, language: String?) =
        try { if (tmdbId <= 0) null else service.fetchMovieDetails(tmdbId, language) }
        catch (e: Throwable) { null }

    override suspend fun fetchShowDetails(tmdbId: Long, language: String?) =
        try { if (tmdbId <= 0) null else service.fetchShowDetails(tmdbId, language) }
        catch (e: Throwable) { null }

    override suspend fun fetchMovieCertificationAge(tmdbId: Long): Int? =
        try {
            if (tmdbId <= 0) null
            else pickCertificationAge(
                service.fetchMovieReleaseDates(tmdbId).results.orEmpty()
                    .associate { c -> c.iso_3166_1.orEmpty().uppercase() to c.release_dates.orEmpty().mapNotNull { it.certification } }
            )
        } catch (e: Throwable) { null }

    override suspend fun fetchShowCertificationAge(tmdbId: Long): Int? =
        try {
            if (tmdbId <= 0) null
            else pickCertificationAge(
                service.fetchShowContentRatings(tmdbId).results.orEmpty()
                    .associate { c -> c.iso_3166_1.orEmpty().uppercase() to listOfNotNull(c.rating) }
            )
        } catch (e: Throwable) { null }

    /**
     * Vyber certifikaci dle priority zemí ([CertificationAge.COUNTRY_PRIORITY]) a převeď na věk. V rámci
     * země bere NEJNIŽŠÍ platnou hranici (u filmů je víc release_dates s různými cert; nejpřísnější =
     * nejbezpečnější floor pro děti). Když prioritní země nic nedá, zkusí kteroukoli.
     */
    override suspend fun movieRecommendations(tmdbId: Long): List<TmdbSearchMovieItem> =
        try { if (tmdbId <= 0) emptyList() else service.fetchMovieRecommendations(tmdbId).results }
        catch (e: Throwable) { emptyList() }

    private fun pickCertificationAge(byCountry: Map<String, List<String>>): Int? {
        fun ageOf(certs: List<String>): Int? =
            certs.mapNotNull { CertificationAge.toAge(it) }.minOrNull()
        for (country in CertificationAge.COUNTRY_PRIORITY) {
            byCountry[country]?.let { ageOf(it)?.let { age -> return age } }
        }
        return byCountry.values.flatMap { it }.let { ageOf(it) }
    }

    override suspend fun fetchShowImages(tmdbId: Long) =
        try { if (tmdbId <= 0) TmdbImages.EMPTY else service.fetchShowImages(tmdbId) }
        catch (e: Throwable) { TmdbImages.EMPTY }

    override suspend fun fetchSeason(tmdbId: Long, seasonNumber: Int): TmdbSeasonDetails? =
        try { if (tmdbId <= 0 || seasonNumber < 0) null else service.fetchSeason(tmdbId, seasonNumber) }
        catch (e: Throwable) { null }

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
            else withReadableTitles(service.discoverMovies(withPeople = personId.toString(), language = language).results) {
                service.discoverMovies(withPeople = personId.toString(), language = EN).results
            }
        } catch (e: Throwable) { emptyList() }

    override suspend fun discoverMoviesByCompany(companyId: Long, language: String): List<TmdbSearchMovieItem> =
        try {
            if (companyId <= 0) emptyList()
            else withReadableTitles(service.discoverMovies(withCompanies = companyId.toString(), language = language).results) {
                service.discoverMovies(withCompanies = companyId.toString(), language = EN).results
            }
        } catch (e: Throwable) { emptyList() }

    override suspend fun moviesByPersonRole(personId: Long, role: PersonRole, language: String): List<TmdbSearchMovieItem> =
        try {
            when {
                personId <= 0 -> emptyList()
                // Neznámá role → původní chování (cast i crew dohromady přes with_people).
                role == PersonRole.GENERIC ->
                    withReadableTitles(service.discoverMovies(withPeople = personId.toString(), language = language).results) {
                        service.discoverMovies(withPeople = personId.toString(), language = EN).results
                    }
                else -> withReadableTitles(personRoleMovies(service.fetchPersonMovieCredits(personId, language), role)) {
                    personRoleMovies(service.fetchPersonMovieCredits(personId, EN), role)
                }
            }
        } catch (e: Throwable) { emptyList() }

    /** VANTAGE (SHW-48): kredity osoby → filmy dle role (režie/kamera/…) → karty. */
    private fun personRoleMovies(credits: TmdbPersonMovieCredits, role: PersonRole): List<TmdbSearchMovieItem> {
        val raw = if (role == PersonRole.ACTING) credits.cast
        else credits.crew.filter { roleMatches(role, it.department, it.job) }
        return raw.map { it.toMovieItem() }.distinctBy { it.id }
    }

    /**
     * PASSPORT (SHW-93) A1 — čitelný název pro TMDB seznamy (filmografie/studio). cs-CZ `title` u titulů
     * bez českého překladu spadne na originál (asijské písmo). Když je nějaký `title` ne-latinka, dotáhne
     * [enFetch] (tentýž seznam v en-US) a ne-latinkové tituly nahradí anglickým názvem (párování přes `id`).
     * Bez ne-latinkových titulů = žádný dotaz navíc (rychlá cesta).
     */
    private suspend fun withReadableTitles(
        cs: List<TmdbSearchMovieItem>,
        enFetch: suspend () -> List<TmdbSearchMovieItem>,
    ): List<TmdbSearchMovieItem> {
        if (cs.none { it.title?.looksNonLatin() == true }) return cs
        val en = runCatching { enFetch() }.getOrNull()?.associateBy { it.id } ?: return cs
        return cs.map { m ->
            if (m.title?.looksNonLatin() == true) {
                val enTitle = en[m.id]?.title?.takeIf { it.isNotBlank() && !it.looksNonLatin() }
                if (enTitle != null) m.copy(title = enTitle) else m
            } else m
        }
    }

    /** Sedí TMDB `job`/`department` kreditu na požadovanou [PersonRole]. */
    private fun roleMatches(role: PersonRole, department: String?, job: String?): Boolean {
        fun jobHas(vararg s: String) = job != null && s.any { job.contains(it, ignoreCase = true) }
        fun dept(d: String) = department.equals(d, ignoreCase = true)
        return when (role) {
            // Jen SKUTEČNÝ režisér — ne „First Assistant Director" / „Second Unit Director" (contains by je
            // pustil) ani celé oddělení Directing (script supervisor apod.). Přesný job.
            PersonRole.DIRECTING -> job.equals("Director", ignoreCase = true) || job.equals("Co-Director", ignoreCase = true)
            PersonRole.WRITING -> dept("Writing") || jobHas("Writer", "Screenplay", "Story", "Author")
            PersonRole.CINEMATOGRAPHY -> jobHas("Director of Photography", "Cinematograph") || dept("Camera")
            PersonRole.PRODUCING -> dept("Production") || jobHas("Producer")
            PersonRole.COMPOSING -> jobHas("Composer", "Music") || dept("Sound") && jobHas("Music")
            PersonRole.ACTING, PersonRole.GENERIC -> false
        }
    }

    private companion object {
        // PASSPORT (SHW-93) A1 — jazyk pro anglický fallback čitelného názvu.
        const val EN = "en-US"
    }
}
