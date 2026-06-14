package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.ui.CsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.CzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.looksCzech
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
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
) : ViewModel(), CsfdRatingProvider, CzechOverviewProvider {

    override suspend fun rating(imdbId: String?, tmdbId: Long?, title: String, year: Int?): Int? =
        csfd.getRating(imdbId.orEmpty(), tmdbId, title, year ?: 0)

    override suspend fun overview(
        imdbId: String?, tmdbId: Long?, title: String, year: Int?, fallback: String?,
    ): String? {
        // 1) Český TMDB překlad (jako detail) — jen když je opravdu česky.
        val tmdbCz = tmdbId?.let {
            runCatching { tmdb.fetchMovieTranslation(it, "cs")?.overview }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        if (looksCzech(tmdbCz)) return tmdbCz
        // 2) ČSFD popis (stejný zdroj jako detail; csfdId i přes tmdb/title/Wikidata).
        val csfdPlot = runCatching {
            csfd.getCsfdId(imdbId.orEmpty(), tmdbId, title, year ?: 0)?.let { csfd.getCzechPlot(it) }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (csfdPlot != null) return csfdPlot
        // 3) Jakýkoli TMDB cs překlad, jinak fallback z položky.
        return tmdbCz ?: fallback
    }
}
