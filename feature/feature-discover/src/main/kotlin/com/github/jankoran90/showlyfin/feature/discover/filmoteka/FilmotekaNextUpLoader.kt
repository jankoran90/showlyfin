package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.feature.discover.home.CtvNextUpLoader
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.home.StreamNextUpLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * VESTIBUL (SHW-120, user 2026-08-24: „udělej mi pro tv zobrazení Filmotéku ve stylu jak máme na webu?
 * to znamená filmotéka začne nabídkou dalších dílů ke shlédnutí, které následují") — řada „Další díly"
 * NAD mřížkou Filmotéky, PARITA s webem (`static/filmy/settings.js`, kde tohle běží od 2026-08-17).
 *
 * Skládá TŘI zdroje, protože rozkoukanost žije na třech místech a uživatel je nerozlišuje:
 *  1. **Jellyfin** `/Shows/NextUp` — další nezhlédnutý díl seriálu z knihovny,
 *  2. **uložené zdroje** (sdilej.cz/RD) přes [StreamNextUpLoader] — zhlédnutost říká Trakt,
 *  3. **ČT iVysílání** přes [CtvNextUpLoader] — rozkoukané pořady.
 *
 * Zdroje 2 a 3 jsou tytéž injektovatelné loadery, které pohání řadu „Další díly" na domově → obě
 * místa ukazují TOTÉŽ a nemůžou se rozejít.
 */
@Singleton
class FilmotekaNextUpLoader @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val profileRepository: ProfileRepository,
    private val streamNextUp: StreamNextUpLoader,
    private val ctvNextUp: CtvNextUpLoader,
    // Tentýž prefs soubor, ze kterého čte Jellyfin session [FilmotekaBaseLoader] (název je
    // historický — drží i jellyfin_* klíče, nejen Trakt).
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {

    /** Zahoď cache podřízených loaderů — po dokoukání dílu musí řada ukázat ten následující. */
    fun invalidate() {
        streamNextUp.invalidate()
        ctvNextUp.invalidate()
    }

    /**
     * Další díly ke zhlédnutí. Selhání JEDNOHO zdroje nesmí shodit celou řadu — každý se sbírá
     * zvlášť a v nejhorším přispěje prázdnem (řada se pak prostě nevykreslí).
     */
    suspend fun load(limit: Int = DEFAULT_LIMIT): List<HomeRowItem> = coroutineScope {
        val jfD = async { runCatching { jellyfinNextUp(limit) }.getOrElse { emptyList() } }
        val streamD = async { runCatching { streamNextUp.load(limit) }.getOrElse { emptyList() } }
        val ctvD = async { runCatching { ctvNextUp.load(limit) }.getOrElse { emptyList() } }
        val all = jfD.await() + streamD.await() + ctvD.await()
        // Týž seriál může přijít z knihovny i z uloženého zdroje — ukázat ho dvakrát by mátlo.
        val seen = mutableSetOf<String>()
        all.filter { seen.add(it.key) }.take(limit)
    }

    // ── Jellyfin ────────────────────────────────────────────────────────────────

    private suspend fun jellyfinNextUp(limit: Int): List<HomeRowItem> {
        val session = prepareJellyfin() ?: return emptyList()
        // ORCHARD: Filmotéka respektuje SVŮJ výběr JF knihoven (null = všechny, prázdné = žádná) —
        // řada nad ní se musí držet téhož výběru, jinak by nabízela díly z knihoven, které tu nejsou.
        val whitelist = profileRepository.activeConfig.value.filmotekaJfLibraries
        if (whitelist != null && whitelist.isEmpty()) return emptyList()
        val items = runCatching {
            apiClient.tvShowsApi.getNextUp(
                userId = session.userUuid,
                limit = limit,
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.GENRES, ItemFields.DATE_CREATED),
                // Bez `enableImages` nechodí imageTags → široká karta by neměla still dílu (OTA 299).
                enableImages = true,
            ).content.items
        }.getOrElse { Timber.w(it, "[Filmoteka] getNextUp selhalo"); return emptyList() }

        val allowed = whitelist?.map { it.replace("-", "").lowercase() }?.toSet()
        return items
            .filter { dto ->
                allowed == null || dto.parentId?.toString()?.replace("-", "")?.lowercase() in allowed ||
                    dto.seriesId?.toString()?.replace("-", "")?.lowercase() in allowed
            }
            .mapNotNull { it.toNextUpRowItem(session.serverUrl, session.token) }
    }

    /** Epizoda jako široká karta: název seriálu + „S1·E4", still dílu na šířku. */
    private fun BaseItemDto.toNextUpRowItem(serverUrl: String, token: String): HomeRowItem? {
        val showTitle = seriesName ?: name ?: return null
        val season = parentIndexNumber
        val episode = indexNumber
        val epLabel = when {
            season != null && episode != null -> "S$season·E$episode"
            episode != null -> "E$episode"
            else -> null
        }
        val still = jellyfinPosterUrl(serverUrl, token)
        return HomeRowItem(
            key = "filmo_nextup_jf_${id}",
            title = showTitle,
            subtitle = listOfNotNull(epLabel, name?.takeIf { it != showTitle }).joinToString(" · ")
                .takeIf { it.isNotBlank() },
            posterUrl = still,
            landscapeUrl = jellyfinBackdropUrl(serverUrl, token) ?: still,
            jellyfinId = id.toString(),
            // 🔴 `mediaItem` ZÁMĚRNĚ null. `providerIds.Tmdb` na epizodě je TMDB id EPIZODY, ne
            // seriálu — kdyby se poslalo dál jako identita titulu, konzumenti (klik → detail, ČSFD,
            // odznak zdroje) by si podle něj dotáhli úplně jiný obsah. Přesně ta past, na kterou
            // Filmotéka narazila u BoxSetů (FOYER, „karta s cizím obsahem"). Klik má jít přes
            // `jellyfinId` → Jellyfin detail epizody, který ji umí rovnou přehrát.
            mediaItem = null,
        )
    }

    private data class JfSession(val serverUrl: String, val token: String, val userUuid: UUID)

    private fun prepareJellyfin(): JfSession? {
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        val userId = prefs.getString("jellyfin_user_id", "").orEmpty()
        if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) return null
        apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
        val userUuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        return JfSession(serverUrl, token, userUuid)
    }

    private companion object {
        /** Kolik dílů řada nabídne. Víc než obrazovka stejně neukáže a každý zdroj stojí dotaz. */
        const val DEFAULT_LIMIT = 12
    }
}
