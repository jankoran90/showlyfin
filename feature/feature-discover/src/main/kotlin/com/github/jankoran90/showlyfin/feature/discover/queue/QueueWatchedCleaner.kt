package com.github.jankoran90.showlyfin.feature.discover.queue

import com.github.jankoran90.showlyfin.core.db.repository.PlayQueueRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAMPA (SHW-121) — „po shlednuti odebrat ze seznamu" (user 2026-08-28).
 *
 * ZÁMĚRNĚ se neptáme přehrávače, ale **Traktu**: uživatel kouká i mimo appku (cast na Zenbook + mpv,
 * web, Jellyfin na TV) — hák v našem přehrávači by většinu jeho sledování minul. Trakt je místo, kam
 * všechny tyhle cesty zhlédnutí hlásí, takže fronta se uklidí bez ohledu na to, kde se koukalo.
 *
 * Pravidla podle dohody s userem (*„serial ok muzem to tak udelat jak rikas"*):
 *  - **film** — je mezi zhlédnutými → pryč z fronty;
 *  - **seriál** — až když **není co pustit dál**, tedy počet zhlédnutých dílů dosáhl počtu dílů podle
 *    TMDB. Jeden dokoukaný díl frontu nemaže.
 *
 * 🔒 **Neúspěšný dotaz NIKDY nemaže** ([[feedback_empty_from_error_is_not_empty_result]]): prázdná
 * odpověď z chyby by vypadala jako „nic nezhlédnuto"… a u seriálu by naopak chybějící TMDB údaj mohl
 * vyrobit `0 >= 0` a frontu vysypat. Obojí je pod tím ošetřené a raději se nechá položka ve frontě.
 */
@Singleton
class QueueWatchedCleaner @Inject constructor(
    private val queue: PlayQueueRepository,
    private val trakt: AuthorizedTraktRemoteDataSource,
    private val tmdb: TmdbRemoteDataSource,
) {
    suspend fun cleanup() {
        val items = queue.snapshot()
        if (items.isEmpty()) return

        val (movies, shows) = coroutineScope {
            val m = async { runCatching { trakt.fetchSyncWatchedMovies() }.getOrNull() }
            val s = async { runCatching { trakt.fetchSyncWatchedShows() }.getOrNull() }
            m.await() to s.await()
        }
        // null = dotaz selhal (offline, vypršelý token). Mlčky NEMAZAT — prázdno z chyby není
        // „nic jsem neviděl"; přesně tenhle vzorec 2026-08-27 smazal 1004 hodnocení.
        if (movies == null || shows == null) {
            Timber.w("[RAMPA] úklid fronty přeskočen — Trakt neodpověděl (fronta zůstává beze změny)")
            return
        }

        val watchedMovieIds = movies.mapNotNull { it.getTmdbId() }.toSet()
        for (item in items.filter { it.kind == FavoriteKind.QUEUE_MOVIE }) {
            if (item.id in watchedMovieIds) {
                Timber.i("[RAMPA] film dokoukán → pryč z fronty: %s (tmdb %d)", item.name, item.id)
                queue.remove(item.id, isShow = false)
            }
        }

        for (item in items.filter { it.kind == FavoriteKind.QUEUE_SHOW }) {
            val entry = shows.firstOrNull { it.getTmdbId() == item.id } ?: continue
            val seen = entry.seasons.orEmpty()
                // Speciálka (season 0) se do „dokoukáno" nepočítá — TMDB ji v počtu dílů taky nemá.
                .filter { (it.number ?: 0) >= 1 }
                .sumOf { it.episodes?.size ?: 0 }
            if (seen <= 0) continue
            val total = runCatching { tmdb.fetchShowDetails(item.id)?.number_of_episodes }.getOrNull()
            // Neznámý počet dílů → nevíme, jestli je co pustit dál. Nech ve frontě.
            if (total == null || total <= 0) continue
            if (seen >= total) {
                Timber.i("[RAMPA] seriál dokoukán (%d/%d dílů) → pryč z fronty: %s", seen, total, item.name)
                queue.remove(item.id, isShow = true)
            }
        }
    }
}
