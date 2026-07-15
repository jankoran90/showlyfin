package com.github.jankoran90.showlyfin.feature.discover.lapidary

import android.content.SharedPreferences
import android.util.Log
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * LAPIDARY (SHW-96) — načte identifikované „vzácné klenoty" jedné země z backendu (`GET /gems/catalog`)
 * a vrátí obohacené [MediaItem] (poster/CZ + věkový gate) — stejný výstupní tvar jako [CuratorLoader].
 *
 * MODEL (revize 2026-07-15): sekce ukazuje SEZNAM identifikovaných klenotů (i necachovaných → `status=all`);
 * RD cache/WorkingSource vznikne až přidáním do „chci vidět"/„oblíbené" (watchlist/favorite trigger, S2).
 */
@Singleton
class LapidaryLoader @Inject constructor(
    private val enricher: MediaEnricher,
    private val parental: ParentalControlsRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private fun capAge(): Int? = parental.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parental.profile.value.hideUnratedForAge
    private fun serverBase(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun serverCookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    /** Klenoty jedné země (ISO). Prázdné = backend nedostupný / země zatím nezbuildovaná (`/gems/rebuild`). */
    suspend fun catalog(countryIso: String, sort: String = "rank"): List<MediaItem> {
        val base = serverBase(); val cookie = serverCookie()
        if (base.isBlank()) return emptyList()
        val raw = runCatching { uploaderDs.gemsCatalog(base, cookie, countryIso, status = "all", sort = sort) }
            .onFailure { Log.w(TAG, "catalog $countryIso selhal", it) }.getOrNull() ?: return emptyList()
        val parsed = runCatching { parseItems(raw) }
            .onFailure { Log.w(TAG, "parse $countryIso selhal", it) }.getOrNull() ?: return emptyList()
        if (parsed.isEmpty()) return emptyList()
        val enriched = enricher.enrich(parsed, withCertification = capAge() != null)
        return ContentAgeGate.filter(capAge(), enriched, hideUnrated())
            .also { Log.i(TAG, "catalog $countryIso: ${it.size} klenotů") }
    }

    /** Odpověď `{items:[{imdb_id,tmdb_id,title,year,poster_path,original_title}]}` → [MediaItem] (dedup). */
    private fun parseItems(raw: String): List<MediaItem> {
        val items = JSONObject(raw).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<MediaItem>(items.length())
        val seen = HashSet<String>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val imdb = o.optString("imdb_id").takeIf { it.isNotBlank() }
            val tmdb = o.optLong("tmdb_id", 0L).takeIf { it > 0L }
            if (tmdb == null && imdb == null) continue
            val id = "tmdb:${tmdb ?: 0}|imdb:${imdb ?: ""}"
            if (!seen.add(id)) continue
            // Backend `poster_path` je PLNÁ URL (https://image.tmdb.org/t/p/w342/xxx.jpg); MediaItem.posterUrl
            // ale očekává FRAGMENT (/xxx.jpg) a prefix si přidá sám → z plné URL vytáhni fragment (jinak 2× prefix).
            val posterRaw = if (o.isNull("poster_path")) null else o.optString("poster_path").ifBlank { null }
            val posterFragment = posterRaw?.let { if (it.startsWith("http")) "/" + it.substringAfterLast('/') else it }
            out += MediaItem(
                traktId = 0L,
                tmdbId = tmdb,
                imdbId = imdb,
                title = o.optString("title", ""),
                year = o.optInt("year", 0).takeIf { it > 0 },
                overview = null,
                rating = null,
                genres = null,
                type = MediaType.MOVIE,
                posterPath = posterFragment,
                originalTitle = o.optString("original_title").ifBlank { null },
            )
        }
        return out
    }

    private companion object { const val TAG = "LAPIDARY_Loader" }
}
