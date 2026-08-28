package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * MUZA (SHW-123, user 2026-08-28 20:38) — tematické doporučení: řekneš téma, appka přes
 * `routes/muza.py` navrhne+ověří+okurátoruje tituly (technika SUMÁŘ: ČSFD+Trakt ohlasy).
 * Vzor přístupu = [SpotlightRepository] (prostý OkHttp+JSON, žádný retrofit servis).
 */
@Singleton
class MuzaRepository @Inject constructor(
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    data class TopicSummary(
        val id: String, val query: String, val createdAt: Double, val status: String, val count: Int,
    )

    data class TopicResult(
        val tmdbId: Long, val imdbId: String, val isShow: Boolean,
        val title: String, val year: Int, val posterUrl: String?, val blurb: String?,
    )

    data class TopicDetail(
        val id: String, val query: String, val status: String,
        val results: List<TopicResult>, val dropped: Int,
    )

    /** 🔴 profileUuid, NE jellyfinUserId — stejný důvod jako u [SpotlightRepository.profileKey]:
     * Oblíbené/fronta/„Chci vidět" (SUBSTRATE) jdou pod profileUuid, MUZA sdílí týž profilový kbelík
     * jen volně (vlastní store), ale pro konzistenci a budoucí případné propojení historie s profilem
     * používáme stejný klíč jako zbytek appky. */
    fun profileKey(): String = profileRepository.activeProfile.value?.profileUuid.orEmpty()

    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trim().trimEnd('/')
    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()

    suspend fun topics(profileKey: String = profileKey()): List<TopicSummary>? =
        withContext(Dispatchers.IO) {
            val base = base()
            if (base.isBlank() || profileKey.isBlank()) return@withContext null
            runCatching {
                val req = get("$base/api/muza/$profileKey/topics")
                val arr = org.json.JSONArray(req)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    TopicSummary(
                        id = id, query = o.optString("query"), createdAt = o.optDouble("createdAt", 0.0),
                        status = o.optString("status"), count = o.optInt("count", 0),
                    )
                }
            }.onFailure { Timber.w(it, "[MUZA] topics selhalo") }.getOrNull()
        }

    suspend fun topicDetail(topicId: String, profileKey: String = profileKey()): TopicDetail? =
        withContext(Dispatchers.IO) {
            val base = base()
            if (base.isBlank() || profileKey.isBlank() || topicId.isBlank()) return@withContext null
            runCatching {
                val json = JSONObject(get("$base/api/muza/$profileKey/topics/$topicId"))
                parseDetail(json)
            }.onFailure { Timber.w(it, "[MUZA] topicDetail selhalo") }.getOrNull()
        }

    /** Založí nové téma. Vrací topicId, nebo null při selhání (offline apod.). */
    suspend fun search(query: String, profileKey: String = profileKey()): String? =
        withContext(Dispatchers.IO) {
            val base = base()
            if (base.isBlank() || profileKey.isBlank() || query.isBlank()) return@withContext null
            runCatching {
                val body = JSONObject().put("query", query).toString()
                val json = JSONObject(post("$base/api/muza/$profileKey/search", body))
                json.optString("topicId").takeIf { it.isNotBlank() }
            }.onFailure { Timber.w(it, "[MUZA] search selhalo") }.getOrNull()
        }

    /** Navázání na existující téma — nové kolo se přidá k dosavadním výsledkům. */
    suspend fun continueTopic(topicId: String, profileKey: String = profileKey()): Boolean =
        withContext(Dispatchers.IO) {
            val base = base()
            if (base.isBlank() || profileKey.isBlank() || topicId.isBlank()) return@withContext false
            runCatching {
                post("$base/api/muza/$profileKey/topics/$topicId/continue", "")
                true
            }.onFailure { Timber.w(it, "[MUZA] continue selhalo") }.getOrDefault(false)
        }

    private fun parseDetail(json: JSONObject): TopicDetail {
        val arr = json.optJSONArray("results")
        val results: List<TopicResult> = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val tmdbId = o.optLong("tmdbId", 0L)
            if (tmdbId <= 0L) return@mapNotNull null
            val posterPath = o.optString("posterPath").takeIf { it.isNotBlank() }
            TopicResult(
                tmdbId = tmdbId, imdbId = o.optString("imdbId"),
                isShow = o.optString("kind") == "show",
                title = o.optString("title"), year = o.optInt("year", 0),
                posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                blurb = o.optString("blurb").takeIf { it.isNotBlank() },
            )
        }
        return TopicDetail(
            id = json.optString("id"), query = json.optString("query"),
            status = json.optString("status"), results = results, dropped = json.optInt("dropped", 0),
        )
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun post(url: String, jsonBody: String): String {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body)
            .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    companion object {
        // Hledání tématu běží přes minutu (brainstorm + N× kurátor na pozadí na serveru) — appka na
        // POST /search čeká jen chvilku (server odpoví hned, práce běží dál), ale GET topicDetail se
        // polluje opakovaně, proto stačí kratší timeout na jednotlivý dotaz.
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
