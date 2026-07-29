package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.uploader.CTV_ID_SCHEME
import com.github.jankoran90.showlyfin.data.uploader.CTV_SCHEME
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.ctvShowSidpOrNull
import com.github.jankoran90.showlyfin.data.uploader.model.CtvNumbering
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * VLTAVA (SHW-110) F6c (user 2026-07-28 „můžeme zahrnout ČT pořady a jednotlivé díly, aby se zobrazovaly
 * v Další díly row?") — ČT pořady z Filmotéky jako položky řady **„Další díly"**.
 *
 * Pro každý uložený ČT pořad (zdroj `ctvshow:<sidp>`) nabídne JEDEN díl, v tomhle pořadí:
 * 1. **rozkoukaný** (má uloženou pozici, viz [VideoResumeStore] pod klíčem `ctv:<idec>`) — pokračuj,
 * 2. jinak **první nedokoukaný** od nejstaršího (user 2026-07-28 „u ČT chci jít vždy od nejstarších,
 *    stejně jako u seriálu od S01E01") — díly proto tahá backend s `order=oldest`.
 *
 * Dokoukané drží `CtvWatchedStore` — smazaná pozice sama nestačí, u dokoukaného i nespuštěného dílu
 * vypadá stejně (a řada by pak napořád nabízela ten první).
 * Jellyfin „Další díly" tím zůstává netknuté — ČT položky se jen připojí za ně.
 */
@Singleton
class CtvNextUpLoader @Inject constructor(
    private val workingSources: WorkingSourceStore,
    private val uploaderDs: UploaderRemoteDataSource,
    private val resumeStore: VideoResumeStore,
    private val watchedStore: com.github.jankoran90.showlyfin.core.domain.resume.CtvWatchedStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    // Cache NESE profil: uložené ČT pořady jsou per profil, takže po přepnutí nesmí přežít (jinak by
    // dětský domov ukázal pořady dospělého, dokud nevyprší TTL).
    // `watchedStamp` = otisk sady zhlédnutých dílů: jakmile se změní, cache je neplatná (jinak by řada
    // až 10 minut nabízela díl, který už je dokoukaný — user 2026-07-28).
    private data class Cached(val profileKey: String, val watchedStamp: Int, val atMs: Long, val items: List<HomeRowItem>)

    @Volatile private var cache: Cached? = null

    /** Zahoď cache (přepnutí profilu / ruční refresh domova) — jiný profil = jiné uložené pořady. */
    fun invalidate() { cache = null }

    suspend fun load(limit: Int): List<HomeRowItem> {
        if (limit <= 0 || baseUrl.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val profileKey = prefs.getString("jellyfin_user_id", "").orEmpty()
        val watchedStamp = watchedStore.watched.value.hashCode()
        cache?.takeIf { it.profileKey == profileKey && it.watchedStamp == watchedStamp && now - it.atMs < CACHE_TTL_MS }
            ?.let { return it.items.take(limit) }

        // Jen POŘADY s díly; ČT film žádné „další díly" nemá (ten patří do Filmotéky jako film).
        val shows = workingSources.getAll().mapNotNull { ws ->
            val sidp = ctvShowSidpOrNull(ws.stream.url) ?: return@mapNotNull null
            ShowRef(sidp = sidp, title = ws.title, poster = ws.poster)
        }.take(MAX_SHOWS)
        if (shows.isEmpty()) {
            cache = Cached(profileKey, watchedStamp, now, emptyList())
            return emptyList()
        }

        // Řada domova nesmí viset na ČT: každý pořad = jeden dotaz do iVysílání a když jeden zatuhne,
        // čekal by na něj celý domov. Strop [SHOW_TIMEOUT_MS] na pořad, co se nestihne, prostě chybí.
        val items = coroutineScope {
            shows.map { show ->
                async { withTimeoutOrNull(SHOW_TIMEOUT_MS) { nextEpisodeOf(show) } }
            }.awaitAll()
        }.filterNotNull()
        cache = Cached(profileKey, watchedStamp, now, items)
        Timber.i("[VLTAVA] Další díly ČT: %d pořadů → %d položek", shows.size, items.size)
        return items.take(limit)
    }

    private data class ShowRef(val sidp: String, val title: String, val poster: String?)

    private suspend fun nextEpisodeOf(show: ShowRef): HomeRowItem? {
        val feed = runCatching { uploaderDs.getCtvFeed(baseUrl, cookie, show.sidp, limit = FEED_LIMIT, order = "oldest") }
            .getOrElse {
                Timber.w(it, "[VLTAVA] díly pořadu %s se nenačetly", show.sidp)
                return null
            }
        // 🔴 Pořadí NEBEREME z feedu. `date` u ČT = poslední REPRÍZA, ne premiéra, takže „nejstarší"
        // podle data hodilo nahoru 2. díl (user 2026-07-28: „vybrala se Jezera a bažiny, a ta určitě
        // není první díl"). Seřadí [CtvNumbering] podle `idec` — tam pořadí dílu reálně je.
        val episodes = CtvNumbering.number(feed.episodes.filter { it.id.isNotBlank() })
        if (episodes.isEmpty()) return null
        val marks = resumeStore.marks.value
        val watched = watchedStore.watched.value
        // Díly chodí od NEJSTARŠÍHO (jako seriál od S01E01) → „další díl" = rozkoukaný, jinak první
        // nedokoukaný. Když je dokoukaný celý pořad, do řady nepatří vůbec.
        // 🔴 user 2026-07-28 („vybrala se Jezera a bažiny, a ta určitě není první díl"): dřív měl přednost
        // JAKÝKOLI rozkoukaný díl — když si člověk ze zvědavosti pustí díl z prostředka, řada přeskočila
        // všechno před ním. Pořadí je seriálové (od nejstaršího), takže „další díl" = PRVNÍ NEDOKOUKANÝ.
        // Rozkoukanost už jen dokresluje progres na kartě.
        val next = episodes.firstOrNull { CTV_SCHEME + it.episode.id !in watched } ?: return null
        val episode = next.episode
        val mark = marks[CTV_SCHEME + episode.id]
        val progress = mark?.takeIf { it.durMs > 0 }?.let { ((it.posMs * 100) / it.durMs).toInt().coerceIn(0, 100) }
        val showTitle = feed.title?.takeIf { it.isNotBlank() } ?: show.title
        return HomeRowItem(
            key = "ctvnext_${show.sidp}",
            title = showTitle,
            // Číslo série a dílu i tady — na kartě řady je hned vidět, kde člověk je (user 2026-07-29).
            subtitle = listOfNotNull(
                CtvNumbering.label(next.seasonNumber, next.episodeNumber),
                next.cleanTitle.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            landscapeUrl = episode.image ?: show.poster,
            posterUrl = show.poster ?: episode.image,
            progressPct = progress,
            // Klik → karta pořadu (seznam dílů od nejstaršího). Identita je ta samá, pod jakou
            // pořad žije ve Filmotéce, takže se otevře jeho ČT karta (viz směrování v shellech).
            mediaItem = MediaItem(
                traktId = 0L,
                tmdbId = null,
                imdbId = CTV_ID_SCHEME + show.sidp,
                title = showTitle,
                year = null,
                overview = null,
                rating = null,
                genres = null,
                type = MediaType.SHOW,
                fallbackPosterUrl = show.poster,
            ),
        )
    }

    private companion object {
        /** Kolik ČT pořadů z Filmotéky vůbec zohlednit (každý = jeden dotaz na díly). */
        const val MAX_SHOWS = 8

        /** Kolik nejstarších dílů si vyžádat — v nich hledáme rozkoukaný / první nedokoukaný. */
        const val FEED_LIMIT = 30

        /** Jak dlouho platí sestavená řada (10 min, stejně jako „Filmotéka — nedávno přidané"). */
        const val CACHE_TTL_MS = 10L * 60 * 1000

        /** Strop na jeden pořad — pomalá/zatuhlá odpověď ČT nesmí držet domov. */
        const val SHOW_TIMEOUT_MS = 4_000L
    }
}
