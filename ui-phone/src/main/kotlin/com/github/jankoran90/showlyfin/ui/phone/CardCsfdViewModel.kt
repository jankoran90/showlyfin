package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.CzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.DirectorProvider
import com.github.jankoran90.showlyfin.core.ui.looksCzech
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Poskytovatel obohacení karet/řádků (ČSFD + český popis), wrappuje [CsfdRepository] a TMDB.
 *
 * - [CsfdRatingProvider] (CANVAS B): líné ČSFD % pro karty `PosterCard` (Objevit/kolekce/Oblíbení).
 * - [CzechOverviewProvider]: líný ČESKÝ popis pro řádky `MediaRow` (Objevit/Chci vidět/Historie/Na RD)
 *   — STEJNÝ fallback jako detail filmu: TMDB cs překlad (looksCzech) → ČSFD popis → jakýkoli TMDB
 *   → fallback z položky. Drží to v jednom místě, aby seznamy nemusely protahovat stav skrz každý VM.
 */
@HiltViewModel
class CardCsfdViewModel @Inject constructor(
    private val csfd: CsfdRepository,
    private val tmdb: TmdbRemoteDataSource,
) : ViewModel(), CsfdRatingProvider, CzechOverviewProvider, DirectorProvider {

    override suspend fun rating(imdbId: String?, tmdbId: Long?, title: String, year: Int?): Int? =
        csfd.getRating(imdbId.orEmpty(), tmdbId, title, year ?: 0)

    /**
     * TENFOOT: líné jméno režiséra pro immersive header (jen pro fokusovaný titul). Stejný zdroj
     * a pravidlo rozpoznání jako detail filmu (`DetailViewModel.loadCast`): TMDB credits → z CREW
     * ber jen přesný job Director/Co-Director. Vrať max 2 (joinnuto ", "); null když nic / bez id.
     */
    override suspend fun director(
        imdbId: String?, tmdbId: Long?, type: MediaType, title: String, year: Int?,
    ): String? {
        val id = tmdbId ?: return null
        val people = runCatching {
            if (type == MediaType.MOVIE) tmdb.fetchMoviePeople(id) else tmdb.fetchShowPeople(id)
        }.getOrNull() ?: return null
        val crew = people[TmdbPerson.Type.CREW].orEmpty()
        fun isDirector(p: TmdbPerson): Boolean {
            val jobs = listOfNotNull(p.job) + p.jobs?.mapNotNull { it.job }.orEmpty()
            return jobs.any { it.equals("Director", true) || it.equals("Co-Director", true) }
        }
        val names = crew.filter { it.id > 0 && isDirector(it) }
            .distinctBy { it.id }
            .mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            .take(2)
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    override suspend fun overview(
        imdbId: String?, tmdbId: Long?, title: String, titleCz: String?, year: Int?, fallback: String?,
    ): String? {
        // 1) Český TMDB překlad (jako detail) — jen když je opravdu česky.
        val translation = tmdbId?.let {
            runCatching { tmdb.fetchMovieTranslation(it, "cs") }.getOrNull()
        }
        val tmdbCz = translation?.overview?.takeIf { it.isNotBlank() }
        // 2) ČSFD popis (stejný zdroj jako detail). csfdId resolvuj jako detail loadCsfd:
        //    nejdřív ČESKÝ název (TMDB překlad → titleCz z položky) → originální → bez názvu.
        //    U cizojazyčných filmů ČSFD title-search vyžaduje český název (anglický nenajde).
        val titles = listOfNotNull(
            translation?.title?.takeIf { it.isNotBlank() },
            titleCz?.takeIf { it.isNotBlank() && it != title },
            title.takeIf { it.isNotBlank() },
        )
        var csfdId: Long? = null
        if (!looksCzech(tmdbCz)) {
            for (t in titles) {
                csfdId = runCatching { csfd.getCsfdId(imdbId.orEmpty(), tmdbId, t, year ?: 0) }.getOrNull()
                if (csfdId != null) break
            }
            if (csfdId == null) {
                csfdId = runCatching { csfd.getCsfdId(imdbId.orEmpty(), tmdbId, "", year ?: 0) }.getOrNull()
            }
        }
        val csfdPlot = csfdId?.let {
            runCatching { csfd.getCzechPlot(it) }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        // Priorita jako detail: český TMDB → ČSFD → jakýkoli TMDB → fallback.
        if (looksCzech(tmdbCz)) return tmdbCz
        if (csfdPlot != null) return csfdPlot
        return tmdbCz ?: fallback
    }
}
