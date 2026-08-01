package com.github.jankoran90.showlyfin.feature.discover.curator

import android.content.SharedPreferences
import android.util.Log
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.CuratorPrefs
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AUTEUR (SHW-91) — kurátorská řada „Pro tebe". Sestaví MODEL VKUSU z appky (Trakt watched+ratings+
 * watchlist ∪ showlyfin Favorites), pošle na backend `POST /curator/recommend` (mozek → resolve na
 * TMDB) a vrátí obohacené [MediaItem] (poster/CZ + věkový gate) — stejný výstupní tvar jako
 * [com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader].
 *
 * ZDROJ VKUSU (user 2026-07-14): dospělé profily = **jen Trakt + Favorites, BEZ Jellyfinu** (na JF
 * filmy/seriály nekoukají). JF watched jako signál vkusu je vyhrazen dětským profilům (fáze C4).
 */
@Singleton
class CuratorLoader @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val favoritesStore: FavoritesRepository,
    private val userRatingStore: com.github.jankoran90.showlyfin.data.uploader.UserRatingStore,
    private val enricher: MediaEnricher,
    private val parental: ParentalControlsRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private fun capAge(): Int? = parental.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parental.profile.value.hideUnratedForAge
    // Kanonický backend klíč (shodný s FavoritesRepository/mirror/substrate `backendKey`):
    // jellyfinUserId (prefer) → profileUuid (fallback). **Filmy profily nemají JF účet** → pref
    // `jellyfin_user_id` je prázdný → BEZ fallbacku na profileUuid by `key.isBlank()` a curator by se
    // serveru NIKDY nezeptal (Pro tebe navěky prázdné — bug do v1.0.10). Showlyfin má JF → pref plný →
    // beze změny (pref má přednost). Fallback míří na profileUuid = `filmy-adult` = bucket mirroru.
    private fun profileKey(): String {
        val prefId = appPrefs.getString("jellyfin_user_id", "").orEmpty()
        if (prefId.isNotBlank()) return prefId
        val p = profileRepository.activeProfile.value
        return p?.jellyfinUserId?.takeIf { it.isNotBlank() } ?: p?.profileUuid.orEmpty()
    }
    private fun serverBase(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun serverCookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    /** Per-profil nastavení kurátora (osy jistoty/nálada/žánr/druh/model) — synced přes [ProfileConfig]. */
    private fun prefs(): CuratorPrefs = profileRepository.activeConfig.value.curator ?: CuratorPrefs.DEFAULT

    /**
     * Kurátorská doporučení „Pro tebe". Prázdné = backend/mozek nedostupný nebo studený vkus →
     * volající (TvHomeViewModel) může fallbacknout na `weightedRecommendations`.
     */
    suspend fun forYou(limit: Int, pollUntilReady: Boolean = false): List<MediaItem> {
        val prefs = prefs()
        // Master switch (C1): vypnutý kurátor → mozek NEvoláme, volající spadne na fallback (weighted).
        if (!prefs.enabled) { Log.i(TAG, "forYou: kurátor vypnutý → fallback"); return emptyList() }
        val base = serverBase(); val cookie = serverCookie(); val key = profileKey()
        if (base.isBlank() || key.isBlank()) {
            Log.i(TAG, "forYou: chybí backend/profil (base=${base.isNotBlank()}, key=${key.isNotBlank()})")
            return emptyList()
        }
        val taste = buildTaste()
        // SUBSTRATE F2c: NEshort-circuituj na prázdný lokální vkus. Server umí postavit vkus ze svého Trakt
        // MIRRORU (tažený serverem, nezávislý na pomalém/padajícím živém Traktu klienta na cold startu).
        // Když má klient vkus, pošle ho (přednost); když ne, pošle prázdný → server použije mirror → Pro tebe
        // naskočí i na cold startu. (Dřív se tady vracelo prázdno a server se vůbec nezavolal = bug.)
        if (taste.isEmpty()) Log.i(TAG, "forYou: prázdný lokální vkus → server použije Trakt mirror")

        val requestJson = buildRequestJson(key, limit.coerceIn(1, 60), taste, prefs)
        // REFLEX (C1): wait=false → cache miss vrátí `source=pending`, mozek se zahřeje na pozadí (~30 s).
        // SUBSTRATE F2c fix: volající, který chce plný obsah (pollUntilReady = sekce „Pro tebe"), na `pending`
        // POČKÁ a dotaz párkrát zopakuje (stale-while-revalidate) → doporučení naskočí na TÉTO obrazovce bez
        // zavření/návratu. Home řada nechává pollUntilReady=false → okamžitý fallback (weighted), žádné blokování.
        var attempt = 0
        while (true) {
            val raw = runCatching { uploaderDs.curatorRecommend(base, cookie, requestJson) }
                .onFailure { Log.w(TAG, "forYou: volání backendu selhalo", it) }
                .getOrNull() ?: return emptyList()
            val source = runCatching { JSONObject(raw).optString("source") }.getOrNull().orEmpty()
            val parsed = runCatching { parseItems(raw) }
                .onFailure { Log.w(TAG, "forYou: parse odpovědi selhal", it) }
                .getOrNull() ?: return emptyList()
            if (parsed.isNotEmpty()) return postProcess(parsed, taste)
            // Prázdno + mozek počítá na pozadí → počkej a zkus znovu (jen když to volající chce).
            if (source == "pending" && pollUntilReady && attempt < PENDING_MAX_RETRIES) {
                attempt++
                Log.i(TAG, "forYou: mozek počítá (pending) → re-poll $attempt/$PENDING_MAX_RETRIES za ${PENDING_RETRY_MS}ms")
                delay(PENDING_RETRY_MS)
                continue
            }
            Log.i(TAG, "forYou: 0 položek (source=$source)")
            return emptyList()
        }
    }

    /**
     * TMDB id všeho, co uživatel UŽ ZNÁ (zhlédnuté ∪ „Chci vidět" ∪ ohodnocené na Traktu ∪ vlastní hvězdy).
     * Používá se k úklidu akumulovaných doporučení — tip uložený dřív se nesmí ukazovat potom, co ho
     * uživatel viděl nebo ohodnotil (user 2026-07-31: „Chuť čaje tam pořád je").
     */
    suspend fun knownTmdbIds(): Set<Long> {
        val fromTaste = runCatching { buildTaste().knownIds }.getOrDefault(emptySet())
        // Vlastní hvězdy fungují i bez Traktu → přidej je taky.
        val fromLocal = userRatingStore.items.value.mapNotNull { it.tmdbId }.toSet()
        return fromTaste + fromLocal
    }

    /**
     * Doporučení JEDNÉ kategorie ([CuratorBucket]) — do mozku jde jen ta výseč vkusu, která kategorii
     * definuje, takže řada umí říct PROČ. `bucket` posíláme i na server (je součástí cache klíče).
     */
    suspend fun forYouBucket(bucket: CuratorBucket, limit: Int, pollUntilReady: Boolean = false): List<MediaItem> {
        val prefs = prefs()
        if (!prefs.enabled) return emptyList()
        val base = serverBase(); val cookie = serverCookie(); val key = profileKey()
        if (base.isBlank() || key.isBlank()) return emptyList()
        val full = buildTaste()
        // Ořež vkus na kategorii. `watchlist`/`avoid`/`knownIds` zůstávají — to nejsou signály kategorie,
        // ale filtr („co už znám" a „čemu se vyhni") a musí platit ve všech řadách stejně.
        val taste = when (bucket) {
            CuratorBucket.TOP -> full.copy(loved = emptyList(), recent = emptyList())
            CuratorBucket.LOVED -> full.copy(top = emptyList(), recent = emptyList())
            CuratorBucket.RECENT -> full.copy(top = emptyList(), loved = emptyList())
        }
        // Kategorie bez vlastního signálu nemá co nabídnout — nezatěžuj mozek prázdným dotazem.
        val hasSignal = when (bucket) {
            CuratorBucket.TOP -> taste.top.isNotEmpty()
            CuratorBucket.LOVED -> taste.loved.isNotEmpty()
            CuratorBucket.RECENT -> taste.recent.isNotEmpty()
        }
        if (!hasSignal) { Log.i(TAG, "forYouBucket(${bucket.wire}): žádný signál → přeskočeno"); return emptyList() }
        val json = JSONObject(buildRequestJson(key, limit.coerceIn(1, 60), taste, prefs))
            .put("bucket", bucket.wire).toString()
        return fetchRecommendations(json, taste, pollUntilReady, "forYouBucket(${bucket.wire})") { body ->
            uploaderDs.curatorRecommend(base, cookie, body)
        }
    }

    /**
     * „Protože jsi viděl X" — balíček svázaný JEDNÍM titulem (user 2026-07-31). Backend `/curator/similar`
     * existoval od AUTEURa, ale appka ho do dneška nikdy nevolala.
     */
    suspend fun similarTo(
        seedTitle: String,
        seedYear: Int?,
        limit: Int,
        isShow: Boolean = false,
        pollUntilReady: Boolean = false,
    ): List<MediaItem> = similarToSplit(seedTitle, seedYear, limit, isShow, pollUntilReady).fresh

    /** [similarTo] s výsledkem rozděleným na nové a už známé — viz [fetchRecommendationsSplit]. */
    suspend fun similarToSplit(
        seedTitle: String,
        seedYear: Int?,
        limit: Int,
        isShow: Boolean = false,
        pollUntilReady: Boolean = false,
    ): CuratorRecs {
        val prefs = prefs()
        if (!prefs.enabled || seedTitle.isBlank()) return CuratorRecs()
        val base = serverBase(); val cookie = serverCookie(); val key = profileKey()
        if (base.isBlank() || key.isBlank()) return CuratorRecs()
        // Vkus stavíme PŘED požadavkem — mozek potřebuje vědět, co divák zná, aby na to nespotřeboval
        // sloty odpovědi (user 2026-08-01). Dřív ho `similar` neznal a vracel dvanáct nejslavnějších
        // podobných, které divák dávno viděl → klient je zahodil a sekce zůstala prázdná.
        val taste = buildTaste()
        val json = JSONObject()
            .put("profileKey", key)
            .put("title", seedTitle)
            .put("kind", if (isShow) "show" else "movie")
            .put("count", limit.coerceIn(1, 40))
            .put("wait", false)
            .put("known", JSONArray(taste.knownTitles))
            .also { o -> seedYear?.takeIf { it > 0 }?.let { o.put("year", it) } }
            .also { o -> prefs.model?.trim()?.takeIf { it.isNotEmpty() }?.let { o.put("model", it) } }
            .also { o -> capAge()?.let { o.put("ageCap", it) } }
            .toString()
        return fetchRecommendationsSplit(json, taste, pollUntilReady, "similarTo($seedTitle)") { body ->
            uploaderDs.curatorSimilar(base, cookie, body)
        }
    }

    /**
     * Doporučení vázaná na RUČNĚ VYBRANÉ filmy (user 2026-07-31: „důležité je možnost volit referenci,
     * na jaký film nebo filmy se doporučení váže").
     *
     * Jedna reference jde na `/curator/similar` (má vlastní prompt „co je podobné X"), víc referencí na
     * `/curator/recommend` s vkusem oříznutým na PRÁVĚ TYHLE tituly — mozek pak hledá průnik toho, co je
     * spojuje. Cache je rozlišená automaticky: serverový `_loved_key` hashuje `loved`, takže jiná sada
     * referencí = jiný klíč.
     */
    suspend fun recommendFromReferences(
        references: List<MediaItem>,
        limit: Int,
        pollUntilReady: Boolean = false,
    ): CuratorRecs {
        val seeds = references.filter { it.displayTitle.isNotBlank() }
        if (seeds.isEmpty()) return CuratorRecs()
        if (seeds.size == 1) {
            val one = seeds.first()
            return similarToSplit(
                seedTitle = one.title.ifBlank { one.displayTitle },
                seedYear = one.year,
                limit = limit,
                isShow = one.type != MediaType.MOVIE,
                pollUntilReady = pollUntilReady,
            )
        }
        val prefs = prefs()
        if (!prefs.enabled) return CuratorRecs()
        val base = serverBase(); val cookie = serverCookie(); val key = profileKey()
        if (base.isBlank() || key.isBlank()) return CuratorRecs()
        val full = buildTaste()
        val taste = full.copy(
            loved = seeds.map { TasteEntry(title = it.title.ifBlank { it.displayTitle }, year = it.year) },
            top = emptyList(),
            recent = emptyList(),
        )
        val json = JSONObject(buildRequestJson(key, limit.coerceIn(1, 60), taste, prefs))
            .put("bucket", "picked").toString()
        return fetchRecommendationsSplit(json, taste, pollUntilReady, "recommendFromReferences(${seeds.size})") { body ->
            uploaderDs.curatorRecommend(base, cookie, body)
        }
    }

    /**
     * Sdílená smyčka dotazu na mozek: `wait=false` → cache miss vrátí `pending` a mozek se zahřeje na
     * pozadí; volající, který chce plný obsah, si počká a zopakuje (stale-while-revalidate).
     */
    private suspend fun fetchRecommendations(
        requestJson: String,
        taste: TastePayload,
        pollUntilReady: Boolean,
        logTag: String,
        call: suspend (String) -> String?,
    ): List<MediaItem> = fetchRecommendationsSplit(requestJson, taste, pollUntilReady, logTag, call).fresh

    /**
     * Táž smyčka, ale výsledek ROZDĚLENÝ na „nové" a „už znáš" — jedním dotazem (druhé volání by znovu
     * stavělo vkus z Traktu i obohacovalo z TMDB). Sekce „Podle filmu" díky tomu umí místo prázdna
     * ukázat aspoň tituly, které divák zná, a říct to nahlas.
     */
    private suspend fun fetchRecommendationsSplit(
        requestJson: String,
        taste: TastePayload,
        pollUntilReady: Boolean,
        logTag: String,
        call: suspend (String) -> String?,
    ): CuratorRecs {
        var attempt = 0
        while (true) {
            val raw = runCatching { call(requestJson) }
                .onFailure { Log.w(TAG, "$logTag: volání backendu selhalo", it) }
                .getOrNull() ?: return CuratorRecs()
            val source = runCatching { JSONObject(raw).optString("source") }.getOrNull().orEmpty()
            val parsed = runCatching { parseItems(raw) }
                .onFailure { Log.w(TAG, "$logTag: parse odpovědi selhal", it) }
                .getOrNull() ?: return CuratorRecs()
            if (parsed.isNotEmpty()) {
                val all = postProcessKeepKnown(parsed)
                val fresh = dropKnown(all, taste)
                val freshKeys = fresh.mapNotNull { it.tmdbId }.toSet()
                val known = all.filter { it.tmdbId != null && it.tmdbId !in freshKeys }
                Log.i(TAG, "$logTag: ${fresh.size} nových, ${known.size} už známých")
                return CuratorRecs(fresh = fresh, known = known)
            }
            if (source == "pending" && pollUntilReady && attempt < PENDING_MAX_RETRIES) {
                attempt++
                delay(PENDING_RETRY_MS)
                continue
            }
            Log.i(TAG, "$logTag: 0 položek (source=$source)")
            return CuratorRecs()
        }
    }

    /** Enrich + věkový gate + skrytí nízko hodnocených/už známých. Sdílené pro první i re-poll odpověď. */
    private suspend fun postProcess(parsed: List<MediaItem>, taste: TastePayload): List<MediaItem> =
        dropKnown(postProcessKeepKnown(parsed), taste)
            .also { Log.i(TAG, "forYou: ${it.size} položek") }

    /**
     * Obohacení + věkový gate + skryt nízko hodnocených — ale BEZ filtru „co už znám". Odděleno kvůli
     * sekci „Podle filmu": tam se filtr známých musí dát vypnout, jinak divákovi s velkou historií
     * vypadne celý výsledek (user 2026-08-01: pro Splitsville vrátil server 12 titulů a appka ukázala
     * „Nic nového nevypadlo" — všech 12 měl viděných/hodnocených).
     */
    private suspend fun postProcessKeepKnown(parsed: List<MediaItem>): List<MediaItem> {
        val enriched = enricher.enrich(parsed, withCertification = capAge() != null)
        // BESPOKE F3 — tvrdý skryt nízko hodnocených (≤4 hvězdy) titulů ze sekce „Pro tebe".
        val disliked = userRatingStore.items.value.filter { it.stars <= AVOID_MAX }
            .mapNotNull { it.tmdbId }.toSet()
        return ContentAgeGate.filter(capAge(), enriched, hideUnrated())
            .filterNot { it.tmdbId != null && it.tmdbId in disliked }
    }

    /** User 2026-07-17: nedoporučuj, co už uživatel zná (zhlédnuté ∪ watchlist ∪ hodnocené). */
    private fun dropKnown(items: List<MediaItem>, taste: TastePayload): List<MediaItem> =
        items.filterNot { it.tmdbId != null && it.tmdbId in taste.knownIds }

    /** Sestaví taste payload z Traktu (watched+plays, ratings, watchlist) + Favorites. */
    private suspend fun buildTaste(): TastePayload = coroutineScope {
        val watchedMoviesD = async { runCatching { authorizedTraktApi.fetchSyncWatchedMovies() }.getOrElse { emptyList() } }
        val watchedShowsD = async { runCatching { authorizedTraktApi.fetchSyncWatchedShows() }.getOrElse { emptyList() } }
        val movieRatingsD = async { runCatching { authorizedTraktApi.fetchMoviesRatings() }.getOrElse { emptyList() } }
        val showRatingsD = async { runCatching { authorizedTraktApi.fetchShowsRatings() }.getOrElse { emptyList() } }
        val watchlistD = async {
            runCatching { authorizedTraktApi.fetchSyncMoviesWatchlist() + authorizedTraktApi.fetchSyncShowsWatchlist() }
                .getOrElse { emptyList() }
        }

        val watched = watchedMoviesD.await() + watchedShowsD.await()
        // tmdbId → label (pro dohledání názvu u ratingů, které nesou jen ids)
        val labelByTmdb = HashMap<Long, TasteEntry>()
        for (si in watched) {
            val tmdb = si.getTmdbId() ?: continue
            si.toEntry()?.let { labelByTmdb.putIfAbsent(tmdb, it) }
        }

        val top = watched.filter { it.toEntry() != null }
            .sortedByDescending { it.plays ?: 1 }
            .mapNotNull { it.toEntry(withPlays = true) }
            .take(TOP_CAP)
        val recent = watched.sortedByDescending { it.lastWatchedMillis() }
            .mapNotNull { it.toEntry() }
            .take(RECENT_CAP)

        // ratings: vysoké → loved, nízké → avoid; název dohledán přes watched mapu (RatingResultValue nemá title)
        val movieRatings = movieRatingsD.await()
        val showRatings = showRatingsD.await()
        val lovedFromRatings = mutableListOf<TasteEntry>()
        val avoid = mutableListOf<String>()
        movieRatings.forEach { r ->
            val tmdb = r.movie.ids.tmdb ?: return@forEach
            val e = labelByTmdb[tmdb] ?: return@forEach
            if (r.rating >= LOVE_MIN) lovedFromRatings += e
            if (r.rating <= AVOID_MAX) avoid += e.title
        }
        showRatings.forEach { r ->
            val tmdb = r.show.ids.tmdb ?: return@forEach
            val e = labelByTmdb[tmdb] ?: return@forEach
            if (r.rating >= LOVE_MIN) lovedFromRatings += e
            if (r.rating <= AVOID_MAX) avoid += e.title
        }

        val lovedFromFavorites = favoritesStore.items.value
            .filter { it.kind == FavoriteKind.MOVIE && it.name.isNotBlank() }
            .map { TasteEntry(title = it.name, year = it.year) }

        // BESPOKE F3 — vlastní hvězdy (lokální store, funguje i bez Traktu): ≥8 loved, ≤4 avoid.
        val localRatings = userRatingStore.items.value.filter { it.title.isNotBlank() }
        val lovedFromLocalRatings = localRatings.filter { it.stars >= LOVE_MIN }
            .map { TasteEntry(title = it.title, year = it.year) }
        localRatings.filter { it.stars <= AVOID_MAX }.forEach { avoid += it.title }

        val watchlistItems = watchlistD.await()
        val watchlist = watchlistItems.mapNotNull { it.toEntry() }.take(WATCHLIST_CAP)

        // Vše, co už uživatel zná → vyloučit z doporučení (user 2026-07-17): zhlédnuté ∪ watchlist ∪ hodnocené.
        val knownIds = HashSet<Long>()
        watched.forEach { it.getTmdbId()?.let(knownIds::add) }
        watchlistItems.forEach { it.getTmdbId()?.let(knownIds::add) }
        movieRatings.forEach { r -> r.movie.ids.tmdb?.let(knownIds::add) }
        showRatings.forEach { r -> r.show.ids.tmdb?.let(knownIds::add) }

        // Názvy toho, co divák zná — jdou do promptu mozku, ať na ně nespotřebuje sloty odpovědi
        // (user 2026-08-01: „ty co znám se nemají vůbec nabízet, ale co ty co neznám?"). Jen NÁZVY:
        // tmdb id mozku nic neřekne. Strop drží velikost požadavku.
        val knownTitles = (watched.mapNotNull { it.toEntry()?.title } +
            watchlistItems.mapNotNull { it.toEntry()?.title } +
            movieRatings.mapNotNull { it.movie.title } +
            showRatings.mapNotNull { it.show.title } +
            localRatings.map { it.title })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(KNOWN_TITLES_CAP)

        TastePayload(
            loved = (lovedFromFavorites + lovedFromRatings + lovedFromLocalRatings).dedupByTitle().take(LOVED_CAP),
            top = top,
            recent = recent,
            watchlist = watchlist,
            avoid = avoid.distinct().take(AVOID_CAP),
            knownIds = knownIds,
            knownTitles = knownTitles,
        )
    }

    private fun SyncItem.toEntry(withPlays: Boolean = false): TasteEntry? {
        val title = (movie?.title ?: show?.title)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val year = movie?.year ?: show?.year
        return TasteEntry(title = title, year = year, plays = if (withPlays) plays else null)
    }

    private fun List<TasteEntry>.dedupByTitle(): List<TasteEntry> {
        val seen = HashSet<String>()
        return filter { seen.add(it.title.lowercase()) }
    }

    // ── JSON (org.json — bez Gson závislosti v tomto modulu) ───────────────────
    private fun buildRequestJson(profileKey: String, count: Int, taste: TastePayload, prefs: CuratorPrefs): String {
        fun entriesToJson(list: List<TasteEntry>, withPlays: Boolean): JSONArray {
            val arr = JSONArray()
            for (e in list) {
                val o = JSONObject().put("title", e.title)
                if (e.year != null) o.put("year", e.year)
                if (withPlays && e.plays != null) o.put("plays", e.plays)
                arr.put(o)
            }
            return arr
        }
        val tasteJson = JSONObject()
            .put("loved", entriesToJson(taste.loved, withPlays = false))
            .put("top", entriesToJson(taste.top, withPlays = true))
            .put("recent", entriesToJson(taste.recent, withPlays = false))
            .put("watchlist", entriesToJson(taste.watchlist, withPlays = false))
            .put("avoid", JSONArray(taste.avoid))
        val root = JSONObject()
            .put("profileKey", profileKey)
            .put("kind", prefs.kindWire())
            .put("count", count)
            // REFLEX (C1): wait=false → cache miss vrátí `pending` + mozek se zahřeje na pozadí; volající
            // pro TENHLE load fallbackne (weighted) → žádné blokující 15-19 s. Kurátor doteče na příští
            // otevření Domů (stale-while-revalidate). Osy níže laděné uživatelem v Nastavení.
            .put("wait", false)
            .put("discovery", prefs.discovery.toDouble())
            .put("taste", tasteJson)
        prefs.mood.trim().takeIf { it.isNotEmpty() }?.let { root.put("mood", it) }
        prefs.genres.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
            ?.let { root.put("genres", JSONArray(it)) }
        prefs.surpriseWire().takeIf { it.isNotEmpty() }?.let { root.put("surprise", JSONArray(it)) }
        prefs.model?.trim()?.takeIf { it.isNotEmpty() }?.let { root.put("model", it) }
        capAge()?.let { root.put("ageCap", it) }
        return root.toString()
    }

    /** Odpověď `{items:[{tmdbId,type,title,year,posterPath}], source}` → [MediaItem] (dedup tmdbId). */
    private fun parseItems(raw: String): List<MediaItem> {
        val items = JSONObject(raw).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<MediaItem>(items.length())
        val seen = HashSet<Long>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val id = o.optLong("tmdbId", 0L)
            if (id <= 0L || !seen.add(id)) continue
            out += MediaItem(
                traktId = 0L,
                tmdbId = id,
                imdbId = null,
                title = o.optString("title", ""),
                year = o.optInt("year", 0).takeIf { it > 0 },
                overview = null,
                rating = null,
                genres = null,
                type = if (o.optString("type") == "show") MediaType.SHOW else MediaType.MOVIE,
                posterPath = if (o.isNull("posterPath")) null else o.optString("posterPath").ifBlank { null },
                backdropPath = null,
            )
        }
        return out
    }

    // ── Interní model vkusu (bez serializace) ─────────────────────────────────
    private data class TasteEntry(val title: String, val year: Int?, val plays: Int? = null)
    private data class TastePayload(
        val loved: List<TasteEntry>,
        val top: List<TasteEntry>,
        val recent: List<TasteEntry>,
        val watchlist: List<TasteEntry>,
        val avoid: List<String>,
        // Vše, co už uživatel zná (zhlédnuté ∪ watchlist ∪ hodnocené) → vyloučit z doporučení (user 2026-07-17).
        val knownIds: Set<Long> = emptySet(),
        /** NÁZVY známých titulů pro prompt mozku (id mu nic neřeknou). Viz `_known_line` na serveru. */
        val knownTitles: List<String> = emptyList(),
    ) {
        // Watchlist se POČÍTÁ do vkusu (user 2026-07-17): když historie/zhlédnuté z Traktu zlobí (pomalý/
        // padající endpoint), watchlist („Chci vidět") stačí jako signál → Pro tebe není prázdné.
        fun isEmpty(): Boolean = top.isEmpty() && recent.isEmpty() && loved.isEmpty() && watchlist.isEmpty()
    }

    private companion object {
        const val TAG = "AUTEUR_Curator"
        const val TOP_CAP = 30
        const val RECENT_CAP = 14
        const val LOVED_CAP = 20
        const val WATCHLIST_CAP = 20
        const val AVOID_CAP = 30
        /** Kolik názvů „co už znám" posílat mozku — víc by nafouklo prompt bez užitku. */
        const val KNOWN_TITLES_CAP = 120
        const val LOVE_MIN = 8      // Trakt rating (1-10) ≥ 8 = „miluje"
        const val AVOID_MAX = 4     // ≤ 4 = palec dolů → veto
        const val PENDING_MAX_RETRIES = 8   // re-poll na `pending` (mozek ~30 s) → strop ~48 s
        const val PENDING_RETRY_MS = 6_000L
    }
}

/**
 * Výsledek kurátora rozdělený podle toho, jestli titul divák UŽ ZNÁ (viděl / hodnotil / má ve
 * watchlistu). Vzniklo 2026-08-01: sekce „Podle filmu" hlásila „Nic nového nevypadlo", i když server
 * poslal dvanáct trefných titulů — jen je divák všechny znal. Prázdná obrazovka je horší odpověď než
 * „tohle sedí, ale už jsi to viděl", takže o rozdělení rozhoduje až UI, ne loader.
 */
data class CuratorRecs(
    /** Tituly, které divák nezná — přednostní obsah sekce. */
    val fresh: List<MediaItem> = emptyList(),
    /** Trefy, které už zná (viděné/hodnocené/watchlist). Ukazují se označené, ne místo [fresh]. */
    val known: List<MediaItem> = emptyList(),
)
