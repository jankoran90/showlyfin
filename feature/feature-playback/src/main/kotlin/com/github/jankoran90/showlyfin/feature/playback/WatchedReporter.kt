package com.github.jankoran90.showlyfin.feature.playback

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportItem
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * CURTAIN (SHW-109) — „dokoukáno" na jednom místě.
 *
 * Dosud showlyfin dokoukání NEHLÁSIL nikam: přehrávání jede přes statický `/Videos/{id}/stream?static=true`,
 * takže ani Jellyfin sám nic neoznačí, a `markPlayed` měl jediného volajícího (long-press v detailu). Epizoda
 * proto zůstala „nezhlédnutá" a odznak „Pokračovat" (= první nezhlédnutá) visel na tomtéž dílu i po dokoukání
 * (user 2026-07-27, Bluey S3E2).
 *
 * Práh [PlayerPrefs.MARK_WATCHED_PCT_KEY] (default 85 %) řeší to, že se závěrečné titulky nedokoukávají —
 * čekat na úplný konec by znamenalo, že se skoro nic nikdy neoznačí.
 *
 * Hlásíme:
 *  - **Jellyfin** (`markPlayed`) u položky z knihovny — tam se to projeví fajfkou i posunem „Pokračovat".
 *  - **Trakt** (`sync/history`) u FILMU ze streamu, a jen když si to user zapne (default vyp — Trakt umí při
 *    zápisu do historie sám odebrat film z „Chci vidět", což by měnilo obsah Filmotéky).
 *
 * [reported] je paměť v rámci procesu, ať tikající přehrávač nebombarduje server tímtéž hlášením dokola.
 */
@Singleton
class WatchedReporter @Inject constructor(
    private val jellyfin: JellyfinLibraryService,
    private val trakt: AuthorizedTraktRemoteDataSource,
    private val videoResumeStore: VideoResumeStore,
    // VLTAVA F6c — dokoukaný ČT díl nemá kam nahlásit (není v Jellyfinu ani na Traktu), ale řada
    // „Další díly" ho musí umět přeskočit → vlastní lehká paměť.
    private val ctvWatchedStore: com.github.jankoran90.showlyfin.core.domain.resume.CtvWatchedStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {

    /**
     * Co se právě přehrává. `jellyfinItemId` = položka knihovny (u seriálu už DOŘEŠENÁ epizoda, ne id
     * seriálu), `imdbId` + `isEpisode` = stream mimo knihovnu, `resumeKey`/`externalResumeKey` = klíče
     * lokální pozice, které při dokoukání zahazujeme.
     */
    data class Target(
        val jellyfinItemId: String? = null,
        val imdbId: String? = null,
        val isEpisode: Boolean = false,
        val videoResumeKey: String? = null,
        val externalResumeKey: String? = null,
        // Číslo sezóny/dílu u epizody ze streamu — bez nich nemá Trakt jak díl identifikovat
        // (`imdbId` je u epizody id SERIÁLU). Doplněno 2026-07-31.
        val season: Int? = null,
        val episode: Int? = null,
    ) {
        /** Klíč pro [reported] — stačí to, čím se položka liší; prázdný cíl nehlásíme vůbec. */
        val identity: String?
            get() = jellyfinItemId?.takeIf { it.isNotBlank() }
                ?: imdbId?.takeIf { it.isNotBlank() }
                ?: videoResumeKey?.takeIf { it.isNotBlank() }
                ?: externalResumeKey?.takeIf { it.isNotBlank() }
    }

    private val reported = mutableSetOf<String>()

    /** Práh v procentech délky, od kterého je titul „dokoukaný". */
    fun thresholdPct(): Int =
        prefs.getInt(PlayerPrefs.MARK_WATCHED_PCT_KEY, PlayerPrefs.DEFAULT_MARK_WATCHED_PCT)
            .coerceIn(50, 100)

    /** Má se přehrávač po dokoukání sám zavřít (návrat o krok zpět)? */
    fun exitOnFinish(): Boolean =
        prefs.getBoolean(PlayerPrefs.EXIT_ON_FINISH_KEY, PlayerPrefs.DEFAULT_EXIT_ON_FINISH)

    /** Dosáhla pozice prahu? Bez známé délky (live/nedotažený manifest) nerozhodujeme. */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L || positionMs <= 0L) return false
        return positionMs * 100L >= durationMs * thresholdPct()
    }

    /** Nová položka v přehrávači → zapomeň guard, ať jde tentýž díl označit i při opakovaném puštění. */
    fun forget(target: Target) {
        target.identity?.let { reported.remove(it) }
    }

    /**
     * Označ jako zhlédnuté (idempotentně). Vrací `true`, když se to v tomhle běhu stalo poprvé — volající
     * podle toho pozná, že má smysl zavřít přehrávač / obnovit stav v detailu.
     */
    suspend fun markWatched(target: Target): Boolean {
        val id = target.identity ?: return false
        if (!reported.add(id)) return false

        // Lokální resume pryč VŽDY — dokoukané se nemá nabízet k pokračování ani offline.
        target.videoResumeKey?.takeIf { it.isNotBlank() }?.let { videoResumeStore.clear(it) }
        target.externalResumeKey?.takeIf { it.isNotBlank() }?.let { key ->
            prefs.edit().remove("resume_$key").apply()
            // ČT díl (`ctv:<idec>`) → zapiš „dokoukáno", jinak by ho „Další díly" nabízely donekonečna
            // (smazaná pozice sama o sobě nerozliší dokoukaný díl od nespuštěného).
            if (key.startsWith("ctv:")) {
                ctvWatchedStore.markWatched(key)
                Timber.i("[VLTAVA] ČT díl dokoukán: %s", key)
            }
        }

        var ok = false
        target.jellyfinItemId?.takeIf { it.isNotBlank() }?.let { jfId ->
            ok = jellyfin.markPlayed(jfId, true)
            Timber.i("[CURTAIN] Jellyfin markPlayed(%s) → %s", jfId, ok)
        }

        if (target.jellyfinItemId.isNullOrBlank()) {
            if (target.isEpisode) reportEpisodeToTrakt(target) else reportToTrakt(target.imdbId)
        }
        return ok || target.jellyfinItemId.isNullOrBlank()
    }

    /**
     * Epizoda ze streamu → Trakt historie. Trakt chce díl jako `shows[{ids, seasons[{number, episodes[…]}]}]`
     * — `imdbId` je tu id SERIÁLU, číslo dílu nese [Target.season]/[Target.episode]. Bez nich hlásit nejde
     * (jinak bychom označili celý seriál). Do 2026-07-31 se epizody nehlásily vůbec.
     */
    private suspend fun reportEpisodeToTrakt(target: Target) {
        if (!prefs.getBoolean(PlayerPrefs.TRAKT_MARK_WATCHED_KEY, PlayerPrefs.DEFAULT_TRAKT_MARK_WATCHED)) return
        val imdb = target.imdbId?.takeIf { it.isNotBlank() } ?: return
        val s = target.season ?: return
        val e = target.episode ?: return
        val item = SyncExportItem(
            ids = SyncExportItem.Ids(imdb = imdb),
            watched_at = null,
            hidden_at = null,
            seasons = listOf(
                SyncExportItem.Season(
                    number = s,
                    episodes = listOf(SyncExportItem.Episode(number = e, watched_at = "released")),
                ),
            ),
        )
        runCatching { trakt.postSyncWatched(SyncExportRequest(shows = listOf(item))) }
            .onSuccess { Timber.i("[CURTAIN] Trakt historie: %s S%02dE%02d", imdb, s, e) }
            .onFailure { Timber.w(it, "[CURTAIN] Trakt historie epizody selhala pro %s S%02dE%02d", imdb, s, e) }
    }

    /** Film ze streamu → Trakt historie. Jen na přání usera; chyba je neškodná (jen log). */
    private suspend fun reportToTrakt(imdbId: String?) {
        if (!prefs.getBoolean(PlayerPrefs.TRAKT_MARK_WATCHED_KEY, PlayerPrefs.DEFAULT_TRAKT_MARK_WATCHED)) return
        val imdb = imdbId?.takeIf { it.isNotBlank() } ?: return
        val item = SyncExportItem.fromIds(traktId = null, tmdbId = null, imdbId = imdb, watchedAt = "released") ?: return
        runCatching { trakt.postSyncWatched(SyncExportRequest(movies = listOf(item))) }
            .onSuccess { Timber.i("[CURTAIN] Trakt historie: %s", imdb) }
            .onFailure { Timber.w(it, "[CURTAIN] Trakt historie selhala pro %s", imdb) }
    }
}
