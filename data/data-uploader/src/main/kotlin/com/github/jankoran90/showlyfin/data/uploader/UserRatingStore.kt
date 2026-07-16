package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * BESPOKE (SHW-95) F3 — jedna položka vlastního hodnocení (1–10 hvězd). [stars] ≥8 = kladný signál
 * kurátorovi (loved), ≤4 = záporný (avoid + tvrdý skryt v „Pro tebe"). [traktId] pro zrcadlení do Traktu
 * (0 = neznámý → jen lokálně). Dedup dle tmdbId (fallback imdbId/traktId).
 */
data class UserRating(
    val tmdbId: Long? = null,
    val imdbId: String? = null,
    val traktId: Long = 0L,
    val type: String = "MOVIE",   // MOVIE | SHOW
    val title: String = "",
    val year: Int? = null,
    val stars: Int = 0,           // 1–10
    val ratedAtMs: Long = 0L,
)

/** Obálka serverového JSONu `{"ratings":[…]}` (endpoint /api/profiles/{key}/ratings). */
private data class RatingsEnvelope(val ratings: List<UserRating> = emptyList())

/**
 * BESPOKE (SHW-95) F3 — úložiště vlastních hodnocení 1–10 hvězd, **synchronizované přes backend** (per-profil,
 * sdílené TV↔telefon). Vzor [FavoritesStore] (server=pravda, union při shodě profilu, adopce při přepnutí,
 * offline chrání lokál). Primární zdroj signálu pro kurátora ([com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader.buildTaste])
 * — funguje i offline / bez Traktu; zrcadlení do Traktu řeší volající (RatingViewModel).
 */
@Singleton
class UserRatingStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val prefs = context.getSharedPreferences("user_ratings", Context.MODE_PRIVATE)
    private val storeKey = "ratings"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<UserRating>> = _items.asStateFlow()

    private var lastSyncedProfile: String?
        get() = appPrefs.getString(KEY_LAST_PROFILE, null)
        set(v) { appPrefs.edit().putString(KEY_LAST_PROFILE, v).apply() }

    init {
        scope.launch { syncFromServer() }
    }

    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty()
    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    /** Dedup klíč položky: tmdbId, fallback imdbId, fallback traktId. */
    private fun keyOf(r: UserRating): String = ratingKey(r.tmdbId, r.imdbId) ?: "trakt_${r.traktId}"

    private fun load(): List<UserRating> {
        val raw = prefs.getString(storeKey, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<UserRating>>(raw, object : TypeToken<List<UserRating>>() {}.type)
        }.onFailure { Timber.w(it, "[BESPOKE] ratings parse failed") }.getOrNull() ?: emptyList()
    }

    private fun persist(list: List<UserRating>) {
        _items.value = list
        prefs.edit().putString(storeKey, gson.toJson(list)).apply()
    }

    fun refresh() {
        scope.launch { syncFromServer() }
    }

    /** Hvězdy pro daný titul (1–10) nebo null. */
    fun ratingOf(tmdbId: Long?, imdbId: String?): Int? {
        val k = ratingKey(tmdbId, imdbId) ?: return null
        return _items.value.firstOrNull { ratingKey(it.tmdbId, it.imdbId) == k }?.stars
    }

    /** Ohodnoť titul (přepíše dřívější hodnocení téhož titulu). [stars] mimo 1–10 se ignoruje. */
    fun rate(rating: UserRating) {
        if (rating.stars !in 1..10) return
        val k = keyOf(rating.copy(ratedAtMs = 0))
        val without = _items.value.filterNot { keyOf(it) == k }
        persist(listOf(rating.copy(ratedAtMs = System.currentTimeMillis())) + without)
        pushToServer()
    }

    /** Zruš hodnocení titulu. */
    fun clear(tmdbId: Long?, imdbId: String?) {
        val k = ratingKey(tmdbId, imdbId) ?: return
        persist(_items.value.filterNot { ratingKey(it.tmdbId, it.imdbId) == k })
        pushToServer()
    }

    private suspend fun syncFromServer() {
        val key = profileKey(); val base = baseUrl(); val cookie = cookie()
        if (key.isBlank() || base.isBlank()) return
        runCatching {
            val server = parseServer(uploaderDs.getProfileRatings(base, cookie, key)) ?: return
            val prev = lastSyncedProfile
            if (prev != null && prev != key) {
                persist(server)
            } else {
                // UNION dle klíče, lokál (novější zápisy) má přednost při shodě.
                val seen = HashSet<String>()
                val merged = (_items.value + server).filter { seen.add(keyOf(it)) }
                persist(merged)
                if (merged.size != server.size) pushNow(key, base, cookie, merged)
            }
            lastSyncedProfile = key
        }.onFailure { Timber.w(it, "[BESPOKE] sync hodnocení selhal") }
    }

    private fun parseServer(raw: String?): List<UserRating>? {
        if (raw == null) return null
        return runCatching { gson.fromJson(raw, RatingsEnvelope::class.java)?.ratings ?: emptyList() }
            .onFailure { Timber.w(it, "[BESPOKE] parse server ratings") }.getOrNull()
    }

    private suspend fun pushNow(key: String, base: String, cookie: String, list: List<UserRating>) {
        runCatching {
            uploaderDs.putProfileRatings(base, cookie, key, gson.toJson(RatingsEnvelope(list)))
        }.onFailure { Timber.w(it, "[BESPOKE] push hodnocení selhal") }
    }

    private fun pushToServer() {
        val key = profileKey(); val base = baseUrl(); val cookie = cookie()
        if (key.isBlank() || base.isBlank()) return
        val snapshot = _items.value
        scope.launch { pushNow(key, base, cookie, snapshot) }
    }

    private companion object {
        const val KEY_LAST_PROFILE = "user_ratings_last_profile"
    }
}

/** Sdílený dedup/lookup klíč hodnocení: tmdbId, fallback imdbId. null = titul bez id. */
fun ratingKey(tmdbId: Long?, imdbId: String?): String? =
    tmdbId?.takeIf { it > 0L }?.let { "t$it" } ?: imdbId?.takeIf { it.isNotBlank() }?.let { "i$it" }
