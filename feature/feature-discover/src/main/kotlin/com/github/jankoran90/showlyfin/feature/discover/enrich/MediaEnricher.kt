package com.github.jankoran90.showlyfin.feature.discover.enrich

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbMovieDetails
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbShowDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COUCH (SHW-88) — SDÍLENÉ TMDB obohacení [MediaItem] pro objevovací plochy (domov / Objevit / Trakt).
 * Sjednocuje dřív trojnásobně duplikovanou logiku (TvHomeViewModel.enrich, TraktRowLoader.enrichOne,
 * DiscoverViewModel.enrich).
 *
 * Řeší:
 * 1. **poster/backdrop** z TMDB details,
 * 2. **CZ titulek + popis** — preferuje explicitní `cs` translations, jinak `details` volané v `cs-CZ`
 *    (TMDB vrací česky-NEBO-originál, nikdy prázdné — opravuje niche/foreign tituly, kde `cs` translations
 *    má prázdný title a dřív padly do angličtiny),
 * 3. **žánry** (pro žánrovou pojistku věkového filtru),
 * 4. **věková certifikace** ([withCertification] = true, jen když je aktivní věkový strop profilu — jinak
 *    zbytečné síťové volání navíc).
 */
@Singleton
class MediaEnricher @Inject constructor(
    private val tmdb: TmdbRemoteDataSource,
) {
    // CINEMATHEQUE (SHW-90): strop paralelních TMDB dotazů. Enrich měl neomezenou paralelitu — Filmotéka se
    // stovkami titulů by TMDB rate-limit rozstřelila. 6 souběžných + cache dekorátor drží zátěž rozumnou.
    private val semaphore = Semaphore(6)

    suspend fun enrich(items: List<MediaItem>, withCertification: Boolean): List<MediaItem> = coroutineScope {
        items.map { item -> async { semaphore.withPermit { enrichOne(item, withCertification) } } }.awaitAll()
    }

    suspend fun enrichOne(item: MediaItem, withCertification: Boolean): MediaItem = coroutineScope {
        val tmdbId = item.tmdbId ?: return@coroutineScope item
        if (item.type == MediaType.SHOW) {
            val detailsD = async { runCatching { tmdb.fetchShowDetails(tmdbId, LANG) }.getOrNull() }
            val trD = async { runCatching { tmdb.fetchShowTranslation(tmdbId, "cs") }.getOrNull() }
            val ageD = async { if (withCertification) runCatching { tmdb.fetchShowCertificationAge(tmdbId) }.getOrNull() else null }
            val details = detailsD.await(); val tr = trD.await()
            val czOverview = firstNonBlank(tr?.overview, details?.overview)
            item.copy(
                posterPath = details?.poster_path ?: item.posterPath,
                backdropPath = details?.backdrop_path ?: item.backdropPath,
                titleCz = firstNonBlank(tr?.name, details?.name) ?: item.titleCz,
                overviewCz = czOverview ?: item.overviewCz,
                // CONVERGE (SHW-97): EN fallback popisu, aby immersive header (fallbackuje na `overview`) nebyl
                // prázdný u titulů bez CZ popisu (Local Hero, Kikudžiro…) — parita s kartou. Extra TMDB fetch
                // JEN když CZ i stávající popis chybí.
                overview = firstNonBlank(item.overview, enOverviewIfMissing(czOverview, item.overview, tmdbId, MediaType.SHOW)) ?: item.overview,
                genres = details?.genres?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: item.genres,
                certificationAge = ageD.await() ?: item.certificationAge,
                originCountries = countriesOfShow(details) ?: item.originCountries,
                originalTitle = details?.original_name?.takeIf { it.isNotBlank() } ?: item.originalTitle,
            )
        } else {
            val detailsD = async { runCatching { tmdb.fetchMovieDetails(tmdbId, LANG) }.getOrNull() }
            val trD = async { runCatching { tmdb.fetchMovieTranslation(tmdbId, "cs") }.getOrNull() }
            val ageD = async { if (withCertification) runCatching { tmdb.fetchMovieCertificationAge(tmdbId) }.getOrNull() else null }
            val details = detailsD.await(); val tr = trD.await()
            val czOverview = firstNonBlank(tr?.overview, details?.overview)
            item.copy(
                posterPath = details?.poster_path ?: item.posterPath,
                backdropPath = details?.backdrop_path ?: item.backdropPath,
                titleCz = firstNonBlank(tr?.title, details?.title) ?: item.titleCz,
                overviewCz = czOverview ?: item.overviewCz,
                // CONVERGE (SHW-97): EN fallback popisu — viz SHOW větev. Parita immersive headeru s kartou.
                overview = firstNonBlank(item.overview, enOverviewIfMissing(czOverview, item.overview, tmdbId, MediaType.MOVIE)) ?: item.overview,
                genres = details?.genres?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: item.genres,
                certificationAge = ageD.await() ?: item.certificationAge,
                originCountries = countriesOfMovie(details) ?: item.originCountries,
                originalTitle = details?.original_title?.takeIf { it.isNotBlank() } ?: item.originalTitle,
            )
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    /**
     * CONVERGE (SHW-97) — EN popis jako fallback JEN pro tituly bez českého i stávajícího popisu (niche/starší
     * filmy, kde TMDB `cs-CZ` vrací prázdný overview). Vrací null, když popis už je → žádný zbytečný fetch
     * navíc pro drtivou většinu (populárních) titulů. Cache dekorátor drží opakované dotazy levné.
     */
    private suspend fun enOverviewIfMissing(czOverview: String?, existing: String?, tmdbId: Long, type: MediaType): String? {
        if (!czOverview.isNullOrBlank() || !existing.isNullOrBlank()) return null
        return runCatching {
            if (type == MediaType.SHOW) tmdb.fetchShowDetails(tmdbId, EN)?.overview
            else tmdb.fetchMovieDetails(tmdbId, EN)?.overview
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** CINEMATHEQUE (SHW-90) F2 — země SHOW: `origin_country` ∪ `production_countries.iso_3166_1`. */
    private fun countriesOfShow(details: TmdbShowDetails?): List<String>? {
        if (details == null) return null
        return normalizeCountries(
            details.origin_country.orEmpty() + details.production_countries.orEmpty().mapNotNull { it.iso_3166_1 }
        )
    }

    /** CINEMATHEQUE (SHW-90) F2 — země MOVIE: `production_countries.iso_3166_1`. */
    private fun countriesOfMovie(details: TmdbMovieDetails?): List<String>? {
        if (details == null) return null
        return normalizeCountries(details.production_countries.orEmpty().mapNotNull { it.iso_3166_1 })
    }

    /** Uppercase + distinct; prázdné → null (nepřepisuj stávající hodnotu prázdnem). */
    private fun normalizeCountries(codes: List<String>): List<String>? =
        codes.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.uppercase() }
            .distinct()
            .takeIf { it.isNotEmpty() }

    private companion object {
        const val LANG = "cs-CZ"
        const val EN = "en-US"
    }
}
