package com.github.jankoran90.showlyfin.feature.discover.trakt

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportItem
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COUCH — jednorázový import zhlédnuté Jellyfin historie AKTIVNÍHO profilu do jeho Trakt účtu
 * (`sync/history`). Běží pod tokenem aktivního profilu (per-profil Trakt) → deti historie jde na deti
 * Trakt, dospělý na dospělý. Rozsah: FILMY + celé SERIÁLY (epizodová granularita = follow-up, viz
 * [JellyfinLibraryService.getWatchedForTraktSync]). Idempotence: pokud Trakt už nějakou watched historii
 * má, přeskočí (pokud `force=false`) → opakované spuštění nezdvojí.
 */
@Singleton
class TraktHistoryImporter @Inject constructor(
    private val jellyfin: JellyfinLibraryService,
    private val trakt: AuthorizedTraktRemoteDataSource,
    private val profileRepository: ProfileRepository,
) {
    sealed interface Result {
        /** Nahráno; kolik filmů + seriálů. */
        data class Success(val movies: Int, val shows: Int) : Result
        /** Trakt už historii má → přeskočeno (idempotence). Uživatel může vynutit `force`. */
        data object AlreadyHasHistory : Result
        /** Aktivní profil nemá Jellyfin identitu (userId). */
        data object NoJellyfinUser : Result
        /** V Jellyfinu nejsou žádné zhlédnuté položky. */
        data object NothingWatched : Result
        data class Error(val message: String) : Result
    }

    /** 32-hex → pomlčková UUID forma (JF ukládá bez pomlček, `UUID.fromString` je vyžaduje). */
    private fun toUuid(raw: String): UUID? {
        val t = raw.trim()
        val dashed = if (t.length == 32 && t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            "${t.substring(0, 8)}-${t.substring(8, 12)}-${t.substring(12, 16)}-${t.substring(16, 20)}-${t.substring(20)}"
        } else {
            t
        }
        return runCatching { UUID.fromString(dashed) }.getOrNull()
    }

    suspend fun importActiveProfileWatched(force: Boolean = false): Result {
        val profile = profileRepository.activeProfile.value ?: return Result.Error("Žádný aktivní profil")
        val uuid = profile.jellyfinUserId.takeIf { it.isNotBlank() }?.let { toUuid(it) }
            ?: return Result.NoJellyfinUser

        return runCatching {
            // Idempotence: aktivní profil = aktivní Trakt token; pokud už má watched, nepřepisuj.
            if (!force) {
                val hasHistory = runCatching { trakt.fetchSyncWatchedMovies() }.getOrNull().orEmpty().isNotEmpty() ||
                    runCatching { trakt.fetchSyncWatchedShows() }.getOrNull().orEmpty().isNotEmpty()
                if (hasHistory) return Result.AlreadyHasHistory
            }

            val watched = jellyfin.getWatchedForTraktSync(uuid)
            val movieItems = watched.movies.mapNotNull { SyncExportItem.fromIds(null, it.tmdb, it.imdb) }
            val showItems = watched.shows.mapNotNull { SyncExportItem.fromIds(null, it.tmdb, it.imdb) }
            if (movieItems.isEmpty() && showItems.isEmpty()) return Result.NothingWatched

            // Batchuj po 100 — velké dávky Trakt zvládne, ale menší dávky jsou robustnější (timeout/retry).
            movieItems.chunked(BATCH).forEach { trakt.postSyncWatched(SyncExportRequest(movies = it)) }
            showItems.chunked(BATCH).forEach { trakt.postSyncWatched(SyncExportRequest(shows = it)) }
            Timber.i("[COUCH] Trakt import hotovo: movies=${movieItems.size} shows=${showItems.size} profil='${profile.name}'")
            Result.Success(movieItems.size, showItems.size)
        }.getOrElse { e ->
            Timber.w(e, "[COUCH] Trakt import selhal")
            Result.Error(e.message ?: "Neznámá chyba")
        }
    }

    private companion object {
        const val BATCH = 100
    }
}
