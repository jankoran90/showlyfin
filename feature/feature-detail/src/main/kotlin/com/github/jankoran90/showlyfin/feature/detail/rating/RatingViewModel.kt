package com.github.jankoran90.showlyfin.feature.detail.rating

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.ui.RatingDialog
import com.github.jankoran90.showlyfin.core.ui.RatingTarget
import com.github.jankoran90.showlyfin.core.ui.UserRatingProvider
import com.github.jankoran90.showlyfin.core.ui.cardRatingKey
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.request.RatingRequest
import com.github.jankoran90.showlyfin.data.trakt.model.request.RatingRequestIds
import com.github.jankoran90.showlyfin.data.trakt.model.request.RatingRequestValue
import com.github.jankoran90.showlyfin.data.uploader.UserRating
import com.github.jankoran90.showlyfin.data.uploader.UserRatingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BESPOKE (SHW-95) F3 — sdílený mozek hvězdičkového hodnocení. Implementuje [UserRatingProvider], takže se
 * v shellu poskytne jednou přes `LocalUserRatingProvider` a všechny karty + detail z něj čtou odznak
 * (`rememberCardRating`) a spouští dialog ([requestRate]).
 *
 * **Lokální hodnocení = Trakt hodnocení (obousměrně):**
 *  - Zobrazený odznak = **sjednocení** lokálního [UserRatingStore] ⊎ Trakt hodnocení ([fetchMoviesRatings]/
 *    [fetchShowsRatings]) → hvězdu vidíš i u filmu, který jsi hodnotil jen na Traktu.
 *  - Ohodnocení/úprava = zápis do lokálního storu (sync přes náš backend) **i do Traktu** ([postRatings]) hned.
 *  - Odebrání = smazání z lokálu **i z Traktu** ([postRemoveRatings]).
 * Kurátor si signál bere z lokálního storu (loved/avoid) i z Traktu — obě cesty.
 */
@HiltViewModel
class RatingViewModel @Inject constructor(
    private val userRatingStore: UserRatingStore,
    private val trakt: AuthorizedTraktRemoteDataSource,
    private val profileRepository: ProfileRepository,
) : ViewModel(), UserRatingProvider {

    /** Hodnocení dohledaná z Traktu AKTIVNÍHO profilu (klíč [cardRatingKey] → hvězdy). Doplňují lokál v [ratings]. */
    private val _traktRatings = MutableStateFlow<Map<String, Int>>(emptyMap())

    override val ratings: StateFlow<Map<String, Int>> = combine(
        userRatingStore.items.map { list ->
            list.mapNotNull { r -> cardRatingKey(r.tmdbId, r.imdbId)?.let { it to r.stars } }.toMap()
        },
        _traktRatings,
    ) { local, trakt -> trakt + local }   // lokál přebíjí Trakt při shodě klíče
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _pending = MutableStateFlow<RatingTarget?>(null)
    val pending: StateFlow<RatingTarget?> = _pending.asStateFlow()

    private var lastProfileId: Long? = null

    init {
        // PER-PROFIL izolace (různé profily = různé Trakt účty): přepnutí profilu OKAMŽITĚ zahodí Trakt
        // hodnocení předchozího účtu (žádný vizuální ani datový konflikt), dorovná lokální store a načte
        // hodnocení Traktu NOVÉHO profilu (aktivní token už patří jemu). Zápisy jdou vždy pod aktivní token.
        profileRepository.activeProfile
            .onEach { profile ->
                if (profile?.id != lastProfileId) {
                    lastProfileId = profile?.id
                    _traktRatings.value = emptyMap()
                    userRatingStore.refresh()
                    refreshTraktRatings()
                }
            }
            .launchIn(viewModelScope)
    }

    /** Načti hodnocení z Traktu do [_traktRatings] (sjednocení s lokálem pro odznaky). */
    private fun refreshTraktRatings() {
        viewModelScope.launch {
            val map = runCatching {
                val movies = trakt.fetchMoviesRatings()
                    .mapNotNull { r -> cardRatingKey(r.movie.ids.tmdb, r.movie.ids.imdb)?.let { it to r.rating } }
                val shows = trakt.fetchShowsRatings()
                    .mapNotNull { r -> cardRatingKey(r.show.ids.tmdb, r.show.ids.imdb)?.let { it to r.rating } }
                (movies + shows).toMap()
            }.onFailure { Timber.w(it, "[BESPOKE] načtení Trakt hodnocení selhalo") }.getOrElse { emptyMap() }
            _traktRatings.value = map
        }
    }

    override fun requestRate(target: RatingTarget) { _pending.value = target }

    fun dismiss() { _pending.value = null }

    /** Ulož hvězdy (1–10): lokální store + zrcadlení do Traktu (přidání/úprava). */
    fun apply(stars: Int) {
        val t = _pending.value ?: return
        _pending.value = null
        if (stars !in 1..10) return
        userRatingStore.rate(
            UserRating(
                tmdbId = t.tmdbId, imdbId = t.imdbId, traktId = t.traktId,
                type = if (t.isShow) "SHOW" else "MOVIE", title = t.title, year = t.year, stars = stars,
            )
        )
        if (t.traktId > 0L) postToTrakt(t, stars, remove = false)
    }

    /** Zruš hodnocení: lokální store + odebrání z Traktu. */
    fun clear() {
        val t = _pending.value ?: return
        _pending.value = null
        userRatingStore.clear(t.tmdbId, t.imdbId)
        cardRatingKey(t.tmdbId, t.imdbId)?.let { key -> _traktRatings.value = _traktRatings.value - key }
        if (t.traktId > 0L) postToTrakt(t, 0, remove = true)
    }

    private fun postToTrakt(t: RatingTarget, stars: Int, remove: Boolean) {
        val ratedAt = if (remove) "" else java.time.Instant.now().toString()
        val value = RatingRequestValue(rating = stars, rated_at = ratedAt, ids = RatingRequestIds(t.traktId))
        val request = if (t.isShow) RatingRequest(shows = listOf(value)) else RatingRequest(movies = listOf(value))
        viewModelScope.launch {
            runCatching { if (remove) trakt.postRemoveRatings(request) else trakt.postRatings(request) }
                .onFailure { Timber.w(it, "[BESPOKE] zrcadlení hodnocení do Traktu selhalo (remove=$remove)") }
        }
    }
}

/**
 * Host hvězdičkového dialogu — vlož do shellu (telefon i TV) SPOLU s `LocalUserRatingProvider provides vm`.
 * Zobrazí [RatingDialog] nad obsahem, když je rozpracovaný cíl.
 */
@Composable
fun RatingDialogHost(vm: RatingViewModel) {
    val target by vm.pending.collectAsStateWithLifecycle()
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    target?.let { t ->
        RatingDialog(
            target = t,
            current = cardRatingKey(t.tmdbId, t.imdbId)?.let { ratings[it] },
            onRate = { vm.apply(it) },
            onClear = { vm.clear() },
            onDismiss = { vm.dismiss() },
        )
    }
}
