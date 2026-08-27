package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.PREF_ACTIVE_PROFILE_IS_CHILD
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
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
 * SPOTLIGHT (FLM-02) — „Novinky od tvůrců": nové tituly od sledovaných osob.
 *
 * Sledování samo o sobě nemá vlastní přenos — tvůrce se sleduje hvězdičkou v jeho filmografii,
 * což je běžná položka Oblíbených, a ta se na server synchronizuje (SUBSTRATE). Server jen jednou
 * týdně (v pátek) porovná filmografie a tady si vyzvedneme výsledek.
 */
@Singleton
class SpotlightRepository @Inject constructor(
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {

    /** Jeden nový titul od sledovaného tvůrce. */
    data class NewTitle(
        val tmdbId: Long,
        val isShow: Boolean,
        val title: String,
        val year: Int?,
        val posterUrl: String?,
        val rating: Float?,
        val personName: String,
        /** Role v 2. pádě pro větu „nový film od <role> <jméno>" (režiséra / herce …). */
        val personRole: String,
    )

    data class Status(val batchId: String?, val count: Int)

    /**
     * 🔴 Klíč profilu = **profileUuid** (`filmy-adult`/`filmy-kids`), NE `jellyfinUserId`. Oblíbené
     * (a tedy i sledovaní tvůrci) jdou delta syncem právě pod `profileUuid` — server si ho kanonizuje
     * (`routes/profiles.py` `_CANON_KEYS`). Kdybychom poslali JF id, sáhli bychom do jiného kbelíku
     * a novinky by byly navždy prázdné. Viz poznámka „appka posílá DVA různé klíče téhož profilu".
     */
    fun profileKey(): String = profileRepository.activeProfile.value?.profileUuid.orEmpty()

    /** Dětský profil = žádná upozornění (ukládat tvůrce ale smí). Pref píše `ProfileRepository.setActive`. */
    fun isChildProfile(): Boolean = prefs.getBoolean(PREF_ACTIVE_PROFILE_IS_CHILD, false)

    private fun base(): String = prefs.getString("uploader_base_url", "").orEmpty().trim().trimEnd('/')
    private fun cookie(): String = prefs.getString("uploader_session_cookie", "").orEmpty()

    /** Lehký dotaz pro periodickou kontrolu: ID týdenní dávky + kolik je novinek. */
    suspend fun status(profileKey: String = profileKey()): Status? =
        get("/spotlight/status", profileKey)?.let { json ->
            Status(
                batchId = json.optString("batchId").takeIf { it.isNotBlank() },
                count = json.optInt("count", 0),
            )
        }

    /** Novinky za aktuální týden. Prázdný seznam = opravdu nic nového; null = server neodpověděl. */
    suspend fun feed(profileKey: String = profileKey()): List<NewTitle>? {
        val json = get("/spotlight/feed", profileKey) ?: return null
        val arr = json.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("tmdbId", 0L)
            val title = o.optString("title").orEmpty()
            if (id <= 0L || title.isBlank()) return@mapNotNull null
            NewTitle(
                tmdbId = id,
                isShow = o.optString("mediaType") == "tv",
                title = title,
                year = o.optString("year").takeIf { it.isNotBlank() }?.toIntOrNull(),
                posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() },
                rating = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.toFloat(),
                personName = o.optString("personName").orEmpty(),
                personRole = o.optString("personRole").ifBlank { "tvůrce" },
            )
        }
    }

    private suspend fun get(path: String, profileKey: String): JSONObject? = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isBlank() || profileKey.isBlank()) return@withContext null
        runCatching {
            val url = "$base$path?profileKey=${URLEncoder.encode(profileKey, "UTF-8")}"
            val req = Request.Builder().url(url)
                .apply { if (cookie().isNotBlank()) header("Cookie", "session=${cookie()}") }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            }
        }.onFailure { Timber.w(it, "[SPOTLIGHT] $path selhalo") }.getOrNull()
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // první dotaz v týdnu server počítá (TMDB per tvůrce)
            .build()
    }
}
