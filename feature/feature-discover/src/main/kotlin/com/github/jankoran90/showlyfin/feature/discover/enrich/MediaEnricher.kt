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
 * 2. **CZ titulek + popis (KANON 2026-08-30: česky → anglicky → originál)** — `titleCz` jen reálná
 *    čeština: explicitní `cs` translation, nebo `details` v `cs-CZ` POUZE u českého originálu
 *    (`original_language == "cs"`). U nepřeložených titulů TMDB vrací v cs-CZ cizopísmný originál
 *    (夜明けのすべて) — ten jde do `titleEn` rungu: EN překlad z `translations.en` (dotah jen když
 *    čeština chybí). Popis dál CZ→EN→fallback jako dřív.
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

    suspend fun enrichOne(item0: MediaItem, withCertification: Boolean): MediaItem = coroutineScope {
        // VLTAVA (SHW-110) F6b — titul z ČT iVysílání nese SYNTETICKOU identitu (`ctvid:<sidp>`), na TMDB
        // ho nikdo nenajde (proto vlastní cesta vůbec vznikla). Bez tohohle skipu by za každou ČT položku
        // odešel zbytečný TMDB dotaz. Vlastní data (název, plakát z ČT) si položka nese sama.
        if (item0.imdbId?.startsWith("ctvid:") == true) return@coroutineScope item0
        // CELLULOID (SHW-98): položky jen s imdb (asijské/art-house z JF knihovny, např. „The Taste of Tea")
        // dřív z enrichmentu rovnou vypadly → bez plakátu i českého názvu = prázdné karty a anglické tituly.
        // Dohledej tmdbId z imdb, ať dostanou poster + titleCz (řadí se pak taky dle CZ názvu).
        val tmdbId = item0.tmdbId
            ?: item0.imdbId?.takeIf { it.isNotBlank() }
                ?.let { runCatching { tmdb.findTmdbIdByImdb(it, item0.type == MediaType.SHOW) }.getOrNull() }
            ?: return@coroutineScope item0
        val item = if (item0.tmdbId == null) item0.copy(tmdbId = tmdbId) else item0
        if (item.type == MediaType.SHOW) {
            val detailsD = async { runCatching { tmdb.fetchShowDetails(tmdbId, LANG) }.getOrNull() }
            val trD = async { runCatching { tmdb.fetchShowTranslation(tmdbId, "cs") }.getOrNull() }
            val ageD = async { if (withCertification) runCatching { tmdb.fetchShowCertificationAge(tmdbId) }.getOrNull() else null }
            val details = detailsD.await(); val tr = trD.await()
            // Stejná past jako u titleCz níž: cs-CZ `details.overview` u nepřeloženého titulu umí
            // vrátit popis v PŮVODNÍM jazyce (ne češtině) — fallback jen u českého originálu.
            val czOverview = firstNonBlank(
                tr?.overview,
                details?.overview?.takeIf { details.original_language.equals("cs", true) },
            )
            // KANON (user 2026-08-30): titleCz = POUZE reálná čeština. Dřív tady byl fallback
            // `details?.name` (cs-CZ) — u nepřeložených titulů TMDB vrací CIZOPISMÝ originál, který
            // se uložil do titleCz, zablokoval líné dorovnávání řádků (guard „titleCz znám") a v seznamech
            // svítilo 夜明けのすべて místo All the Long Nights. Details rung zůstává JEN pro české originály
            // (český seriál nemusí mít „cs translation" — jeho originál česky je ten správný název).
            val csTitle = tr?.name?.takeIf { it.isNotBlank() }
                ?: details?.name?.takeIf { it.isNotBlank() && details.original_language.equals("cs", true) }
            // EN rung politiky „česky → anglicky → originál": tahá se JEN když čeština chybí
            // (u přeložených titulů vyhrává CZ a dotaz by byl zbytečný).
            val enTitle = if (csTitle == null) {
                runCatching { tmdb.fetchShowTranslation(tmdbId, "en") }.getOrNull()
                    ?.name?.takeIf { it.isNotBlank() }
            } else null
            item.copy(
                year = item.year ?: details?.first_air_date?.take(4)?.toIntOrNull(),
                posterPath = details?.poster_path ?: item.posterPath,
                backdropPath = details?.backdrop_path ?: item.backdropPath,
                titleCz = csTitle ?: item.titleCz,
                titleEn = enTitle ?: item.titleEn,
                overviewCz = czOverview ?: item.overviewCz,
                genres = details?.genres?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: item.genres,
                certificationAge = ageD.await() ?: item.certificationAge,
                originCountries = countriesOfShow(details) ?: item.originCountries,
                originalTitle = details?.original_name?.takeIf { it.isNotBlank() } ?: item.originalTitle,
                // MERIDIAN (SHW-119): stopáž se veze s detailem, který enrich tahá tak jako tak —
                // žádný dotaz navíc. U seriálu = typická délka epizody.
                runtimeMinutes = details?.episode_run_time?.firstOrNull { it > 0 } ?: item.runtimeMinutes,
            )
        } else {
            val detailsD = async { runCatching { tmdb.fetchMovieDetails(tmdbId, LANG) }.getOrNull() }
            val trD = async { runCatching { tmdb.fetchMovieTranslation(tmdbId, "cs") }.getOrNull() }
            val ageD = async { if (withCertification) runCatching { tmdb.fetchMovieCertificationAge(tmdbId) }.getOrNull() else null }
            val details = detailsD.await(); val tr = trD.await()
            val czOverview = firstNonBlank(
                tr?.overview,
                details?.overview?.takeIf { details.original_language.equals("cs", true) },
            )
            // KANON — viz SHOW větev: titleCz jen reálná čeština (cs translation, nebo cs-CZ details
            // u českého originálu), jinak EN překlad do titleEn. Cizopísmný originál do titleCz NESMÍ.
            val csTitle = tr?.title?.takeIf { it.isNotBlank() }
                ?: details?.title?.takeIf { it.isNotBlank() && details.original_language.equals("cs", true) }
            val enTitle = if (csTitle == null) {
                runCatching { tmdb.fetchMovieTranslation(tmdbId, "en") }.getOrNull()
                    ?.title?.takeIf { it.isNotBlank() }
            } else null
            item.copy(
                year = item.year ?: details?.release_date?.take(4)?.toIntOrNull(),
                posterPath = details?.poster_path ?: item.posterPath,
                backdropPath = details?.backdrop_path ?: item.backdropPath,
                titleCz = csTitle ?: item.titleCz,
                titleEn = enTitle ?: item.titleEn,
                overviewCz = czOverview ?: item.overviewCz,
                genres = details?.genres?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: item.genres,
                certificationAge = ageD.await() ?: item.certificationAge,
                originCountries = countriesOfMovie(details) ?: item.originCountries,
                originalTitle = details?.original_title?.takeIf { it.isNotBlank() } ?: item.originalTitle,
                runtimeMinutes = details?.runtime?.takeIf { it > 0 } ?: item.runtimeMinutes,
            )
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

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

    internal companion object {
        /** Jazyk TMDB dotazů. SDÍLENÝ — kdo tahá details mimo enrich (např. [FilmotekaCollectionResolver]),
         * musí použít TENTÝŽ, jinak minie cache (je keyed `(id, jazyk)`) a dotazy se zdvojí. */
        const val LANG = "cs-CZ"
    }
}
