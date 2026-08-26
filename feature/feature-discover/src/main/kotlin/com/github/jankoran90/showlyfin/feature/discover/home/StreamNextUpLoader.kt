package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.WorkingSource
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.isSavedPlayable
import com.github.jankoran90.showlyfin.data.uploader.isSeasonRecipe
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * SEZONA (SHW-113) f3c — SERIÁLY ZE STREAMU do řady **„Další díly"**.
 *
 * 🔴 Device test 2026-08-01: user měl Bleach ve Filmotéce, rozkoukané S01E01–E02, a přesto v „Další díly"
 * nebyl (*„v další díly není ten, který mám neshlédnutý na pořadí"*). Ta řada uměla jen **Jellyfin**
 * `nextUp` a **ČT** ([CtvNextUpLoader]) — seriál, který hraje ze streamu, do ní neměl kudy: jeho
 * rozkoukanost žije v Traktu, ne v knihovně.
 *
 * Bere seriály s uloženým zdrojem SEZÓNY (ty, co se dají hned pustit) a u každého se Traktu zeptá na
 * první nezhlédnutý díl (`trakt/show-progress` → `nextEpisode`). Dokoukaný seriál se neukáže vůbec.
 */
@Singleton
class StreamNextUpLoader @Inject constructor(
    private val workingSources: WorkingSourceStore,
    private val uploaderDs: UploaderRemoteDataSource,
    private val enricher: MediaEnricher,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""
    private fun profileKey() = prefs.getString("jellyfin_user_id", "").orEmpty()

    // Cache nese profil (uložené zdroje jsou per profil) — po přepnutí nesmí přežít.
    private data class Cached(val profileKey: String, val atMs: Long, val items: List<HomeRowItem>)

    @Volatile private var cache: Cached? = null

    /** Zahoď cache (přepnutí profilu / ruční refresh domova / dokoukaný díl). */
    fun invalidate() { cache = null }

    suspend fun load(limit: Int): List<HomeRowItem> {
        if (limit <= 0 || baseUrl.isBlank()) return emptyList()
        val key = profileKey()
        if (key.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        cache?.takeIf { it.profileKey == key && now - it.atMs < CACHE_TTL_MS }
            ?.let { return it.items.take(limit) }

        // 🔴 2026-08-26 — po přepnutí profilu nesmí vzniknout cache z NEDOROVNANÉHO úložiště. Dokud
        // [WorkingSourceStore] nedotáhl zdroje nového profilu, vrací prázdno (izolace) — kdybychom to
        // prázdno zacachovali pod klíč nového profilu, řada zůstane prázdná celé TTL, i když se zdroje
        // mezitím dotáhnou. Nic necachuj a příště se zeptej znovu.
        if (!workingSources.isReadyForActiveProfile()) return emptyList()

        val shows = workingSources.getLibraryEntries()
            .filter { it.isSeasonRecipe() && it.isSavedPlayable() }
            .take(MAX_SHOWS)
        if (shows.isEmpty()) {
            cache = Cached(key, now, emptyList())
            return emptyList()
        }
        // Každý seriál = jeden dotaz na Trakt (přes náš server). Strop na seriál, ať pomalá odpověď
        // nedrží celý domov — co se nestihne, prostě v řadě chybí a příště se doplní.
        val raw = coroutineScope {
            shows.map { ws -> async { withTimeoutOrNull(SHOW_TIMEOUT_MS) { nextOf(ws, key) } } }.awaitAll()
        }.filterNotNull()
        if (raw.isEmpty()) {
            cache = Cached(key, now, emptyList())
            return emptyList()
        }
        // Plakát/backdrop dodá sdílený enricher (WorkingSource je nenese) — týž, co plní ostatní řady.
        val enriched = enricher.enrich(raw.map { it.item }, withCertification = false)
        val items = raw.zip(enriched).map { (n, item) ->
            HomeRowItem(
                key = "streamnext_${n.item.tmdbId ?: n.item.imdbId}",
                title = item.displayTitle,
                subtitle = listOfNotNull(
                    "S${n.season}E${n.episode}",
                    n.title?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                posterUrl = item.posterUrl("w342"),
                landscapeUrl = item.backdropUrl("w780"),
                // Klik → karta seriálu (seznam dílů). Přehrát rovnou nejde: díl si musí vybrat divák,
                // a „Přehrát" u dílu už zdroj sezóny použije samo.
                mediaItem = item.copy(type = MediaType.SHOW),
            )
        }
        cache = Cached(key, now, items)
        Timber.i("[SEZONA] Další díly ze streamu: %d seriálů → %d položek", shows.size, items.size)
        return items.take(limit)
    }

    private data class NextUp(val item: MediaItem, val season: Int, val episode: Int, val title: String?)

    private suspend fun nextOf(ws: WorkingSource, profile: String): NextUp? {
        val res = runCatching {
            uploaderDs.showProgress(baseUrl, cookie, profile, ws.imdb.takeIf { it.isNotBlank() },
                ws.tmdb.takeIf { it > 0L }, false)
        }.getOrNull() ?: return null
        // `aired == 0` = Trakt seriál nedohledal (vrací 200 s prázdnem) → nevymýšlet si „další díl".
        if (!res.ok || res.aired <= 0) return null
        val next = res.nextEpisode ?: return null          // null = dokoukáno → do řady nepatří
        val s = next.season ?: return null
        val e = next.number ?: return null
        return NextUp(
            item = MediaItem(
                traktId = 0L,
                tmdbId = ws.tmdb.takeIf { it > 0L },
                imdbId = ws.imdb.takeIf { it.isNotBlank() },
                title = ws.title,
                year = null,
                overview = null,
                rating = null,
                genres = null,
                type = MediaType.SHOW,
            ),
            season = s, episode = e, title = next.title,
        )
    }

    private companion object {
        /** Kolik seriálů zohlednit (každý = jeden dotaz na Trakt přes náš server). */
        const val MAX_SHOWS = 8

        /** Jak dlouho platí sestavená řada (10 min, stejně jako ostatní řady domova). */
        const val CACHE_TTL_MS = 10L * 60 * 1000

        /** Strop na jeden seriál — pomalá odpověď Traktu nesmí držet domov. */
        const val SHOW_TIMEOUT_MS = 4_000L
    }
}
