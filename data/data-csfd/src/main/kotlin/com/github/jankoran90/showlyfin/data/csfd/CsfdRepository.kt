package com.github.jankoran90.showlyfin.data.csfd

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CsfdRepository @Inject constructor(
    private val scraper: CsfdScraper,
    @Named("csfdPreferences") private val prefs: SharedPreferences,
) {

    companion object {
        private const val TTL_PLOT_MS = 24 * 60 * 60 * 1000L
    }

    suspend fun getCsfdId(imdbId: String, tmdbId: Long? = null, title: String = "", year: Int = 0): Long? = withContext(Dispatchers.IO) {
        Timber.d("[CsfdRepository] getCsfdId(imdbId=$imdbId, tmdbId=$tmdbId, title=$title, year=$year)")
        if (imdbId.isBlank() && tmdbId == null && title.isBlank()) return@withContext null

        if (imdbId.isNotBlank()) {
            val override = prefs.getLong("CSFD_OVERRIDE_$imdbId", -1L)
            if (override != -1L) {
                Timber.d("[CsfdRepository] override hit: csfdId=$override")
                return@withContext override
            }
        }

        val cacheKey = when {
            imdbId.isNotBlank() -> "CSFD_ID_$imdbId"
            tmdbId != null -> "CSFD_ID_TMDB_$tmdbId"
            else -> "CSFD_ID_TITLE_${normalize(title)}_$year"
        }
        val cached = prefs.getLong(cacheKey, -1L)
        if (cached != -1L) {
            Timber.d("[CsfdRepository] cache hit: csfdId=$cached")
            return@withContext cached
        }

        // Wikidata P2529 (ČSFD id) je autoritativní mapování → důvěřujeme bez scrape-verify.
        // Dřívější verifyCsfdId (scrapeTitle != null) zbytečně zahazoval validní id, když scrape
        // momentálně selhal → ČSFD popis se vůbec nenačetl. Parity s funkčním yeshowly.
        if (imdbId.isNotBlank()) {
            val wikidataId = wikidataLookupByImdb(imdbId)
            Timber.d("[CsfdRepository] wikidata(imdb=$imdbId) → csfdId=$wikidataId")
            if (wikidataId != null) {
                prefs.edit { putLong(cacheKey, wikidataId) }
                return@withContext wikidataId
            }
        }

        if (tmdbId != null && tmdbId > 0) {
            val wikidataId = wikidataLookupByTmdb(tmdbId)
            Timber.d("[CsfdRepository] wikidata(tmdb=$tmdbId) → csfdId=$wikidataId")
            if (wikidataId != null) {
                prefs.edit { putLong(cacheKey, wikidataId) }
                return@withContext wikidataId
            }
        }

        if (title.isNotBlank()) {
            val searchId = runCatching { scraper.searchByTitle(title, year) }.getOrNull()
            if (searchId != null) {
                Timber.d("[CsfdRepository] title search('$title', $year) → csfdId=$searchId")
                prefs.edit { putLong(cacheKey, searchId) }
                return@withContext searchId
            }
        }

        Timber.w("[CsfdRepository] getCsfdId returned null for imdbId=$imdbId, tmdbId=$tmdbId, title=$title")
        null
    }

    suspend fun getCzechPlot(csfdId: Long): String? = withContext(Dispatchers.IO) {
        val cacheKey = "CSFD_PLOT_$csfdId"
        val cachedAt = prefs.getLong("CSFD_PLOT_AT_$csfdId", 0L)
        if (System.currentTimeMillis() - cachedAt < TTL_PLOT_MS) {
            val cached = prefs.getString(cacheKey, null)
            if (cached != null) return@withContext cached.ifBlank { null }
        }
        val plot = scraper.scrapePlot(csfdId)
        prefs.edit {
            putString(cacheKey, plot ?: "")
            putLong("CSFD_PLOT_AT_$csfdId", System.currentTimeMillis())
        }
        plot
    }

    /**
     * VISTA V3 — ČSFD hodnocení (0–100 %) pro řádek „Chci vidět". Resolve csfdId (Wikidata/cache) →
     * scrape hodnocení, vše s prefs cache (TTL jako plot). Volá se LÍNĚ jen pro viditelné řádky.
     * Vrací null, když se nepodařilo zjistit (necachuje se neúspěch → příště zkusí znovu).
     */
    suspend fun getRating(imdbId: String, tmdbId: Long? = null, title: String = "", year: Int = 0): Int? =
        withContext(Dispatchers.IO) {
            val csfdId = getCsfdId(imdbId, tmdbId, title, year) ?: return@withContext null
            getRatingById(csfdId)
        }

    suspend fun getRatingById(csfdId: Long): Int? = withContext(Dispatchers.IO) {
        val cacheKey = "CSFD_RATING_$csfdId"
        val cachedAt = prefs.getLong("CSFD_RATING_AT_$csfdId", 0L)
        if (System.currentTimeMillis() - cachedAt < TTL_PLOT_MS) {
            val cached = prefs.getInt(cacheKey, -1)
            if (cached >= 0) return@withContext cached
        }
        val rating = scraper.scrapeRating(csfdId)
        if (rating != null) {
            prefs.edit {
                putInt(cacheKey, rating)
                putLong("CSFD_RATING_AT_$csfdId", System.currentTimeMillis())
            }
        }
        rating
    }

    fun setOverrideCsfdId(imdbId: String, csfdId: Long) {
        prefs.edit {
            putLong("CSFD_OVERRIDE_$imdbId", csfdId)
            remove("CSFD_ID_$imdbId")
        }
    }

    fun clearOverrideCsfdId(imdbId: String) {
        prefs.edit {
            remove("CSFD_OVERRIDE_$imdbId")
            remove("CSFD_ID_$imdbId")
        }
    }

    fun clearAllCache() {
        val csfdKeys = prefs.all.keys.filter { it.startsWith("CSFD_") }
        prefs.edit { csfdKeys.forEach { remove(it) } }
    }

    private fun wikidataLookupByImdb(imdbId: String): Long? = wikidataSparql(
        """SELECT ?csfdId WHERE { ?item wdt:P345 "$imdbId". ?item wdt:P2529 ?csfdId }""",
        "imdb=$imdbId",
    )

    private fun wikidataLookupByTmdb(tmdbId: Long): Long? = wikidataSparql(
        """SELECT ?csfdId WHERE { ?item wdt:P4947 "$tmdbId". ?item wdt:P2529 ?csfdId }""",
        "tmdb=$tmdbId",
    )

    private fun wikidataSparql(query: String, debugLabel: String): Long? {
        return try {
            val url = "https://query.wikidata.org/sparql?query=${URLEncoder.encode(query, "UTF-8")}&format=json"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Showlyfin/0.13.4 (https://github.com/jankoran90/showlyfin)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseWikidataCsfdId(json)
        } catch (e: Exception) {
            Timber.w(e, "[CsfdRepository] wikidataSparql failed: $debugLabel")
            null
        }
    }

    internal fun parseWikidataCsfdId(json: String): Long? {
        return try {
            val obj = JSONObject(json)
            val bindings = obj.optJSONObject("results")?.optJSONArray("bindings") ?: return null
            for (i in 0 until bindings.length()) {
                val binding = bindings.optJSONObject(i) ?: continue
                val csfdField = binding.optJSONObject("csfdId") ?: continue
                val value = csfdField.optString("value", null) ?: continue
                value.toLongOrNull()?.let { return it }
            }
            null
        } catch (e: Exception) {
            Timber.w(e, "[CsfdRepository] parseWikidataCsfdId failed")
            null
        }
    }

    private fun normalize(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}
