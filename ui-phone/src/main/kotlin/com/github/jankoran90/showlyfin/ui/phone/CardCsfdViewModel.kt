package com.github.jankoran90.showlyfin.ui.phone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.ui.CsfdRatingProvider
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * CANVAS (SHW-47) B — poskytovatel ČSFD hodnocení pro karty (PosterCard). Wrappuje [CsfdRepository]
 * (líný resolve csfdId + scrape + prefs cache) a vystaví ho přes [CsfdRatingProvider] do
 * `LocalCsfdRatingProvider`, aby karty napříč appkou (Objevit/kolekce/filmografie/Oblíbení) mohly
 * líně dotáhnout ČSFD % bez protahování stavu skrz každý ViewModel.
 */
@HiltViewModel
class CardCsfdViewModel @Inject constructor(
    private val csfd: CsfdRepository,
) : ViewModel(), CsfdRatingProvider {
    override suspend fun rating(imdbId: String?, tmdbId: Long?, title: String, year: Int?): Int? =
        csfd.getRating(imdbId.orEmpty(), tmdbId, title, year ?: 0)
}
