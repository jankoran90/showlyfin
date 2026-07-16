package com.github.jankoran90.showlyfin.feature.detail.rating

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BESPOKE (SHW-95) F3 — sdílený mozek hvězdičkového hodnocení. Implementuje [UserRatingProvider], takže se
 * v shellu poskytne jednou přes [LocalUserRatingProvider] a všechny karty + detail z něj čtou odznak
 * ([com.github.jankoran90.showlyfin.core.ui.rememberCardRating]) a spouští dialog ([requestRate]).
 * Zápis = lokální [UserRatingStore] (primární, sync přes backend) + **zrcadlení do Traktu** (`postRatings`,
 * jen když má titul traktId). Kurátor si signál bere ze [UserRatingStore] (loved/avoid) + z Traktu.
 */
@HiltViewModel
class RatingViewModel @Inject constructor(
    private val userRatingStore: UserRatingStore,
    private val trakt: AuthorizedTraktRemoteDataSource,
) : ViewModel(), UserRatingProvider {

    override val ratings: StateFlow<Map<String, Int>> = userRatingStore.items
        .map { list -> list.mapNotNull { r -> cardRatingKey(r.tmdbId, r.imdbId)?.let { it to r.stars } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _pending = MutableStateFlow<RatingTarget?>(null)
    val pending: StateFlow<RatingTarget?> = _pending.asStateFlow()

    override fun requestRate(target: RatingTarget) { _pending.value = target }

    fun dismiss() { _pending.value = null }

    /** Ulož hvězdy (1–10) pro rozpracovaný cíl: lokální store + zrcadlení do Traktu. */
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
        if (t.traktId > 0L) mirrorToTrakt(t, stars)
    }

    /** Zruš hodnocení rozpracovaného cíle (lokálně; Trakt necháváme být — nedestruktivní). */
    fun clear() {
        val t = _pending.value ?: return
        _pending.value = null
        userRatingStore.clear(t.tmdbId, t.imdbId)
    }

    private fun mirrorToTrakt(t: RatingTarget, stars: Int) {
        val ratedAt = java.time.Instant.now().toString()
        val value = RatingRequestValue(rating = stars, rated_at = ratedAt, ids = RatingRequestIds(t.traktId))
        val request = if (t.isShow) RatingRequest(shows = listOf(value)) else RatingRequest(movies = listOf(value))
        viewModelScope.launch {
            runCatching { trakt.postRatings(request) }
                .onFailure { Timber.w(it, "[BESPOKE] zrcadlení hodnocení do Traktu selhalo") }
        }
    }
}

/**
 * Host hvězdičkového dialogu — vlož do shellu (telefon i TV) SPOLU s `LocalUserRatingProvider provides vm`.
 * Zobrazí [RatingDialog] nad obsahem, když je rozpracovaný cíl. Aktuální hvězdy dohledá ze storu (přes provider mapu).
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
