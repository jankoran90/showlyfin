package com.github.jankoran90.showlyfin.data.csfd

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun getCsfdId(imdbId: String, title: String = "", year: Int = 0): Long? = withContext(Dispatchers.IO) {
        if (imdbId.isBlank() && title.isBlank()) return@withContext null

        val overrideKey = "CSFD_OVERRIDE_$imdbId"
        if (imdbId.isNotBlank()) {
            val override = prefs.getLong(overrideKey, -1L)
            if (override != -1L) return@withContext override
        }

        val cacheKey = if (imdbId.isNotBlank()) "CSFD_ID_$imdbId" else "CSFD_ID_TITLE_${normalize(title)}_$year"
        val cached = prefs.getLong(cacheKey, -1L)
        if (cached != -1L) return@withContext cached

        val wikidataId = if (imdbId.isNotBlank()) wikidataLookup(imdbId) else null

        if (wikidataId != null) {
            val csfdTitle = runCatching { scraper.scrapeTitle(wikidataId) }.getOrNull()
            if (csfdTitle != null) {
                prefs.edit { putLong(cacheKey, wikidataId) }
                return@withContext wikidataId
            } else {
                Timber.w("[CsfdRepository] Wikidata id=$wikidataId REJECTED (page inaccessible) for title=$title → title search")
            }
        }

        if (title.isNotBlank()) {
            val searchId = runCatching { scraper.searchByTitle(title, year) }.getOrNull()
            if (searchId != null) {
                prefs.edit { putLong(cacheKey, searchId) }
                return@withContext searchId
            }
        }

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

    private fun wikidataLookup(imdbId: String): Long? {
        return try {
            val query = """SELECT ?csfdId WHERE { ?item wdt:P345 "$imdbId". ?item wdt:P2529 ?csfdId }"""
            val url = "https://query.wikidata.org/sparql?query=${URLEncoder.encode(query, "UTF-8")}&format=json"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Showlyfin/0.13.1 (https://github.com/jankoran90/showlyfin)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Regex(""""value"\s*:\s*"(\d+)"""").find(json)?.groupValues?.get(1)?.toLongOrNull()
        } catch (e: Exception) {
            Timber.w(e, "[CsfdRepository] Wikidata lookup failed for imdbId=$imdbId")
            null
        }
    }

    private fun normalize(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}
