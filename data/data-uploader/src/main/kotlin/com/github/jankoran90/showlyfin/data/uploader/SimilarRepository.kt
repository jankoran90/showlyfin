package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * SIMILAR (user 2026-08-27) — pruh „Podobné" pod kartou.
 *
 * Data staví backend (`/api/similar/{movie|show}/{imdb|tmdb}`): páteří je **Trakt related**, TMDB
 * `recommendations` je jen záchrana. Volba zdroje NENÍ dojem — user si vyžádal průzkum a ten
 * porovnal tři kandidáty na jeho titulech (Valhalla Rising, Taste of Tea, Kubo, Memento):
 * TMDB `similar` vracelo nesouvisející neznámé filmy, `recommendations` průměr, Trakt trefil
 * i zapadlé japonské drama a české filmy. Detail v handoffu.
 *
 * Server odpovědi cachuje (24 h) a vrací rovnou české názvy i plakáty, takže appka nic nedopočítává.
 */
@Singleton
class SimilarRepository @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {

    data class Title(
        val tmdbId: Long,
        val isShow: Boolean,
        val title: String,
        val year: Int?,
        val posterUrl: String?,
        val rating: Float?,
    )

    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trim().trimEnd('/')
    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()

    /**
     * Podobné tituly. [ident] = IMDb `tt…` (preferováno) nebo TMDB id — endpoint zvládne obojí.
     * null = nepodařilo se zeptat; prázdný seznam = server opravdu nic nenašel.
     */
    suspend fun similar(ident: String, isShow: Boolean, limit: Int = 12): List<Title>? =
        withContext(Dispatchers.IO) {
            val base = base()
            if (base.isBlank() || ident.isBlank()) return@withContext null
            val kind = if (isShow) "show" else "movie"
            runCatching {
                val url = "$base/api/similar/$kind/${URLEncoder.encode(ident, "UTF-8")}?limit=$limit"
                val req = Request.Builder().url(url)
                    .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: return@runCatching null
                    val arr = JSONObject(body).optJSONArray("items") ?: return@runCatching emptyList()
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val id = o.optLong("tmdbId", 0L)
                        val t = o.optString("title").orEmpty()
                        if (id <= 0L || t.isBlank()) return@mapNotNull null
                        Title(
                            tmdbId = id,
                            isShow = o.optString("mediaType", if (isShow) "tv" else "movie") == "tv",
                            title = t,
                            year = o.optInt("year", 0).takeIf { it > 0 },
                            posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() },
                            rating = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.toFloat(),
                        )
                    }
                }
            }.onFailure { Timber.w(it, "[SIMILAR] dotaz selhal") }.getOrNull()
        }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
